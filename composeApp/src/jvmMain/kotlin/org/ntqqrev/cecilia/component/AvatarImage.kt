package org.ntqqrev.cecilia.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.ProgressRing
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.ntqqrev.cecilia.core.LocalAvatarCache
import org.ntqqrev.cecilia.core.LocalHttpClient

@Composable
fun AvatarImage(
    uin: Long,
    size: Dp = 48.dp,
    isGroup: Boolean = false,
    quality: Int = 100,
) {
    val httpClient = LocalHttpClient.current
    val avatarCache = LocalAvatarCache.current
    val cachedBitmap = remember(uin, isGroup, quality) {
        avatarCache.get(uin, isGroup, quality)
    }
    var avatarBitmap by remember(uin, isGroup, quality) {
        mutableStateOf(cachedBitmap)
    }
    var isLoading by remember(uin, isGroup, quality) {
        mutableStateOf(cachedBitmap == null)
    }

    LaunchedEffect(uin, isGroup, quality) {
        if (cachedBitmap != null) {
            return@LaunchedEffect
        }

        launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val url = if (isGroup) {
                        "https://p.qlogo.cn/gh/$uin/$uin/$quality"
                    } else {
                        "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=$quality"
                    }
                    val response = httpClient.get(url)
                    val imageBytes = response.readRawBytes()
                    Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
                }
            }.onSuccess { bitmap ->
                avatarCache.put(uin, isGroup, quality, bitmap)
                avatarBitmap = bitmap
            }
            isLoading = false
        }
    }

    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
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
                    ProgressRing(
                        modifier = Modifier.size(size / 2),
                    )
                }
            }
        }
    }
}