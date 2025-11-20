@file:JvmName("Cecilia")

package org.ntqqrev.cecilia

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import org.ntqqrev.cecilia.view.App
import java.awt.Dimension
import java.io.PrintStream

fun main() {
    val utf8out = PrintStream(System.out, true, "UTF-8")
    System.setOut(utf8out)
    appMain()
}

fun appMain() = application {
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
        FluentTheme {
            App()
        }
    }
}
