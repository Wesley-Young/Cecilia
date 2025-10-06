package org.ntqqrev.cecilia

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.components.*

@Composable
@Preview
fun App(
    bot: Bot?,
    loadingError: String? = null,
    onLoginStateChange: ((Boolean, Long) -> Unit)? = null
) {
    MaterialTheme(
        colors = GreenColors,
        typography = Typography(
            defaultFontFamily = FontFamily
        )
    ) {
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
                // Bot 加载完成
                var isLoggedIn by remember { mutableStateOf(bot.isLoggedIn) }

                // 通知登录状态变化
                LaunchedEffect(isLoggedIn) {
                    onLoginStateChange?.invoke(isLoggedIn, bot.sessionStore.uin)
                }

                if (isLoggedIn) {
                    // 已登录，显示主界面
                    MainContent(bot)
                } else {
                    // 未登录，显示登录界面
                    LoginScreen(
                        bot = bot,
                        onLoginSuccess = { isLoggedIn = true },
                        onLoginStateChange = onLoginStateChange
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContent(bot: Bot) {
    var selectedTab by remember { mutableStateOf(NavigationTab.MESSAGES) }

    Row(modifier = Modifier.fillMaxSize()) {
        // 最左侧：导航栏
        NavigationRail(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        // 分界线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )

        // 中间和右侧：根据选择的标签显示不同内容
        when (selectedTab) {
            NavigationTab.MESSAGES -> MessagesPanel(bot)
            NavigationTab.CONTACTS -> ContactsPanel()
            NavigationTab.SETTINGS -> SettingsPanel()
        }
    }
}
