package org.ntqqrev.cecilia.structs

import org.ntqqrev.acidify.message.MessageScene

/**
 * 会话数据类
 *
 * 表示一个聊天会话，可以是好友会话或群聊会话
 *
 * @property id 会话唯一标识符，通常为 peerUin 的字符串形式
 * @property peerUin 对端的 UIN（QQ号或群号）
 * @property scene 消息场景（好友/群聊/临时会话）
 * @property name 会话显示名称（好友备注/昵称 或 群名）
 * @property lastMessage 最新消息预览文本
 * @property lastMessageSeq 最新消息的序列号
 * @property lastMessageTimestamp 最新消息的时间戳（Unix秒）
 * @property time 格式化后的时间显示字符串
 * @property unreadCount 未读消息数量
 */
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
