package org.ntqqrev.cecilia.utils

import androidx.compose.runtime.staticCompositionLocalOf
import org.ntqqrev.acidify.Bot

val LocalBot = staticCompositionLocalOf<Bot> {
    error("No Bot provided")
}

val LocalCacheManager = staticCompositionLocalOf<CacheManager> {
    error("No CacheManager provided")
}

val LocalConversationManager = staticCompositionLocalOf<ConversationManager> {
    error("No ConversationManager provided")
}