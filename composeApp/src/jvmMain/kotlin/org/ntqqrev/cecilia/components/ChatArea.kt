package org.ntqqrev.cecilia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.cecilia.ChatBackgroundColor
import org.ntqqrev.cecilia.structs.Conversation
import org.ntqqrev.cecilia.utils.LocalBot

@Composable
fun ChatArea(conversation: Conversation) {
    val bot = LocalBot.current

    val coroutineScope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val messages = remember { mutableStateListOf<BotIncomingMessage>() }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextLoadSequence by remember { mutableStateOf<Long?>(null) }
    val pendingMessages = remember { mutableMapOf<Int, BotIncomingMessage>() }

    fun isUserAtBottom(): Boolean {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return true
        
        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
        
        // 检查最后一个可见项是否是列表中的最后一项（或倒数第二项）
        return lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
    }

    suspend fun scrollToSeq(seq: Long) {
        val index = messages.indexOfFirst { it.sequence == seq }
        if (index != -1) {
            listState.scrollToItem(index)
        }
    }

    suspend fun scrollToBottom() {
        listState.scrollToItem(messages.size - 1)
    }

    // 初始化：加载历史消息
    LaunchedEffect(conversation.id) {
        messages.clear()
        isLoadingMore = false

        try {
            val historyMessages = when (conversation.scene) {
                org.ntqqrev.acidify.message.MessageScene.FRIEND -> {
                    bot.getFriendHistoryMessages(
                        friendUin = conversation.peerUin,
                        limit = 30
                    )
                }

                org.ntqqrev.acidify.message.MessageScene.GROUP -> {
                    bot.getGroupHistoryMessages(
                        groupUin = conversation.peerUin,
                        limit = 30
                    )
                }

                else -> return@LaunchedEffect
            }

            messages.addAll(historyMessages.messages)
            messages.sortBy { it.sequence }
            nextLoadSequence = historyMessages.nextStartSequence
            scrollToBottom()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 监听属于当前会话的消息
    LaunchedEffect(conversation.id) {
        bot.eventFlow.filterIsInstance<MessageReceiveEvent>().collect { event ->
            val message = event.message

            // 只处理属于当前会话的消息
            if (message.peerUin == conversation.peerUin) {
                // 检查是否是待确认的占位符消息
                val placeholder = pendingMessages.remove(message.random)
                if (placeholder != null) {
                    // 删除占位符
                    val index = messages.indexOfFirst {
                        it.random == message.random && it.messageUid == -1L
                    }
                    if (index != -1) {
                        messages.removeAt(index)
                    }
                }

                if (
                    messages.none {
                        it.clientSequence == message.clientSequence &&
                                it.random == message.random
                    }
                ) {
                    messages.add(message)
                    messages.sortBy { it.sequence }
                }

                if (isUserAtBottom()) {
                    scrollToBottom()
                }
            }
        }
    }
    
    // 检测滚动到顶部，触发加载更多
    LaunchedEffect(listState.canScrollBackward, listState.firstVisibleItemIndex) {
        if (
            listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset < 100 && 
            !isLoadingMore &&
            messages.isNotEmpty() &&
            nextLoadSequence != null
        ) {

            // 加载更多历史消息
            isLoadingMore = true
            val oldestMessageSeq = messages.firstOrNull()?.sequence

            try {
                val historyMessages = when (conversation.scene) {
                    org.ntqqrev.acidify.message.MessageScene.FRIEND -> {
                        bot.getFriendHistoryMessages(
                            friendUin = conversation.peerUin,
                            limit = 30,
                            startSequence = nextLoadSequence!!
                        )
                    }

                    org.ntqqrev.acidify.message.MessageScene.GROUP -> {
                        bot.getGroupHistoryMessages(
                            groupUin = conversation.peerUin,
                            limit = 30,
                            startSequence = nextLoadSequence!!
                        )
                    }

                    else -> null
                }

                if (historyMessages != null && historyMessages.messages.isNotEmpty()) {
                    messages.addAll(0, historyMessages.messages)
                    nextLoadSequence = historyMessages.nextStartSequence

                    // 滚动到之前最老的消息
                    if (oldestMessageSeq != null) {
                        scrollToSeq(oldestMessageSeq)
                    }
                } else {
                    nextLoadSequence = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBackgroundColor)
    ) {
        // 顶部标题栏
        ChatHeader(conversation)

        Divider()

        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            contentPadding = PaddingValues(top = 48.dp, bottom = 16.dp)
        ) {
            // 加载指示器
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    selfUin = bot.uin
                )
            }
        }

        Divider()

        // 输入框区域
        ChatInputArea(
            messageText = messageText,
            onMessageTextChange = { messageText = it },
            onSendMessage = {
                if (messageText.isNotBlank()) {
                    val content = messageText
                    messageText = ""

                    coroutineScope.launch {
                        try {
                            val clientSequence = kotlin.random.Random.nextLong()
                            val random = kotlin.random.Random.nextInt()

                            // 创建占位符消息
                            val tempSequence = (messages.maxOfOrNull { it.sequence } ?: 0L) + 1
                            val placeholder = BotIncomingMessage(
                                scene = conversation.scene,
                                peerUin = conversation.peerUin,
                                peerUid = conversation.peerUin.toString(),
                                sequence = tempSequence,
                                timestamp = System.currentTimeMillis() / 1000,
                                senderUin = bot.uin,
                                senderUid = bot.uin.toString(),
                                clientSequence = clientSequence,
                                random = random,
                                initSegments = listOf(BotIncomingSegment.Text(content)),
                                messageUid = -1L
                            )

                            // 添加占位符消息
                            messages.add(placeholder)
                            scrollToBottom()
                            pendingMessages[random] = placeholder

                            // 发送真实消息
                            when (conversation.scene) {
                                org.ntqqrev.acidify.message.MessageScene.FRIEND -> {
                                    val result = bot.sendFriendMessage(
                                        friendUin = conversation.peerUin,
                                        clientSequence = clientSequence,
                                        random = random
                                    ) {
                                        text(content)
                                    }

                                    // 如果是给别人发的私聊消息（不是给自己），需要手动替换占位符
                                    if (conversation.peerUin != bot.uin) {
                                        pendingMessages.remove(random)
                                        val index = messages.indexOfFirst {
                                            it.random == random && it.messageUid == -1L
                                        }
                                        if (index != -1) {
                                            messages.removeAt(index)
                                        }

                                        val realMessage = BotIncomingMessage(
                                            scene = conversation.scene,
                                            peerUin = conversation.peerUin,
                                            peerUid = conversation.peerUin.toString(),
                                            sequence = result.sequence,
                                            timestamp = result.sendTime,
                                            senderUin = bot.uin,
                                            senderUid = bot.uin.toString(),
                                            clientSequence = clientSequence,
                                            random = random,
                                            initSegments = listOf(BotIncomingSegment.Text(content)),
                                            messageUid = 0L
                                        )

                                        messages.add(realMessage)
                                        messages.sortBy { it.sequence }
                                    }
                                }

                                org.ntqqrev.acidify.message.MessageScene.GROUP -> {
                                    bot.sendGroupMessage(
                                        groupUin = conversation.peerUin,
                                        clientSequence = clientSequence,
                                        random = random
                                    ) {
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
            }
        )
    }
}

@Composable
private fun ChatHeader(conversation: Conversation) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = conversation.name,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChatInputArea(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 120.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            (event.isMetaPressed || event.isCtrlPressed)
                        ) {
                            onSendMessage()
                            true
                        } else {
                            false
                        }
                    },
                placeholder = { Text("输入消息... (⌘/Ctrl+Enter 发送)") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(56.dp),
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}