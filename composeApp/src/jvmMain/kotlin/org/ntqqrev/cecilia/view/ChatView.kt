package org.ntqqrev.cecilia.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.DraggableDivider
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.model.Conversation
import org.ntqqrev.cecilia.util.formatToShortTime
import java.time.Instant

@Composable
fun ChatView() {
    val windowState = rememberWindowState()
    var leftPanelWidth by remember { mutableStateOf(windowState.size.width * 0.35f) }

    val bot = LocalBot.current
    val conversations = remember { mutableStateMapOf<Conversation.Key, Conversation>() }
    val activeConversation = remember { mutableStateOf<Conversation.Key?>(null) }

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
        initialConversations.forEach { conversation ->
            conversations[conversation.asKey] = conversation
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.width(leftPanelWidth)) {
            Layer {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "聊天",
                        style = FluentTheme.typography.body
                    )
                }
            }
            val conversationListScrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxHeight()
                    .verticalScroll(conversationListScrollState),
            ) {
                conversations.values.sorted().forEach { conversation ->
                    ConversationDisplay(
                        conversation = conversation,
                        isSelected = conversation.asKey == activeConversation.value,
                        onClick = {
                            activeConversation.value = conversation.asKey
                        }
                    )
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
            Text(
                text = "选择一个会话开始聊天",
                style = FluentTheme.typography.bodyLarge,
                color = FluentTheme.colors.text.text.secondary
            )
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
    Box(
        Modifier.onClick { onClick() }
            .then(
                if (isSelected) {
                    Modifier.background(FluentTheme.colors.fillAccent.default.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                uin = conversation.peerUin,
                size = 48.dp,
                isGroup = conversation.scene == MessageScene.GROUP,
                quality = 100
            )
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
                    if (conversation.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Pin,
                            contentDescription = "置顶",
                            modifier = Modifier.size(16.dp),
                            tint = FluentTheme.colors.fillAccent.default
                        )
                    }
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
                            style = FluentTheme.typography.body,
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
                                text = conversation.unreadCount.toString(),
                                fontSize = textSize,
                                lineHeight = textSize,
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