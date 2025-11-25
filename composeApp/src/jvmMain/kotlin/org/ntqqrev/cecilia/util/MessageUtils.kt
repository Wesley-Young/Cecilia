package org.ntqqrev.cecilia.util

import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.BotIncomingMessage

val BotFriend.displayName: String
    get() = this.remark.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

val BotGroupMember.displayName: String
    get() = this.card.ifEmpty { this.nickname }.ifEmpty { this.uin.toString() }

fun BotIncomingMessage.toPreviewText() = segments.joinToString("")