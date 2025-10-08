package org.ntqqrev.cecilia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.ntqqrev.acidify.struct.BotUserInfo
import org.ntqqrev.cecilia.utils.AvatarCache
import org.ntqqrev.cecilia.utils.LocalBot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AvatarImage(
    uin: Long,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    quality: Int = 100,  // 100, 140, 640
    clickable: Boolean = false  // 是否可点击显示用户信息
) {
    val bot = LocalBot.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    // 先从缓存中获取
    val cachedBitmap = remember(uin, isGroup, quality) {
        AvatarCache.get(uin, isGroup, quality)
    }

    var avatarBitmap by remember(uin, isGroup, quality) {
        mutableStateOf(cachedBitmap)
    }
    var isLoading by remember(uin, isGroup, quality) {
        mutableStateOf(cachedBitmap == null)
    }

    // 用户信息悬浮窗相关状态
    var showUserInfo by remember { mutableStateOf(false) }
    var userInfo by remember { mutableStateOf<BotUserInfo?>(null) }
    var isLoadingUserInfo by remember { mutableStateOf(false) }

    // 头像位置信息，用于智能定位
    var avatarPosition by remember { mutableStateOf(IntOffset.Zero) }
    var avatarSizePx by remember { mutableStateOf(0) }

    LaunchedEffect(uin, isGroup, quality) {
        // 如果缓存中已有，不需要重新加载
        if (cachedBitmap != null) {
            return@LaunchedEffect
        }

        launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val url = if (isGroup) {
                        "https://p.qlogo.cn/gh/$uin/$uin/$quality"
                    } else {
                        "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=$quality"
                    }
                    val response = bot.httpClient.get(url)
                    val imageBytes = response.readRawBytes()
                    Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
                }
                // 存入缓存
                AvatarCache.put(uin, isGroup, quality, bitmap)
                avatarBitmap = bitmap
            } catch (e: Exception) {
                // 加载失败，保持占位符
            } finally {
                isLoading = false
            }
        }
    }

    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary.copy(alpha = 0.2f))
                .onGloballyPositioned { coordinates ->
                    // 记录头像的位置和大小
                    val position = coordinates.positionInWindow()
                    avatarPosition = IntOffset(position.x.toInt(), position.y.toInt())
                    avatarSizePx = coordinates.size.width
                }
                .then(
                    if (clickable && !isGroup) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null  // 禁用点击动画效果
                        ) {
                            showUserInfo = true
                            // 加载用户信息
                            if (userInfo == null && !isLoadingUserInfo) {
                                isLoadingUserInfo = true
                                scope.launch {
                                    try {
                                        userInfo = withContext(Dispatchers.IO) {
                                            bot.fetchUserInfoByUin(uin)
                                        }
                                    } catch (e: Exception) {
                                        // 加载失败
                                    } finally {
                                        isLoadingUserInfo = false
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            avatarBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "头像",
                    modifier = Modifier.size(size).clip(CircleShape)
                )
            } ?: run {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size / 2),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // 用户信息悬浮窗 - 智能定位显示
        if (clickable && showUserInfo && !isGroup) {
            // 获取窗口尺寸
            val windowWidth = windowInfo.containerSize.width
            val windowHeight = windowInfo.containerSize.height

            // UserInfoCard 的尺寸（需要与 UserInfoCard.kt 中定义的一致）
            val cardWidth = with(density) { 280.dp.toPx().toInt() }
            val cardHeight = with(density) { 280.dp.toPx().toInt() }  // 预估高度
            val spacing = with(density) { 8.dp.toPx().toInt() }

            // 计算各个方向是否有足够空间
            val rightSpace = windowWidth - (avatarPosition.x + avatarSizePx)
            val leftSpace = avatarPosition.x
            val bottomSpace = windowHeight - (avatarPosition.y + avatarSizePx)
            val topSpace = avatarPosition.y
            println("Avatar position: $avatarPosition, window size: ${windowWidth}x${windowHeight}, spaces - right: $rightSpace, left: $leftSpace, bottom: $bottomSpace, top: $topSpace")

            // 智能选择显示位置
            val (alignment, offset) = if (rightSpace >= cardWidth + spacing) {
                if (bottomSpace >= cardHeight + spacing) {
                    Alignment.TopStart to IntOffset(avatarSizePx + spacing, 0)
                } else {
                    Alignment.BottomStart to IntOffset(avatarSizePx + spacing, 0)
                }
            } else {
                if (bottomSpace >= cardHeight + spacing) {
                    Alignment.TopEnd to IntOffset(-(avatarSizePx + spacing), 0)
                } else {
                    Alignment.BottomEnd to IntOffset(-(avatarSizePx + spacing), 0)
                }
            }

            Popup(
                alignment = alignment,
                offset = offset,
                onDismissRequest = { showUserInfo = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false  // 不裁剪，允许超出父容器边界
                )
            ) {
                UserInfoCard(
                    userInfo = userInfo,
                    isLoading = isLoadingUserInfo,
                    uin = uin
                )
            }
        }
    }
}
