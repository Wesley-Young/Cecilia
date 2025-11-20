package org.ntqqrev.cecilia.view

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Settings
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot

@Composable
fun LoginView(
    showConfigInitDialog: () -> Unit
) {
    val bot = LocalBot.current
    val hasSession = bot.sessionStore.uin != 0L && bot.sessionStore.a2.isNotEmpty()
    var usingQrCode by remember { mutableStateOf(!hasSession) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Layer(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Cecilia",
                        style = FluentTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = FluentTheme.colors.text.accent.primary
                    )
                    if (hasSession && !usingQrCode) {
                        AvatarImage(
                            uin = bot.sessionStore.uin,
                            size = 160.dp,
                            quality = 640
                        )
                        Text("检测到已有登录信息")
                        Text(
                            text = "账号：${bot.sessionStore.uin}",
                            color = FluentTheme.colors.text.text.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        AccentButton(
                            disabled = false,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("快捷登录")
                        }
                    }
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
}