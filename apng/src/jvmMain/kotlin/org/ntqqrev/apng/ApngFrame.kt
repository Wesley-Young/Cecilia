package org.ntqqrev.apng

import java.awt.image.BufferedImage

data class ApngFrame(
    val image: BufferedImage,
    val delayMillis: Long,
    val width: Int,
    val height: Int,
    val xOffset: Int,
    val yOffset: Int,
    val disposeOp: Int,
    val blendOp: Int
)
