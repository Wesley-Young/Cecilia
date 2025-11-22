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