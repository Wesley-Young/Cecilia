package org.ntqqrev.cecilia.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlin.math.min

/**
 * 获取当前操作系统的标题栏高度
 */
private fun getTitleBarHeight(): Int {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> 28 // macOS
        osName.contains("win") -> 32 // Windows
        else -> 30 // Linux 或其他系统
    }
}

@Composable
fun ImagePreviewWindow(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    title: String,
    onCloseRequest: () -> Unit
) {
    // 计算初始窗口大小：图片尺寸限制在 800x800
    val initialSize = remember(imageWidth, imageHeight) {
        val maxDimension = 800
        val scale = min(
            maxDimension.toFloat() / imageWidth,
            maxDimension.toFloat() / imageHeight
        ).coerceAtMost(1f) // 如果图片小于800x800，不放大

        // 标题栏高度补偿（根据操作系统自动适配）
        val titleBarHeight = getTitleBarHeight().dp
        
        DpSize(
            width = (imageWidth * scale).dp,
            height = (imageHeight * scale).dp + titleBarHeight
        )
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = title,
        state = rememberWindowState(size = initialSize)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            // 使用 ContentScale.Fit 保持图片宽高比
            Image(
                bitmap = imageBitmap,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
