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
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot.Companion.withMutableSnapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Pin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.event.MessageRecallEvent
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.event.PinChangedEvent
import org.ntqqrev.acidify.message.*
import org.ntqqrev.cecilia.component.AnimatedImage
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.DraggableDivider
import org.ntqqrev.cecilia.component.message.Bubble
import org.ntqqrev.cecilia.component.message.GreyTip
import org.ntqqrev.cecilia.component.message.LocalBubble
import org.ntqqrev.cecilia.component.message.MessageReply
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalConfig
import org.ntqqrev.cecilia.core.LocalEmojiImageFallback
import org.ntqqrev.cecilia.core.LocalEmojiImages
import org.ntqqrev.cecilia.model.*
import org.ntqqrev.cecilia.model.lightapp.LightAppPayload
import org.ntqqrev.cecilia.util.*
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

    suspend fun Conversation.Key.resolveConversation(isPinnedOnCreating: Boolean): Conversation? {
        return when (scene) {
            MessageScene.FRIEND -> {
                val friend = bot.getFriend(peerUin)
                friend?.let {
                    Conversation.fromFriend(
                        friend = it,
                        isPinned = isPinnedOnCreating
                    )
                }
            }

            MessageScene.GROUP -> {
                val group = bot.getGroup(peerUin)
                group?.let {
                    Conversation.fromGroup(
                        group = it,
                        isPinned = isPinnedOnCreating
                    )
                }
            }

            else -> null
        }
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
            when (event) {
                is MessageReceiveEvent -> withMutableSnapshot {
                    val conversationKey = Conversation.Key(
                        scene = event.message.scene,
                        peerUin = event.message.peerUin
                    )
                    val prev = conversations[conversationKey]
                        ?: conversationKey.resolveConversation(isPinnedOnCreating = false)
                    val current = prev?.copy(
                        lastMessageTime = event.message.timestamp,
                        lastMessagePreview = event.message.buildPreview(),
                        unreadCount = if (activeConversation == conversationKey) 0
                        else prev.unreadCount + 1,
                    )
                    current?.let { conversations[conversationKey] = it }
                }

                is PinChangedEvent -> withMutableSnapshot {
                    val conversationKey = Conversation.Key(
                        scene = event.scene,
                        peerUin = event.peerUin
                    )
                    val prev = conversations[conversationKey]
                    when (event.isPinned) {
                        true -> {
                            val current = prev
                                ?: conversationKey.resolveConversation(isPinnedOnCreating = true)
                            current?.let {
                                conversations[conversationKey] = it.copy(isPinned = true)
                            }
                        }

                        false -> {
                            if (prev != null) {
                                conversations[conversationKey] = prev.copy(isPinned = false)
                            }
                        }
                    }
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
                            .height(44.dp)
                            .padding(horizontal = 16.dp),
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
                    Box(
                        Modifier.fillMaxHeight()
                            .padding(top = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
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

                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(conversationListScrollState),
                        )
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
                        color = Color.Black.copy(alpha = 0.05f),
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
    var replyElement by remember(conversation.asKey) { mutableStateOf<Element.Reply?>(null) }

    val listState = rememberLazyListState()
    val lastVisibleItemIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 1
        }
    }
    var isLoadingFurtherHistoryMessages by remember(conversation) { mutableStateOf(false) }
    var haveSentMessage by remember(conversation) { mutableStateOf(false) }

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
                                MessageScene.FRIEND -> add(
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

                                MessageScene.GROUP -> add(
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

                        is BotIncomingSegment.Forward -> {
                            flush()
                            add(
                                Element.Forward(
                                    resId = it.resId,
                                    title = it.title,
                                    preview = it.preview,
                                    summary = it.summary,
                                )
                            )
                        }

                        is BotIncomingSegment.LightApp -> {
                            flush()
                            val lightApp = LightAppPayload.fromJson(it.jsonPayload)
                            add(
                                Element.LightApp(
                                    payload = lightApp
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
                        event.message.peerUin == conversation.peerUin
                    ) {
                        if (event.message.senderUin != bot.uin) {
                            messageLikeList.add(event.message.toModel())
                        } else {
                            // is self message, check its random
                            withMutableSnapshot {
                                // for better performance, search from end and limit to last 20 messages
                                for (i in messageLikeList.size - 1 downTo (messageLikeList.size - 20).coerceAtLeast(0)) {
                                    val messageLike = messageLikeList[i]
                                    if (messageLike is LocalMessage &&
                                        messageLike.random == event.message.random
                                    ) {
                                        return@withMutableSnapshot
                                    }
                                }
                                messageLikeList.add(event.message.toModel()) // treat as normal incoming message
                            }
                        }
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

    LaunchedEffect(messageLikeList.size, haveSentMessage) {
        // auto scroll to bottom when new message arrives
        if (listState.firstVisibleItemIndex <= 2 || haveSentMessage) {
            listState.animateScrollToItem(0)
            haveSentMessage = false
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
                                index = itemIndex,
                                scrollOffset = -listState.layoutInfo.viewportSize.height / 2
                            )
                            blinkSequence = sequence
                            delay(800L)
                            blinkSequence = null
                        }
                    }
                }
            }
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                            blink = currentMessageLike.sequence == blinkSequence,
                                            onDoubleClick = {
                                                scope.launch {
                                                    replyElement = currentMessageLike.let {
                                                        Element.Reply(
                                                            sequence = it.sequence,
                                                            senderName = when (it.scene) {
                                                                MessageScene.FRIEND -> {
                                                                    if (it.senderUin == bot.uin) {
                                                                        "你"
                                                                    } else {
                                                                        val friend = bot.getFriend(it.senderUin)
                                                                        friend?.displayName ?: it.senderUin.toString()
                                                                    }
                                                                }

                                                                MessageScene.GROUP -> {
                                                                    resolveSubject(
                                                                        groupUin = it.peerUin,
                                                                        memberUin = it.senderUin
                                                                    )
                                                                }

                                                                else -> it.senderUin.toString()
                                                            },
                                                            content = it.toPreviewText()
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }

                                    is LocalMessage -> {
                                        LocalBubble(
                                            message = currentMessageLike,
                                            blink = currentMessageLike.sequence != null
                                                    && currentMessageLike.sequence == blinkSequence,
                                            onDoubleClick = {
                                                if (currentMessageLike.sequence != null) {
                                                    replyElement = Element.Reply(
                                                        sequence = currentMessageLike.sequence,
                                                        senderName = "你",
                                                        content = currentMessageLike.toPreviewText()
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }

                                // compare with prev timestamp
                                val currentMessageTimestamp = when (currentMessageLike) {
                                    is Message -> currentMessageLike.timestamp
                                    is LocalMessage -> currentMessageLike.timestamp
                                }
                                val previousMessageTimestamp =
                                    messageLikeList.slice(0..messageLikeList.size - index - 2)
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

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    adapter = rememberScrollbarAdapter(listState),
                    reverseLayout = true,
                )
            }
        }
        Layer(Modifier.padding(horizontal = 8.dp)) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                ChatInput(
                    conversationKey = conversation.asKey,
                    replyElement = replyElement,
                    onCancelReply = {
                        replyElement = null
                    },
                    onSendMessage = {
                        replyElement = null
                        withMutableSnapshot {
                            messageLikeList += it
                            haveSentMessage = true
                        }
                    },
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
    replyElement: Element.Reply?,
    onCancelReply: () -> Unit,
    onSendMessage: (LocalMessage) -> Unit,
    onSendMessageComplete: (LocalMessage) -> Unit,
) {
    val bot = LocalBot.current
    val config = LocalConfig.current
    var textValue by remember { mutableStateOf(TextFieldValue()) }
    val imageAttachments = remember { mutableStateListOf<ImageAttachment>() }
    var enterStartedWithModifier by remember { mutableStateOf(false) }
    var enterConsumedForSend by remember { mutableStateOf(false) }

    suspend fun sendMessage() {
        // snapshot current input state
        val sendText = textValue.text.trim()
        val sendImages = imageAttachments.toList()
        val sendReply = replyElement

        val hasText = sendText.isNotEmpty()
        val hasImages = sendImages.isNotEmpty()

        if (!hasText && !hasImages) return

        val localMessage = LocalMessage(
            scene = conversationKey.scene,
            peerUin = conversationKey.peerUin,
            random = Random.nextInt(),
            timestamp = Instant.now().epochSecond,
            elements = buildList {
                sendReply?.let { add(it) }
                sendImages.forEach { attachment ->
                    add(Element.LocalImage(attachment.bitmap))
                }
                if (hasText) {
                    add(
                        Element.RichText(
                            content = AnnotatedString(sendText),
                            inlines = emptyMap(),
                        )
                    )
                }
            }
        )
        onSendMessage(localMessage)

        val sendMessageBlock: suspend BotOutgoingMessageBuilder.() -> Unit = {
            sendReply?.let {
                reply(it.sequence)
            }
            if (hasImages) {
                sendImages.forEach { attachment ->
                    image(
                        raw = attachment.bytes,
                        format = attachment.format,
                        width = attachment.bitmap.width,
                        height = attachment.bitmap.height,
                    )
                }
            }
            if (hasText) {
                text(sendText)
            }
        }
        textValue = TextFieldValue("")
        imageAttachments.clear()

        val result = when (conversationKey.scene) {
            MessageScene.FRIEND -> {
                bot.sendFriendMessage(
                    friendUin = conversationKey.peerUin,
                    random = localMessage.random,
                    build = sendMessageBlock,
                )
            }

            MessageScene.GROUP -> {
                bot.sendGroupMessage(
                    groupUin = conversationKey.peerUin,
                    random = localMessage.random,
                    build = sendMessageBlock,
                )
            }

            else -> null
        }
        result?.let { onSendMessageComplete(localMessage.copy(sequence = result.sequence)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (replyElement != null) {
            MessageReply(
                reply = replyElement,
                isSelf = false,
                innerModifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            )
        }
        if (imageAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                imageAttachments.forEachIndexed { index, attachment ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = attachment.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(FluentTheme.colors.background.layer.default)
                                .clickable {
                                    imageAttachments.removeAt(index)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                style = FluentTheme.typography.caption,
                                color = FluentTheme.colors.text.text.secondary,
                            )
                        }
                    }
                }
            }
        }
        TextField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = { Text("输入消息...") },
            modifier = modifier.fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        if (replyElement != null) {
                            onCancelReply()
                            return@onPreviewKeyEvent true
                        }
                    }

                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.V &&
                        (event.isCtrlPressed || event.isMetaPressed)
                    ) {
                        if (imageAttachments.tryPasteImages()) {
                            return@onPreviewKeyEvent true
                        }
                    }
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
                                    textValue = TextFieldValue(
                                        text = textValue.text + "\n",
                                        selection = TextRange(textValue.selection.start + 1)
                                    )
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
}