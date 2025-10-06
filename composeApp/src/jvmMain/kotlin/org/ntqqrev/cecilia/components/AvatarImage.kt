package org.ntqqrev.cecilia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.ntqqrev.cecilia.AvatarCache
import org.ntqqrev.cecilia.LocalBot

@Composable
fun AvatarImage(
    uin: Long,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    quality: Int = 100  // 100, 140, 640
) {
    val bot = LocalBot.current

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

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
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
}

