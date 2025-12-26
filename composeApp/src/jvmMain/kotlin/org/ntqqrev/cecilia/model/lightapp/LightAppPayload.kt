package org.ntqqrev.cecilia.model.lightapp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
class LightAppPayload(
    val prompt: String,
    val app: String,
    val meta: Map<String, JsonElement>,
) {
    companion object {
        val jsonModule = Json {
            ignoreUnknownKeys = true
        }

        fun fromJson(json: String): LightAppPayload = jsonModule.decodeFromString(json)
    }
}