package org.ntqqrev.cecilia.struct

import org.ntqqrev.acidify.message.MessageScene

data class Conversation(
    // 核心标识字段
    val id: String,
    val peerUin: Long,
    val scene: MessageScene,

    // 显示信息
    val name: String,

    // 最新消息信息
    val lastMessage: String,
    val lastMessageSeq: Long,
    val lastMessageTimestamp: Long,
    val time: String,

    // 状态信息
    val unreadCount: Int = 0
)
