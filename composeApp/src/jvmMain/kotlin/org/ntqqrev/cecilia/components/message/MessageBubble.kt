package org.ntqqrev.cecilia.components.message

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.ntqqrev.acidify.message.BotIncomingMessage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.components.AvatarImage
import org.ntqqrev.cecilia.structs.DisplayElem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageBubble(
    message: BotIncomingMessage,
    selfUin: Long,
    allMessages: List<BotIncomingMessage> = emptyList(),
    onScrollToMessage: ((Long) -> Unit)? = null,
    onReplyToMessage: ((BotIncomingMessage) -> Unit)? = null
) {
    val isSent = message.senderUin == selfUin

    // 检测是否为占位消息（messageUid == -1L 表示占位符）
    val isPlaceholder = message.messageUid == -1L

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

    // 是否原本应该显示昵称
    val shouldShowNickname = message.scene == MessageScene.GROUP && !isSent
    
    // 鼠标悬停状态
    var isHovering by remember { mutableStateOf(false) }
    var showSeq by remember { mutableStateOf(false) }
    
    // 当前显示的文本
    val displayText = when {
        showSeq -> if (shouldShowNickname) "$senderName #${message.sequence}" else "#${message.sequence}"
        shouldShowNickname -> senderName
        else -> null
    }
    
    // 悬停延迟逻辑
    LaunchedEffect(isHovering) {
        if (isHovering) {
            delay(1000) // 悬停 1 秒后
            showSeq = true
        } else {
            showSeq = false
        }
    }

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
        modifier = Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) {
                isHovering = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovering = false
            }
            .pointerInput(message.sequence) {
                detectTapGestures(
                    onDoubleTap = {
                        onReplyToMessage?.invoke(message)
                    }
                )
            },
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
    ) {
        if (!isSent) {
            AvatarImage(
                uin = message.senderUin,
                size = 44.dp,
                isGroup = false,
                quality = 100,
                clickable = true
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // 显示群昵称或消息序列号
            if (displayText != null) {
                Text(
                    text = displayText,
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
                                Column(
                                    modifier = Modifier.padding(end = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
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
                quality = 100,
                clickable = true
            )
        }
    }
}

/**
 * 格式化消息时间戳
 */
@Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
private fun formatMessageTime(timestamp: Long): String {
    val zoneId = ZoneId.systemDefault()
    val messageDateTime = Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDateTime()
    val messageDate = messageDateTime.toLocalDate()
    val today = LocalDate.now(zoneId)

    return when {
        messageDate == today -> {
            // 今天：显示时:分
            messageDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        else -> {
            // 其他：显示日期
            messageDateTime.format(DateTimeFormatter.ofPattern("MM/dd"))
        }
    }
}

private fun buildDisplayList(segments: List<BotIncomingSegment>): List<DisplayElem> = buildList {
    var buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            add(DisplayElem.Text(buffer.toString()))
            buffer = StringBuilder()
        }
    }
    for (seg in segments) {
        when (seg) {
            is BotIncomingSegment.Text -> buffer.append(seg.text)
            is BotIncomingSegment.Mention -> buffer.append(seg.name)
            is BotIncomingSegment.Reply -> {
                flush()
                add(DisplayElem.Reply(seg))
            }
            is BotIncomingSegment.MarketFace -> {
                flush()
                add(
                    DisplayElem.Image(
                        BotIncomingSegment.Image(
                            fileId = seg.url,
                            width = 300,
                            height = 300,
                            subType = ImageSubType.STICKER,
                            summary = if (seg.url == "https://gxh.vip.qq.com/club/item/parcel/item/a3/a322b9f5bf8e2f0f2370d933f6fd4239/raw300.gif")
                                "[逼？！]" // easter egg
                            else
                                seg.summary
                        )
                    )
                )
            }

            is BotIncomingSegment.Image -> {
                flush()
                add(DisplayElem.Image(seg))
            }

            is BotIncomingSegment.Record -> {
                flush()
                add(DisplayElem.Record(seg))
            }

            is BotIncomingSegment.Video -> {
                flush()
                add(DisplayElem.Video(seg))
            }
            else -> {
                flush()
                add(DisplayElem.Other(seg))
            }
        }
    }
    flush()
}

@Composable
private fun DisplayElement(
    item: DisplayElem,
    isSent: Boolean,
    allMessages: List<BotIncomingMessage>,
    onScrollToMessage: ((Long) -> Unit)?
) {
    when (item) {
        is DisplayElem.Text -> {
            if (item.text.isNotEmpty()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.body1,
                    color = if (isSent)
                        MaterialTheme.colors.onPrimary
                    else
                        MaterialTheme.colors.onSurface
                )
            }
        }

        is DisplayElem.Reply -> {
            // 查找被引用的消息
            val referencedMessage = allMessages.find { it.sequence == item.segment.sequence }

            MessageReply(
                replySegment = item.segment,
                referencedMessage = referencedMessage,
                isSent = isSent,
                onReplyClick = if (referencedMessage != null && onScrollToMessage != null) {
                    { onScrollToMessage(item.segment.sequence) }
                } else null
            )
        }

        is DisplayElem.Image -> {
            MessageImage(imageSegment = item.segment, isSent = isSent)
        }

        is DisplayElem.Record -> {
            Text(
                text = item.segment.toString(),
                style = MaterialTheme.typography.body2,
                color = if (isSent)
                    MaterialTheme.colors.onPrimary
                else
                    MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
            )
        }

        is DisplayElem.Video -> {
            Text(
                text = item.segment.toString(),
                style = MaterialTheme.typography.body2,
                color = if (isSent)
                    MaterialTheme.colors.onPrimary
                else
                    MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
            )
        }

        is DisplayElem.Other -> {
            // 其他段落以 toString 文本显示
            val text = item.segment.toString()
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