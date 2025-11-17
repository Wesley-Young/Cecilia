package org.ntqqrev.cecilia.struct

import androidx.compose.ui.graphics.ImageBitmap
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.ImageSubType

sealed class DisplaySegment {
    data class Text(val text: String) : DisplaySegment()
    data class Reply(val segment: BotIncomingSegment.Reply) : DisplaySegment()
    data class Image(val segment: BotIncomingSegment.Image) : DisplaySegment()
    data class Record(val segment: BotIncomingSegment.Record) : DisplaySegment()
    data class Video(val segment: BotIncomingSegment.Video) : DisplaySegment()
    data class PendingImage(
        val bitmap: ImageBitmap,
        val summary: String,
        val width: Int,
        val height: Int,
        val subType: ImageSubType = ImageSubType.NORMAL
    ) : DisplaySegment()
}
