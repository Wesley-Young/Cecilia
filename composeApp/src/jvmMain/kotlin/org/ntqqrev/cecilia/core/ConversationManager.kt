package org.ntqqrev.cecilia.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.struct.Conversation
import org.ntqqrev.cecilia.util.segmentsToPreviewString
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 会话管理器
 *
 * 负责管理所有会话的状态，包括：
 * - 后台监听消息并更新会话列表
 * - 在内存中维护会话列表
 * - 缓存每个会话的消息
 * - 提供会话列表给 UI
 */
class ConversationManager(
    private val bot: Bot,
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
    private var selectedConversationIdState: String? by mutableStateOf(null)
    val selectedConversationId: String?
        get() = selectedConversationIdState

    private val logger = bot.createLogger(this)

    init {
        // 启动后台消息监听
        startMessageListener()
    }

    /**
     * 设置当前选中的会话
     */
    fun setSelectedConversation(conversationId: String?) {
        currentSelectedConversationId = conversationId
        selectedConversationIdState = conversationId
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
     * 处理收到的消息（仅更新会话列表和预览）
     */
    private suspend fun handleIncomingMessage(event: MessageReceiveEvent) {
        val message = event.message
        val conversationId = message.peerUin.toString()

        // 提取消息内容文本
        val messageContent = message.segmentsToPreviewString()

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
                val name = when (message.scene) {
                    MessageScene.FRIEND -> {
                        bot.getFriend(message.peerUin, forceUpdate = false)
                            ?.remark?.takeIf { it.isNotEmpty() }
                            ?: bot.getFriend(message.peerUin, forceUpdate = false)?.nickname
                            ?: message.peerUin.toString()
                    }

                    MessageScene.GROUP -> {
                        bot.getGroup(message.peerUin, forceUpdate = false)
                            ?.name ?: message.peerUin.toString()
                    }

                    else -> {
                        message.peerUin.toString()
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
     * 查找或创建会话
     * @param peerUin 对端的 UIN（QQ号或群号）
     * @param scene 消息场景
     * @return 会话 ID
     */
    suspend fun findOrCreateConversation(peerUin: Long, scene: MessageScene): String {
        val conversationId = peerUin.toString()

        // 检查会话是否已存在
        val existing = conversations.find { it.id == conversationId }
        if (existing != null) {
            return conversationId
        }

        // 会话不存在，创建新会话
        try {
            val (name, _) = when (scene) {
                MessageScene.FRIEND -> {
                    val displayName = bot.getFriend(peerUin, forceUpdate = false)
                        ?.remark?.takeIf { it.isNotEmpty() }
                        ?: bot.getFriend(peerUin, forceUpdate = false)?.nickname
                        ?: peerUin.toString()
                    displayName to ""
                }

                MessageScene.GROUP -> {
                    val group = bot.getGroup(peerUin, forceUpdate = false)
                    val groupName = group?.name ?: peerUin.toString()
                    groupName to ""
                }

                else -> peerUin.toString() to ""
            }

            // 创建新会话并添加到列表末尾
            val newConversation = Conversation(
                id = conversationId,
                peerUin = peerUin,
                scene = scene,
                name = name,
                lastMessage = "",
                lastMessageSeq = 0,
                lastMessageTimestamp = System.currentTimeMillis() / 1000,
                time = "",
                unreadCount = 0
            )
            conversations.add(newConversation)

            return conversationId
        } catch (e: Exception) {
            logger.e(e) { "消息创建失败" }
            val newConversation = Conversation(
                id = conversationId,
                peerUin = peerUin,
                scene = scene,
                name = peerUin.toString(),
                lastMessage = "",
                lastMessageSeq = 0,
                lastMessageTimestamp = System.currentTimeMillis() / 1000,
                time = "",
                unreadCount = 0
            )
            conversations.add(newConversation)
            return conversationId
        }
    }

    /**
     * 格式化消息时间戳
     */
    @Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
    private fun formatMessageTime(timestamp: Long): String {
        val zoneId = ZoneId.systemDefault()
        val messageDateTime = Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDateTime()
        val messageDate = messageDateTime.toLocalDate()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return when {
            messageDate == today -> {
                // 今天：显示时:分
                messageDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            }

            messageDate == yesterday -> {
                // 昨天
                "昨天"
            }

            messageDate >= weekStart -> {
                // 本周：显示星期几
                when (messageDate.dayOfWeek) {
                    DayOfWeek.MONDAY -> "周一"
                    DayOfWeek.TUESDAY -> "周二"
                    DayOfWeek.WEDNESDAY -> "周三"
                    DayOfWeek.THURSDAY -> "周四"
                    DayOfWeek.FRIDAY -> "周五"
                    DayOfWeek.SATURDAY -> "周六"
                    DayOfWeek.SUNDAY -> "周日"
                }
            }

            else -> {
                // 更早：显示日期
                messageDateTime.format(DateTimeFormatter.ofPattern("MM/dd"))
            }
        }
    }

    fun getSelectedConversation(): Conversation? {
        val targetId = currentSelectedConversationId ?: return null
        return conversations.find { it.id == targetId }
    }
}
