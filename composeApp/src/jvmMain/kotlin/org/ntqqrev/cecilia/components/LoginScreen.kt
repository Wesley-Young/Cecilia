package org.ntqqrev.cecilia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.ntqqrev.acidify.event.QRCodeGeneratedEvent
import org.ntqqrev.acidify.event.QRCodeStateQueryEvent
import org.ntqqrev.acidify.struct.QRCodeState
import org.ntqqrev.cecilia.LocalBot
import qrcode.QRCode

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onLoginStateChange: ((Boolean, Long) -> Unit)?
) {
    val bot = LocalBot.current
    val hasSession = bot.sessionStore.uin != 0L
    val qrCodeColorArgb = MaterialTheme.colors.primary.toArgb()
    var qrCodeImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrCodeState by remember { mutableStateOf<QRCodeState?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var userAvatar by remember { mutableStateOf<ImageBitmap?>(null) }
    var isUsingQRCode by remember { mutableStateOf(!hasSession) }

    // 加载用户头像
    LaunchedEffect(bot.sessionStore.uin) {
        if (bot.sessionStore.uin != 0L) {
            launch {
                try {
                    val avatarBitmap = withContext(Dispatchers.IO) {
                        val response = bot.httpClient.get("https://q1.qlogo.cn/g?b=qq&nk=${bot.sessionStore.uin}&s=640")
                        val imageBytes = response.readRawBytes()
                        Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
                    }
                    userAvatar = avatarBitmap
                } catch (e: Exception) {
                    // 加载头像失败，忽略
                }
            }
        }
    }

    // 监听事件
    LaunchedEffect(bot) {

        launch {
            bot.eventFlow.collect { event ->
                when (event) {
                    is QRCodeGeneratedEvent -> {
                        // 使用 qrcode-kotlin 生成自定义样式的二维码
                        qrCodeImage = Image.makeFromEncoded(
                            QRCode.ofCircles()
                                .withColor(qrCodeColorArgb)
                                .build(event.url)
                                .render()
                                .getBytes()
                        ).toComposeImageBitmap()
                        qrCodeState = QRCodeState.WAITING_FOR_SCAN
                    }

                    is QRCodeStateQueryEvent -> {
                        qrCodeState = event.state
                        if (event.state == QRCodeState.CONFIRMED) {
                            onLoginSuccess()
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .wrapContentHeight(),
            elevation = 4.dp
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
                    style = MaterialTheme.typography.h4.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.primary
                )

                if (hasSession && !isUsingQRCode) {
                    // 有会话，显示快捷登录
                    Spacer(modifier = Modifier.height(8.dp))
                    // 显示用户头像
                    if (userAvatar != null) {
                        Image(
                            bitmap = userAvatar!!,
                            contentDescription = "用户头像",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colors.primary, CircleShape)
                        )
                    } else {
                        // 头像加载中的占位符
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "检测到已有登录信息",
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        text = "账号: ${bot.sessionStore.uin}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isLoggingIn = true
                            loginError = null
                            bot.launch {
                                try {
                                    bot.tryLogin()
                                    onLoginSuccess()
                                } catch (e: Exception) {
                                    loginError = e.message ?: "登录失败"
                                    isLoggingIn = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoggingIn
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colors.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isLoggingIn) "登录中..." else "快捷登录")
                    }

                    TextButton(
                        onClick = {
                            bot.sessionStore.clear()
                            onLoginStateChange?.invoke(false, 0L)
                            isUsingQRCode = true
                            isLoggingIn = true
                            loginError = null
                            qrCodeImage = null
                            qrCodeState = null
                            bot.launch {
                                try {
                                    bot.qrCodeLogin(queryInterval = 1000L)
                                } catch (e: Exception) {
                                    loginError = e.message ?: "二维码登录失败"
                                    isLoggingIn = false
                                    qrCodeImage = null
                                    qrCodeState = null
                                }
                            }
                        },
                        enabled = !isLoggingIn
                    ) {
                        Text("使用二维码登录")
                    }
                } else {
                    // 无会话或正在登录，显示二维码登录
                    if (qrCodeImage == null && qrCodeState == null) {
                        if (!isLoggingIn) {
                            Text(
                                text = "首次登录",
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = "请使用手机QQ扫码登录",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    isUsingQRCode = true
                                    isLoggingIn = true
                                    loginError = null
                                    bot.launch {
                                        try {
                                            bot.qrCodeLogin(queryInterval = 1000L)
                                        } catch (e: Exception) {
                                            loginError = e.message ?: "二维码登录失败"
                                            isLoggingIn = false
                                            qrCodeImage = null
                                            qrCodeState = null
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("获取二维码")
                            }
                        } else {
                            CircularProgressIndicator()
                            Text("正在获取二维码...")
                        }
                    } else {
                        // 显示二维码
                        qrCodeImage?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = "登录二维码",
                                modifier = Modifier.size(250.dp)
                            )
                        }

                        // 显示状态
                        when (qrCodeState) {
                            QRCodeState.WAITING_FOR_SCAN -> {
                                Text(
                                    text = "请使用手机 QQ 扫码",
                                    style = MaterialTheme.typography.body1
                                )
                            }

                            QRCodeState.WAITING_FOR_CONFIRMATION -> {
                                Text(
                                    text = "已扫码，请在手机上确认",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.primary
                                )
                            }

                            QRCodeState.CONFIRMED -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "登录中...",
                                        style = MaterialTheme.typography.body1,
                                        color = MaterialTheme.colors.primary
                                    )
                                }
                            }

                            QRCodeState.CODE_EXPIRED -> {
                                Text(
                                    text = "二维码已过期",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.error
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = {
                                            qrCodeImage = null
                                            qrCodeState = null
                                            isLoggingIn = false
                                        }
                                    ) {
                                        Text("重新获取")
                                    }
                                    if (hasSession) {
                                        TextButton(
                                            onClick = {
                                                qrCodeImage = null
                                                qrCodeState = null
                                                isLoggingIn = false
                                                isUsingQRCode = false
                                            }
                                        ) {
                                            Text("返回")
                                        }
                                    }
                                }
                            }

                            QRCodeState.CANCELLED -> {
                                Text(
                                    text = "已取消登录",
                                    style = MaterialTheme.typography.body1,
                                    color = MaterialTheme.colors.error
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = {
                                            qrCodeImage = null
                                            qrCodeState = null
                                            isLoggingIn = false
                                        }
                                    ) {
                                        Text("重新获取")
                                    }
                                    if (hasSession) {
                                        TextButton(
                                            onClick = {
                                                qrCodeImage = null
                                                qrCodeState = null
                                                isLoggingIn = false
                                                isUsingQRCode = false
                                            }
                                        ) {
                                            Text("返回")
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }

                // 显示错误信息
                loginError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}