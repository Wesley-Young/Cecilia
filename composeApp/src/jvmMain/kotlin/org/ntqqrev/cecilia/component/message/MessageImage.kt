package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.cecilia.component.AnimatedImage
import org.ntqqrev.cecilia.core.AnimationFrame
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalHttpClient
import org.ntqqrev.cecilia.core.LocalMediaCache
import org.ntqqrev.cecilia.model.Element
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun MessageImage(
    image: Element.Image,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bot = LocalBot.current
    val httpClient = LocalHttpClient.current
    val mediaCache = LocalMediaCache.current
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var animationFrames by remember { mutableStateOf<List<AnimationFrame>?>(null) }
    var hasLoadingError by remember { mutableStateOf(false) }

    LaunchedEffect(image.fileId) {
        runCatching {
            val cachedContent = mediaCache.getContentByFileId(image.fileId)
            val bytes = if (cachedContent != null) {
                cachedContent
            } else {
                val url = bot.getDownloadUrl(image.fileId)
                withContext(Dispatchers.IO) {
                    val response = httpClient.get(url)
                    val bytes = response.readRawBytes()
                    bytes.also {
                        mediaCache.putFileIdAndContent(image.fileId, url, bytes)
                    }
                }
            }
            imageBytes = bytes
            val data = Data.makeFromBytes(bytes)
            Codec.makeFromData(data).use { codec ->
                if (codec.frameCount > 1) {
                    val frames = mutableListOf<ImageBitmap>()
                    val durations = mutableListOf<Int>()
                    repeat(codec.frameCount) { index ->
                        val bitmap = SkiaBitmap()
                        bitmap.allocPixels(codec.imageInfo)
                        codec.readPixels(bitmap, index)
                        frames += SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
                        val duration = codec.getFrameInfo(index).duration
                        durations += duration.coerceAtLeast(16)
                    }
                    animationFrames = frames.zip(durations) { img, dur ->
                        AnimationFrame(
                            imageData = img,
                            durationMillis = dur.toLong(),
                        )
                    }
                } else {
                    imageBitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }
        }.onFailure {
            it.printStackTrace()
            hasLoadingError = true
        }
    }

    val maxWidth = when (image.subType) {
        ImageSubType.NORMAL -> 300.dp
        ImageSubType.STICKER -> 200.dp
    }

    val (displayWidth, displayHeight) = run {
        val aspectRatio = image.width.toFloat() / image.height.toFloat()
        var w = image.width.dp
        var h = image.height.dp
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
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            animationFrames != null -> {
                AnimatedImage(
                    frames = animationFrames!!,
                    modifier = modifier,
                    contentDescription = image.summary,
                    contentScale = contentScale
                )
            }

            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    modifier = modifier,
                    contentDescription = image.summary,
                    contentScale = contentScale
                )
            }

            hasLoadingError -> {
                Text(
                    text = "[图片加载失败]",
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.tertiary,
                )
            }

            else -> {
                // is loading
                ProgressRing()
            }
        }
    }
}