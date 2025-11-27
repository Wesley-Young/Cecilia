package org.ntqqrev.cecilia.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import org.ntqqrev.cecilia.util.APNG
import org.ntqqrev.cecilia.util.ResourceLoader.getResourceBytes
import org.ntqqrev.cecilia.util.isNumeric

class FaceEntry(
    val png: ImageBitmap,
    val apng: List<AnimationFrame>?,
    val lottie: String? = null,
) {
    @Serializable
    data class JsonModel(
        val emojiId: String,
        val describe: String,
        val qzoneCode: String,
        val qcid: Int,
        val emojiType: Int,
        val aniStickerPackId: Int,
        val aniStickerId: Int,
        val associateWords: List<String>,
        val isHide: Boolean,
        val startTime: String,
        val endTime: String,
        val animationWidth: Int,
        val animationHeigh: Int,
        val assets: List<FaceAsset>,
    ) {
        @Serializable
        data class FaceAsset(
            val type: Int,
            val name: String,
            val path: String,
        )
    }

    companion object {
        val all: Map<String, FaceEntry> by lazy {
            val indexedEmojis = Json.decodeFromString<List<JsonModel>>(
                getResourceBytes("assets/qq_emoji/_index.json")!!.decodeToString()
            )
            val fallback = getResourceBytes("assets/default.png")!!

            indexedEmojis.parallelStream().filter { it.emojiId.isNumeric() }.map {
                val pngBytes = getResourceBytes("assets/qq_emoji/${it.emojiId}/png/${it.emojiId}.png") ?: fallback
                val apngBytes = getResourceBytes("assets/qq_emoji/${it.emojiId}/apng/${it.emojiId}.png")
                val lottieString =
                    getResourceBytes("assets/qq_emoji/${it.emojiId}/lottie/${it.emojiId}.json")?.decodeToString()
                it.emojiId to FaceEntry(
                    png = Image.makeFromEncoded(pngBytes).toComposeImageBitmap(),
                    apng = apngBytes?.let {
                        runCatching {
                            APNG.parse(apngBytes).map { apngFrame ->
                                AnimationFrame(
                                    durationMillis = apngFrame.delayMillis,
                                    imageData = apngFrame.image.toComposeImageBitmap()
                                )
                            }
                        }.getOrNull()
                    },
                    lottie = lottieString
                )
            }.toList().toMap()
        }
    }
}