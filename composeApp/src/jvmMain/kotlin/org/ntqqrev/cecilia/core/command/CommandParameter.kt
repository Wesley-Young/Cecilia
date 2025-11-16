package org.ntqqrev.cecilia.core.command

data class CommandParameter(
    val key: String,
    val name: String = key,
    val description: String = "",
    val required: Boolean = true,
    val suggestionsProvider: suspend CommandCompletionContext.(String) -> List<CommandSuggestion> = { emptyList() },
)