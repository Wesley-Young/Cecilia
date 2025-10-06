package org.ntqqrev.cecilia

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.components.ChatArea
import org.ntqqrev.cecilia.components.ConversationList

@Composable
@Preview
fun App(bot: Bot?, loadingError: String? = null) {
    MaterialTheme(colors = GreenColors) {
        when {
            loadingError != null -> {
                // 显示错误界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "加载失败",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.error
                        )
                        Text(
                            text = loadingError,
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            bot == null -> {
                // 显示加载界面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "正在初始化...",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            else -> {
                // Bot 加载完成，显示主界面
                MainContent(bot)
            }
        }
    }
}

@Composable
private fun MainContent(bot: Bot) {
    var selectedConversationId by remember { mutableStateOf<String?>(null) }

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
        ConversationList(
            conversations = conversations,
            selectedId = selectedConversationId,
            onConversationClick = { selectedConversationId = it }
        )

        // 分界线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )

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
