package org.ntqqrev.cecilia.view

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot.Companion.withMutableSnapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Pin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.event.MessageRecallEvent
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.*
import org.ntqqrev.cecilia.component.AnimatedImage
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.DraggableDivider
import org.ntqqrev.cecilia.component.message.Bubble
import org.ntqqrev.cecilia.component.message.GreyTip
import org.ntqqrev.cecilia.component.message.LocalBubble
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalConfig
import org.ntqqrev.cecilia.core.LocalEmojiImageFallback
import org.ntqqrev.cecilia.core.LocalEmojiImages
import org.ntqqrev.cecilia.model.*
import org.ntqqrev.cecilia.util.displayName
import org.ntqqrev.cecilia.util.formatToConvenientTime
import org.ntqqrev.cecilia.util.formatToShortTime
import org.ntqqrev.cecilia.util.toPreviewText
import java.time.Instant
import kotlin.random.Random

val LocalJumpToMessage = compositionLocalOf<((Long) -> Unit)?> { null }

@Composable
fun ChatView() {
    val windowState = rememberWindowState()
    var leftPanelWidth by remember { mutableStateOf(windowState.size.width * 0.35f) }

    val bot = LocalBot.current
    val conversations = remember { mutableStateMapOf<Conversation.Key, Conversation>() }
    var activeConversation by remember { mutableStateOf<Conversation.Key?>(null) }

    suspend fun BotIncomingMessage.buildPreview() = buildString {
        if (scene == MessageScene.GROUP) {
            val groupMember = bot.getGroupMember(
                groupUin = peerUin,
                memberUin = senderUin
            )
            if (groupMember != null) {
                append(groupMember.displayName)
            } else {
                append(senderUin)
            }
            append(": ")
        }
        append(toPreviewText())
    }

    LaunchedEffect(bot) {
        val pinnedChats = bot.getPins()
        val initialConversations = buildList {
            addAll(
                pinnedChats.friendUins
                    .mapNotNull { bot.getFriend(it) }
                    .map { friend -> Conversation.fromFriend(friend, isPinned = true) }
            )
            addAll(
                pinnedChats.groupUins
                    .mapNotNull { bot.getGroup(it) }
                    .map { group -> Conversation.fromGroup(group, isPinned = true) }
            )
        }
        withMutableSnapshot {
            initialConversations.forEach { conversation ->
                conversations[conversation.asKey] = conversation
            }
        }

        bot.eventFlow.collect { event ->
            if (event is MessageReceiveEvent) {
                withMutableSnapshot {
                    val conversationKey = Conversation.Key(
                        scene = event.message.scene,
                        peerUin = event.message.peerUin
                    )
                    val prev = conversations[conversationKey] ?: when (event.message.scene) {
                        MessageScene.FRIEND -> {
                            val friend = bot.getFriend(event.message.peerUin)
                            friend?.let {
                                Conversation.fromFriend(
                                    friend = it,
                                    isPinned = false
                                )
                            }
                        }

                        MessageScene.GROUP -> {
                            val group = bot.getGroup(event.message.peerUin)
                            group?.let {
                                Conversation.fromGroup(
                                    group = it,
                                    isPinned = false
                                )
                            }
                        }

                        else -> null
                    }
                    val current = prev?.copy(
                        lastMessageTime = event.message.timestamp,
                        lastMessagePreview = event.message.buildPreview(),
                        unreadCount = if (activeConversation == conversationKey) 0
                        else prev.unreadCount + 1,
                    )
                    current?.let { conversations[conversationKey] = it }
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.width(leftPanelWidth)) {
            Layer {
                Column {
                    val conversationListScrollState = rememberScrollState()
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "聊天",
                            fontSize = 16.sp,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .height(1.dp)
                            .background(FluentTheme.colors.stroke.divider.default)
                    )
                    Column(
                        modifier = Modifier.fillMaxHeight()
                            .padding(top = 4.dp)
                            .verticalScroll(conversationListScrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        conversations.values.sorted().forEach { conversation ->
                            ConversationDisplay(
                                conversation = conversation,
                                isSelected = conversation.asKey == activeConversation,
                                onClick = {
                                    withMutableSnapshot {
                                        activeConversation = if (activeConversation != conversation.asKey) {
                                            conversation.asKey
                                        } else {
                                            null
                                        }
                                        conversations[conversation.asKey] = conversation.copy(unreadCount = 0)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        DraggableDivider(
            currentWidth = leftPanelWidth,
            onWidthChange = { leftPanelWidth = it },
            showDivider = false,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (activeConversation != null) {
                ChatArea(
                    conversation = conversations[activeConversation]!!
                )
            } else {
                Text(
                    text = "选择一个会话开始聊天",
                    style = FluentTheme.typography.bodyLarge,
                    color = FluentTheme.colors.text.text.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationDisplay(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier.padding(horizontal = 4.dp)
            .then(
                if (isSelected) {
                    Modifier.background(
                        color = FluentTheme.colors.fillAccent.default.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                } else if (isHovered) {
                    Modifier.background(
                        color = Color(0f, 0f, 0f, 0.05f),
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
            .hoverable(interactionSource)
            .onClick { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                AvatarImage(
                    uin = conversation.peerUin,
                    size = 48.dp,
                    isGroup = conversation.scene == MessageScene.GROUP,
                    quality = 100
                )
                if (conversation.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.Pin,
                        contentDescription = "置顶",
                        modifier = Modifier.size(12.dp),
                        tint = FluentTheme.colors.fillAccent.default
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.displayName,
                        style = FluentTheme.typography.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    conversation.lastMessageTime?.let {
                        Text(
                            text = Instant.ofEpochSecond(it).formatToShortTime(),
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    conversation.lastMessagePreview?.let {
                        Text(
                            text = it,
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (conversation.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(FluentTheme.colors.system.critical),
                            contentAlignment = Alignment.Center
                        ) {
                            val textSize = 11.sp
                            Text(
                                text = conversation.unreadCount.coerceIn(1..99).toString(),
                                fontSize = textSize,
                                lineHeight = textSize,
                                fontWeight = FontWeight.SemiBold,
                                color = FluentTheme.colors.text.onAccent.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.wrapContentSize(Alignment.Center)
                            )
                        }
                    } else {
                        // a 20dp placeholder box
                        Box(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatArea(conversation: Conversation) {
    val bot = LocalBot.current
    val scope = rememberCoroutineScope()
    var blinkSequence by remember { mutableStateOf<Long?>(null) }
    var groupMemberCount by remember(conversation.asKey) { mutableStateOf<Int?>(null) }
    val messageLikeList = remember(bot, conversation.asKey) {
        mutableStateListOf<MessageLike>()
    }

    val listState = rememberLazyListState()
    val lastVisibleItemIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 1
        }
    }
    var isLoadingFurtherHistoryMessages by remember(conversation) { mutableStateOf(false) }

    suspend fun resolveSubject(groupUin: Long, memberUin: Long): String {
        return if (memberUin == bot.uin) {
            "你"
        } else {
            val member = bot.getGroupMember(
                groupUin = groupUin,
                memberUin = memberUin
            )
            member?.displayName ?: memberUin.toString()
        }
    }

    suspend fun BotIncomingMessage.toModel(): MessageLike = if (senderUin != 0L) {
        Message(
            scene = this.scene,
            peerUin = this.peerUin,
            sequence = this.sequence,
            senderUin = this.senderUin,
            senderName = this.extraInfo?.groupCard ?: this.extraInfo?.nick ?: this.senderUin.toString(),
            timestamp = this.timestamp,
            elements = buildList {
                var buffer = AnnotatedString.Builder()
                val inlines = mutableMapOf<String, InlineTextContent>()

                fun flush() {
                    if (buffer.length > 0) {
                        add(
                            Element.RichText(
                                content = buffer.toAnnotatedString(),
                                inlines = inlines
                            )
                        )
                        buffer = AnnotatedString.Builder()
                    }
                }

                segments.forEach {
                    when (it) {
                        is BotIncomingSegment.Text -> {
                            buffer.append(it.text)
                        }

                        is BotIncomingSegment.Mention -> {
                            buffer.append(it.name)
                        }

                        is BotIncomingSegment.Face -> {
                            if (it.isLarge) {
                                flush()
                                add(Element.LargeFace(faceId = it.faceId))
                            } else {
                                buffer.appendInlineContent(
                                    id = "face/${it.faceId}",
                                    alternateText = it.summary,
                                )
                                inlines["face/${it.faceId}"] = InlineTextContent(
                                    placeholder = Placeholder(
                                        width = 16.sp,
                                        height = 16.sp,
                                        PlaceholderVerticalAlign.Center
                                    )
                                ) { _ ->
                                    val emojiImages = LocalEmojiImages.current
                                    if (emojiImages != null) {
                                        emojiImages[it.faceId.toString()]?.let { v ->
                                            if (v.apng != null) {
                                                AnimatedImage(
                                                    frames = v.apng,
                                                    contentDescription = "表情 ${it.faceId}",
                                                    modifier = Modifier.size(16.sp.value.dp)
                                                )
                                            } else {
                                                Image(
                                                    bitmap = v.png,
                                                    contentDescription = "表情 ${it.faceId}",
                                                    modifier = Modifier.size(16.sp.value.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Image(
                                            bitmap = LocalEmojiImageFallback.current,
                                            contentDescription = "表情 ${it.faceId}",
                                            modifier = Modifier.size(16.sp.value.dp)
                                        )
                                    }
                                }
                            }
                        }

                        is BotIncomingSegment.Reply -> {
                            flush()
                            when (this@toModel.scene) {
                                MessageScene.FRIEND -> add (
                                    Element.Reply(
                                        sequence = it.sequence,
                                        senderName = if (it.senderUin == bot.uin) {
                                            "你"
                                        } else {
                                            val friend = bot.getFriend(it.senderUin)
                                            friend?.displayName ?: it.senderUin.toString()
                                        },
                                        content = it.segments.toPreviewText()
                                    )
                                )

                                MessageScene.GROUP -> add (
                                    Element.Reply(
                                        sequence = it.sequence,
                                        senderName = resolveSubject(
                                            groupUin = peerUin,
                                            memberUin = it.senderUin
                                        ),
                                        content = it.segments.toPreviewText()
                                    )
                                )

                                else -> {}
                            }
                        }

                        is BotIncomingSegment.Image -> {
                            flush()
                            add(
                                Element.Image(
                                    fileId = it.fileId,
                                    width = it.width,
                                    height = it.height,
                                    subType = it.subType,
                                    summary = it.summary,
                                )
                            )
                        }

                        is BotIncomingSegment.MarketFace -> {
                            flush()
                            add(
                                Element.Image(
                                    fileId = it.url,
                                    width = 300,
                                    height = 300,
                                    subType = ImageSubType.STICKER,
                                    summary = it.summary,
                                )
                            )
                        }

                        else -> {
                            buffer.append("[${it::class.simpleName}]")
                        }
                    }
                }
                flush()
            },
            member = if (this.scene == MessageScene.GROUP) {
                bot.getGroupMember(
                    groupUin = this.peerUin,
                    memberUin = this.senderUin
                )
            } else {
                null
            }
        )
    } else {
        Notification("该消息已被撤回")
    }

    suspend fun MessageRecallEvent.buildRecallNotice(): String = when (this.scene) {
        MessageScene.FRIEND -> buildString {
            if (senderUin == bot.uin) {
                append("你")
            } else {
                append("对方")
            }
            append("撤回了一条消息")
        }

        MessageScene.GROUP -> buildString {
            // Subject
            append(
                resolveSubject(
                    groupUin = peerUin,
                    memberUin = operatorUin
                )
            )
            // Action
            append("撤回了")
            // Optional - sender of the message, if sender != operator
            if (operatorUin != senderUin) {
                append(
                    resolveSubject(
                        groupUin = peerUin,
                        memberUin = senderUin
                    )
                )
                append("的")
            }
            append("一条消息")
            displaySuffix.takeIf { it.isNotEmpty() }?.let {
                append("，$it")
            }
        }

        else -> "该消息已被撤回"
    }

    LaunchedEffect(bot, conversation.asKey) {
        if (conversation.scene == MessageScene.GROUP) {
            val group = bot.getGroup(conversation.peerUin)
            groupMemberCount = group?.memberCount
        }
    }

    LaunchedEffect(bot, conversation.asKey) {
        runCatching {
            when (conversation.scene) {
                MessageScene.FRIEND -> bot.getFriendHistoryMessages(
                    friendUin = conversation.peerUin,
                    limit = 30,
                ).messages

                MessageScene.GROUP -> bot.getGroupHistoryMessages(
                    groupUin = conversation.peerUin,
                    limit = 30,
                ).messages

                else -> emptyList()
            }
                .map { async { it.toModel() } }
                .awaitAll()
        }.onSuccess { messageLikes ->
            messageLikeList.addAll(index = 0, elements = messageLikes)
        }

        bot.eventFlow.collect { event ->
            when (event) {
                is MessageReceiveEvent -> {
                    if (event.message.scene == conversation.scene &&
                        event.message.peerUin == conversation.peerUin &&
                        event.message.senderUin != bot.uin
                    ) {
                        messageLikeList.add(event.message.toModel())
                    }
                }

                is MessageRecallEvent -> {
                    // Search from end for better performance
                    withMutableSnapshot {
                        for (i in messageLikeList.size - 1 downTo 0) {
                            val messageLike = messageLikeList[i]
                            if ((messageLike as? Message)?.sequence == event.messageSeq) {
                                messageLikeList[i] = Notification(event.buildRecallNotice())
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(lastVisibleItemIndex) {
        val oldestMessage = messageLikeList.firstOrNull { it is Message } as Message?
        if (
            lastVisibleItemIndex == messageLikeList.size - 1 &&
            !isLoadingFurtherHistoryMessages &&
            oldestMessage != null &&
            oldestMessage.sequence > 1
        ) {
            isLoadingFurtherHistoryMessages = true

            runCatching {
                when (conversation.scene) {
                    MessageScene.FRIEND -> bot.getFriendHistoryMessages(
                        friendUin = conversation.peerUin,
                        startSequence = oldestMessage.sequence - 1,
                        limit = 30,
                    ).messages

                    MessageScene.GROUP -> bot.getGroupHistoryMessages(
                        groupUin = conversation.peerUin,
                        startSequence = oldestMessage.sequence - 1,
                        limit = 30,
                    ).messages

                    else -> emptyList()
                }
                    .map { async { it.toModel() } }
                    .awaitAll()
            }.onSuccess { furtherMessageLikes ->
                if (furtherMessageLikes.isNotEmpty()) {
                    messageLikeList.addAll(index = 0, elements = furtherMessageLikes)
                    isLoadingFurtherHistoryMessages = false
                } // otherwise reached the end of history, no longer load more
            }
        }
    }

    LaunchedEffect(isLoadingFurtherHistoryMessages) {
        if (listState.firstVisibleItemIndex > 0 && !isLoadingFurtherHistoryMessages) {
            // make previously loaded history messages visible
            listState.scrollToItem(listState.firstVisibleItemIndex + 1)
        }
    }

    LaunchedEffect(messageLikeList.size) {
        // auto scroll to bottom when new message arrives
        if (listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Layer(Modifier.padding(horizontal = 8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = buildString {
                        append(conversation.displayName)
                        if (groupMemberCount != null) {
                            append(" ($groupMemberCount)")
                        }
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        CompositionLocalProvider(
            LocalJumpToMessage provides { sequence ->
                val currentOldestSequence = messageLikeList.firstOrNull { it is Message }?.let {
                    (it as Message).sequence
                } ?: 0L
                if (sequence >= currentOldestSequence) {
                    val targetIndex = messageLikeList.indexOfFirst {
                        (it as? Message)?.sequence == sequence ||
                                (it as? LocalMessage)?.sequence == sequence
                    }
                    if (targetIndex >= 0) {
                        scope.launch {
                            val itemIndex = messageLikeList.size - targetIndex - 1
                            listState.scrollToItem(
                                (itemIndex - listState.layoutInfo.visibleItemsInfo.size / 2)
                                    .coerceAtLeast(0)
                                // make the target message at center of the viewport
                            )
                            blinkSequence = sequence
                            delay(1500L)
                            blinkSequence = null
                        }
                    }
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
            ) {
                items(
                    count = messageLikeList.size,
                    key = { index ->
                        when (val messageLike = messageLikeList[messageLikeList.size - index - 1]) {
                            is Message -> "message-${messageLike.sequence}"
                            is LocalMessage -> "local-message-${messageLike.random}-${messageLike.timestamp}"
                            is Notification -> "notification-$index-${messageLike.hashCode()}"
                        }
                    }
                ) { index ->
                    when (val currentMessageLike = messageLikeList[messageLikeList.size - index - 1]) {
                        is Message, is LocalMessage -> {
                            when (currentMessageLike) {
                                is Message -> {
                                    Bubble(
                                        message = currentMessageLike,
                                        blink = currentMessageLike.sequence == blinkSequence
                                    )
                                }

                                is LocalMessage -> {
                                    LocalBubble(
                                        message = currentMessageLike,
                                        blink = currentMessageLike.sequence != null
                                                && currentMessageLike.sequence == blinkSequence
                                    )
                                }

                                else -> {}
                            }

                            // compare with prev timestamp
                            val currentMessageTimestamp = when (currentMessageLike) {
                                is Message -> currentMessageLike.timestamp
                                is LocalMessage -> currentMessageLike.timestamp
                                else -> 0L
                            }
                            val previousMessageTimestamp = messageLikeList.slice(0..messageLikeList.size - index - 2)
                                .lastOrNull { it is Message || it is LocalMessage }
                                ?.let {
                                    when (it) {
                                        is Message -> it.timestamp
                                        is LocalMessage -> it.timestamp
                                        else -> 0L
                                    }
                                }
                            val shouldShowTimeTip = previousMessageTimestamp?.let {
                                currentMessageTimestamp - previousMessageTimestamp >= 300
                            } ?: true
                            if (shouldShowTimeTip) {
                                GreyTip(
                                    content = Instant.ofEpochSecond(currentMessageTimestamp)
                                        .formatToConvenientTime()
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        is Notification -> {
                            GreyTip(content = currentMessageLike.content)
                        }
                    }
                }
            }
        }
        Layer(Modifier.padding(horizontal = 8.dp)) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                ChatInput(
                    conversationKey = conversation.asKey,
                    onSendMessage = { messageLikeList += it },
                    onSendMessageComplete = {
                        withMutableSnapshot {
                            val lastIndex = messageLikeList.indexOfLast { ml ->
                                ml is LocalMessage && ml.random == it.random && ml.timestamp == it.timestamp
                            }
                            if (lastIndex >= 0) {
                                messageLikeList[lastIndex] = it
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatInput(
    modifier: Modifier = Modifier,
    conversationKey: Conversation.Key,
    onSendMessage: (LocalMessage) -> Unit,
    onSendMessageComplete: (LocalMessage) -> Unit,
) {
    val bot = LocalBot.current
    val config = LocalConfig.current
    val state = rememberTextFieldState()
    var enterStartedWithModifier by remember { mutableStateOf(false) }
    var enterConsumedForSend by remember { mutableStateOf(false) }

    suspend fun sendMessage() {
        if (state.text.isBlank()) return

        val sendText = state.text.toString().trim()
        val localMessage = LocalMessage(
            scene = conversationKey.scene,
            peerUin = conversationKey.peerUin,
            random = Random.nextInt(),
            timestamp = Instant.now().epochSecond,
            text = sendText,
        )
        onSendMessage(localMessage)

        val sendMessageBlock: suspend BotOutgoingMessageBuilder.() -> Unit = {
            text(sendText)
        }
        state.edit { delete(0, length) }

        val result = when (conversationKey.scene) {
            MessageScene.FRIEND -> {
                bot.sendFriendMessage(
                    friendUin = conversationKey.peerUin,
                    build = sendMessageBlock,
                )
            }

            MessageScene.GROUP -> {
                bot.sendGroupMessage(
                    groupUin = conversationKey.peerUin,
                    build = sendMessageBlock,
                )
            }

            else -> null
        }
        result?.let { onSendMessageComplete(localMessage.copy(sequence = result.sequence)) }
    }

    TextField(
        state = state,
        modifier = modifier.fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Enter) return@onPreviewKeyEvent false

                if (config.useCtrlEnterToSend) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            enterStartedWithModifier = event.isCtrlPressed || event.isMetaPressed
                            false
                        }

                        KeyEventType.KeyUp -> {
                            val hasModifierNow = event.isCtrlPressed || event.isMetaPressed
                            val effectiveHasModifier = hasModifierNow || enterStartedWithModifier
                            enterStartedWithModifier = false
                            if (effectiveHasModifier) {
                                bot.launch { sendMessage() }
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val hasModifier = event.isCtrlPressed || event.isMetaPressed
                            return@onPreviewKeyEvent if (!hasModifier) {
                                // Enter alone: send message, do not insert newline.
                                bot.launch { sendMessage() }
                                enterConsumedForSend = true
                                true
                            } else {
                                // Ctrl/Cmd + Enter: manually insert newline.
                                state.edit { append('\n') }
                                enterConsumedForSend = false
                                true
                            }
                        }

                        KeyEventType.KeyUp -> {
                            // If Enter was already used to send, also consume KeyUp.
                            return@onPreviewKeyEvent if (enterConsumedForSend) {
                                enterConsumedForSend = false
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                }
            },
    )
}