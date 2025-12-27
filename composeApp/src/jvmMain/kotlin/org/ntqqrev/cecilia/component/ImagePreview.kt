package org.ntqqrev.cecilia.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
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
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
                        awaitPointerEvent() // Keep the overlay active without swallowing events needed for gestures.
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
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset += dragAmount * scale // Faster pan when zoomed-in
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollY != 0f) {
                                val zoom = (scale - scrollY * 0.1f).coerceIn(0.2f, 5f)
                                scale = zoom
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        if (bitmap != null) {
            val (displayWidth, displayHeight) = (bitmap.width to bitmap.height).coerceInRectBox(
                maxWidth = this.maxWidth.value.toInt(),
                maxHeight = this.maxHeight.value.toInt() - 32,
            )
            Image(
                bitmap = bitmap,
                contentDescription = "",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(displayWidth.dp, displayHeight.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
            )
        }

        if (animatedFrames != null) {
            val firstFrame = animatedFrames.first().imageData
            val (displayWidth, displayHeight) = (firstFrame.width to firstFrame.height).coerceInRectBox(
                maxWidth = this.maxWidth.value.toInt(),
                maxHeight = this.maxHeight.value.toInt() - 32,
            )
            AnimatedImage(
                frames = animatedFrames,
                contentDescription = "",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(displayWidth.dp, displayHeight.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
            )
        }

        SubtleButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(72.dp)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
        )
    }
}
