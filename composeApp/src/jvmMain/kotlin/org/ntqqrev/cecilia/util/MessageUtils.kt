package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.Message

val BotGroupMember.displayName: String
    get() = this.card.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

fun BotIncomingMessage.toPreviewText() = segments.joinToString("")

fun BotIncomingMessage.toModeledMessage(): Message = Message(
    scene = this.scene,
    peerUin = this.peerUin,
    sequence = this.sequence,
    senderUin = this.senderUin,
    senderName = this.extraInfo?.groupCard ?: "",
    timestamp = this.timestamp,
    elements = this.segments.map { it.toElement() }
)

fun BotIncomingSegment.toElement(): Element = when (this) {
    is BotIncomingSegment.Text -> Element.Text(
        content = this.text
    )

    is BotIncomingSegment.Image -> Element.Image(
        fileId = this.fileId,
        width = this.width,
        height = this.height,
        subType = this.subType,
        summary = this.summary,
    )

    else -> Element.Text(
        content = "[${this::class.simpleName}]"
    )
}