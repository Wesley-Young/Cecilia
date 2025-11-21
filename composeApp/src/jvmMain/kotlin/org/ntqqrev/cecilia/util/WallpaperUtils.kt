package org.ntqqrev.cecilia.util

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object WallpaperProvider {
    fun get(): BufferedImage =
        getWallpaperWindows()
            ?: getWallpaperMac()
            ?: getWallpaperLinux()
            ?: getWallpaperFallback()

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

    private fun tryGnomeWallpaper(): String? {
        return try {
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
        } catch (e: Exception) {
            null
        }
    }

    private fun tryKdeWallpaper(): String? {
        return try {
            val config = File(
                System.getProperty("user.home"),
                ".config/plasma-org.kde.plasma.desktop-appletsrc"
            )
            if (!config.exists()) return null

            val line = config.readLines().firstOrNull {
                it.trim().startsWith("Image=")
            }

            line?.substringAfter("Image=")
        } catch (e: Exception) {
            null
        }
    }

    private fun getWallpaperFallback(): BufferedImage {
        return BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = Color(0, 0, 0)
            g.fillRect(0, 0, 64, 64)
            g.dispose()
        }
    }
}