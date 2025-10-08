package org.ntqqrev.cecilia.structs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ntqqrev.acidify.util.log.LogLevel

@Serializable
data class CeciliaConfig(
    val signApiUrl: String = "https://sign.lagrangecore.org/api/sign/39038",
    val signApiHttpProxy: String = "",
    val minLogLevel: LogLevel = LogLevel.DEBUG,
) {
    companion object {
        val jsonModule = Json {
            encodeDefaults = true
            prettyPrint = true
        }
    }
}