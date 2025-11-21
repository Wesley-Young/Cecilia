@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Settings
import io.ktor.client.*
import kotlinx.coroutines.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.common.AppInfo
import org.ntqqrev.acidify.common.SessionStore
import org.ntqqrev.acidify.common.UrlSignProvider
import org.ntqqrev.acidify.event.SessionStoreUpdatedEvent
import org.ntqqrev.acidify.logging.SimpleLogHandler
import org.ntqqrev.cecilia.component.ConfigInitDialog
import org.ntqqrev.cecilia.core.*
import org.ntqqrev.cecilia.util.WallpaperProvider
import org.ntqqrev.cecilia.util.getAppDataDirectory
import org.ntqqrev.cecilia.view.LoginView
import org.ntqqrev.cecilia.view.MainView
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
    var loadError by remember { mutableStateOf<Throwable?>(null) }

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
    val avatarCache = remember { AvatarCache() }

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
                logHandler = SimpleLogHandler
            )

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
            loadError = e
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
            LocalConfig provides config,
            LocalConfigSetter provides { newConfig ->
                config = newConfig
                config.writeToPath(configPath)
            },
            LocalAvatarCache provides avatarCache,
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
                    bot = bot,
                    loadError = loadError,
                    showConfigInitDialog = {
                        isConfigRefining = true
                    }
                )
            }
        }
    }
}

@Composable
fun App(
    bot: Bot?,
    loadError: Throwable?,
    showConfigInitDialog: () -> Unit
) {
    val screenshot = remember { WallpaperProvider.get().toComposeImageBitmap() }
    var isLoggedIn by remember { mutableStateOf(bot?.isLoggedIn ?: false) }

    Mica(
        modifier = Modifier.fillMaxSize(),
        background = {
            Image(
                bitmap = screenshot,
                contentDescription = null,
            )
        }
    ) {
        if (bot == null) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProgressRing()
                        if (loadError == null) {
                            Text("正在初始化")
                        } else {
                            Text(
                                "初始化失败：${loadError.localizedMessage}\n" +
                                        "请检查配置文件，确保指定了有效的签名地址。"
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Button(
                        iconOnly = true,
                        onClick = showConfigInitDialog
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            }
        } else {
            CompositionLocalProvider(
                LocalBot provides bot
            ) {
                if (!isLoggedIn) {
                    LoginView(
                        onLoggedIn = { isLoggedIn = true },
                        showConfigInitDialog = showConfigInitDialog
                    )
                } else {
                    MainView()
                }
            }
        }
    }
}