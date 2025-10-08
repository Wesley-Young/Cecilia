package org.ntqqrev.cecilia.structs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ntqqrev.acidify.util.log.LogLevel
import org.ntqqrev.cecilia.ThemeType
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class CeciliaConfig(
    val signApiUrl: String = "https://sign.lagrangecore.org/api/sign/39038",
    val signApiHttpProxy: String = "",
    val minLogLevel: LogLevel = LogLevel.DEBUG,
    val theme: ThemeType = ThemeType.GREEN,
    val displayScale: Float = 1.0f,
    val macUseNotoSansSC: Boolean = false,
    val useCtrlEnterToSend: Boolean = true,
) {
    companion object {
        val jsonModule = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        fun fromPath(path: Path): CeciliaConfig {
            val config = if (path.exists()) {
                jsonModule.decodeFromString(path.readText())
            } else {
                val defaultConfig = CeciliaConfig()
                path.writeText(jsonModule.encodeToString(defaultConfig))
                defaultConfig
            }
            return config
        }
    }

    fun writeToPath(path: Path) {
        path.writeText(jsonModule.encodeToString(this))
    }
}