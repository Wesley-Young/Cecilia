package org.ntqqrev.cecilia.model

import androidx.compose.ui.graphics.ImageBitmap
import org.ntqqrev.acidify.message.ImageFormat

class ImageAttachment(
    val bytes: ByteArray,
    val bitmap: ImageBitmap,
    val format: ImageFormat,
)
