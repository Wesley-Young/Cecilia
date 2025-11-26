package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment

val BotFriend.displayName: String
    get() = this.remark.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

val BotGroupMember.displayName: String
    get() = this.card.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

fun BotIncomingMessage.toPreviewText() = segments.joinToString("")

fun List<BotIncomingSegment>.toPreviewText() = joinToString("")