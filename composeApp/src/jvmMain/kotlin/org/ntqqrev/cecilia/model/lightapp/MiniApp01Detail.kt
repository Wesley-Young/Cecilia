package org.ntqqrev.cecilia.model.lightapp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class MiniApp01Detail(
    val appid: String,
    val appType: Int,
    val title: String,
    val desc: String,
    val icon: String,
    val preview: String,
    val url: String,
    val scene: Int,
    val host: JsonElement,
    val shareTemplateId: String,
    val shareTemplateData: JsonElement,
    val qqdocurl: String,
    val showLittleTail: String,
    val gamePoints: String,
    val gamePointsUrl: String,
    val shareOrigin: Int,
)