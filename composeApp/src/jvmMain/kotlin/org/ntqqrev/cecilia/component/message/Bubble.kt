package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.elevation
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.entity.BotGroupMember
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.acidify.struct.GroupMemberRole
import org.ntqqrev.cecilia.component.AnimatedImage
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalEmojiImages
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.Message
import org.ntqqrev.cecilia.util.displayName

@Composable
fun Bubble(
    message: Message,
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
                            text = message.senderName,
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                text = member.displayName,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.tertiary
            )
        } else {
            Text(
                text = member.displayName,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.text.text.tertiary
            )
            Spacer(modifier = Modifier.width(4.dp))
            MemberBadge(member)
        }
    }
}

@Composable
private fun MemberBadge(member: BotGroupMember) {
    Box(
        Modifier.background(
            color = when (member.role) {
                GroupMemberRole.OWNER -> Color(0xFFFFB74D)
                GroupMemberRole.ADMIN -> Color(0xFF26C6DA)
                GroupMemberRole.MEMBER -> {
                    if (member.specialTitle.isNotBlank()) {
                        Color(0xFFD269DA)
                    } else {
                        Color(0xFF90A4AE)
                    }
                }
            },
            shape = RoundedCornerShape(999.dp),
        )
    ) {
        Text(
            text = buildString {
                val titlePart = member.specialTitle.takeIf { it.isNotBlank() } ?: when (member.role) {
                    GroupMemberRole.OWNER -> "群主"
                    GroupMemberRole.ADMIN -> "管理员"
                    else -> null
                }
                append("Lv")
                append(member.level)
                if (titlePart != null) {
                    append(" ")
                    append(titlePart)
                }
            },
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.onAccent.secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
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
                        reply = e,
                        isSelf = isSelf,
                    )
                }
            }
        }
    }
}