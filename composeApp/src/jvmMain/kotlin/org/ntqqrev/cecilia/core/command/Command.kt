package org.ntqqrev.cecilia.core.command

data class Command(
    val id: String,
    val title: String,
    val description: String = "",
    val parameters: List<CommandParameter> = emptyList(),
    val execute: suspend CommandExecutionContext.() -> Unit
)