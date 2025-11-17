package org.ntqqrev.cecilia.component.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.event.*
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.message.GreyTip
import org.ntqqrev.cecilia.component.message.MessageBubble
import org.ntqqrev.cecilia.component.message.PlaceholderMessageBubble
import org.ntqqrev.cecilia.core.*
import org.ntqqrev.cecilia.core.CustomFaceCache.toAttachmentInstance
import org.ntqqrev.cecilia.struct.*
import org.ntqqrev.cecilia.struct.GroupMemberDisplayInfo.Companion.toDisplayInfo
import org.ntqqrev.cecilia.util.*
import java.awt.Toolkit
import java.awt.datatransfer.Transferable
import kotlin.random.Random

@Composable
fun ChatArea(conversation: Conversation) {
    val bot = LocalBot.current
    val config = LocalConfig.current
    val httpClient = LocalHttpClient.current
    val logger = remember { bot.createLogger("ChatArea") }

    val coroutineScope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }

    val messages = remember { mutableStateListOf<DisplayMessage>() }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextLoadSequence by remember { mutableStateOf<Long?>(null) }
    val pendingMessages = remember { mutableMapOf<Int, PlaceholderMessage>() }

    var replyToMessage by remember { mutableStateOf<BotIncomingMessage?>(null) }
    val pendingImages = remember { mutableStateListOf<OutgoingImageAttachment>() }
    var showCustomFacePicker by remember { mutableStateOf(false) }
    var customFaceUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCustomFaceLoading by remember { mutableStateOf(false) }
    var customFaceLoadError by remember { mutableStateOf<String?>(null) }

    fun isUserAtBottom(): Boolean {
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return true

        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false

        // 检查最后一个可见项是否是列表中的最后一项（或倒数第二项）
        return lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
    }

    suspend fun scrollToSeq(seq: Long) {
        val index = messages.indexOfFirst { it.real?.sequence == seq }
        if (index != -1) {
            listState.scrollToItem(index)
        }
    }

    suspend fun scrollToBottom() {
        listState.scrollToItem((messages.size - 1).coerceAtLeast(0))
    }

    fun loadCustomFaceList() {
        if (isCustomFaceLoading) return
        coroutineScope.launch {
            isCustomFaceLoading = true
            customFaceLoadError = null
            runCatching { bot.getCustomFaceUrl() }
                .onSuccess { customFaceUrls = it }
                .onFailure { error ->
                    customFaceLoadError = error.message ?: "加载自定义表情失败"
                }
            isCustomFaceLoading = false
        }
    }

    suspend fun sendMessage(
        content: String,
        replySeq: Long?,
        imageAttachments: List<OutgoingImageAttachment>,
    ) {
        val groupMemberInfo = if (conversation.scene == MessageScene.GROUP) {
            runCatching {
                bot.getGroupMember(conversation.peerUin, bot.uin)
            }.onFailure {
                if (it is CancellationException) throw it
            }.getOrNull()?.toDisplayInfo()
        } else {
            null
        }

        val clientSequence = Random.nextLong()
        val random = Random.nextInt()

        val placeholder = PlaceholderMessage(
            clientSequence = clientSequence,
            random = random,
            timestamp = System.currentTimeMillis() / 1000,
            displaySegments = buildList {
                if (replySeq != null) {
                    add(DisplaySegment.Reply(BotIncomingSegment.Reply(replySeq)))
                }
                imageAttachments.forEach { attachment ->
                    add(
                        DisplaySegment.PendingImage(
                            bitmap = attachment.preview,
                            summary = attachment.summary,
                            width = attachment.width,
                            height = attachment.height,
                            subType = attachment.subType
                        )
                    )
                }
                if (content.isNotBlank()) {
                    add(DisplaySegment.Text(content))
                }
            },
            groupMemberInfo = groupMemberInfo
        )

        messages.add(DisplayMessage(placeholder = placeholder))
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
                    imageAttachments.forEach { attachment ->
                        image(
                            raw = attachment.raw,
                            format = attachment.format,
                            width = attachment.width,
                            height = attachment.height,
                            summary = attachment.summary,
                            subType = attachment.subType
                        )
                    }
                    if (content.isNotBlank()) {
                        text(content)
                    }
                }

                if (conversation.peerUin != bot.uin) {
                    val realMessage = bot.getFriendHistoryMessages(
                        friendUin = conversation.peerUin,
                        limit = 1,
                        startSequence = result.sequence
                    ).messages.firstOrNull()
                    realMessage?.let {
                        val placeholder = pendingMessages.remove(random)
                        val index = messages.indexOfFirst {
                            it.placeholder == placeholder
                        }
                        if (index != -1) {
                            messages[index] = DisplayMessage(real = realMessage)
                        } else {
                            messages.add(DisplayMessage(real = realMessage))
                            messages.sortBy { it.real?.sequence ?: Long.MAX_VALUE }
                        }
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
                    imageAttachments.forEach { attachment ->
                        image(
                            raw = attachment.raw,
                            format = attachment.format,
                            width = attachment.width,
                            height = attachment.height,
                            summary = attachment.summary,
                            subType = attachment.subType
                        )
                    }
                    if (content.isNotBlank()) {
                        text(content)
                    }
                }
            }

            else -> {}
        }
    }

    fun handleCustomFaceSelected(url: String) {
        coroutineScope.launch {
            val cached = CustomFaceCache.getOrLoad(url, httpClient) ?: return@launch
            val replySeq = replyToMessage?.sequence
            replyToMessage = null
            val attachment = cached.toAttachmentInstance()
            try {
                sendMessage(
                    content = "",
                    replySeq = replySeq,
                    imageAttachments = listOf(attachment)
                )
            } catch (e: Exception) {
                logger.e(e) { "发送自定义表情失败" }
            }
        }
    }

    fun handleTransferable(transferable: Transferable) {
        coroutineScope.launch {
            val attachments = readAttachmentsFromTransferable(transferable)
            if (attachments.isNotEmpty()) {
                pendingImages.addAll(attachments)
            }
        }
    }

    fun handleClipboardImage(): Boolean {
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() ?: return false
        val transferable = runCatching { clipboard.getContents(null) }.getOrNull() ?: return false
        if (!transferable.hasSupportedImagePayload()) {
            return false
        }
        handleTransferable(transferable)
        return true
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

            messages.addAll(
                0,
                historyMessages.messages.map(BotIncomingMessage::toDisplayMessage)
            )
            nextLoadSequence = historyMessages.nextStartSequence
            scrollToBottom()
        } catch (e: Exception) {
            logger.e(e) { "消息初始化失败" }
        }
    }

    // 监听属于当前会话的消息
    LaunchedEffect(conversation.id) {
        bot.eventFlow.filterIsInstance<MessageReceiveEvent>()
            .filter {
                it.message.let {
                    it.scene == conversation.scene && it.peerUin == conversation.peerUin
                }
            }
            .collect { event ->
                val message = event.message
                // 检查是否是待确认的占位符消息
                val placeholder = pendingMessages.remove(message.random)
                if (placeholder != null) {
                    // 删除占位符
                    val index = messages.indexOfFirst { it.placeholder == placeholder }
                    if (index != -1) {
                        messages[index] = DisplayMessage(real = message)
                    }
                } else {
                    messages.add(DisplayMessage(real = message))
                }

                if (isUserAtBottom()) {
                    scrollToBottom()
                }
            }
    }

    // 监听会话相关的其他事件，并以灰色提示的形式展示
    LaunchedEffect(conversation.id) {
        bot.eventFlow.collect { event ->
            val greyTipText = formatGreyTipForEvent(event, conversation, bot)
            if (greyTipText != null) {
                messages.add(DisplayMessage(greyTip = greyTipText))
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
            val oldestMessageSeq = messages.firstOrNull()?.real?.sequence

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
                    messages.addAll(
                        0,
                        historyMessages.messages.map(BotIncomingMessage::toDisplayMessage)
                    )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(config.theme.chatBackgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部标题栏
            ChatHeader(conversation)

            Divider()

            // 消息列表
            CompositionLocalProvider(LocalAllMessages provides messages) {
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
                        when {
                            message.real != null -> MessageBubble(
                                message = message.real,
                                onScrollToMessage = { seq ->
                                    coroutineScope.launch {
                                        scrollToSeq(seq)
                                    }
                                },
                                onReplyToMessage = { replyToMessage = it }
                            )

                            message.placeholder != null -> PlaceholderMessageBubble(
                                message = message.placeholder,
                                isGroup = conversation.scene == MessageScene.GROUP
                            )

                            message.greyTip != null -> GreyTip(
                                text = message.greyTip
                            )
                        }
                    }
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
            val canSendMessage = messageText.text.isNotBlank() || pendingImages.isNotEmpty()
            ChatInputArea(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                useCtrlEnterToSend = config.useCtrlEnterToSend,
                focusRequester = inputFocusRequester,
                imageAttachments = pendingImages,
                onRemoveAttachment = { attachmentId ->
                    pendingImages.removeAll { it.id == attachmentId }
                },
                canSendMessage = canSendMessage,
                onPasteImage = ::handleClipboardImage,
                onOpenCustomFacePicker = {
                    showCustomFacePicker = true
                    if (customFaceUrls.isEmpty()) {
                        loadCustomFaceList()
                    }
                },
                onSendMessage = {
                    if (!canSendMessage) return@ChatInputArea
                    val content = messageText.text
                    val replySeq = replyToMessage?.sequence
                    val attachmentsSnapshot = pendingImages.toList()

                    messageText = TextFieldValue("")
                    pendingImages.clear()
                    replyToMessage = null  // 立即隐藏回复UI

                    coroutineScope.launch {
                        try {
                            sendMessage(content, replySeq, attachmentsSnapshot)
                        } catch (e: Exception) {
                            logger.e(e) { "消息发送失败" }
                        }
                    }
                }
            )
        }

        if (showCustomFacePicker) {
            CustomFacePanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                urls = customFaceUrls,
                isLoading = isCustomFaceLoading,
                errorMessage = customFaceLoadError,
                onClose = { showCustomFacePicker = false },
                onRetry = { loadCustomFaceList() },
                loadFace = { url -> CustomFaceCache.getOrLoad(url, httpClient) },
                onSelect = { url ->
                    handleCustomFaceSelected(url)
                    showCustomFacePicker = false
                }
            )
        }
    }
}

private fun BotIncomingMessage.toDisplayMessage() = when {
    this.senderUin == 0L -> DisplayMessage(
        greyTip = "该消息已被撤回"
    )

    else -> DisplayMessage(
        real = this
    )
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChatInputArea(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    useCtrlEnterToSend: Boolean,
    focusRequester: FocusRequester,
    imageAttachments: List<OutgoingImageAttachment>,
    onRemoveAttachment: (String) -> Unit,
    canSendMessage: Boolean,
    onPasteImage: () -> Boolean,
    onOpenCustomFacePicker: () -> Unit,
    onSendMessage: () -> Unit
) {
    val pasteHandler by rememberUpdatedState(onPasteImage)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (imageAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imageAttachments, key = { it.id }) { attachment ->
                        AttachmentPreview(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(attachment.id) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onOpenCustomFacePicker,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.InsertEmoticon,
                        contentDescription = "自定义表情",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 100.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                if ((event.isMetaPressed || event.isCtrlPressed) && event.key == Key.V) {
                                    if (pasteHandler()) {
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                if (event.key == Key.Enter) {
                                    if (useCtrlEnterToSend) {
                                        if (event.isMetaPressed || event.isCtrlPressed) {
                                            onSendMessage()
                                            return@onPreviewKeyEvent true
                                        }
                                    } else {
                                        if (event.isMetaPressed || event.isCtrlPressed) {
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
                                            return@onPreviewKeyEvent true
                                        } else {
                                            onSendMessage()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                            }
                            false
                        },
                    placeholder = {
                        Text(
                            if (useCtrlEnterToSend) "输入消息... (⌘/Ctrl+Enter 发送，支持粘贴图片)"
                            else "输入消息... (Enter 发送，支持粘贴图片)"
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
                    onClick = { if (canSendMessage) onSendMessage() },
                    modifier = Modifier.size(40.dp),
                    backgroundColor = if (canSendMessage)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (canSendMessage)
                            MaterialTheme.colors.onPrimary
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    attachment: OutgoingImageAttachment,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Image(
            bitmap = attachment.preview,
            contentDescription = "[图片]",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    color = MaterialTheme.colors.surface.copy(alpha = 0.85f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除图片",
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CustomFacePanel(
    modifier: Modifier = Modifier,
    urls: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    loadFace: suspend (String) -> CustomFaceCache.CachedCustomFace?,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = 6.dp,
        color = MaterialTheme.colors.surface.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "自定义表情",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onClose) {
                    Text("关闭")
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            Text("重试")
                        }
                    }
                }

                urls.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无自定义表情",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    CustomFaceGrid(
                        urls = urls,
                        loadFace = loadFace,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFaceGrid(
    urls: List<String>,
    loadFace: suspend (String) -> CustomFaceCache.CachedCustomFace?,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(urls) { url ->
            CustomFaceGridItem(
                url = url,
                loadFace = loadFace,
                onSelect = { onSelect(url) }
            )
        }
    }
}

@Composable
private fun CustomFaceGridItem(
    url: String,
    loadFace: suspend (String) -> CustomFaceCache.CachedCustomFace?,
    onSelect: () -> Unit
) {
    var cachedFace by remember(url) { mutableStateOf<CustomFaceCache.CachedCustomFace?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var hasError by remember(url) { mutableStateOf(false) }
    var reloadSignal by remember { mutableStateOf(0) }

    LaunchedEffect(url, reloadSignal) {
        isLoading = true
        hasError = false
        cachedFace = loadFace(url)
        if (cachedFace == null) {
            hasError = true
        }
        isLoading = false
    }

    val clickModifier = when {
        cachedFace != null -> Modifier.clickable { onSelect() }
        hasError -> Modifier.clickable { reloadSignal++ }
        else -> Modifier
    }

    Surface(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            )
            .then(clickModifier),
        color = MaterialTheme.colors.surface,
        elevation = 2.dp
    ) {
        when {
            cachedFace != null -> {
                Image(
                    bitmap = cachedFace!!.attachment.preview,
                    contentDescription = "自定义表情",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            hasError -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

private suspend fun readAttachmentsFromTransferable(
    transferable: Transferable
): List<OutgoingImageAttachment> = withContext(Dispatchers.IO) {
    val attachments = mutableListOf<OutgoingImageAttachment>()

    val fileAttachments = transferable.extractImageFilePaths()
        .mapNotNull { path -> OutgoingImageAttachment.fromFile(path) }
    attachments.addAll(fileAttachments)

    if (attachments.isEmpty()) {
        val binaryImages = transferable.extractBinaryImages()
        binaryImages.forEachIndexed { index, raw ->
            val attachment = OutgoingImageAttachment.fromBytes(raw.bytes)
            if (attachment != null) {
                attachments.add(attachment)
            }
        }
    }

    if (attachments.isEmpty()) {
        val image = transferable.extractImageFromClipboard()
        val attachment = image?.let { OutgoingImageAttachment.fromAwtImage(it) }
        if (attachment != null) {
            attachments.add(attachment)
        }
    }

    attachments
}

private suspend fun formatGreyTipForEvent(
    event: Any,
    conversation: Conversation,
    bot: Bot
): String? = when (event) {
    is MessageRecallEvent -> formatMessageRecallGreyTip(event, conversation, bot)
    is FriendNudgeEvent -> formatFriendNudgeGreyTip(event, conversation, bot)
    is GroupAdminChangeEvent -> formatGroupAdminChangeGreyTip(event, conversation, bot)
    is GroupEssenceMessageChangeEvent -> formatGroupEssenceMessageGreyTip(event, conversation, bot)
    is GroupInvitationEvent -> formatGroupInvitationGreyTip(event, conversation, bot)
    is GroupInvitedJoinRequestEvent -> formatGroupInvitedJoinGreyTip(event, conversation, bot)
    is GroupJoinRequestEvent -> formatGroupJoinRequestGreyTip(event, conversation, bot)
    is GroupMemberIncreaseEvent -> formatGroupMemberIncreaseGreyTip(event, conversation, bot)
    is GroupMemberDecreaseEvent -> formatGroupMemberDecreaseGreyTip(event, conversation, bot)
    is GroupNameChangeEvent -> formatGroupNameChangeGreyTip(event, conversation, bot)
    is GroupMessageReactionEvent -> formatGroupMessageReactionGreyTip(event, conversation, bot)
    is GroupMuteEvent -> formatGroupMuteGreyTip(event, conversation, bot)
    is GroupWholeMuteEvent -> formatGroupWholeMuteGreyTip(event, conversation, bot)
    is GroupNudgeEvent -> formatGroupNudgeGreyTip(event, conversation, bot)
    else -> null
}

private suspend fun formatMessageRecallGreyTip(
    event: MessageRecallEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (event.scene != conversation.scene || event.peerUin != conversation.peerUin) {
        return null
    }
    return when (event.scene) {
        MessageScene.FRIEND -> {
            val friendLabel = bot.friendDisplayName(event.peerUin)
            buildString {
                if (event.senderUin == bot.uin) {
                    append("你撤回了一条消息")
                } else {
                    append(friendLabel)
                    append("撤回了一条消息")
                }
                if (event.displaySuffix.isNotBlank()) {
                    append("，")
                    append(event.displaySuffix)
                }
            }
        }

        MessageScene.GROUP -> {
            val senderLabel = bot.groupMemberDisplayName(event.peerUin, event.senderUin)
            val operatorLabel = bot.groupMemberDisplayName(event.peerUin, event.operatorUin)
            buildString {
                append(senderLabel)
                if (event.senderUin == event.operatorUin) {
                    append("撤回了一条消息")
                } else {
                    append("的消息被")
                    append(operatorLabel)
                    append("撤回")
                }
                if (event.displaySuffix.isNotBlank()) {
                    append("，")
                    append(event.displaySuffix)
                }
            }
        }

        else -> null
    }
}

private suspend fun formatFriendNudgeGreyTip(
    event: FriendNudgeEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesFriend(event.userUin)) return null
    val friendLabel = bot.friendDisplayName(event.userUin)
    return buildString {
        if (event.isSelfSend) {
            append("你")
            append(event.displayAction)
            if (event.isSelfReceive) {
                append("自己")
            } else {
                append(friendLabel)
            }
            append(event.displaySuffix)
        } else {
            append(friendLabel)
            append(event.displayAction)
            if (event.isSelfReceive) {
                append("你")
            } else {
                append("自己")
            }
            append(event.displaySuffix)
        }
    }
}

private suspend fun formatGroupAdminChangeGreyTip(
    event: GroupAdminChangeEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val userLabel = bot.groupMemberDisplayName(event.groupUin, event.userUin)
    return buildString {
        append(userLabel)
        append(if (event.isSet) "被设置为" else "被取消")
        append("管理员")
    }
}

private fun formatGroupEssenceMessageGreyTip(
    event: GroupEssenceMessageChangeEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    return buildString {
        append("消息#")
        append(event.messageSeq)
        append(if (event.isSet) "被设置为" else "被取消")
        append("精华消息")
    }
}

private suspend fun formatGroupInvitationGreyTip(
    event: GroupInvitationEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val initiatorLabel = bot.groupMemberDisplayName(event.groupUin, event.initiatorUin)
    return "${initiatorLabel}发起了群邀请"
}

private suspend fun formatGroupInvitedJoinGreyTip(
    event: GroupInvitedJoinRequestEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val initiatorLabel = bot.groupMemberDisplayName(event.groupUin, event.initiatorUin)
    return buildString {
        append(initiatorLabel)
        append("邀请")
        append(event.targetUserUin)
        append("加入群聊")
    }
}

private fun formatGroupJoinRequestGreyTip(
    event: GroupJoinRequestEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val comment = event.comment
    return buildString {
        append("收到")
        append(event.initiatorUin)
        append("的入群申请")
        if (comment.isNotBlank()) {
            append("，附加信息：")
            append(comment)
        }
    }
}

private suspend fun formatGroupMemberIncreaseGreyTip(
    event: GroupMemberIncreaseEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val userLabel = bot.groupMemberDisplayName(event.groupUin, event.userUin)
    return buildString {
        append(userLabel)
        when {
            event.operatorUin != null -> {
                append("被")
                append(bot.groupMemberDisplayName(event.groupUin, event.operatorUin!!))
                append("同意加入群聊")
            }

            event.invitorUin != null -> {
                append("被")
                append(bot.groupMemberDisplayName(event.groupUin, event.invitorUin!!))
                append("邀请加入群聊")
            }

            else -> append("加入了群聊")
        }
    }
}

private suspend fun formatGroupMemberDecreaseGreyTip(
    event: GroupMemberDecreaseEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val userLabel = bot.groupMemberDisplayName(event.groupUin, event.userUin)
    return buildString {
        append(userLabel)
        if (event.operatorUin != null && event.operatorUin != event.userUin) {
            append("被")
            append(bot.groupMemberDisplayName(event.groupUin, event.operatorUin!!))
            append("移出群聊")
        } else {
            append("退出了群聊")
        }
    }
}

private suspend fun formatGroupNameChangeGreyTip(
    event: GroupNameChangeEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val operatorLabel = bot.groupMemberDisplayName(event.groupUin, event.operatorUin)
    return buildString {
        append(operatorLabel)
        append("将群名称修改为：")
        append(event.newGroupName)
    }
}

private suspend fun formatGroupMessageReactionGreyTip(
    event: GroupMessageReactionEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val userLabel = bot.groupMemberDisplayName(event.groupUin, event.userUin)
    val faceDescription = bot.faceDetailMap[event.faceId]?.qDes ?: event.faceId
    return buildString {
        append(userLabel)
        if (event.isAdd) {
            append("对消息#")
            append(event.messageSeq)
            append("添加了表情回应")
        } else {
            append("取消了对消息#")
            append(event.messageSeq)
            append("的表情回应")
        }
        append(faceDescription)
    }
}

private suspend fun formatGroupMuteGreyTip(
    event: GroupMuteEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val userLabel = bot.groupMemberDisplayName(event.groupUin, event.userUin)
    val operatorLabel = bot.groupMemberDisplayName(event.groupUin, event.operatorUin)
    return buildString {
        append(userLabel)
        if (event.duration == 0) {
            append("被")
            append(operatorLabel)
            append("解除禁言")
        } else {
            append("被")
            append(operatorLabel)
            append("禁言")
            append(event.duration)
            append("秒")
        }
    }
}

private suspend fun formatGroupWholeMuteGreyTip(
    event: GroupWholeMuteEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val operatorLabel = bot.groupMemberDisplayName(event.groupUin, event.operatorUin)
    return buildString {
        append(operatorLabel)
        if (event.isMute) {
            append("开启了全员禁言")
        } else {
            append("关闭了全员禁言")
        }
    }
}

private suspend fun formatGroupNudgeGreyTip(
    event: GroupNudgeEvent,
    conversation: Conversation,
    bot: Bot
): String? {
    if (!conversation.matchesGroup(event.groupUin)) return null
    val senderIsSelf = event.senderUin == bot.uin
    val receiverIsSelf = event.receiverUin == bot.uin
    val senderLabel = bot.groupMemberDisplayName(event.groupUin, event.senderUin)
    val receiverLabel = bot.groupMemberDisplayName(event.groupUin, event.receiverUin)
    return buildString {
        if (senderIsSelf) {
            append("你")
        } else {
            append(senderLabel)
        }
        append(event.displayAction)
        if (receiverIsSelf) {
            if (senderIsSelf) {
                append("自己")
            } else {
                append("你")
            }
        } else {
            append(receiverLabel)
        }
        append(event.displaySuffix)
    }
}

private fun Conversation.matchesGroup(groupUin: Long) =
    scene == MessageScene.GROUP && peerUin == groupUin

private fun Conversation.matchesFriend(uin: Long) =
    scene == MessageScene.FRIEND && peerUin == uin

private suspend fun Bot.friendDisplayName(uin: Long): String {
    val friend = safeFriend(uin)
    return friend?.displayString ?: uin.toString()
}

private suspend fun Bot.groupMemberDisplayName(groupUin: Long, memberUin: Long): String {
    val member = safeGroupMember(groupUin, memberUin)
    return member?.displayString ?: memberUin.toString()
}

private suspend fun Bot.safeFriend(uin: Long): BotFriend? = runIgnoringCancellation {
    getFriend(uin)
}

private suspend fun Bot.safeGroupMember(groupUin: Long, memberUin: Long): BotGroupMember? = runIgnoringCancellation {
    getGroupMember(groupUin, memberUin)
}

private suspend fun <T> runIgnoringCancellation(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

private val BotFriend.displayName: String
    get() = remark.takeIf { it.isNotBlank() } ?: nickname

private val BotFriend.displayString: String
    get() = displayName.toSingleLine()

private val BotGroupMember.displayName: String
    get() = card.takeIf { it.isNotBlank() } ?: nickname

private val BotGroupMember.displayString: String
    get() = displayName.toSingleLine()

private fun String.toSingleLine(): String {
    val builder = StringBuilder(length)
    for (c in this) {
        builder.append(
            if (c == '\n' || c == '\r') {
                ' '
            } else {
                c
            }
        )
    }
    return builder.toString()
}
