@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

// import org.ntqqrev.cecilia.utils.AvatarCache
import androidx.compose.runtime.*
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
import org.ntqqrev.acidify.util.log.LogLevel
import org.ntqqrev.acidify.util.log.SimpleColoredLogHandler
import org.ntqqrev.cecilia.structs.CeciliaConfig
import java.awt.Dimension
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main() = application {
    // 配置头像缓存有效期（可选，默认 24 小时）
    // AvatarCache.setMaxAge(12.hours)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var bot by remember { mutableStateOf<Bot?>(null) }
    var cacheManager by remember { mutableStateOf<org.ntqqrev.cecilia.utils.CacheManager?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userUin by remember { mutableStateOf(0L) }

    // 异步加载 Bot
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                val configPath = Path("config.json")
                val config: CeciliaConfig = if (configPath.exists()) {
                    Json.decodeFromString(configPath.readText())
                } else {
                    val defaultConfig = CeciliaConfig()
                    configPath.writeText(Json.encodeToString(defaultConfig))
                    defaultConfig
                }

                val sessionStorePath = Path("session-store.json")
                val sessionStore: SessionStore = if (sessionStorePath.exists()) {
                    Json.decodeFromString(SessionStore.serializer(), sessionStorePath.readText())
                } else {
                    val emptySessionStore = SessionStore.empty()
                    sessionStorePath.writeText(Json.encodeToString(SessionStore.serializer(), emptySessionStore))
                    emptySessionStore
                }

                val signProvider = UrlSignProvider(config.signApiUrl)
                val appInfo = signProvider.getAppInfo()!!

                val newBot = Bot(
                    appInfo = appInfo,
                    sessionStore = sessionStore,
                    signProvider = signProvider,
                    scope = scope,
                    minLogLevel = LogLevel.DEBUG,
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
                cacheManager = org.ntqqrev.cecilia.utils.CacheManager(newBot, scope)
                userUin = newBot.sessionStore.uin
                isLoggedIn = newBot.isLoggedIn
            } catch (e: Exception) {
                loadingError = e.message ?: "未知错误"
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
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        )
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

        App(
            bot = bot,
            cacheManager = cacheManager,
            loadingError = loadingError,
            onLoginStateChange = { loggedIn, uin ->
                isLoggedIn = loggedIn
                userUin = uin
            }
        )
    }
}