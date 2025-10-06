@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.common.SessionStore
import org.ntqqrev.acidify.util.UrlSignProvider
import org.ntqqrev.acidify.util.log.LogLevel
import org.ntqqrev.acidify.util.log.SimpleColoredLogHandler
import org.ntqqrev.cecilia.structs.CeciliaConfig
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main() = application {
    var bot by remember { mutableStateOf<Bot?>(null) }
    var loadingError by remember { mutableStateOf<String?>(null) }

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

                bot = Bot(
                    appInfo = appInfo,
                    sessionStore = sessionStore,
                    signProvider = signProvider,
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    minLogLevel = LogLevel.DEBUG,
                    logHandler = SimpleColoredLogHandler
                )
            } catch (e: Exception) {
                loadingError = e.message ?: "未知错误"
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
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
        
        App(bot = bot, loadingError = loadingError)
    }
}