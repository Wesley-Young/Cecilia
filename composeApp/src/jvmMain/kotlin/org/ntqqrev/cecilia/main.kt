@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.common.SessionStore
import org.ntqqrev.acidify.event.SessionStoreUpdatedEvent
import org.ntqqrev.acidify.util.UrlSignProvider
import org.ntqqrev.acidify.util.log.SimpleColoredLogHandler
import org.ntqqrev.cecilia.structs.CeciliaConfig
import org.ntqqrev.cecilia.utils.CacheManager
import org.ntqqrev.cecilia.utils.ConversationManager
import java.awt.Dimension
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main() = application {
    val configPath = Path("config.json")
    val config = CeciliaConfig.fromPath(configPath)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var bot by remember { mutableStateOf<Bot?>(null) }
    var cacheManager by remember { mutableStateOf<CacheManager?>(null) }
    var conversationManager by remember { mutableStateOf<ConversationManager?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userUin by remember { mutableStateOf(0L) }

    // 异步加载 Bot
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                val sessionStorePath = Path("session-store.json")
                val sessionStore: SessionStore = if (sessionStorePath.exists()) {
                    Json.decodeFromString(sessionStorePath.readText())
                } else {
                    val emptySessionStore = SessionStore.empty()
                    sessionStorePath.writeText(Json.encodeToString(emptySessionStore))
                    emptySessionStore
                }

                val signProvider = UrlSignProvider(config.signApiUrl, config.signApiHttpProxy)
                val appInfo = signProvider.getAppInfo()!!

                val newBot = Bot(
                    appInfo = appInfo,
                    sessionStore = sessionStore,
                    signProvider = signProvider,
                    scope = scope,
                    minLogLevel = config.minLogLevel,
                    logHandler = SimpleColoredLogHandler
                )

                // 监听 SessionStore 更新事件并保存
                newBot.launch {
                    newBot.eventFlow.collect { event ->
                        if (event is SessionStoreUpdatedEvent) {
                            try {
                                sessionStorePath.writeText(
                                    Json.encodeToString(SessionStore.serializer(), event.sessionStore)
                                )
                            } catch (e: Exception) {
                                // 忽略保存失败
                            }
                        }
                    }
                }

                bot = newBot
                // 创建缓存管理器
                val newCacheManager = CacheManager(newBot, scope)
                cacheManager = newCacheManager
                // 创建会话管理器（在 CacheManager 之后）
                conversationManager = ConversationManager(newBot, newCacheManager, scope)
                userUin = newBot.sessionStore.uin
                isLoggedIn = newBot.isLoggedIn
            } catch (e: Exception) {
                loadingError = e.message ?: e::class.qualifiedName
            }
        }
    }

    Window(
        onCloseRequest = {
            // 如果已登录，先调用 offline
            if (bot != null && isLoggedIn) {
                runBlocking {
                    try {
                        bot?.offline()
                    } catch (e: Exception) {
                        // 忽略 offline 错误，继续退出
                    }
                }
            }
            // 取消 scope
            scope.cancel()
            // 退出应用
            exitApplication()
        },
        title = "Cecilia",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp) * config.displayScale)
    ) {
        // 设置窗口最小尺寸
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 600)
        }

        // 动态更新窗口标题
        LaunchedEffect(bot, userUin, isLoggedIn) {
            val title = when {
                bot == null -> "Cecilia"
                userUin == 0L -> "Cecilia - 未登录"
                !isLoggedIn -> "Cecilia - $userUin (未登录)"
                else -> "Cecilia - $userUin"
            }
            window.title = title
        }

        // 通过调整 Density 来实现全局缩放（包括字体和布局）
        val scaleFactor = config.displayScale
        val originalDensity = LocalDensity.current
        val scaledDensity = Density(originalDensity.density * scaleFactor)

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            App(
                config = config,
                setConfig = { it.writeToPath(configPath) },
                bot = bot,
                cacheManager = cacheManager,
                conversationManager = conversationManager,
                loadingError = loadingError,
                onLoginStateChange = { loggedIn, uin ->
                    isLoggedIn = loggedIn
                    userUin = uin
                }
            )
        }
    }
}