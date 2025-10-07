package org.ntqqrev.cecilia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.cecilia.utils.segmentsToPreviewString

@Composable
fun MessageReply(
    replySegment: BotIncomingSegment.Reply,
    referencedMessage: BotIncomingMessage?,
    isSent: Boolean,
    onReplyClick: (() -> Unit)? = null
) {
    val backgroundColor = if (isSent) {
        // 自己发送的消息：颜色较浅
        MaterialTheme.colors.onPrimary.copy(alpha = 0.15f)
    } else {
        // 接收的消息：颜色稍深
        MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(
                if (onReplyClick != null) {
                    Modifier.clickable { onReplyClick() }
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
    ) {
        if (referencedMessage != null) {
            // 找到被引用的消息：显示两行内容
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 第一行：发送者昵称
                val senderName = referencedMessage.extraInfo?.let { info ->
                    info.groupCard.takeIf { it.isNotEmpty() } ?: info.nick
                } ?: referencedMessage.senderUin.toString()

                Text(
                    text = senderName,
                    style = MaterialTheme.typography.caption,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = if (isSent)
                        MaterialTheme.colors.onPrimary.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 第二行：消息内容预览
                val preview = referencedMessage.segmentsToPreviewString()
                Text(
                    text = preview,
                    style = MaterialTheme.typography.body2,
                    color = if (isSent)
                        MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // 找不到被引用的消息：显示一行内容
            Text(
                text = replySegment.toString(),
                style = MaterialTheme.typography.body2,
                color = if (isSent)
                    MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                else
                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}