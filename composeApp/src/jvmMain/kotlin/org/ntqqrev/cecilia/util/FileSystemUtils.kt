package org.ntqqrev.cecilia.util

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

fun getAppDataDirectory(): Path {
    val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }

    val baseDirectory = when {
        osName.contains("mac") -> Path(userHome) / "Library" / "Application Support" / "Cecilia"
        osName.contains("win") -> getWindowsAppDataDirectory(userHome)
        else -> getLinuxAppDataDirectory(userHome)
    }

    baseDirectory.createDirectories()
    return baseDirectory
}

private fun getWindowsAppDataDirectory(userHome: String): Path {
    val appDataEnv = System.getenv("APPDATA")
    val appDataRoot = if (appDataEnv.isNullOrBlank()) {
        Path(userHome) / "AppData" / "Roaming"
    } else {
        Path(appDataEnv)
    }
    return appDataRoot / "Cecilia"
}

private fun getLinuxAppDataDirectory(userHome: String): Path {
    val xdgDataHome = System.getenv("XDG_DATA_HOME")
    val dataRoot = if (xdgDataHome.isNullOrBlank()) {
        Path(userHome) / ".local" / "share"
    } else {
        Path(xdgDataHome)
    }
    return dataRoot / "cecilia"
}