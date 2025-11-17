package org.ntqqrev.cecilia.component.view

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.struct.Conversation

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    selectedId: String?,
    onConversationClick: (String) -> Unit,
    onPinToggle: (Conversation, Boolean) -> Unit,
    width: Dp = 320.dp
) {

    val displayedConversations by remember(conversations) {
        derivedStateOf { conversations.toList() }
    }

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colors.surface)
    ) {
        // 顶部搜索栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "消息",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Divider()

        // 会话列表
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayedConversations, key = { "${it.scene.name}:${it.id}" }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = conversation.id == selectedId,
                    onClick = { onConversationClick(conversation.id) },
                    onPinToggle = { shouldPin -> onPinToggle(conversation, shouldPin) }
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPinToggle: (Boolean) -> Unit
) {
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(
                    if (conversation.isPinned) "取消置顶" else "置顶"
                ) {
                    onPinToggle(!conversation.isPinned)
                }
            )
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f)
            else MaterialTheme.colors.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                AvatarImage(
                    uin = conversation.peerUin,
                    size = 48.dp,
                    isGroup = conversation.scene == MessageScene.GROUP,
                    quality = 100
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 消息内容
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = conversation.name,
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (conversation.isPinned) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "置顶",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colors.primary
                                )
                            }
                        }
                        Text(
                            text = conversation.time,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = conversation.lastMessage,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            // 自定义 Badge
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colors.error),
                                contentAlignment = Alignment.Center
                            ) {
                                val textSize = 11.sp
                                Text(
                                    text = conversation.unreadCount.toString(),
                                    fontSize = textSize,
                                    lineHeight = textSize,
                                    color = MaterialTheme.colors.onError,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.wrapContentSize(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
