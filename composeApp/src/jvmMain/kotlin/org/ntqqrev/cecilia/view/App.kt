package org.ntqqrev.cecilia.view

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.util.getAppDataDirectory
import kotlin.io.path.div
import kotlin.io.path.exists

@Composable
@Preview
fun App() {
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
    val bot = remember { mutableStateOf<Bot?>(null) }

    val scaleFactor = config.displayScale
    val originalDensity = LocalDensity.current
    val scaledDensity = Density(originalDensity.density * scaleFactor)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity
    ) {
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
            showRestartReminder = isConfigRefining,
        )
    }
}