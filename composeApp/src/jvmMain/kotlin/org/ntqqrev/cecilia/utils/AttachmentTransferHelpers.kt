package org.ntqqrev.cecilia.utils

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths

private val supportedExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "heic", "heif")
internal fun Transferable.hasSupportedImagePayload(): Boolean {
    if (extractImageFilePaths().isNotEmpty()) {
        return true
    }
    if (isDataFlavorSupported(DataFlavor.imageFlavor)) {
        return true
    }
    return false
}

internal fun Transferable.extractImageFilePaths(): List<Path> {
    val paths = mutableListOf<Path>()

    if (isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = runCatching {
            getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        }.getOrNull()
        files?.forEach { file ->
            val realFile = (file as? File) ?: return@forEach
            if (realFile.isFile && realFile.extensionIsImage()) {
                paths.add(realFile.toPath())
            }
        }
    }

    uriListFlavor()?.let { flavor ->
        if (isDataFlavorSupported(flavor)) {
            val uriData = runCatching { getTransferData(flavor) as? String }.getOrNull()
            uriData?.let {
                it.lineSequence()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
                    .forEach { entry ->
                        val uri = runCatching { URI(entry) }.getOrNull()
                        val path = uri?.let { Paths.get(it) }
                        if (path != null && path.toFile().isFile && path.fileName.toString().extensionIsImage()) {
                            paths.add(path)
                        }
                    }
            }
        }
    }

    if (paths.isEmpty() && isDataFlavorSupported(DataFlavor.stringFlavor)) {
        val rawText = runCatching { getTransferData(DataFlavor.stringFlavor) as? String }.getOrNull()
        rawText?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { candidate ->
                val cleaned = candidate.removePrefix("file://")
                val file = File(cleaned)
                if (file.isFile && file.extensionIsImage()) {
                    paths.add(file.toPath())
                }
            }
    }

    return paths.distinct()
}

internal fun Transferable.extractImageFromClipboard(): Image? {
    if (isDataFlavorSupported(DataFlavor.imageFlavor)) {
        return runCatching { getTransferData(DataFlavor.imageFlavor) as? Image }.getOrNull()
    }
    return null
}

internal data class ClipboardImageBytes(
    val bytes: ByteArray,
    val formatHint: String?
)

internal fun Transferable.extractBinaryImages(): List<ClipboardImageBytes> {
    val results = mutableListOf<ClipboardImageBytes>()
    val flavors = transferDataFlavors ?: return results

    for (flavor in flavors) {
        val primaryType = flavor.primaryType?.lowercase() ?: continue
        if (primaryType != "image") continue

        val data = runCatching { getTransferData(flavor) }.getOrNull() ?: continue
        val bytes = when (data) {
            is ByteArray -> data
            is InputStream -> data.use { it.readBytes() }
            is ByteBuffer -> {
                val buffer = data.slice()
                val arr = ByteArray(buffer.remaining())
                buffer.get(arr)
                arr
            }

            else -> null
        } ?: continue

        results += ClipboardImageBytes(bytes = bytes, formatHint = flavor.subType)
    }

    return results
}

private fun uriListFlavor(): DataFlavor? = runCatching {
    DataFlavor("text/uri-list;class=java.lang.String")
}.getOrNull()

private fun File.extensionIsImage(): Boolean = name.substringAfterLast('.', "").lowercase() in supportedExtensions

private fun String.extensionIsImage(): Boolean = substringAfterLast('.', "").lowercase() in supportedExtensions
