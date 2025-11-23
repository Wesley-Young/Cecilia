package org.ntqqrev.cecilia.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@Serializable
data class FaceEntry(
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

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val all by lazy {
            Json.decodeFromStream<List<FaceEntry>>(
                this::class.java.classLoader
                    .getResourceAsStream("assets/qq_emoji/_index.json")
            )
        }
    }
}