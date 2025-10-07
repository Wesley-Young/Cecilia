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
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.cecilia.ChatBackgroundColor
import org.ntqqrev.cecilia.Conversation

@Composable
fun ChatArea(
    conversation: Conversation,
    messages: List<BotIncomingMessage>,
    selfUin: Long,
    isLoadingMore: Boolean,
    scrollToMessageSequence: Long?,
    onLoadMore: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // 记录上一次最后一条消息的序列号，用于判断是否有新消息
    var lastMessageSequence by remember { mutableStateOf<Long?>(null) }
    
    // 标记是否是首次加载
    var isInitialLoad by remember { mutableStateOf(true) }

    // 检查用户是否在底部（或接近底部）
    fun isUserAtBottom(): Boolean {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return true
        
        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
        
        // 检查最后一个可见项是否是列表中的最后一项（或倒数第二项）
        return lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
    }

    LaunchedEffect(messages.size, isLoadingMore) {
        if (messages.isNotEmpty() && !isLoadingMore) {
            val currentLastSequence = messages.lastOrNull()?.sequence

            // 首次加载：滚动到底部
            if (isInitialLoad) {
                listState.scrollToItem(messages.size - 1)
                lastMessageSequence = currentLastSequence
                isInitialLoad = false
            }
            // 有新消息到达（最后一条消息的序列号变了）
            else if (currentLastSequence != null && currentLastSequence != lastMessageSequence) {
                // 只有用户在底部时才滚动
                if (isUserAtBottom()) {
                    listState.scrollToItem(messages.size - 1)
                }
                lastMessageSequence = currentLastSequence
            }
            // 否则是加载历史消息，不滚动
        }
    }
    
    // 加载历史消息后，滚动到指定的消息位置
    LaunchedEffect(scrollToMessageSequence) {
        scrollToMessageSequence?.let { targetSeq ->
            val targetIndex = messages.indexOfFirst { it.sequence == targetSeq }
            if (targetIndex != -1) {
                listState.scrollToItem(targetIndex)
            }
        }
    }
    
    // 检测滚动到顶部，触发加载更多
    LaunchedEffect(listState.canScrollBackward, listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex == 0 && 
            listState.firstVisibleItemScrollOffset < 100 && 
            !isLoadingMore &&
            messages.isNotEmpty()) {
            onLoadMore()
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
                    selfUin = selfUin
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
                    onSendMessage(messageText)
                    messageText = ""
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