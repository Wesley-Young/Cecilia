package org.ntqqrev.cecilia.util

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.div

object AppDataDirectoryProvider {
    val value by lazy {
        val osName = System.getProperty("os.name")?.lowercase().orEmpty()
        val userHome = System.getProperty("user.home").orEmpty().ifBlank { "." }

        val baseDirectory = when {
            osName.contains("mac") -> Path(userHome) / "Library" / "Application Support" / "Cecilia"
            osName.contains("win") -> getWindowsAppDataDirectory(userHome)
            else -> getLinuxAppDataDirectory(userHome)
        }

        baseDirectory.also { createDirectories(it) }
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
}

object WallpaperProvider {
    val value by lazy {
        getWallpaperWindows()
            ?: getWallpaperMac()
            ?: getWallpaperLinux()
            ?: getWallpaperFallback()
    }

    private fun getWallpaperWindows(): BufferedImage? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return null

        return try {
            val process = ProcessBuilder(
                "reg", "query",
                "HKCU\\Control Panel\\Desktop",
                "/v", "WallPaper"
            ).start()

            val output = process.inputStream.bufferedReader().readText()

            val regex = Regex("WallPaper\\s+REG_SZ\\s+(.*)")
            val path = regex.find(output)?.groupValues?.getOrNull(1)?.trim()

            if (path.isNullOrEmpty()) null else ImageIO.read(File(path))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getWallpaperMac(): BufferedImage? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return null

        return try {
            val process = ProcessBuilder(
                "osascript", "-e",
                "tell application \"System Events\" to get picture of current desktop"
            ).start()

            val path = process.inputStream.bufferedReader().readText().trim()
            if (path.isEmpty()) null else ImageIO.read(File(path))

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getWallpaperLinux(): BufferedImage? {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("linux")) return null

        val path =
            tryGnomeWallpaper()
                ?: tryKdeWallpaper()
                ?: return null

        return try {
            ImageIO.read(File(path))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun tryGnomeWallpaper() = runCatching {
        val process = ProcessBuilder(
            "gsettings", "get",
            "org.gnome.desktop.background",
            "picture-uri"
        ).start()

        val raw = process.inputStream.bufferedReader().readText().trim()
        raw
            .removePrefix("'")
            .removeSuffix("'")
            .removePrefix("file://")
    }.getOrNull()

    private fun tryKdeWallpaper() = runCatching {
        val config = File(
            System.getProperty("user.home"),
            ".config/plasma-org.kde.plasma.desktop-appletsrc"
        )
        if (!config.exists()) return null

        val line = config.readLines().firstOrNull {
            it.trim().startsWith("Image=")
        }

        line?.substringAfter("Image=")
    }.getOrNull()

    private fun getWallpaperFallback(): BufferedImage {
        return BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = Color(0, 0, 0)
            g.fillRect(0, 0, 64, 64)
            g.dispose()
        }
    }
}