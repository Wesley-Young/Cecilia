package org.ntqqrev.cecilia.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image.Companion.makeFromEncoded
import org.ntqqrev.acidify.event.QRCodeGeneratedEvent
import org.ntqqrev.acidify.event.QRCodeStateQueryEvent
import org.ntqqrev.acidify.struct.QRCodeState
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot
import qrcode.QRCode

@Composable
fun LoginView(
    onLoggedIn: () -> Unit,
    showConfigInitDialog: () -> Unit
) {
    val bot = LocalBot.current
    val hasSession = bot.sessionStore.uin != 0L && bot.sessionStore.a2.isNotEmpty()
    val qrCodeColorArgb = FluentTheme.colors.text.accent.primary.toArgb()
    var usingQrCode by remember { mutableStateOf(!hasSession) }
    var qrCodeImage by remember { mutableStateOf<ByteArray?>(null) }
    var qrCodeState by remember { mutableStateOf<QRCodeState?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bot) {
        bot.eventFlow.collect { event ->
            when (event) {
                is QRCodeGeneratedEvent -> {
                    qrCodeImage = QRCode.ofCircles()
                        .withColor(qrCodeColorArgb)
                        .build(event.url)
                        .render()
                        .getBytes()
                    qrCodeState = QRCodeState.WAITING_FOR_SCAN
                }

                is QRCodeStateQueryEvent -> {
                    qrCodeState = event.state
                }
            }
        }
    }

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
                            onClick = {
                                loginError = null
                                bot.launch {
                                    try {
                                        bot.online()
                                        onLoggedIn()
                                    } catch (e: Throwable) {
                                        loginError = "登录失败：${e.localizedMessage}\n" +
                                                "请尝试使用二维码登录。"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("快捷登录")
                        }
                        Button(
                            onClick = { usingQrCode = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("使用二维码登录")
                        }
                    }

                    if (usingQrCode) {
                        if (qrCodeImage != null) {
                            qrCodeImage?.let {
                                Image(
                                    bitmap = makeFromEncoded(it).toComposeImageBitmap(),
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.size(250.dp)
                                )
                            }
                        } else {
                            // placeholder
                            Box(Modifier.size(250.dp))
                        }

                        when (qrCodeState) {
                            QRCodeState.WAITING_FOR_SCAN -> {
                                Text("请使用手机 QQ 扫描二维码")
                            }

                            QRCodeState.WAITING_FOR_CONFIRMATION -> {
                                Text("扫码成功，请在手机 QQ 上确认登录")
                            }

                            QRCodeState.CONFIRMED -> {
                                Text("登录中")
                            }

                            QRCodeState.CODE_EXPIRED, QRCodeState.CANCELLED -> {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        qrCodeImage = null
                                        qrCodeState = null
                                        loginError = null // an error has been thrown
                                        usingQrCode = !hasSession
                                    }
                                ) {
                                    Text("返回")
                                }
                            }

                            else -> {
                                AccentButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        bot.launch {
                                            try {
                                                bot.qrCodeLogin(1000L)
                                                onLoggedIn()
                                            } catch (e: Throwable) {
                                                loginError = "登录失败：${e.localizedMessage}"
                                            }
                                        }
                                    }
                                ) {
                                    Text("获取二维码")
                                }
                            }
                        }
                    }

                    loginError?.let { Text(it) }
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