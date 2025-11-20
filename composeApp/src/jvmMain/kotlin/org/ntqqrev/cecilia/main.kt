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
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.util.getAppDataDirectory
import org.ntqqrev.cecilia.view.App
import java.awt.Dimension
import java.io.PrintStream
import kotlin.io.path.exists

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
            if (isConfigInitialized) Config.fromPath(configPath) else Config()
        )
    }

    Window(
        onCloseRequest = {
            exitApplication()
        },
        title = "Cecilia",
        state = rememberWindowState(size = DpSize(1000.dp, 750.dp)),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(800, 600)
        }
        val scaleFactor = config.displayScale
        val originalDensity = LocalDensity.current
        val scaledDensity = Density(originalDensity.density * scaleFactor)

        CompositionLocalProvider(
            LocalDensity provides scaledDensity
        ) {
            FluentTheme {
                App()
            }
        }
    }
}
