package org.ntqqrev.cecilia.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import io.ktor.client.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.structs.CeciliaConfig
import org.ntqqrev.cecilia.structs.DisplayMessage

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
