package org.ntqqrev.cecilia.struct

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.ntqqrev.acidify.message.ImageFormat
import org.ntqqrev.acidify.message.ImageSubType
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import java.awt.Image as AwtImage

data class OutgoingImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val raw: ByteArray,
    val format: ImageFormat,
    val width: Int,
    val height: Int,
    val preview: ImageBitmap,
    val summary: String = "[图片]",
    val subType: ImageSubType = ImageSubType.NORMAL
) {
    companion object {
        fun fromBytes(
            raw: ByteArray,
            formatOverride: ImageFormat? = null,
            widthOverride: Int? = null,
            heightOverride: Int? = null,
            summary: String = "[图片]",
            subType: ImageSubType = ImageSubType.NORMAL
        ): OutgoingImageAttachment? {
            val (format, width, height) = if (formatOverride != null && widthOverride != null && heightOverride != null) {
                Triple(formatOverride, widthOverride, heightOverride)
            } else {
                detectMeta(raw) ?: return null
            }

            val imageBitmap = runCatching {
                Image.makeFromEncoded(raw).toComposeImageBitmap()
            }.getOrElse {
                return null
            }

            return OutgoingImageAttachment(
                raw = raw,
                format = format,
                width = width,
                height = height,
                preview = imageBitmap,
                summary = summary,
                subType = subType
            )
        }

        fun fromFile(path: Path): OutgoingImageAttachment? {
            val bytes = runCatching { Files.readAllBytes(path) }.getOrElse { return null }
            return fromBytes(bytes)
        }

        fun fromBufferedImage(
            image: BufferedImage,
            summary: String = "[图片]",
            subType: ImageSubType = ImageSubType.NORMAL
        ): OutgoingImageAttachment? {
            val output = ByteArrayOutputStream()
            return runCatching {
                val writer = ImageIO.getImageWritersByFormatName("png").asSequence().firstOrNull()
                    ?: return@runCatching null
                MemoryCacheImageOutputStream(output).use { stream ->
                    writer.output = stream
                    val param = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_DEFAULT
                    }
                    writer.write(null, IIOImage(image, null, null), param)
                }
                fromBytes(
                    raw = output.toByteArray(),
                    formatOverride = ImageFormat.PNG,
                    widthOverride = image.width,
                    heightOverride = image.height,
                    summary = summary,
                    subType = subType
                )
            }.getOrNull()
        }

        fun fromAwtImage(
            image: AwtImage,
            summary: String = "[剪贴板图片]",
            subType: ImageSubType = ImageSubType.NORMAL
        ): OutgoingImageAttachment? {
            val resolved = when (image) {
                is MultiResolutionImage -> {
                    image.resolutionVariants
                        .maxByOrNull { variant ->
                            (variant.getWidth(null).coerceAtLeast(1) *
                                    variant.getHeight(null).coerceAtLeast(1))
                        } ?: image
                }

                else -> image
            }

            val buffered = if (resolved is BufferedImage) {
                resolved
            } else {
                val bufferedImage = BufferedImage(
                    resolved.getWidth(null).coerceAtLeast(1),
                    resolved.getHeight(null).coerceAtLeast(1),
                    BufferedImage.TYPE_INT_ARGB
                )
                val g2d = bufferedImage.createGraphics()
                g2d.drawImage(resolved, 0, 0, null)
                g2d.dispose()
                bufferedImage
            }
            return fromBufferedImage(buffered, summary, subType)
        }

        private fun detectMeta(raw: ByteArray): Triple<ImageFormat, Int, Int>? {
            return ImageIO.createImageInputStream(ByteArrayInputStream(raw))?.use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return null
                val reader = readers.next()
                return@use runCatching {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    val formatName = reader.formatName
                    val format = formatName.toImageFormat() ?: return@runCatching null
                    Triple(format, width, height)
                }.getOrNull().also { reader.dispose() }
            }
        }

        private fun String.toImageFormat(): ImageFormat? {
            val normalized = when (lowercase()) {
                "jpeg" -> "jpg"
                else -> lowercase()
            }
            return ImageFormat.entries.firstOrNull { it.ext.equals(normalized, ignoreCase = true) }
        }
    }
}
