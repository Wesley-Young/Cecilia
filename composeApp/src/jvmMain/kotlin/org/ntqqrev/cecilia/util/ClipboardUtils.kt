package org.ntqqrev.cecilia.util

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.ntqqrev.acidify.message.ImageFormat
import org.ntqqrev.cecilia.model.ImageAttachment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

fun BufferedImage.toPngByteArray(): ByteArray {
    val out = ByteArrayOutputStream()
    ImageIO.write(this, ImageFormat.PNG.ext, out)
    return out.toByteArray()
}

fun MutableList<ImageAttachment>.tryPasteImages(): Boolean {
    val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        ?: return false
    val contents = clipboard.getContents(null) ?: return false
    val imageStreamFlavors = listOf(
        DataFlavor("image/png; class=java.io.InputStream"),
        DataFlavor("image/tiff; class=java.io.InputStream"),
        DataFlavor("image/jpeg; class=java.io.InputStream"),
        DataFlavor("image/bmp; class=java.io.InputStream"),
    )

    imageStreamFlavors.firstOrNull { contents.isDataFlavorSupported(it) }?.let { flavor ->
        val stream = runCatching {
            contents.getTransferData(flavor) as? InputStream
        }.getOrNull()

        stream?.use {
            val image = ImageIO.read(it)
            if (image != null) {
                val bytes = image.toPngByteArray()
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                add(
                    ImageAttachment(
                        bytes = bytes,
                        bitmap = bitmap,
                        format = ImageFormat.PNG,
                    )
                )
                return true
            }
        }
    }

    if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
        val awtImage = runCatching {
            contents.getTransferData(DataFlavor.imageFlavor)
        }.getOrNull() ?: return false
        val buffered = when (awtImage) {
            is BufferedImage -> awtImage
            is MultiResolutionImage -> {
                val best = awtImage.resolutionVariants.maxByOrNull { variant ->
                    variant.getWidth(null) * variant.getHeight(null)
                } ?: return false
                val b = BufferedImage(
                    best.getWidth(null),
                    best.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB
                )
                val g = b.createGraphics()
                g.drawImage(best, 0, 0, null)
                g.dispose()
                b
            }
            is java.awt.Image -> {
                val b = BufferedImage(
                    awtImage.getWidth(null),
                    awtImage.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB
                )
                val g = b.createGraphics()
                g.drawImage(awtImage, 0, 0, null)
                g.dispose()
                b
            }

            else -> return false
        }
        val bytes = buffered.toPngByteArray()
        val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
        add(
            ImageAttachment(
                bytes = bytes,
                bitmap = bitmap,
                format = ImageFormat.PNG,
            )
        )
        return true
    }

    if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        val files = runCatching {
            contents.getTransferData(DataFlavor.javaFileListFlavor)
        }.getOrNull() as? List<*>

        if (!files.isNullOrEmpty()) {
            var added = false
            files.forEach { anyFile ->
                val file = anyFile as? java.io.File ?: return@forEach
                val image = ImageIO.read(file) ?: return@forEach
                val baos = ByteArrayOutputStream()
                val ext = file.extension.lowercase()
                val format = ImageFormat.fromExtension(ext) ?: ImageFormat.PNG
                ImageIO.write(image, format.ext, baos)
                val bytes = baos.toByteArray()
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                add(
                    ImageAttachment(
                        bytes = bytes,
                        bitmap = bitmap,
                        format = format,
                    )
                )
                added = true
            }
            if (added) return true
        }
    }

    return false
}
