package org.ntqqrev.cecilia.component.message

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.ntqqrev.acidify.message.BotIncomingSegment
import org.ntqqrev.acidify.message.ImageSubType
import org.ntqqrev.cecilia.component.view.ImagePreviewWindow
import org.ntqqrev.cecilia.core.LocalBot
import org.ntqqrev.cecilia.core.LocalHttpClient
import org.ntqqrev.cecilia.core.MediaCache
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageImage(
    imageSegment: BotIncomingSegment.Image,
    isSent: Boolean
) {
    val bot = LocalBot.current
    val httpClient = LocalHttpClient.current
    var imageBitmap by remember(imageSegment.fileId) { mutableStateOf<ImageBitmap?>(null) }
    var imageBytes by remember(imageSegment.fileId) { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember(imageSegment.fileId) { mutableStateOf(true) }
    var hasError by remember(imageSegment.fileId) { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var animatedFrames by remember(imageSegment.fileId) { mutableStateOf<List<ImageBitmap>?>(null) }
    var animatedDurations by remember(imageSegment.fileId) { mutableStateOf<List<Int>>(emptyList()) }


    LaunchedEffect(imageSegment.fileId) {
        launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val cachedContent = MediaCache.getContentByFileId(imageSegment.fileId)
                    val imageBytes = if (cachedContent != null) {
                        cachedContent
                    } else {
                        val url = bot.getDownloadUrl(imageSegment.fileId)
                        val response = httpClient.get(url)
                        val bytes = response.readRawBytes()
                        MediaCache.putFileIdAndContent(imageSegment.fileId, url, bytes)
                        bytes
                    }
                    val data = Data.makeFromBytes(imageBytes)
                    val codec = Codec.makeFromData(data)
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
                        codec.close()
                        Triple(imageBytes, frames.first(), frames to durations)
                    } else {
                        codec.close()
                        Triple(imageBytes, SkiaImage.makeFromEncoded(imageBytes).toComposeImageBitmap(), null)
                    }
                }
            }.onSuccess { bitmap ->
                imageBytes = bitmap.first
                imageBitmap = bitmap.second
                val animatedData = bitmap.third
                animatedFrames = animatedData?.first
                animatedDurations = animatedData?.second ?: emptyList()
            }.onFailure {
                hasError = true
            }
            isLoading = false
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
                    Text(
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
                DisableSelection {
                    ContextMenuArea(
                        enabled = imageBytes != null,
                        items = {
                            if (imageBytes != null) {
                                listOf(
                                    ContextMenuItem("复制图片") {
                                        imageBytes?.let { copyImageToClipboard(it) }
                                    }
                                )
                            } else emptyList()
                        }
                    ) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = imageSegment.summary,
                            modifier = modifier.combinedClickable(
                                onDoubleClick = { showPreview = true },
                                onClick = { /* 单击不做任何操作 */ }
                            ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }

    // 图片预览窗口
    if (showPreview && imageBitmap != null) {
        ImagePreviewWindow(
            imageBitmap = imageBitmap!!,
            imageWidth = imageSegment.width,
            imageHeight = imageSegment.height,
            title = "预览 - ${imageSegment.summary}",
            onCloseRequest = { showPreview = false }
        )
    }

    LaunchedEffect(imageSegment.fileId, animatedFrames, animatedDurations) {
        val frames = animatedFrames
        if (frames.isNullOrEmpty()) return@LaunchedEffect
        val durations = animatedDurations.takeIf { it.size == frames.size } ?: return@LaunchedEffect
        var frameIndex = 0
        while (true) {
            val delayMs = durations.getOrElse(frameIndex) { 100 }
            delay(delayMs.toLong())
            frameIndex = (frameIndex + 1) % frames.size
            imageBitmap = frames[frameIndex]
        }
    }
}

private fun copyImageToClipboard(bytes: ByteArray) {
    val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull() ?: return
    val bufferedImage = runCatching {
        ByteArrayInputStream(bytes).use { ImageIO.read(it) }
    }.getOrNull() ?: return
    val transferable = object : Transferable {
        private val supportedFlavors = arrayOf(DataFlavor.imageFlavor)
        override fun getTransferDataFlavors(): Array<DataFlavor> = supportedFlavors
        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor?): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return bufferedImage
        }
    }
    runCatching {
        clipboard.setContents(transferable, null)
    }
}
