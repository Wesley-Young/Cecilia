package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.elevation
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.model.Element
import org.ntqqrev.cecilia.model.Message

@Composable
fun Bubble(message: Message) {
    val bot = LocalBot.current
    val isSelf = message.senderUin == bot.uin
    val isGroup = message.scene == MessageScene.GROUP

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
                horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isGroup) {
                    Text(
                        text = message.senderName,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.text.text.tertiary
                    )
                }
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
                            is Element.Text -> {
                                Text(
                                    text = e.content,
                                    color = if (isSelf) {
                                        FluentTheme.colors.text.onAccent.primary
                                    } else {
                                        FluentTheme.colors.text.text.primary
                                    }
                                )
                            }

                            is Element.Image -> {
                                Text(e.summary)
                            }
                        }
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