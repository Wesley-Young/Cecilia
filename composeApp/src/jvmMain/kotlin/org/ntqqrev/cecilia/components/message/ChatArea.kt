package org.ntqqrev.cecilia.components.message

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.structs.Conversation
import org.ntqqrev.cecilia.utils.LocalBot
import org.ntqqrev.cecilia.utils.LocalConfig
import org.ntqqrev.cecilia.utils.segmentsToPreviewString
import kotlin.random.Random

@Composable
fun ChatArea(conversation: Conversation) {
    val bot = LocalBot.current
    val config = LocalConfig.current
    val logger = remember { bot.createLogger("ChatArea") }

    val coroutineScope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }

    val messages = remember { mutableStateListOf<BotIncomingMessage>() }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextLoadSequence by remember { mutableStateOf<Long?>(null) }
    val pendingMessages = remember { mutableMapOf<Int, BotIncomingMessage>() }
    
    var replyToMessage by remember { mutableStateOf<BotIncomingMessage?>(null) }

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
        listState.scrollToItem((messages.size - 1).coerceAtLeast(0))
    }

    suspend fun sendMessage(
        content: String,
        replySeq: Long?,
    ) {
        val clientSequence = Random.nextLong()
        val random = Random.nextInt()

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
            initSegments = buildList {
                if (replySeq != null) {
                    add(
                        BotIncomingSegment.Reply(
                            sequence = replySeq
                        )
                    )
                }
                add(BotIncomingSegment.Text(content))
            },
            messageUid = -1L
        )

        messages.add(placeholder)
        scrollToBottom()
        pendingMessages[random] = placeholder

        // 发送真实消息
        when (conversation.scene) {
            MessageScene.FRIEND -> {
                val result = bot.sendFriendMessage(
                    friendUin = conversation.peerUin,
                    clientSequence = clientSequence,
                    random = random
                ) {
                    replySeq?.let { reply(it) }
                    text(content)
                }

                if (conversation.peerUin != bot.uin) {
                    val realMessage = bot.getFriendHistoryMessages(
                        friendUin = conversation.peerUin,
                        limit = 1,
                        startSequence = result.sequence
                    ).messages.firstOrNull()
                    realMessage?.let {
                        pendingMessages.remove(random)
                        val index = messages.indexOfFirst {
                            it.random == random && it.messageUid == -1L
                        }
                        if (index != -1) {
                            messages.removeAt(index)
                        }
                        messages.add(realMessage)
                        messages.sortBy { it.sequence }
                    }
                }
            }

            MessageScene.GROUP -> {
                bot.sendGroupMessage(
                    groupUin = conversation.peerUin,
                    clientSequence = clientSequence,
                    random = random
                ) {
                    replySeq?.let { reply(it) }
                    text(content)
                }
            }

            else -> {}
        }
    }

    // 初始化：加载历史消息
    LaunchedEffect(conversation.id) {
        messages.clear()
        isLoadingMore = false

        try {
            val historyMessages = when (conversation.scene) {
                MessageScene.FRIEND -> {
                    bot.getFriendHistoryMessages(
                        friendUin = conversation.peerUin,
                        limit = 30
                    )
                }

                MessageScene.GROUP -> {
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
            logger.e(e) { "消息初始化失败" }
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
                    MessageScene.FRIEND -> {
                        bot.getFriendHistoryMessages(
                            friendUin = conversation.peerUin,
                            limit = 30,
                            startSequence = nextLoadSequence!!
                        )
                    }

                    MessageScene.GROUP -> {
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
                if (e::class.qualifiedName != "androidx.compose.runtime.LeftCompositionCancellationException")
                    logger.e(e) { "消息拉取失败" }
            } finally {
                isLoadingMore = false
                delay(100)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(config.theme.chatBackgroundColor)
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
                    selfUin = bot.uin,
                    allMessages = messages,
                    onScrollToMessage = { seq ->
                        coroutineScope.launch {
                            scrollToSeq(seq)
                        }
                    },
                    onReplyToMessage = { replyToMessage = it }
                )
            }
        }
        
        // 当回复预览显示/隐藏时，如果用户在底部，保持滚动到底部
        LaunchedEffect(replyToMessage) {
            if (replyToMessage != null) {
                // 双击消息时，请求输入框焦点
                inputFocusRequester.requestFocus()
            }
            if (isUserAtBottom()) {
                scrollToBottom()
            }
        }

        Divider()

        // 回复预览区域
        if (replyToMessage != null) {
            ReplyPreview(
                replyToMessage = replyToMessage!!,
                onCancelReply = { replyToMessage = null }
            )
        }

        // 输入框区域
        ChatInputArea(
            messageText = messageText,
            onMessageTextChange = { messageText = it },
            useCtrlEnterToSend = config.useCtrlEnterToSend,
            focusRequester = inputFocusRequester,
            onSendMessage = {
                if (messageText.text.isNotBlank()) {
                    val content = messageText.text
                    val replySeq = replyToMessage?.sequence

                    messageText = TextFieldValue("")
                    replyToMessage = null  // 立即隐藏回复UI

                    coroutineScope.launch {
                        try {
                            sendMessage(content, replySeq)
                        } catch (e: Exception) {
                            logger.e(e) { "消息发送失败" }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ReplyPreview(
    replyToMessage: BotIncomingMessage,
    onCancelReply: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.9f),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧竖线
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp),
                color = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(2.dp)
            ) {}
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 回复内容
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 发送者名称
                val senderName = when (replyToMessage.scene) {
                    MessageScene.GROUP -> {
                        replyToMessage.extraInfo?.let { info ->
                            info.groupCard.takeIf { it.isNotEmpty() } ?: info.nick
                        } ?: replyToMessage.senderUin.toString()
                    }
                    else -> replyToMessage.senderUin.toString()
                }
                
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // 消息预览
                val previewText = remember(replyToMessage) {
                    replyToMessage.segmentsToPreviewString()
                }
                
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            
            // 取消按钮
            IconButton(
                onClick = onCancelReply,
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
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
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    useCtrlEnterToSend: Boolean,
    focusRequester: FocusRequester,
    onSendMessage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 100.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            if (useCtrlEnterToSend) {
                                if (event.isMetaPressed || event.isCtrlPressed) {
                                    onSendMessage()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                if (event.isMetaPressed || event.isCtrlPressed) {
                                    // 在光标位置插入换行符
                                    val currentText = messageText.text
                                    val cursorPos = messageText.selection.start
                                    val newText = currentText.take(cursorPos) + "\n" +
                                            currentText.substring(cursorPos)
                                    val newCursorPos = cursorPos + 1
                                    onMessageTextChange(
                                        TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursorPos)
                                        )
                                    )
                                    true
                                } else {
                                    onSendMessage()
                                    true
                                }
                            }
                        } else {
                            false
                        }
                    },
                placeholder = {
                    Text(
                        if (useCtrlEnterToSend) "输入消息... (⌘/Ctrl+Enter 发送)"
                        else "输入消息... (Enter 发送)"
                    )
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedBorderColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = onSendMessage,
                modifier = Modifier.size(40.dp),
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