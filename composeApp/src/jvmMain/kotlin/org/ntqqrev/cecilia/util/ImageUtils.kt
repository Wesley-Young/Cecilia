package org.ntqqrev.cecilia.util

import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp

fun BufferedImage.desaturate(amount: Float): BufferedImage {
    val grayOp = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
    val gray = grayOp.filter(this, null)

    val result = BufferedImage(width, height, type)

    for (y in 0 until height) {
        for (x in 0 until width) {

            val rgb1 = getRGB(x, y)
            val rgb2 = gray.getRGB(x, y)

            val r = ((rgb1 shr 16 and 0xFF) * (1 - amount) +
                    (rgb2 shr 16 and 0xFF) * amount).toInt()

            val g = ((rgb1 shr 8 and 0xFF) * (1 - amount) +
                    (rgb2 shr 8 and 0xFF) * amount).toInt()

            val b = ((rgb1 and 0xFF) * (1 - amount) +
                    (rgb2 and 0xFF) * amount).toInt()

            val rgb = (r shl 16) or (g shl 8) or b
            result.setRGB(x, y, rgb)
        }
    }

    return result
}

fun Pair<Int, Int>.coerceInRectBox(maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    val (width, height) = this
    if (width <= maxWidth && height <= maxHeight) {
        return this
    }
    val aspectRatio = width.toFloat() / height.toFloat()
    val boxAspectRatio = maxWidth.toFloat() / maxHeight.toFloat()
    return if (aspectRatio >= boxAspectRatio) {
        // limited by width
        val newWidth = maxWidth
        val newHeight = (maxWidth / aspectRatio).toInt()
        Pair(newWidth, newHeight)
    } else {
        // limited by height
        val newHeight = maxHeight
        val newWidth = (maxHeight * aspectRatio).toInt()
        Pair(newWidth, newHeight)
    }
}

fun Pair<Int, Int>.coerceInSquareBox(size: Int) =
    coerceInRectBox(size, size)