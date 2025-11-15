package org.ntqqrev.cecilia.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import org.ntqqrev.cecilia.commands.Command
import org.ntqqrev.cecilia.commands.CommandParameter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommandPalette(
    commands: List<Command>,
    onDismiss: () -> Unit,
    onCommandError: (String) -> Unit = {}
) {
    val sortedCommands = remember(commands) { commands.sortedBy { it.id } }
    var manualInput by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

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

    val parameterSuggestionState = remember(selectedCommand, consoleState) {
        if (selectedCommand != null && consoleState.isArgumentPhase) {
            val parameter = selectedCommand.parameters.getOrNull(consoleState.currentArgumentIndex)
            val suggestions =
                parameter?.suggestionsProvider?.invoke(consoleState.currentArgumentValue)?.take(8).orEmpty()
            ParameterSuggestionState(parameter, suggestions)
        } else {
            ParameterSuggestionState(null, emptyList())
        }
    }

    val isExactCommandMatch = selectedCommand?.id?.equals(consoleState.commandToken, ignoreCase = true) == true
    val parameterSuggestionsActive =
        selectedCommand != null &&
                consoleState.isArgumentPhase &&
                parameterSuggestionState.parameter != null &&
                parameterSuggestionState.suggestions.isNotEmpty()
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
                    SuggestionItem(
                        primary = suggestion,
                        secondary = "",
                        applyText = fillArgumentValue(selectedCommand, consoleState, suggestion)
                    )
                }
            }

            commandSuggestionsActive -> {
                commandMatches.map { command ->
                    SuggestionItem(
                        primary = "/${command.id}",
                        secondary = command.description,
                        applyText = buildCommandInput(
                            command,
                            emptyList()
                        )
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
        if (selectedCommand != null) {
            executeCommand(selectedCommand, consoleState.arguments)
        }
        validateCommand(selectedCommand, consoleState)?.let(onCommandError)
        onDismiss()
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
                                val display = if (isFilled) value else "<${parameter.name}>"
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
            LazyColumn {
                itemsIndexed(suggestions) { index, suggestion ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == highlightedIndex)
                                    MaterialTheme.colors.primary.copy(alpha = 0.12f)
                                else
                                    Color.Transparent
                            )
                            .clickable { onSuggestionClicked(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(text = suggestion.primary, style = MaterialTheme.typography.subtitle1)
                        if (suggestion.secondary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = suggestion.secondary,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

private data class ParameterSuggestionState(
    val parameter: CommandParameter?,
    val suggestions: List<String>
)

private data class SuggestionItem(
    val primary: String,
    val secondary: String,
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

private fun executeCommand(command: Command, args: List<String>) {
    val payload = mutableMapOf<String, String>()
    command.parameters.forEachIndexed { index, parameter ->
        payload[parameter.name] = args.getOrNull(index).orEmpty()
    }
    command.execute(payload)
}
