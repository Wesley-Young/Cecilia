package org.ntqqrev.cecilia.views

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.ntqqrev.cecilia.Conversation
import org.ntqqrev.cecilia.utils.LocalBot
import org.ntqqrev.cecilia.Message
import org.ntqqrev.cecilia.components.ChatArea
import org.ntqqrev.cecilia.components.ConversationList
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessagesPanel(width: Dp = 320.dp) {
    val bot = LocalBot.current
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var leftPanelWidth by remember { mutableStateOf(width) }

    // 模拟数据
    val conversations = remember {
        listOf(
            Conversation("1", "开发群", "D", "最新消息内容...", "14:30", 3),
            Conversation("2", "张三", "张", "好的，明天见", "昨天", 0),
            Conversation("3", "李四", "李", "收到，谢谢", "周一", 1),
            Conversation("4", "产品讨论组", "产", "新功能已上线", "12/20", 0),
            Conversation("5", "王五", "王", "文件已发送", "12/19", 0),
        )
    }

    val messages = remember {
        mutableStateListOf(
            Message("1", "你好！", false, "14:20", "张三"),
            Message("2", "你好，有什么可以帮你的吗？", true, "14:21"),
            Message("3", "我想问一下关于项目的事情", false, "14:25", "张三"),
            Message("4", "请说", true, "14:26"),
            Message("5", "最新的功能什么时候上线？", false, "14:28", "张三"),
            Message("6", "预计下周三上线", true, "14:30"),
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：会话列表
        Box(modifier = Modifier.width(leftPanelWidth)) {
            ConversationList(
                conversations = conversations,
                selectedId = selectedConversationId,
                onConversationClick = { selectedConversationId = it },
                width = leftPanelWidth,
                showMenuButton = false
            )
        }

        // 可拖拽的分界线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    var dragStartWidth = 0.dp
                    var totalDrag = 0f
                    detectDragGestures(
                        onDragStart = {
                            dragStartWidth = leftPanelWidth
                            totalDrag = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount.x
                            val newWidth = (dragStartWidth + totalDrag.toDp()).coerceIn(200.dp, 600.dp)
                            leftPanelWidth = newWidth
                        }
                    )
                }
                .background(MaterialTheme.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            )
        }

        // 右侧：聊天区域
        if (selectedConversationId != null) {
            val selectedConversation = conversations.find { it.id == selectedConversationId }
            selectedConversation?.let { conversation ->
                ChatArea(
                    conversation = conversation,
                    messages = messages,
                    onSendMessage = { content ->
                        messages.add(
                            Message(
                                id = (messages.size + 1).toString(),
                                content = content,
                                isSent = true,
                                time = "刚刚"
                            )
                        )
                    }
                )
            }
        } else {
            // 未选择会话时的占位界面
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "选择一个会话开始聊天",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

