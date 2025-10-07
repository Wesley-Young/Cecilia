package org.ntqqrev.cecilia.structs

import kotlinx.serialization.Serializable
import org.ntqqrev.acidify.util.log.LogLevel

@Serializable
class CeciliaConfig(
    var signApiUrl: String = "https://sign.lagrangecore.org/api/sign/39038",
    var minLogLevel: LogLevel = LogLevel.DEBUG,
)