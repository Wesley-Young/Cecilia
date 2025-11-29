package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import io.ktor.http.*
import io.ktor.utils.io.*
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
import org.ntqqrev.cecilia.util.coerceInSquareBox
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
    var progress by remember { mutableStateOf(0f) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var animationFrames by remember { mutableStateOf<List<AnimationFrame>?>(null) }
    var hasLoadingError by remember { mutableStateOf(false) }

    LaunchedEffect(image.fileId) {
        runCatching {
            // reset states for new image
            progress = 0f
            imageBitmap = null
            animationFrames = null
            hasLoadingError = false

            val cachedContent = mediaCache.getContentByFileId(image.fileId)
            val bytes = if (cachedContent != null) {
                cachedContent
            } else {
                val url = bot.getDownloadUrl(image.fileId)
                val downloaded = httpClient.get(url).let { response ->
                    val contentLength = response.contentLength()
                    if (contentLength != null) {
                        val channel = response.bodyAsChannel()
                        val result = ByteArray(contentLength.toInt())
                        var totalRead = 0
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(result, totalRead, result.size - totalRead)
                            if (read == -1) break
                            totalRead += read
                            progress = totalRead.toFloat() / contentLength
                        }
                        result
                    } else {
                        response.readRawBytes()
                    }
                }
                downloaded.also {
                    mediaCache.putFileIdAndContent(image.fileId, url, it)
                }
            }
            var decodedStatic: ImageBitmap? = null
            var decodedFrames: List<AnimationFrame>? = null
            withContext(Dispatchers.Default) {
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
                        decodedFrames = frames.zip(durations) { img, dur ->
                            AnimationFrame(
                                imageData = img,
                                durationMillis = dur.toLong(),
                            )
                        }
                    } else {
                        decodedStatic = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                    }
                }
            }
            imageBitmap = decodedStatic
            animationFrames = decodedFrames
        }.onFailure {
            it.printStackTrace()
            hasLoadingError = true
        }
    }

    val maxWidth = when (image.subType) {
        ImageSubType.NORMAL -> 300.dp
        ImageSubType.STICKER -> 200.dp
    }

    val (displayWidth, displayHeight) = Pair(
        image.width,
        image.height
    ).coerceInSquareBox(maxWidth.value.toInt())

    val modifier = Modifier.size(width = displayWidth.dp, height = displayHeight.dp)
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
                if (progress == 0f || progress > 1f) {
                    ProgressRing()
                } else {
                    ProgressRing(progress = progress)
                }
            }
        }
    }
}