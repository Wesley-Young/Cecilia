@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import kotlinx.coroutines.runBlocking
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.util.getAppDataDirectory
import org.ntqqrev.cecilia.view.App
import org.ntqqrev.cecilia.view.ConfigInitDialog
import java.awt.Dimension
import java.io.PrintStream
import kotlin.io.path.div
import kotlin.io.path.exists

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
    val scope = rememberCoroutineScope()
    var bot by remember { mutableStateOf<Bot?>(null) }

    val scaleFactor = config.displayScale
    val originalDensity = LocalDensity.current
    val scaledDensity = Density(originalDensity.density * scaleFactor)

    Window(
        onCloseRequest = {
            runBlocking { bot?.offline() }
            exitApplication()
        },
        title = "Cecilia",
        state = rememberWindowState(size = DpSize(1000.dp, 750.dp)),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 600)
        }
        CompositionLocalProvider(
            LocalDensity provides scaledDensity
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
