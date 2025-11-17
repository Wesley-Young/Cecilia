package org.ntqqrev.cecilia.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DraggableDivider(
    currentWidth: Dp,
    onWidthChange: (Dp) -> Unit,
    minWidth: Dp = 200.dp,
    maxWidth: Dp = 600.dp,
    dividerWidth: Dp = 6.dp,
    dividerLineWidth: Dp = 1.dp,
    modifier: Modifier = Modifier
) {
    // 使用 rememberUpdatedState 确保闭包中始终能获取到最新的 currentWidth
    val currentWidthState = rememberUpdatedState(currentWidth)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(dividerWidth)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                var dragStartWidth = 0.dp
                var totalDrag = 0f
                detectDragGestures(
                    onDragStart = {
                        // 每次拖拽开始时获取最新的宽度
                        dragStartWidth = currentWidthState.value
                        totalDrag = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount.x
                        val newWidth = (dragStartWidth + totalDrag.toDp()).coerceIn(minWidth, maxWidth)
                        onWidthChange(newWidth)
                    }
                )
            }
            .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(dividerLineWidth),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )
    }
}

