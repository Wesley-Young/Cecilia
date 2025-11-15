package org.ntqqrev.cecilia.commands

import io.ktor.client.*
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroup
import kotlin.reflect.KProperty

data class CommandParameter(
    val key: String,
    val name: String = key,
    val description: String = "",
    val placeholder: String = "",
    val required: Boolean = true,
    val suggestionsProvider: suspend CommandCompletionContext.(String) -> List<CommandSuggestion> = { emptyList() },
)

data class Command(
    val id: String,
    val title: String,
    val description: String = "",
    val parameters: List<CommandParameter> = emptyList(),
    val execute: suspend CommandExecutionContext.() -> Unit
)

class CommandCompletionContext(
    val bot: Bot,
    val httpClient: HttpClient,
    val currentFriend: BotFriend? = null,
    val currentGroup: BotGroup? = null,
)

data class CommandSuggestion(
    val content: String,
    val display: String? = null
)

class CommandExecutionContext(
    val arguments: Map<String, String>,
    val bot: Bot,
    val httpClient: HttpClient,
    val currentFriend: BotFriend? = null,
    val currentGroup: BotGroup? = null,
) {
    val args = CommandArguments(arguments)
}

class CommandArguments internal constructor(
    private val values: Map<String, String>
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return values[property.name]
            ?: error("缺少参数 \"${property.name}\"")
    }
}