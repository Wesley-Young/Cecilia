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
    val logger = remember { bot.createLogger("MessagesPanel") }

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

    // 初始化缓存
    LaunchedEffect(cacheManager) {
        launch {
            try {
                cacheManager.initialize()
            } catch (e: Exception) {
                logger.e(e) { "缓存初始化失败" }
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
                                // 标记已读失败
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
                // 使用 key 确保切换会话时重新创建 ChatArea
                key(selectedConversationId) {
                    ChatArea(conversation)
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

