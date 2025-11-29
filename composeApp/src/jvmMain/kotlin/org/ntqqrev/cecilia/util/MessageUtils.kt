package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.LocalMessage
import org.ntqqrev.cecilia.model.Message

val BotFriend.displayName: String
    get() = this.remark.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

val BotGroupMember.displayName: String
    get() = this.card.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

fun String.zipIntoSingleLine() = replace('\n', ' ')
    .replace('\r', ' ')

fun BotIncomingMessage.toPreviewText() = segments.toPreviewText()

fun List<BotIncomingSegment>.toPreviewText() = joinToString("").zipIntoSingleLine()

fun Message.toPreviewText() = elements.toPreviewText()

fun LocalMessage.toPreviewText() = elements.toPreviewText()

@JvmName("elementsToPreviewText")
fun List<Element>.toPreviewText() = joinToString("").zipIntoSingleLine()