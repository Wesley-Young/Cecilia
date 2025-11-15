@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.common.AppInfo
import org.ntqqrev.acidify.common.SessionStore
import org.ntqqrev.acidify.common.UrlSignProvider
import org.ntqqrev.acidify.event.SessionStoreUpdatedEvent
import org.ntqqrev.cecilia.components.SignApiSetupDialog
import org.ntqqrev.cecilia.structs.CeciliaConfig
import org.ntqqrev.cecilia.utils.ConversationManager
import org.ntqqrev.cecilia.utils.getAppDataDirectory
import java.awt.Dimension
import java.io.PrintStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main() {
    val utf8out = PrintStream(System.out, true, "UTF-8")
    System.setOut(utf8out)
    appMain()
}

fun appMain() = application {
    val appDataDirectory = remember { getAppDataDirectory() }
    val configPath = appDataDirectory.resolve("config.json")
    var isConfigInitialized by remember { mutableStateOf(configPath.exists()) }
    var config by remember {
        mutableStateOf(
            if (isConfigInitialized) CeciliaConfig.fromPath(configPath) else CeciliaConfig()
        )
    }
    val scope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    var bot by remember { mutableStateOf<Bot?>(null) }
    var conversationManager by remember { mutableStateOf<ConversationManager?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userUin by remember { mutableStateOf(0L) }
    val httpClient = remember { HttpClient() }
    var isCommandPaletteVisible by remember { mutableStateOf(false) }

    // 在完成配置前不要初始化 Bot
    LaunchedEffect(isConfigInitialized) {
        if (!isConfigInitialized) return@LaunchedEffect

        launch(Dispatchers.IO) {
            try {
                val sessionStorePath = appDataDirectory.resolve("session-store.json")
                val sessionStore: SessionStore = if (sessionStorePath.exists()) {
                    Json.decodeFromString(sessionStorePath.readText())
                } else {
                    val emptySessionStore = SessionStore.empty()
                    sessionStorePath.writeText(Json.encodeToString(emptySessionStore))
                    emptySessionStore
                }

                val signProvider = UrlSignProvider(config.signApiUrl, config.signApiHttpProxy)
                val appInfo = signProvider.getAppInfo() ?: run {
                    println("获取 AppInfo 失败，使用内置默认值")
                    AppInfo.Bundled.Linux
                }

                val newBot = Bot.create(
                    appInfo = appInfo,
                    sessionStore = sessionStore,
                    signProvider = signProvider,
                    scope = scope,
                    minLogLevel = config.minLogLevel,
                    logHandler = { level, tag, message, throwable ->
                        println("[$level] [$tag] $message")
                        throwable?.printStackTrace()
                    }
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
                // 创建会话管理器
                conversationManager = ConversationManager(newBot, scope)
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
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp) * config.displayScale),
        onKeyEvent = { event: KeyEvent ->
            if (!isCommandPaletteVisible &&
                event.type == KeyEventType.KeyDown &&
                event.isShiftPressed &&
                (event.isMetaPressed || event.isCtrlPressed) &&
                event.key == Key.P
            ) {
                isCommandPaletteVisible = true
                true
            } else {
                false
            }
        }
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
                setConfig = {
                    config = it
                    it.writeToPath(configPath)
                },
                bot = bot,
                httpClient = httpClient,
                conversationManager = conversationManager,
                loadingError = loadingError,
                onLoginStateChange = { loggedIn, uin ->
                    isLoggedIn = loggedIn
                    userUin = uin
                },
                showCommandPalette = isCommandPaletteVisible,
                onCommandPaletteDismiss = { isCommandPaletteVisible = false }
            )
        }

        if (!isConfigInitialized) {
            MaterialTheme(
                colors = ThemeType.GREEN.colorScheme,
                typography = Typography(defaultFontFamily = NotoFontFamily)
            ) {
                SignApiSetupDialog(
                    initialSignApiUrl = config.signApiUrl,
                    initialSignApiHttpProxy = config.signApiHttpProxy,
                    onConfirm = { newUrl, newProxy ->
                        val updatedConfig = config.copy(
                            signApiUrl = newUrl,
                            signApiHttpProxy = newProxy
                        )
                        config = updatedConfig
                        updatedConfig.writeToPath(configPath)
                        isConfigInitialized = true
                    }
                )
            }
        }
    }
}
