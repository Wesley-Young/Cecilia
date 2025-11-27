package org.ntqqrev.cecilia.model

import org.ntqqrev.acidify.message.MessageScene

data class LocalMessage(
    val scene: MessageScene,
    val peerUin: Long,
    val random: Int,
    val sequence: Long? = null,
    val timestamp: Long,
    val text: String? = null
    // more fields can be added later
) : MessageLike