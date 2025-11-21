package org.ntqqrev.cecilia.util

fun String.isValidUrl(): Boolean = runCatching {
    if (isEmpty()) return@runCatching false
    if (!startsWith("http://") && !startsWith("https://")) return@runCatching false
    val urlPattern = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")
    urlPattern.matches(this)
}.getOrElse { false }