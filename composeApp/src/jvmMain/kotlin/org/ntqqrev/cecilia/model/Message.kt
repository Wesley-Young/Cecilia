package org.ntqqrev.cecilia.model

import org.ntqqrev.acidify.message.MessageScene

data class Message(
    val scene: MessageScene,
    val peerUin: Long,
    val sequence: Long,
    val senderUin: Long,
    val senderName: String,
    val timestamp: Long,
    val elements: List<Element>,
) : MessageLike