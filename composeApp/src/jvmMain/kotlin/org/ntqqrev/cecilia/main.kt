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
import io.github.composefluent.FluentTheme
import io.ktor.client.*
import kotlinx.coroutines.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.common.AppInfo
import org.ntqqrev.acidify.common.SessionStore
import org.ntqqrev.acidify.common.UrlSignProvider
import org.ntqqrev.acidify.event.SessionStoreUpdatedEvent
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.core.LocalHttpClient
import org.ntqqrev.cecilia.util.getAppDataDirectory
import org.ntqqrev.cecilia.view.App
import org.ntqqrev.cecilia.view.ConfigInitDialog
import java.awt.Dimension
import java.io.PrintStream
import kotlin.io.path.div
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
    val configPath = appDataDirectory / "config.json"
    var isConfigInitialized by remember { mutableStateOf(configPath.exists()) }
    var isConfigRefining by remember { mutableStateOf(false) }
    var config by remember {
        mutableStateOf(
            if (isConfigInitialized) Config.fromPath(configPath) else Config()
        )
    }

    val scope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    var bot by remember { mutableStateOf<Bot?>(null) }

    val httpClient = remember { HttpClient() }

    val scaleFactor = config.displayScale
    val originalDensity = LocalDensity.current
    val scaledDensity = Density(originalDensity.density * scaleFactor)

    LaunchedEffect(isConfigInitialized) {
        if (!isConfigInitialized) {
            return@LaunchedEffect
        }

        try {
            val sessionStorePath = appDataDirectory.resolve("session-store.json")
            val sessionStore: SessionStore = if (sessionStorePath.exists()) {
                SessionStore.fromJson(sessionStorePath.readText())
            } else {
                val emptySessionStore = SessionStore.empty()
                emptySessionStore.also { store ->
                    sessionStorePath.writeText(store.toJson())
                }
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
                        runCatching {
                            sessionStorePath.writeText(event.sessionStore.toJson())
                        }
                    }
                }
            }

            bot = newBot
        } catch (e: Exception) {
            // loadingError = e.message ?: e::class.qualifiedName
        }
    }

    Window(
        onCloseRequest = {
            if (bot?.isLoggedIn ?: false) {
                runCatching {
                    runBlocking { bot?.offline() }
                }
            }
            exitApplication()
        },
        title = "Cecilia",
        state = rememberWindowState(size = DpSize(1000.dp, 750.dp)),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 600)
        }

        LaunchedEffect(bot) {
            val title = if (bot == null) {
                "Cecilia"
            } else {
                "Cecilia - ${bot?.sessionStore?.uin}"
            }
            window.title = title
        }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalHttpClient provides httpClient,
        ) {
            FluentTheme {
                ConfigInitDialog(
                    visible = isConfigRefining || !isConfigInitialized,
                    initialSignApiHttpProxy = config.signApiHttpProxy,
                    initialSignApiUrl = config.signApiUrl,
                    onConfirm = { signApiUrl, signApiHttpProxy ->
                        config = config.copy(
                            signApiUrl = signApiUrl,
                            signApiHttpProxy = signApiHttpProxy
                        )
                        config.writeToPath(configPath)
                        isConfigInitialized = true
                        if (isConfigRefining) {
                            Runtime.getRuntime().exit(0)
                        }
                    },
                    onDismissRequest = {
                        isConfigRefining = false
                    },
                    isRefining = isConfigRefining,
                )
                App(
                    config = config,
                    bot = bot,
                )
            }
        }
    }
}
