package org.ntqqrev.cecilia.model.lightapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TuwenLuaNews(
    @SerialName("app_type") val appType: Int,
    val appid: Long,
    val ctime: Long,
    val desc: String,
    val jumpUrl: String,
    val preview: String,
    val tag: String,
    val tagIcon: String,
    val title: String,
    val uin: Long,
)