package org.ntqqrev.cecilia.core

import androidx.compose.runtime.compositionLocalOf
import io.ktor.client.*
import org.ntqqrev.acidify.Bot

val LocalBot = compositionLocalOf<Bot> {
    error("No Bot provided")
}

val LocalHttpClient = compositionLocalOf<HttpClient> {
    error("No HttpClient provided")
}