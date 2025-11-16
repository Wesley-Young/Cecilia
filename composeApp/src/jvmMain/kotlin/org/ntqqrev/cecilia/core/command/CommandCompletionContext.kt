package org.ntqqrev.cecilia.core.command

import io.ktor.client.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroup
import org.ntqqrev.cecilia.core.ContactsState

class CommandCompletionContext(
    val bot: Bot,
    val httpClient: HttpClient,
    val currentFriend: BotFriend? = null,
    val currentGroup: BotGroup? = null,
    val contactsState: ContactsState? = null,
)