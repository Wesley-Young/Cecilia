package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
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
import org.ntqqrev.cecilia.model.Element

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun MessageReply(
    reply: Element.Reply,
    isSelf: Boolean,
    onJumpToMessage: (Long) -> Unit,
) {
    var isHovering by remember { mutableStateOf(false) }

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
            .onClick {
                onJumpToMessage(reply.sequence)
            }
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = reply.senderName,
                style = FluentTheme.typography.caption,
                color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                else FluentTheme.colors.text.text.tertiary,
            )
            Text(
                text = reply.content,
                style = FluentTheme.typography.caption,
                color = if (isSelf) FluentTheme.colors.text.onAccent.secondary
                else FluentTheme.colors.text.text.tertiary,
            )
        }
    }
}