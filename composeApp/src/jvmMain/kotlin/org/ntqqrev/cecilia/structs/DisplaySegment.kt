package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.message.BotIncomingSegment

sealed class DisplaySegment {
    data class Text(val text: String) : DisplaySegment()
    data class Reply(val segment: BotIncomingSegment.Reply) : DisplaySegment()
    data class Image(val segment: BotIncomingSegment.Image) : DisplaySegment()
    data class Record(val segment: BotIncomingSegment.Record) : DisplaySegment()
    data class Video(val segment: BotIncomingSegment.Video) : DisplaySegment()
}