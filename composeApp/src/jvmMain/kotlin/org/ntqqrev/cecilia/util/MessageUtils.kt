package org.ntqqrev.cecilia.util

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.Message
import org.ntqqrev.cecilia.model.MessageLike
import org.ntqqrev.cecilia.model.Notification

val BotFriend.displayName: String
    get() = this.remark.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

val BotGroupMember.displayName: String
    get() = this.card.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

fun BotIncomingMessage.toPreviewText() = segments.joinToString("")

fun BotIncomingMessage.toModel(): MessageLike = if (senderUin != 0L) {
    Message(
        scene = this.scene,
        peerUin = this.peerUin,
        sequence = this.sequence,
        senderUin = this.senderUin,
        senderName = this.extraInfo?.groupCard ?: "",
        timestamp = this.timestamp,
        elements = buildList {
            var buffer = AnnotatedString.Builder()

            fun flush() {
                if (buffer.length > 0) {
                    add(Element.RichText(buffer.toAnnotatedString()))
                    buffer = AnnotatedString.Builder()
                }
            }

            segments.forEach {
                when (it) {
                    is BotIncomingSegment.Text -> {
                        buffer.append(it.text)
                    }

                    is BotIncomingSegment.Mention -> {
                        buffer.append(it.name)
                    }

                    is BotIncomingSegment.Face -> {
                        buffer.appendInlineContent(
                            id = "face/${it.faceId}",
                            alternateText = it.summary,
                        )
                    }

                    is BotIncomingSegment.Reply -> {
                        flush()
                        add(Element.Reply(sequence = it.sequence))
                    }

                    is BotIncomingSegment.Image -> {
                        flush()
                        add(
                            Element.Image(
                                fileId = it.fileId,
                                width = it.width,
                                height = it.height,
                                subType = it.subType,
                                summary = it.summary,
                            )
                        )
                    }

                    else -> {
                        buffer.append("[${it::class.simpleName}]")
                    }
                }
            }
            flush()
        }
    )
} else {
    Notification("该消息已被撤回")
}