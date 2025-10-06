package org.ntqqrev.cecilia.utils

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.Conversation
import java.text.SimpleDateFormat
import java.util.*

/**
 * 会话管理器
 * 
 * 负责管理所有会话的状态，包括：
 * - 后台监听消息并更新会话列表
 * - 在内存中维护会话列表
 * - 提供会话列表给 UI
 */
class ConversationManager(
    private val bot: Bot,
    private val cacheManager: CacheManager,
    private val scope: CoroutineScope
) {
    /**
     * 会话列表（可观察的状态）
     */
    val conversations: SnapshotStateList<Conversation> = mutableStateListOf()

    /**
     * 当前选中的会话 ID
     */
    private var currentSelectedConversationId: String? = null

    init {
        // 启动后台消息监听
        startMessageListener()
    }

    /**
     * 设置当前选中的会话
     */
    fun setSelectedConversation(conversationId: String?) {
        currentSelectedConversationId = conversationId
    }

    /**
     * 启动后台消息监听
     */
    private fun startMessageListener() {
        scope.launch {
            bot.eventFlow.filterIsInstance<MessageReceiveEvent>().collect { event ->
                handleIncomingMessage(event)
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private suspend fun handleIncomingMessage(event: MessageReceiveEvent) {
        val message = event.message
        val conversationId = message.peerUin.toString()

        // 提取消息内容文本
        val messageContent = message.segments.joinToString("") { segment ->
            segment.toString()
        }

        // 构建消息预览文本
        val messagePreview = when (message.scene) {
            MessageScene.GROUP -> {
                // 群消息：包含发送者名称
                val senderName = message.extraInfo?.let { info ->
                    info.groupCard.takeIf { it.isNotEmpty() } ?: info.nick
                } ?: message.senderUin.toString()

                "$senderName: $messageContent".take(30).let {
                    if (it.length >= 30) "$it..." else it
                }
            }
            else -> {
                // 好友消息或其他：只显示消息内容
                messageContent.take(30).let {
                    if (it.length >= 30) "$it..." else it
                }
            }
        }

        // 格式化时间
        val timeStr = formatMessageTime(message.timestamp)

        // 查找是否已存在该会话
        val existingIndex = conversations.indexOfFirst { it.id == conversationId }

        if (existingIndex != -1) {
            // 会话已存在，更新它
            val existing = conversations[existingIndex]

            // 判断是否需要增加未读数
            val isCurrentlySelected = conversationId == currentSelectedConversationId
            val newUnreadCount = if (isCurrentlySelected) {
                // 当前选中该会话，不增加未读数
                existing.unreadCount
            } else {
                // 未选中，增加未读数
                existing.unreadCount + 1
            }

            // 创建更新后的会话对象
            val updated = existing.copy(
                lastMessage = messagePreview,
                time = timeStr,
                unreadCount = newUnreadCount,
                lastMessageSeq = message.sequence,
                lastMessageTimestamp = message.timestamp
            )

            // 移除旧的位置，插入到最前面
            conversations.removeAt(existingIndex)
            conversations.add(0, updated)
        } else {
            // 会话不存在，需要获取会话信息并创建新条目
            try {
                // 根据消息场景获取名称和头像
                val (name, avatar) = when (message.scene) {
                    MessageScene.FRIEND -> {
                        // 从缓存获取好友信息
                        val friend = cacheManager.friendCache[message.peerUin]
                        val displayName = friend?.remark?.takeIf { it.isNotEmpty() }
                            ?: friend?.nickname
                            ?: message.peerUin.toString()
                        val avatarStr = displayName.firstOrNull()?.toString() ?: "?"
                        displayName to avatarStr
                    }

                    MessageScene.GROUP -> {
                        // 从缓存获取群信息
                        val group = cacheManager.groupCache[message.peerUin]
                        val groupName = group?.name ?: message.peerUin.toString()
                        val avatarStr = groupName.firstOrNull()?.toString() ?: "?"
                        groupName to avatarStr
                    }

                    else -> {
                        // 临时会话等其他类型
                        message.peerUin.toString() to "?"
                    }
                }

                // 判断是否需要设置未读数
                val isCurrentlySelected = conversationId == currentSelectedConversationId
                val unreadCount = if (isCurrentlySelected) 0 else 1

                // 创建新会话并添加到列表最前面
                val newConversation = Conversation(
                    id = conversationId,
                    peerUin = message.peerUin,
                    scene = message.scene,
                    name = name,
                    lastMessage = messagePreview,
                    lastMessageSeq = message.sequence,
                    lastMessageTimestamp = message.timestamp,
                    time = timeStr,
                    unreadCount = unreadCount
                )
                conversations.add(0, newConversation)
            } catch (e: Exception) {
                // 如果获取失败，使用默认信息
                val isCurrentlySelected = conversationId == currentSelectedConversationId
                val unreadCount = if (isCurrentlySelected) 0 else 1
                
                conversations.add(
                    0, Conversation(
                        id = conversationId,
                        peerUin = message.peerUin,
                        scene = message.scene,
                        name = message.peerUin.toString(),
                        lastMessage = messagePreview,
                        lastMessageSeq = message.sequence,
                        lastMessageTimestamp = message.timestamp,
                        time = timeStr,
                        unreadCount = unreadCount
                    )
                )
            }
        }
    }

    /**
     * 清除指定会话的未读消息
     */
    fun clearUnread(conversationId: String) {
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index != -1) {
            val conversation = conversations[index]
            if (conversation.unreadCount > 0) {
                conversations[index] = conversation.copy(unreadCount = 0)
            }
        }
    }

    /**
     * 格式化消息时间戳
     */
    private fun formatMessageTime(timestamp: Long): String {
        val messageTime = Date(timestamp * 1000)
        val now = Date()
        val calendar = Calendar.getInstance()

        // 今天的开始时间
        calendar.time = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time

        // 昨天的开始时间
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = calendar.time

        // 本周的开始时间（周一）
        calendar.time = now
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weekStart = calendar.time

        return when {
            messageTime.after(todayStart) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)
            }

            messageTime.after(yesterdayStart) -> {
                "昨天"
            }

            messageTime.after(weekStart) -> {
                val dayOfWeek = Calendar.getInstance().apply { time = messageTime }.get(Calendar.DAY_OF_WEEK)
                when (dayOfWeek) {
                    Calendar.MONDAY -> "周一"
                    Calendar.TUESDAY -> "周二"
                    Calendar.WEDNESDAY -> "周三"
                    Calendar.THURSDAY -> "周四"
                    Calendar.FRIDAY -> "周五"
                    Calendar.SATURDAY -> "周六"
                    Calendar.SUNDAY -> "周日"
                    else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(messageTime)
                }
            }

            else -> {
                SimpleDateFormat("MM/dd", Locale.getDefault()).format(messageTime)
            }
        }
    }
}