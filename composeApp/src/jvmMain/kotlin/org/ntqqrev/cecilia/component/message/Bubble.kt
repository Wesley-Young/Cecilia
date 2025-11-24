package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.elevation
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.AnimatedImage
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalEmojiImages
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.Message
import org.ntqqrev.cecilia.util.displayName

@Composable
fun Bubble(message: Message) {
    val bot = LocalBot.current
    val isSelf = message.senderUin == bot.uin
    val isGroup = message.scene == MessageScene.GROUP
    var displayName by remember(message) { mutableStateOf(message.senderName) }

    LaunchedEffect(bot, message) {
        if (isGroup) {
            val member = bot.getGroupMember(message.peerUin, message.senderUin)
            member?.let { displayName = it.displayName }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // not self, avatar displayed first
            if (!isSelf) {
                AvatarImage(
                    uin = message.senderUin,
                    size = 32.dp,
                )
            }

            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
            ) {
                if (isGroup) {
                    Text(
                        text = displayName,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.tertiary
                    )
                }
                when (message.elements.size) {
                    1 if (message.elements[0] is Element.Image) -> {
                        Spacer(Modifier.height(4.dp))
                        MessageImage(image = message.elements[0] as Element.Image)
                    }

                    1 if (message.elements[0] is Element.LargeFace) -> {
                        Spacer(Modifier.height(4.dp))
                        val largeFace = message.elements[0] as Element.LargeFace
                        MessageLargeFace(faceId = largeFace.faceId)
                    }

                    else -> {
                        Spacer(Modifier.height(2.dp))
                        BubbleBody(message = message, isSelf = isSelf)
                    }
                }
            }

            // self, avatar displayed last
            if (isSelf) {
                AvatarImage(
                    uin = message.senderUin,
                    size = 32.dp,
                )
            }
        }
    }
}

@Composable
private fun BubbleBody(
    message: Message,
    isSelf: Boolean,
) {
    val bubbleShape = remember { RoundedCornerShape(12.dp) }
    Column(
        Modifier
            .elevation(
                elevation = 4.dp,
                shape = bubbleShape
            )
            .border(
                width = 1.dp,
                color = FluentTheme.colors.stroke.divider.default,
                shape = bubbleShape
            )
            .background(
                color = if (isSelf) {
                    FluentTheme.colors.fillAccent.secondary
                } else {
                    FluentTheme.colors.background.layer.default
                },
                shape = bubbleShape
            )
            .padding(12.dp)
    ) {
        message.elements.forEach { e ->
            when (e) {
                is Element.RichText -> {
                    Text(
                        text = e.content,
                        color = if (isSelf) {
                            FluentTheme.colors.text.onAccent.primary
                        } else {
                            FluentTheme.colors.text.text.primary
                        },
                        inlineContent = LocalEmojiImages.current
                            ?.map { (k, v) ->
                                "face/$k" to InlineTextContent(
                                    Placeholder(
                                        width = 16.sp,
                                        height = 16.sp,
                                        PlaceholderVerticalAlign.Center
                                    )
                                ) {
                                    if (v.apng != null) {
                                        AnimatedImage(
                                            frames = v.apng,
                                            contentDescription = "表情 $k",
                                            modifier = Modifier.size(16.sp.value.dp)
                                        )
                                    } else {
                                        Image(
                                            bitmap = v.png,
                                            contentDescription = "表情 $k",
                                            modifier = Modifier.size(16.sp.value.dp)
                                        )
                                    }
                                }
                            }
                            ?.associate { it }
                            ?: emptyMap()
                    )
                }

                is Element.LargeFace -> {
                    // It should not be here
                }

                is Element.Image -> {
                    MessageImage(image = e)
                }

                is Element.Reply -> {
                    MessageReply(
                        repliedSequence = e.sequence,
                        peerUin = message.peerUin,
                        scene = message.scene,
                        isSelf = isSelf,
                        onJumpToMessage = {

                        }
                    )
                }
            }
        }
    }
}