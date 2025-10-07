package org.ntqqrev.cecilia.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.cecilia.utils.LocalBot

@Composable
fun MessageImage(
    imageSegment: BotIncomingSegment.Image,
    isSent: Boolean
) {
    val bot = LocalBot.current
    var imageBitmap by remember(imageSegment.fileId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember(imageSegment.fileId) { mutableStateOf(true) }
    var hasError by remember(imageSegment.fileId) { mutableStateOf(false) }

    LaunchedEffect(imageSegment.fileId) {
        launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val url = bot.getDownloadUrl(imageSegment.fileId)
                    val response = bot.httpClient.get(url)
                    val imageBytes = response.readRawBytes()
                    SkiaImage.makeFromEncoded(imageBytes).toComposeImageBitmap()
                }
                imageBitmap = bitmap
                isLoading = false
            } catch (e: Exception) {
                hasError = true
                isLoading = false
            }
        }
    }

    val maxWidth = when (imageSegment.subType) {
        ImageSubType.NORMAL -> 300.dp
        ImageSubType.STICKER -> 200.dp
    }
    // 计算图片显示尺寸，保持宽高比，使用 imageSegment.width 和 height
    val (displayWidth, displayHeight) = run {
        val aspectRatio = imageSegment.width.toFloat() / imageSegment.height.toFloat()
        var w = imageSegment.width.dp
        var h = imageSegment.height.dp
        if (w > maxWidth) {
            w = maxWidth
            h = w / aspectRatio
        }
        if (h > maxWidth) {
            h = maxWidth
            w = h * aspectRatio
        }
        w to h
    }

    val modifier = Modifier
        .width(displayWidth)
        .height(displayHeight)
        .clip(RoundedCornerShape(4.dp))

    Box(
        modifier = modifier
            .background(
                if (isSent)
                    MaterialTheme.colors.primary.copy(alpha = 0.3f)
                else
                    MaterialTheme.colors.surface.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = if (isSent)
                            MaterialTheme.colors.onPrimary
                        else
                            MaterialTheme.colors.primary
                    )
                }
            }
            hasError -> {
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material.Text(
                        text = "[图片加载失败]",
                        style = MaterialTheme.typography.caption,
                        color = if (isSent)
                            MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = imageSegment.summary,
                    modifier = modifier,
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

