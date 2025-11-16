package org.ntqqrev.cecilia.core

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.client.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.struct.DisplayMessage

val LocalConfig = compositionLocalOf<CeciliaConfig> {
    error("No CeciliaConfig provided")
}

val LocalSetConfig = staticCompositionLocalOf<(CeciliaConfig) -> Unit> {
    error("No set CeciliaConfig provided")
}

val LocalBot = staticCompositionLocalOf<Bot> {
    error("No Bot provided")
}

val LocalHttpClient = staticCompositionLocalOf<HttpClient> {
    error("No HttpClient builder provided")
}

val LocalConversationManager = staticCompositionLocalOf<ConversationManager> {
    error("No ConversationManager provided")
}

val LocalAllMessages = compositionLocalOf<List<DisplayMessage>> {
    error("No Messages provided")
}

val LocalContactsState = staticCompositionLocalOf<ContactsState> {
    error("No ContactsState provided")
}
