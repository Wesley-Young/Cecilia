package org.ntqqrev.cecilia.core

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.client.*
import org.ntqqrev.acidify.Bot

val LocalBot = compositionLocalOf<Bot> {
    error("No Bot provided")
}

val LocalConfig = compositionLocalOf<Config> {
    error("No Config provided")
}

val LocalHttpClient = staticCompositionLocalOf<HttpClient> {
    error("No HttpClient provided")
}