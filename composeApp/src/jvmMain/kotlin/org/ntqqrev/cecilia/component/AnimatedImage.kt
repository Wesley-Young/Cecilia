package org.ntqqrev.cecilia.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import org.ntqqrev.cecilia.core.AnimationFrame

@Composable
fun AnimatedImage(
    frames: List<AnimationFrame>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var frameIndex by remember { mutableStateOf(0) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = frames[frameIndex].imageData,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }

    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(frames[frameIndex].durationMillis)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }
}