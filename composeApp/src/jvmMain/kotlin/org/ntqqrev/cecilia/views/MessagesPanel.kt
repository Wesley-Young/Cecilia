package org.ntqqrev.cecilia.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.Conversation
import org.ntqqrev.cecilia.Message
import org.ntqqrev.cecilia.components.ChatArea
import org.ntqqrev.cecilia.components.ConversationList
import org.ntqqrev.cecilia.components.DraggableDivider
import org.ntqqrev.cecilia.utils.LocalBot
import org.ntqqrev.cecilia.utils.LocalCacheManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessagesPanel(width: Dp = 320.dp) {
    val bot = LocalBot.current
    val cacheManager = LocalCacheManager.current
    val coroutineScope = rememberCoroutineScope()
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var leftPanelWidth by remember { mutableStateOf(width) }

    // 使用可变列表管理会话列表
    val conversations = remember { mutableStateListOf<Conversation>() }

    // 初始化缓存
    LaunchedEffect(cacheManager) {
        launch {
            try {
                cacheManager.initialize()
            } catch (e: Exception) {
                // 初始化失败，记录错误但不影响运行
                e.printStackTrace()
            }
        }
    }

    // 示例消息数据
    val messages = remember {
        mutableStateListOf(
            Message("1", "你好！", false, "14:20", "张三"),
            Message("2", "你好，有什么可以帮你的吗？", true, "14:21"),
            Message("3", "我想问一下关于项目的事情", false, "14:25", "张三"),
            Message("4", "请说", true, "14:26"),
            Message("5", "最新的功能什么时候上线？", false, "14:28", "张三"),
            Message("6", "预计下周三上线", true, "14:30"),
        )
    }

    // 监听消息接收事件
    LaunchedEffect(bot) {
        bot.eventFlow.filterIsInstance<MessageReceiveEvent>().collect { event ->
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
                val isCurrentlySelected = selectedConversationId == conversationId
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
                coroutineScope.launch {
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
                        val isCurrentlySelected = selectedConversationId == conversationId
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
                        val isCurrentlySelected = selectedConversationId == conversationId
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
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：会话列表
        Box(modifier = Modifier.width(leftPanelWidth)) {
            ConversationList(
                conversations = conversations,
                selectedId = selectedConversationId,
                onConversationClick = { conversationId ->
                    // 查找被点击的会话
                    val clickedIndex = conversations.indexOfFirst { it.id == conversationId }
                    if (clickedIndex != -1) {
                        val conversation = conversations[clickedIndex]

                        // 如果有未读消息，清除未读并标记为已读
                        if (conversation.unreadCount > 0) {
                            // 更新会话，清除未读消息数
                            val updated = conversation.copy(unreadCount = 0)
                            conversations[clickedIndex] = updated

                            // 调用 bot 标记消息为已读
                            coroutineScope.launch {
                                try {
                                    when (conversation.scene) {
                                        MessageScene.FRIEND -> {
                                            bot.markFriendMessagesAsRead(
                                                friendUin = conversation.peerUin,
                                                startSequence = conversation.lastMessageSeq,
                                                startTime = conversation.lastMessageTimestamp
                                            )
                                        }

                                        MessageScene.GROUP -> {
                                            bot.markGroupMessagesAsRead(
                                                groupUin = conversation.peerUin,
                                                startSequence = conversation.lastMessageSeq
                                            )
                                        }

                                        else -> {
                                            // 其他类型暂不处理
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 标记已读失败，记录错误但不影响UI
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    // 更新选中状态
                    selectedConversationId = conversationId
                },
                width = leftPanelWidth,
                showMenuButton = false
            )
        }

        // 可拖拽的分界线
        DraggableDivider(
            currentWidth = leftPanelWidth,
            onWidthChange = { leftPanelWidth = it }
        )

        // 右侧：聊天区域
        if (selectedConversationId != null) {
            val selectedConversation = conversations.find { it.id == selectedConversationId }
            selectedConversation?.let { conversation ->
                ChatArea(
                    conversation = conversation,
                    messages = messages,
                    onSendMessage = { content ->
                        messages.add(
                            Message(
                                id = (messages.size + 1).toString(),
                                content = content,
                                isSent = true,
                                time = "刚刚"
                            )
                        )
                    }
                )
            }
        } else {
            // 未选择会话时的占位界面
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "选择一个会话开始聊天",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 格式化消息时间戳
 * @param timestamp Unix时间戳（秒）
 * @return 格式化后的时间字符串
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
            // 今天：显示时:分
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)
        }

        messageTime.after(yesterdayStart) -> {
            // 昨天
            "昨天"
        }

        messageTime.after(weekStart) -> {
            // 本周：显示星期
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
            // 更早：显示月/日
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(messageTime)
        }
    }
}

