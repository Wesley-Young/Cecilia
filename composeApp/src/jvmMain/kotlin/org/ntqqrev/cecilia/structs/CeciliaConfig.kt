package org.ntqqrev.cecilia.structs

import kotlinx.serialization.Serializable

@Serializable
class CeciliaConfig(
    var signApiUrl: String = "https://sign.lagrangecore.org/api/sign/39038",
)