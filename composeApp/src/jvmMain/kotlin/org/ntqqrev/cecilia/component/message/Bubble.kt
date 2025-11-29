package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.elevation
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.acidify.struct.GroupMemberRole
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.MemberBadge
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.LocalMessage
import org.ntqqrev.cecilia.model.Message
import org.ntqqrev.cecilia.util.coerceInSquareBox
import org.ntqqrev.cecilia.util.displayName
import org.ntqqrev.cecilia.util.zipIntoSingleLine

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bubble(
    message: Message,
    onDoubleClick: () -> Unit,
    blink: Boolean = false,
) {
    val bot = LocalBot.current
    val isSelf = message.senderUin == bot.uin
    val isGroup = message.scene == MessageScene.GROUP
    val member = message.member

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                color = if (blink) {
                    Color(0f, 0f, 0f, 0.05f)
                } else {
                    Color.Transparent
                }
            )
            .onClick(
                onDoubleClick = onDoubleClick,
                onClick = {}
            )
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
                    if (member != null) {
                        SenderHeader(
                            member = member,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = message.senderName.zipIntoSingleLine(),
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ElementsDisplay(
                    elements = message.elements,
                    isSelf = isSelf,
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalBubble(
    message: LocalMessage,
    onDoubleClick: () -> Unit,
    blink: Boolean
) {
    val bot = LocalBot.current
    val isGroup = message.scene == MessageScene.GROUP
    var selfAsMember by remember { mutableStateOf<BotGroupMember?>(null) }

    LaunchedEffect(message) {
        if (isGroup) {
            selfAsMember = bot.getGroupMember(message.peerUin, bot.uin)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                color = if (blink) {
                    Color(0f, 0f, 0f, 0.05f)
                } else {
                    Color.Transparent
                }
            )
            .onClick(
                onDoubleClick = onDoubleClick,
                onClick = {}
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (selfAsMember != null) {
                    SenderHeader(
                        member = selfAsMember!!,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    Text(
                        text = "你",
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                ElementsDisplay(
                    elements = message.elements,
                    isSelf = true,
                )
            }
            AvatarImage(
                uin = bot.uin,
                size = 32.dp,
            )
        }
    }
}

@Composable
private fun ElementsDisplay(
    elements: List<Element>,
    isSelf: Boolean,
) {
    when (elements.size) {
        1 if (elements[0] is Element.Image) -> {
            Spacer(Modifier.height(4.dp))
            MessageImage(image = elements[0] as Element.Image)
        }

        1 if (elements[0] is Element.LocalImage) -> {
            Spacer(Modifier.height(4.dp))
            val e = elements[0] as Element.LocalImage
            val (displayWidth, displayHeight) = Pair(
                e.bitmap.width,
                e.bitmap.height
            ).coerceInSquareBox(300)
            Image(
                bitmap = e.bitmap,
                contentDescription = null,
                modifier = Modifier.size(width = displayWidth.dp, height = displayHeight.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        1 if (elements[0] is Element.LargeFace) -> {
            Spacer(Modifier.height(4.dp))
            val largeFace = elements[0] as Element.LargeFace
            MessageLargeFace(faceId = largeFace.faceId)
        }

        else -> {
            Spacer(Modifier.height(2.dp))
            BubbleBody(elements = elements, isSelf = isSelf)
        }
    }
}

@Composable
private fun SenderHeader(
    member: BotGroupMember,
    modifier: Modifier = Modifier,
) {
    val bot = LocalBot.current
    val isSent = member.uin == bot.uin
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSent) {
            MemberBadge(member)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = member.displayName.zipIntoSingleLine(),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = member.displayName.zipIntoSingleLine(),
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
            MemberBadge(member)
        }
    }
}

@Composable
private fun BubbleBody(
    elements: List<Element>,
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
        elements.forEach { e ->
            when (e) {
                is Element.RichText -> {
                    Text(
                        text = e.content,
                        color = if (isSelf) {
                            FluentTheme.colors.text.onAccent.primary
                        } else {
                            FluentTheme.colors.text.text.primary
                        },
                        inlineContent = e.inlines,
                    )
                }

                is Element.LargeFace -> {
                    // It should not be here
                }

                is Element.Image -> {
                    MessageImage(image = e)
                }

                is Element.LocalImage -> {
                    val (displayWidth, displayHeight) = Pair(
                        e.bitmap.width,
                        e.bitmap.height
                    ).coerceInSquareBox(300)
                    Image(
                        bitmap = e.bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(width = displayWidth.dp, height = displayHeight.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                is Element.Reply -> {
                    MessageReply(
                        reply = e,
                        isSelf = isSelf,
                    )
                }
            }
        }
    }
}