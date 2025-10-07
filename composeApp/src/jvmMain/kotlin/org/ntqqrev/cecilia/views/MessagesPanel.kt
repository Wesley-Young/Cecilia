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
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.components.ChatArea
import org.ntqqrev.cecilia.components.ConversationList
import org.ntqqrev.cecilia.components.DraggableDivider
import org.ntqqrev.cecilia.utils.LocalBot
import org.ntqqrev.cecilia.utils.LocalCacheManager
import org.ntqqrev.cecilia.utils.LocalConversationManager

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessagesPanel(
    width: Dp = 320.dp,
    initialSelectedConversationId: String? = null,
    onConversationSelected: () -> Unit = {}
) {
    val bot = LocalBot.current
    val cacheManager = LocalCacheManager.current
    val conversationManager = LocalConversationManager.current
    val coroutineScope = rememberCoroutineScope()
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var leftPanelWidth by remember { mutableStateOf(width) }

    // 处理外部传入的初始选中会话
    LaunchedEffect(initialSelectedConversationId) {
        initialSelectedConversationId?.let {
            selectedConversationId = it
            conversationManager.setSelectedConversation(it)
            onConversationSelected()
        }
    }

    // 使用 ConversationManager 中的会话列表
    val conversations = conversationManager.conversations

    // 当前会话的消息列表（直接使用 BotIncomingMessage）
    val messages = remember { mutableStateListOf<BotIncomingMessage>() }
    
    // 加载状态
    var isLoadingMore by remember { mutableStateOf(false) }
    
    // 用于触发滚动到指定消息的序列号
    var scrollToMessageSequence by remember { mutableStateOf<Long?>(null) }

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

    // 当选择会话改变时，加载历史消息
    LaunchedEffect(selectedConversationId) {
        // 清空之前会话的消息
        messages.clear()
        
        // 重置加载状态和滚动目标
        isLoadingMore = false
        scrollToMessageSequence = null
        
        selectedConversationId?.let { conversationId ->
            launch {
                try {
                    // 加载历史消息（最多30条）
                    conversationManager.loadHistoryMessages(conversationId, 30)
                    
                    // 获取消息（直接使用 BotIncomingMessage）
                    val botMessages = conversationManager.getMessages(conversationId)
                    messages.addAll(botMessages)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 监听新消息并更新当前会话的消息列表
    LaunchedEffect(selectedConversationId, conversations.size) {
        selectedConversationId?.let { conversationId ->
            launch {
                // 定期检查新消息（每500毫秒检查一次）
                while (true) {
                    kotlinx.coroutines.delay(500)
                    try {
                        val botMessages = conversationManager.getMessages(conversationId)
                        if (botMessages.size != messages.size) {
                            // 只更新新增的消息，不清空整个列表
                            val newMessagesCount = botMessages.size - messages.size
                            if (newMessagesCount > 0) {
                                val newBotMessages = botMessages.takeLast(newMessagesCount)
                                messages.addAll(newBotMessages)
                            } else if (newMessagesCount < 0) {
                                // 如果消息数变少了（不应该发生），重新加载
                                messages.clear()
                                messages.addAll(botMessages)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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
                    val conversation = conversations.find { it.id == conversationId }
                    if (conversation != null && conversation.unreadCount > 0) {
                        // 清除未读消息数
                        conversationManager.clearUnread(conversationId)

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

                    // 更新选中状态
                    selectedConversationId = conversationId
                    // 通知 ConversationManager 当前选中的会话
                    conversationManager.setSelectedConversation(conversationId)
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
                // 使用 key 确保切换会话时重新创建 ChatArea，重置滚动状态
                key(selectedConversationId) {
                    ChatArea(
                        conversation = conversation,
                        messages = messages,
                        selfUin = bot.uin,
                        isLoadingMore = isLoadingMore,
                        scrollToMessageSequence = scrollToMessageSequence,
                        onLoadMore = {
                        // 加载更多历史消息
                        if (!isLoadingMore) {
                            coroutineScope.launch {
                                isLoadingMore = true
                                // 记录加载前最老的消息序列号
                                val oldestMessageSeq = messages.firstOrNull()?.sequence
                                
                                try {
                                    val hasMore = conversationManager.loadMoreMessages(selectedConversationId!!, 30)
                                    
                                    // 更新消息列表
                                    val botMessages = conversationManager.getMessages(selectedConversationId!!)
                                    messages.clear()
                                    messages.addAll(botMessages)
                                    
                                    // 加载完成后，触发滚动到之前最老的消息
                                    scrollToMessageSequence = oldestMessageSeq
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    // 即使失败也尝试滚动到之前的位置
                                    scrollToMessageSequence = oldestMessageSeq
                                } finally {
                                    isLoadingMore = false
                                    // 重置滚动目标
                                    kotlinx.coroutines.delay(100)
                                    scrollToMessageSequence = null
                                }
                            }
                        }
                    },
                    onSendMessage = { content ->
                        // 发送消息
                        coroutineScope.launch {
                            try {
                                when (conversation.scene) {
                                    MessageScene.FRIEND -> {
                                        bot.sendFriendMessage(conversation.peerUin) {
                                            text(content)
                                        }
                                    }
                                    MessageScene.GROUP -> {
                                        bot.sendGroupMessage(conversation.peerUin) {
                                            text(content)
                                        }
                                    }
                                    else -> {
                                        // 其他类型暂不支持
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    )
                }
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

