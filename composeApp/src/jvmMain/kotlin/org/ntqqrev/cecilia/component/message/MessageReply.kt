package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.util.displayName
import org.ntqqrev.cecilia.util.toPreviewText

@Composable
fun MessageReply(
    scene: MessageScene,
    peerUin: Long,
    repliedSequence: Long,
    isSelf: Boolean,
    onJumpToMessage: (Long) -> Unit,
) {
    val bot = LocalBot.current
    var actualMessageSenderName by remember(repliedSequence) { mutableStateOf<String?>(null) }
    var actualMessagePreview by remember(repliedSequence) { mutableStateOf<String?>(null) }

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
                        actualMessageSenderName = if (it.senderUin == bot.uin) {
                            "You"
                        } else {
                            bot.getFriend(it.senderUin)?.displayName
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
                        actualMessageSenderName = if (it.senderUin == bot.uin) {
                            "You"
                        } else {
                            bot.getGroupMember(peerUin, it.senderUin)?.displayName
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
                color = Color(0f, 0f, 0f, 0.05f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (actualMessageSenderName != null && actualMessagePreview != null) {
                Text(
                    text = actualMessageSenderName!!,
                    style = FluentTheme.typography.caption,
                    color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                    else FluentTheme.colors.text.text.tertiary,
                )
                Text(
                    text = actualMessagePreview!!,
                    style = FluentTheme.typography.caption,
                    color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                    else FluentTheme.colors.text.text.tertiary,
                )
            }
        }
    }
}