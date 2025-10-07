package org.ntqqrev.cecilia.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.MessageScene
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: BotIncomingMessage,
    selfUin: Long
) {
    val isSent = message.senderUin == selfUin
    
    // 提取消息内容（使用 segment.toString()）
    val content = message.segments.joinToString("") { it.toString() }
    
    // 获取发送者名称（仅用于显示头像）
    val senderName = when (message.scene) {
        MessageScene.GROUP -> {
            message.extraInfo?.let { info ->
                info.groupCard.takeIf { it.isNotEmpty() } ?: info.nick
            } ?: message.senderUin.toString()
        }
        else -> message.senderUin.toString()
    }
    
    // 格式化时间
    val timeStr = formatMessageTime(message.timestamp)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
    ) {
        if (!isSent) {
            AvatarImage(
                uin = message.senderUin,
                size = 44.dp,
                isGroup = false,
                quality = 100
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isSent) 16.dp else 4.dp,
                    bottomEnd = if (isSent) 4.dp else 16.dp
                ),
                color = if (isSent)
                    MaterialTheme.colors.primary
                else
                    MaterialTheme.colors.surface,
                elevation = 1.dp
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.body1,
                        color = if (isSent)
                            MaterialTheme.colors.onPrimary
                        else
                            MaterialTheme.colors.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timeStr,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (isSent) {
            Spacer(modifier = Modifier.width(8.dp))
            AvatarImage(
                uin = message.senderUin,
                size = 44.dp,
                isGroup = false,
                quality = 100
            )
        }
    }
}

/**
 * 格式化消息时间戳
 */
private fun formatMessageTime(timestamp: Long): String {
    val messageTime = Date(timestamp * 1000)
    val now = Date()
    val calendar = Calendar.getInstance()

    // 今天的开始时间
    calendar.time = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.time

    return when {
        messageTime.after(todayStart) -> {
            // 今天：显示时:分
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)
        }
        else -> {
            // 其他：显示完整日期时间
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(messageTime)
        }
    }
}

