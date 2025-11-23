package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.util.displayName
import org.ntqqrev.cecilia.util.toPreviewText

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageReply(
    scene: MessageScene,
    peerUin: Long,
    repliedSequence: Long,
    isSelf: Boolean,
    onJumpToMessage: (Long) -> Unit,
) {
    val bot = LocalBot.current
    var isHovering by remember(repliedSequence) { mutableStateOf(false) }
    var actualMessageSenderName by remember(repliedSequence) { mutableStateOf("引用消息") }
    var actualMessagePreview by remember(repliedSequence) { mutableStateOf("#$repliedSequence") }

    LaunchedEffect(repliedSequence) {
        runCatching {
            when (scene) {
                MessageScene.FRIEND -> {
                    val message = bot.getFriendHistoryMessages(
                        friendUin = peerUin,
                        limit = 1,
                        startSequence = repliedSequence,
                    ).messages.firstOrNull()
                    message?.let {
                        if (it.senderUin == bot.uin) {
                            "You"
                        } else {
                            bot.getFriend(it.senderUin)?.displayName
                        }?.let {
                            actualMessageSenderName = it
                        }
                        actualMessagePreview = it.toPreviewText()
                    }
                }

                MessageScene.GROUP -> {
                    val message = bot.getGroupHistoryMessages(
                        groupUin = peerUin,
                        limit = 1,
                        startSequence = repliedSequence,
                    ).messages.firstOrNull()
                    message?.let {
                        if (it.senderUin == bot.uin) {
                            "You"
                        } else {
                            bot.getGroupMember(peerUin, it.senderUin)?.displayName
                        }?.let {
                            actualMessageSenderName = it
                        }
                        actualMessagePreview = it.toPreviewText()
                    }
                }

                else -> {}
            }
        }
    }

    Box(
        Modifier.fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(
                color = Color(
                    red = 0f, green = 0f, blue = 0f,
                    alpha = if (isHovering) 0.08f else 0.04f
                ),
                shape = RoundedCornerShape(4.dp),
            )
            .onPointerEvent(PointerEventType.Enter) { isHovering = true }
            .onPointerEvent(PointerEventType.Exit) { isHovering = false }
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = actualMessageSenderName,
                style = FluentTheme.typography.caption,
                color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                else FluentTheme.colors.text.text.tertiary,
            )
            Text(
                text = actualMessagePreview,
                style = FluentTheme.typography.caption,
                color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                else FluentTheme.colors.text.text.tertiary,
            )
        }
    }
}