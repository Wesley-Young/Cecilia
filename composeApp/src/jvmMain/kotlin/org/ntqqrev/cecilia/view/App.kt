package org.ntqqrev.cecilia.view

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.background.Mica
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Settings
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.core.LocalBot

@Composable
fun App(
    config: Config,
    bot: Bot?,
    loadError: Throwable?,
    showConfigInitDialog: () -> Unit
) {
    var isLoggedIn by remember { mutableStateOf(bot?.isLoggedIn ?: false) }

    Mica(Modifier.fillMaxSize()) {
        if (bot == null) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProgressRing()
                        if (loadError == null) {
                            Text("正在初始化")
                        } else {
                            Text("初始化失败：${loadError.localizedMessage}\n" +
                                    "请检查配置文件，确保指定了有效的签名地址。")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Button(
                        iconOnly = true,
                        onClick = showConfigInitDialog
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            }
        } else {
            CompositionLocalProvider(
                LocalBot provides bot
            ) {
                if (!isLoggedIn) {
                    LoginView(
                        showConfigInitDialog
                    )
                }
            }
        }
    }
}