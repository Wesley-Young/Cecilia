package org.ntqqrev.cecilia.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Icon
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Dismiss
import org.ntqqrev.cecilia.core.AnimationFrame
import org.ntqqrev.cecilia.util.coerceInRectBox

@Composable
fun ImagePreview(
    bitmap: ImageBitmap?,
    animatedFrames: List<AnimationFrame>?,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .background(Color.Black.copy(alpha = 0.8f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .onKeyEvent { event ->
                if (event.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val (displayWidth, displayHeight) = (bitmap.width to bitmap.height).coerceInRectBox(
                maxWidth = this.maxWidth.value.toInt(),
                maxHeight = this.maxHeight.value.toInt(),
            )
            Image(
                bitmap = bitmap,
                contentDescription = "",
                modifier = Modifier.size(displayWidth.dp, displayHeight.dp)
            )
        }

        if (animatedFrames != null) {
            val firstFrame = animatedFrames.first().imageData
            val (displayWidth, displayHeight) = (firstFrame.width to firstFrame.height).coerceInRectBox(
                maxWidth = this.maxWidth.value.toInt(),
                maxHeight = this.maxHeight.value.toInt(),
            )
            AnimatedImage(
                frames = animatedFrames,
                contentDescription = "",
                modifier = Modifier.size(displayWidth.dp, displayHeight.dp)
            )
        }

        SubtleButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dismiss,
                contentDescription = "关闭预览",
                tint = Color.White,
            )
        }

        Text(
            text = "按 Esc 或点击右上角关闭预览",
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}