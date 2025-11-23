package org.ntqqrev.cecilia.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import java.awt.Cursor

@Composable
fun DraggableDivider(
    currentWidth: Dp,
    onWidthChange: (Dp) -> Unit,
    minWidth: Dp = 200.dp,
    maxWidth: Dp = 600.dp,
    dividerWidth: Dp = 3.dp,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentWidthState = rememberUpdatedState(currentWidth)
    Box(
        modifier.fillMaxHeight()
            .width(dividerWidth)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                var dragStartWidth = 0.dp
                var totalDrag = 0f
                detectDragGestures(
                    onDragStart = {
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
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showDivider) {
                Box(
                    Modifier.fillMaxHeight()
                        .width(1.dp)
                        .background(FluentTheme.colors.stroke.divider.default)
                )
            }
        }
    }
}