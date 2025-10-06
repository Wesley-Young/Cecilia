package org.ntqqrev.cecilia

// 会话数据类
data class Conversation(
    val id: String,
    val name: String,
    val avatar: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0
)

// 消息数据类
data class Message(
    val id: String,
    val content: String,
    val isSent: Boolean,
    val time: String,
    val senderName: String = ""
)
