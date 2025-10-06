package org.ntqqrev.cecilia.utils

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 提供 ConversationManager 的 CompositionLocal
 */
val LocalConversationManager = staticCompositionLocalOf<ConversationManager> {
    error("No ConversationManager provided")
}

