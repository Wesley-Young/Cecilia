package org.ntqqrev.cecilia.component.message

import KottieAnimation
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kottieComposition.KottieCompositionSpec
import kottieComposition.animateKottieCompositionAsState
import kottieComposition.rememberKottieComposition
import org.ntqqrev.cecilia.core.LocalEmojiImages

@Composable
fun MessageLargeFace(faceId: Int) {
    val emojiImages = LocalEmojiImages.current
    val animationString = remember(faceId) {
        emojiImages?.get(faceId.toString())?.lottie
    } ?: return
    val composition = rememberKottieComposition(
        spec = KottieCompositionSpec.File(animationString)
    )

    val animationState by animateKottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = Int.MAX_VALUE
    )

    KottieAnimation(
        composition = composition,
        progress = { animationState.progress },
        modifier = Modifier.size(150.dp)
    )
}