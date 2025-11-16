package org.ntqqrev.cecilia.component.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroup
import org.ntqqrev.acidify.message.MessageScene
import org.ntqqrev.cecilia.core.ContactsState
import org.ntqqrev.cecilia.core.ConversationManager
import org.ntqqrev.cecilia.core.command.*
import org.ntqqrev.cecilia.struct.Conversation

@Composable
fun CommandPalette(
    bot: Bot,
    httpClient: HttpClient,
    commands: List<Command>,
    conversationManager: ConversationManager,
    contactsState: ContactsState,
    onDismiss: () -> Unit,
    onCommandError: (String) -> Unit = {}
) {
    val sortedCommands = remember(commands) { commands.sortedBy { it.id } }
    var manualInput by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val activeConversation by remember(conversationManager) {
        derivedStateOf {
            val targetId = conversationManager.selectedConversationId
            if (targetId == null) null else conversationManager.conversations.find { it.id == targetId }
        }
    }
    var conversationParticipants by remember { mutableStateOf(ConversationParticipants()) }

    LaunchedEffect(activeConversation?.id) {
        conversationParticipants = resolveConversationParticipants(bot, activeConversation)
    }

    fun updateText(newText: String, commitManual: Boolean) {
        textFieldValue = TextFieldValue(newText, TextRange(newText.length))
        if (commitManual) {
            manualInput = newText
        }
    }

    val consoleState = remember(manualInput) { ConsoleInputState.from(manualInput) }

    val commandMatches = remember(consoleState.commandToken, sortedCommands) {
        if (!consoleState.hasSlashPrefix || consoleState.commandToken.isBlank()) sortedCommands
        else sortedCommands.filter { it.id.startsWith(consoleState.commandToken, ignoreCase = true) }
    }

    val selectedCommand = if (consoleState.hasSlashPrefix)
        sortedCommands.firstOrNull { it.id.equals(consoleState.commandToken, ignoreCase = true) }
    else null

    rememberCoroutineScope()
    var parameterSuggestionState by remember { mutableStateOf(ParameterSuggestionState()) }

    val isExactCommandMatch = selectedCommand?.id?.equals(consoleState.commandToken, ignoreCase = true) == true
    val parameterSuggestionsActive =
        selectedCommand != null &&
                consoleState.isArgumentPhase &&
                parameterSuggestionState.parameter != null
    val commandSuggestionsActive = consoleState.hasSlashPrefix && !parameterSuggestionsActive && !isExactCommandMatch

    val suggestionItems = remember(
        parameterSuggestionsActive,
        commandSuggestionsActive,
        selectedCommand,
        parameterSuggestionState,
        commandMatches,
        consoleState
    ) {
        when {
            parameterSuggestionsActive -> {
                parameterSuggestionState.suggestions.map { suggestion ->
                    val displayText = suggestion.display ?: suggestion.content
                    val subtitle = if (!suggestion.display.isNullOrBlank() && suggestion.display != suggestion.content)
                        suggestion.content else ""
                    SuggestionItem(
                        title = displayText,
                        subtitle = subtitle,
                        applyText = fillArgumentValue(selectedCommand, consoleState, suggestion.content),
                    )
                }
            }

            commandSuggestionsActive -> {
                commandMatches.map { command ->
                    val content = buildCommandInput(command, emptyList())
                    SuggestionItem(
                        title = command.signature(),
                        subtitle = command.description,
                        applyText = content
                    )
                }
            }

            else -> emptyList()
        }
    }

    val currentParameterIndex = remember(selectedCommand, consoleState) {
        val params = selectedCommand?.parameters ?: return@remember null
        if (params.isEmpty()) return@remember null
        consoleState.currentArgumentIndex.coerceIn(0, params.lastIndex)
    }
    val currentArgumentValue = remember(currentParameterIndex, consoleState) {
        currentParameterIndex?.let { idx ->
            consoleState.arguments.getOrNull(idx).orEmpty()
        } ?: ""
    }
    LaunchedEffect(
        selectedCommand?.id,
        currentParameterIndex,
        currentArgumentValue,
        consoleState.isArgumentPhase,
        conversationParticipants
    ) {
        if (selectedCommand == null ||
            currentParameterIndex == null ||
            !consoleState.isArgumentPhase
        ) {
            parameterSuggestionState = ParameterSuggestionState()
            return@LaunchedEffect
        }
        val parameter = selectedCommand.parameters.getOrNull(currentParameterIndex)
        if (parameter == null) {
            parameterSuggestionState = ParameterSuggestionState()
            return@LaunchedEffect
        }
        val commandId = selectedCommand.id
        val sameParam =
            parameterSuggestionState.commandId == commandId &&
                    parameterSuggestionState.parameterIndex == currentParameterIndex
        parameterSuggestionState = if (sameParam) {
            parameterSuggestionState.copy(
                parameter = parameter,
                query = currentArgumentValue,
                isLoading = true
            )
        } else {
            ParameterSuggestionState(
                commandId = commandId,
                parameterIndex = currentParameterIndex,
                parameter = parameter,
                query = currentArgumentValue,
                suggestions = emptyList(),
                isLoading = true
            )
        }
        val suggestions = try {
            val completionContext = CommandCompletionContext(
                bot = bot,
                httpClient = httpClient,
                currentFriend = conversationParticipants.friend,
                currentGroup = conversationParticipants.group,
                contactsState = contactsState
            )
            parameter.suggestionsProvider.invoke(completionContext, currentArgumentValue)
        } catch (e: Exception) {
            emptyList()
        }
        if (currentParameterIndex == parameterSuggestionState.parameterIndex) {
            parameterSuggestionState = ParameterSuggestionState(
                commandId = commandId,
                parameterIndex = currentParameterIndex,
                parameter = parameter,
                query = currentArgumentValue,
                suggestions = suggestions,
                isLoading = false
            )
        }
    }

    var suggestionIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(suggestionItems) {
        suggestionIndex = -1
        if (suggestionItems.isEmpty() && textFieldValue.text != manualInput) {
            updateText(manualInput, commitManual = false)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun attemptExecuteAndExit() {
        val argsSnapshot = consoleState.arguments.toList()
        val validationError = validateCommand(selectedCommand, consoleState)
        if (validationError != null) {
            onCommandError(validationError)
            onDismiss()
            return
        }

        onDismiss()
        bot.launch {
            if (selectedCommand == null) return@launch
            try {
                executeCommand(
                    selectedCommand,
                    bot,
                    httpClient,
                    argsSnapshot,
                    conversationParticipants.friend,
                    conversationParticipants.group,
                    contactsState
                )
            } catch (e: Exception) {
                val errorMessage = e.message ?: e::class.simpleName ?: "未知错误"
                onCommandError("执行失败: $errorMessage")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val dismissInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = dismissInteraction, indication = null) { onDismiss() }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 36.dp)
                .padding(top = 48.dp)
                .widthIn(max = 780.dp),
            elevation = 24.dp,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .heightIn(max = 580.dp)
            ) {
                Text(text = "命令控制台", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "使用 / 开头的指令。Esc 关闭，Enter 执行，Tab 自动补全。",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val previewSuffix = remember(textFieldValue.text, manualInput) {
                    if (textFieldValue.text.startsWith(manualInput)) {
                        textFieldValue.text.removePrefix(manualInput)
                    } else {
                        ""
                    }
                }
                val displayText = buildAnnotatedString {
                    append(manualInput)
                    if (previewSuffix.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        ) {
                            append(previewSuffix)
                        }
                    }
                }
                val textFieldInteraction = remember { MutableInteractionSource() }
                val outlinedColors = TextFieldDefaults.outlinedTextFieldColors()
                val onSurfaceColor = MaterialTheme.colors.onSurface
                val pendingColor = onSurfaceColor.copy(alpha = 0.4f)
                val currentColor = MaterialTheme.colors.primary
                val commandPreviewText = remember(
                    selectedCommand,
                    consoleState.arguments,
                    currentParameterIndex,
                    onSurfaceColor,
                    pendingColor,
                    currentColor
                ) {
                    selectedCommand?.let { command ->
                        buildAnnotatedString {
                            append("/${command.id}")
                            command.parameters.forEachIndexed { index, parameter ->
                                append(" ")
                                val value = consoleState.arguments.getOrNull(index).orEmpty()
                                val isFilled = value.isNotBlank()
                                val display = if (currentParameterIndex == index) {
                                    "<${parameter.name}: ${parameter.description}>"
                                } else {
                                    "<${parameter.name}>"
                                }
                                val color = when {
                                    isFilled -> onSurfaceColor
                                    currentParameterIndex == index -> currentColor
                                    else -> pendingColor
                                }
                                withStyle(SpanStyle(color = color)) {
                                    append(display)
                                }
                            }
                        }
                    }
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { value ->
                        textFieldValue = value
                        manualInput = value.text
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter -> {
                                    if (!event.isShiftPressed) {
                                        attemptExecuteAndExit()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.Tab -> {
                                    if (suggestionItems.isNotEmpty()) {
                                        val target = when {
                                            suggestionIndex in suggestionItems.indices -> suggestionItems[suggestionIndex]
                                            else -> suggestionItems.first()
                                        }
                                        updateText(target.applyText, commitManual = true)
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionDown -> {
                                    if (suggestionItems.isNotEmpty()) {
                                        val lastIndex = suggestionItems.lastIndex
                                        val nextIndex = when {
                                            suggestionIndex < 0 -> 0
                                            suggestionIndex >= lastIndex -> lastIndex
                                            else -> suggestionIndex + 1
                                        }
                                        suggestionIndex = nextIndex
                                        suggestionItems.getOrNull(nextIndex)?.let {
                                            updateText(it.applyText, commitManual = false)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionUp -> {
                                    if (suggestionItems.isNotEmpty()) {
                                        val lastIndex = suggestionItems.lastIndex
                                        val nextIndex = when {
                                            suggestionIndex < 0 -> lastIndex
                                            suggestionIndex <= 0 -> 0
                                            else -> suggestionIndex - 1
                                        }
                                        suggestionIndex = nextIndex
                                        suggestionItems.getOrNull(nextIndex)?.let {
                                            updateText(it.applyText, commitManual = false)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }

                                else -> false
                            }
                        },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary)
                ) { innerTextField ->
                    TextFieldDefaults.OutlinedTextFieldDecorationBox(
                        value = textFieldValue.text,
                        visualTransformation = VisualTransformation.None,
                        innerTextField = {
                            Box {
                                Text(
                                    text = displayText,
                                    style = LocalTextStyle.current,
                                    color = MaterialTheme.colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                                innerTextField()
                            }
                        },
                        singleLine = true,
                        enabled = true,
                        isError = false,
                        interactionSource = textFieldInteraction,
                        placeholder = {
                            if (manualInput.isEmpty() && previewSuffix.isEmpty()) {
                                Text("输入 / 以开始补全")
                            }
                        },
                        colors = outlinedColors,
                        shape = MaterialTheme.shapes.small
                    )
                }

                if (commandPreviewText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = commandPreviewText, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    if (suggestionItems.isNotEmpty()) {
                        SuggestionList(
                            suggestions = suggestionItems,
                            highlightedIndex = suggestionIndex,
                            onSuggestionClicked = { item ->
                                updateText(item.applyText, commitManual = true)
                            }
                        )
                    }
                }

                if (suggestionItems.isEmpty() && selectedCommand != null && selectedCommand.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedCommand.description,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { attemptExecuteAndExit() }) {
                        Text("执行")
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<SuggestionItem>,
    highlightedIndex: Int,
    onSuggestionClicked: (SuggestionItem) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedIndex, suggestions.size) {
        if (highlightedIndex in suggestions.indices) {
            listState.animateScrollToItem(highlightedIndex)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colors.surface,
        elevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    ) {
        if (suggestions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有可用的补全",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(suggestions) { index, suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == highlightedIndex)
                                    MaterialTheme.colors.primary.copy(alpha = 0.12f)
                                else
                                    Color.Transparent
                            )
                            .clickable { onSuggestionClicked(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = true)) {
                            Text(text = suggestion.title, style = MaterialTheme.typography.subtitle1)
                            if (suggestion.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = suggestion.subtitle,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

private data class ParameterSuggestionState(
    val commandId: String? = null,
    val parameterIndex: Int = -1,
    val parameter: CommandParameter? = null,
    val query: String = "",
    val suggestions: List<CommandSuggestion> = emptyList(),
    val isLoading: Boolean = false
)

private data class SuggestionItem(
    val title: String,
    val subtitle: String = "",
    val applyText: String
)

private data class ConsoleInputState(
    val commandToken: String,
    val arguments: List<String>,
    val isArgumentPhase: Boolean,
    val isCursorAtNewArgument: Boolean,
    val currentArgumentIndex: Int,
    val currentArgumentValue: String,
    val hasSlashPrefix: Boolean
) {
    companion object {
        fun from(input: String): ConsoleInputState {
            val sanitized = input.replace('\n', ' ')
            val trimmed = sanitized.trimStart()
            val hasSlashPrefix = trimmed.startsWith('/')
            val content = if (hasSlashPrefix) trimmed.removePrefix("/") else ""
            val tokens = if (hasSlashPrefix) content.split(Regex("\\s+")).filter { it.isNotEmpty() } else emptyList()
            val commandToken = if (hasSlashPrefix) tokens.firstOrNull()?.lowercase() ?: "" else ""
            val arguments = if (hasSlashPrefix && tokens.size > 1) tokens.drop(1) else emptyList()
            val argumentsStarted = hasSlashPrefix && trimmed.contains(" ") && trimmed.length > 1
            val cursorAtNewArgument = argumentsStarted && trimmed.last() == ' '
            val currentIndex = when {
                !argumentsStarted -> 0
                arguments.isEmpty() -> 0
                cursorAtNewArgument -> arguments.size
                else -> arguments.lastIndex
            }
            val currentValue = when {
                !argumentsStarted -> ""
                arguments.isEmpty() -> ""
                cursorAtNewArgument -> ""
                else -> arguments.last()
            }
            return ConsoleInputState(
                commandToken = commandToken,
                arguments = arguments,
                isArgumentPhase = argumentsStarted,
                isCursorAtNewArgument = cursorAtNewArgument,
                currentArgumentIndex = currentIndex,
                currentArgumentValue = currentValue,
                hasSlashPrefix = hasSlashPrefix
            )
        }
    }
}

private fun validateCommand(command: Command?, state: ConsoleInputState): String? {
    if (!state.hasSlashPrefix || state.commandToken.isBlank()) {
        return "请输入以 / 开头的命令"
    }
    val targetCommand = command ?: return "未知命令：${state.commandToken}"
    val missingParameter = targetCommand.parameters.withIndex().firstOrNull { (index, parameter) ->
        parameter.required && state.arguments.getOrNull(index).isNullOrBlank()
    }?.value
    return missingParameter?.let { "参数 \"${it.name}\" 尚未填写" }
}

private fun Command.signature(): String {
    val builder = StringBuilder().apply {
        append("/")
        append(id)
        parameters.forEach { param ->
            append(' ')
            append('<')
            append(param.name)
            append('>')
        }
    }
    return builder.toString()
}

private fun buildCommandInput(command: Command, args: List<String>): String {
    val builder = StringBuilder().apply {
        append("/")
        append(command.id)
        if (args.isNotEmpty()) {
            append(' ')
            append(args.joinToString(" "))
        }
    }
    return builder.toString()
}

private fun fillArgumentValue(command: Command, state: ConsoleInputState, suggestion: String): String {
    val args = state.arguments.toMutableList()
    val index = state.currentArgumentIndex.coerceAtLeast(0)
    if (state.isCursorAtNewArgument || args.size <= index) {
        args.add(suggestion)
    } else {
        args[index] = suggestion
    }
    return buildCommandInput(command, args)
}

private suspend fun executeCommand(
    command: Command,
    bot: Bot,
    httpClient: HttpClient,
    args: List<String>,
    currentFriend: BotFriend?,
    currentGroup: BotGroup?,
    contactsState: ContactsState?
) {
    val payload = mutableMapOf<String, String>()
    command.parameters.forEachIndexed { index, parameter ->
        payload[parameter.key] = args.getOrNull(index).orEmpty()
    }
    val ctx = CommandExecutionContext(
        arguments = payload,
        bot = bot,
        httpClient = httpClient,
        currentFriend = currentFriend,
        currentGroup = currentGroup,
        contactsState = contactsState
    )
    command.execute.invoke(ctx)
}

private data class ConversationParticipants(
    val friend: BotFriend? = null,
    val group: BotGroup? = null
)

private suspend fun resolveConversationParticipants(
    bot: Bot,
    conversation: Conversation?
): ConversationParticipants {
    if (conversation == null) return ConversationParticipants()
    return when (conversation.scene) {
        MessageScene.FRIEND -> {
            val friend = try {
                bot.getFriend(conversation.peerUin, forceUpdate = false)
            } catch (e: Exception) {
                null
            }
            ConversationParticipants(friend = friend)
        }

        MessageScene.GROUP -> {
            val group = try {
                bot.getGroup(conversation.peerUin, forceUpdate = false)
            } catch (e: Exception) {
                null
            }
            ConversationParticipants(group = group)
        }

        else -> ConversationParticipants()
    }
}
