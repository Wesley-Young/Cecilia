package org.ntqqrev.cecilia.view

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot.Companion.withMutableSnapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Pin
import org.ntqqrev.acidify.event.MessageRecallEvent
import org.ntqqrev.acidify.event.MessageReceiveEvent
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.DraggableDivider
import org.ntqqrev.cecilia.component.message.Bubble
import org.ntqqrev.cecilia.component.message.GreyTip
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.model.Conversation
import org.ntqqrev.cecilia.model.Message
import org.ntqqrev.cecilia.model.MessageLike
import org.ntqqrev.cecilia.model.Notification
import org.ntqqrev.cecilia.util.*
import java.time.Instant

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
                            .verticalScroll(conversationListScrollState),
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
            onWidthChange = { leftPanelWidth = it }
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
        Modifier.padding(start = 2.dp, top = 2.dp)
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
        val messages = when (conversation.scene) {
            MessageScene.FRIEND -> bot.getFriendHistoryMessages(
                friendUin = conversation.peerUin,
                limit = 30,
            ).messages

            MessageScene.GROUP -> bot.getGroupHistoryMessages(
                groupUin = conversation.peerUin,
                limit = 30,
            ).messages

            else -> emptyList()
        }.map { it.toModel() }
        messageLikeList.addAll(index = 0, elements = messages)

        bot.eventFlow.collect { event ->
            when (event) {
                is MessageReceiveEvent -> {
                    if (event.message.scene == conversation.scene &&
                        event.message.peerUin == conversation.peerUin
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

            val furtherMessages = when (conversation.scene) {
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
            }.map { it.toModel() }
            if (furtherMessages.isNotEmpty()) {
                messageLikeList.addAll(index = 0, elements = furtherMessages)
                isLoadingFurtherHistoryMessages = false
            } // otherwise reached the end of history, no longer load more
        }
    }

    LaunchedEffect(isLoadingFurtherHistoryMessages) {
        if (listState.firstVisibleItemIndex > 0 && !isLoadingFurtherHistoryMessages) {
            // make previously loaded history messages visible
            listState.scrollToItem(listState.firstVisibleItemIndex + 1)
        }
    }

    Column {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
        ) {
            items(messageLikeList.size) { index ->
                when (val currentMessageLike = messageLikeList[messageLikeList.size - index - 1]) {
                    is Message -> {
                        Bubble(message = currentMessageLike)

                        // compare with prev timestamp
                        val previousMessage = messageLikeList.slice(0..messageLikeList.size - index - 2)
                            .lastOrNull { it is Message } as Message?
                        val shouldShowTimeTip = previousMessage?.let {
                            currentMessageLike.timestamp - previousMessage.timestamp >= 300
                        } ?: true
                        if (shouldShowTimeTip) {
                            GreyTip(
                                content = Instant.ofEpochSecond(currentMessageLike.timestamp)
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
}