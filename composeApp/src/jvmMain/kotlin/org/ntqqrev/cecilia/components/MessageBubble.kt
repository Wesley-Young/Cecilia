package org.ntqqrev.cecilia.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.acidify.message.MessageScene
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: BotIncomingMessage,
    selfUin: Long,
    allMessages: List<BotIncomingMessage> = emptyList(),
    onScrollToMessage: ((Long) -> Unit)? = null
) {
    val isSent = message.senderUin == selfUin

    // 检测是否为占位消息（messageUid == -1L 表示占位符）
    val isPlaceholder = message.messageUid == -1L

    // 提取消息内容（使用 segment.toString()）
    val content = message.segments.joinToString("") { it.toString() }

    // 获取发送者名称
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

    // 是否显示昵称（群聊且不是自己发送的消息）
    val shouldShowNickname = message.scene == MessageScene.GROUP && !isSent

    // if senderuin=0 是撤回的消息，显示一个小灰条
    if (message.senderUin == 0L) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "撤回了一条消息",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        return
    }
    
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
            // 显示群昵称
            if (shouldShowNickname) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                )
            }

            // 消息气泡容器（包含加载动画和气泡）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
            ) {
                // 如果是占位消息且是自己发送的，在气泡左侧显示加载动画
                if (isSent && isPlaceholder) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSent)
                        MaterialTheme.colors.primary
                    else
                        MaterialTheme.colors.surface,
                    elevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    ) {
                        // 消息内容（为时间留出空间）
                        // 为自己发送的消息使用自定义选中颜色
                        val customTextSelectionColors = if (isSent) {
                            TextSelectionColors(
                                handleColor = MaterialTheme.colors.onPrimary,
                                backgroundColor = Color.White.copy(alpha = 0.3f)
                            )
                        } else {
                            LocalTextSelectionColors.current
                        }

                        CompositionLocalProvider(
                            LocalTextSelectionColors provides customTextSelectionColors
                        ) {
                            SelectionContainer {
                                Column(modifier = Modifier.padding(end = 40.dp)) {
                                    val mergedItems = remember(message.segments) {
                                        buildDisplayList(message.segments)
                                    }
                                    mergedItems.forEach { item ->
                                        DisplayElement(
                                            item = item,
                                            isSent = isSent,
                                            allMessages = allMessages,
                                            onScrollToMessage = onScrollToMessage
                                        )
                                    }
                                }
                            }
                        }

                        // 时间显示在右下角
                        Text(
                            text = timeStr,
                            modifier = Modifier.align(Alignment.BottomEnd),
                            style = MaterialTheme.typography.caption,
                            color = if (isSent)
                                MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
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
            // 其他：显示日期
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(messageTime)
        }
    }
}

private fun buildDisplayList(segments: List<BotIncomingSegment>): List<Any> = buildList {
    var buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            add(buffer.toString())
            buffer = StringBuilder()
        }
    }
    for (seg in segments) {
        when (seg) {
            is BotIncomingSegment.Text -> buffer.append(seg.text)
            is BotIncomingSegment.Mention -> buffer.append(seg.name)
            is BotIncomingSegment.MarketFace -> {
                flush()
                add(
                    BotIncomingSegment.Image(
                        fileId = seg.url,
                        width = 512,
                        height = 512,
                        subType = ImageSubType.STICKER,
                        summary = seg.summary
                    )
                )
            }

            else -> {
                flush()
                add(seg)
            }
        }
    }
    flush()
}

@Composable
private fun DisplayElement(
    item: Any,
    isSent: Boolean,
    allMessages: List<BotIncomingMessage>,
    onScrollToMessage: ((Long) -> Unit)?
) {
    when (item) {
        is String -> {
            if (item.isNotEmpty()) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.body1,
                    color = if (isSent)
                        MaterialTheme.colors.onPrimary
                    else
                        MaterialTheme.colors.onSurface
                )
            }
        }

        is BotIncomingSegment.Reply -> {
            // 查找被引用的消息
            val referencedMessage = allMessages.find { it.sequence == item.sequence }

            MessageReply(
                replySegment = item,
                referencedMessage = referencedMessage,
                isSent = isSent,
                onReplyClick = if (referencedMessage != null && onScrollToMessage != null) {
                    { onScrollToMessage(item.sequence) }
                } else null
            )
        }

        is BotIncomingSegment.Image -> {
            MessageImage(imageSegment = item, isSent = isSent)
        }

        is BotIncomingSegment.Record -> {
            Text(
                text = item.toString(),
                style = MaterialTheme.typography.body2,
                color = if (isSent)
                    MaterialTheme.colors.onPrimary
                else
                    MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
            )
        }

        is BotIncomingSegment.Video -> {
            Text(
                text = item.toString(),
                style = MaterialTheme.typography.body2,
                color = if (isSent)
                    MaterialTheme.colors.onPrimary
                else
                    MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
            )
        }

        is BotIncomingSegment -> {
            // 其他段落以 toString 文本显示
            val text = item.toString()
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.body1,
                    color = if (isSent)
                        MaterialTheme.colors.onPrimary
                    else
                        MaterialTheme.colors.onSurface
                )
            }
        }
    }
}