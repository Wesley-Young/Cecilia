package org.ntqqrev.cecilia.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*
import org.ntqqrev.acidify.logging.LogLevel
import org.ntqqrev.cecilia.core.LocalConfig
import org.ntqqrev.cecilia.core.LocalConfigSetter
import org.ntqqrev.cecilia.util.isValidUrl
import kotlin.math.roundToInt

private val DefaultScaleRange = 0.85f..1.5f

@Composable
fun SettingsView() {
    val config = LocalConfig.current
    val setConfig = LocalConfigSetter.current
    val logLevels = remember { LogLevel.entries.toList() }

    var signApiUrl by remember(config) { mutableStateOf(config.signApiUrl) }
    var signApiHttpProxy by remember(config) { mutableStateOf(config.signApiHttpProxy) }
    var logLevelIndex by remember(config) {
        mutableStateOf(
            logLevels.indexOf(config.minLogLevel).takeIf { it >= 0 } ?: 0
        )
    }
    var displayScale by remember(config) { mutableStateOf(config.displayScale) }
    var useCtrlEnter by remember(config) { mutableStateOf(config.useCtrlEnterToSend) }

    val trimmedUrl = signApiUrl.trim()
    val trimmedProxy = signApiHttpProxy.trim()
    val urlValid = trimmedUrl.isNotEmpty() && trimmedUrl.isValidUrl()
    val proxyValid = trimmedProxy.isEmpty() || trimmedProxy.isValidUrl()
    val selectedLogLevel = logLevels.getOrElse(logLevelIndex.coerceIn(0, logLevels.lastIndex)) {
        logLevels.first()
    }

    val pendingConfig = config.copy(
        signApiUrl = trimmedUrl,
        signApiHttpProxy = trimmedProxy,
        minLogLevel = selectedLogLevel,
        displayScale = displayScale,
        useCtrlEnterToSend = useCtrlEnter
    )

    val isDirty = pendingConfig != config
    val isFormValid = urlValid && proxyValid
    val canSave = isDirty && isFormValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(
                title = "签名服务",
                description = "配置签名服务地址和可选的 HTTP 代理。"
            ) {
                TextField(
                    value = signApiUrl,
                    onValueChange = { signApiUrl = it },
                    header = { Text("签名服务地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!urlValid) {
                    Text(
                        text = "请输入有效的 http(s) 地址。",
                        style = FluentTheme.typography.caption
                    )
                }
                TextField(
                    value = signApiHttpProxy,
                    onValueChange = { signApiHttpProxy = it },
                    header = { Text("HTTP 代理（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!proxyValid) {
                    Text(
                        text = "请输入有效的 http(s) 代理地址，或留空。",
                        style = FluentTheme.typography.caption
                    )
                }
            }

            SettingsSection(
                title = "日志",
                description = "控制 Acidify 输出的最低日志级别。"
            ) {
                ComboBox(
                    header = "最低日志等级",
                    items = logLevels.map { it.name },
                    selected = logLevelIndex,
                    onSelectionChange = { index, _ ->
                        logLevelIndex = index
                    }
                )
            }

            SettingsSection(
                title = "界面",
                description = "调整界面缩放比例以适配不同 DPI。"
            ) {
                val displayScalePercent = (displayScale * 100).roundToInt()
                Text(
                    text = "显示缩放：$displayScalePercent%",
                    style = FluentTheme.typography.bodyStrong
                )
                Slider(
                    value = displayScale,
                    onValueChange = { newScale ->
                        displayScale = (newScale * 20).roundToInt() / 20f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = DefaultScaleRange,
                    steps = 12,
                    tooltipContent = { state ->
                        val percent = (state.value * 20).roundToInt() * 5
                        Text("$percent%")
                    }
                )
                Text(
                    text = "如果界面元素过大或过小，可以通过滑块微调缩放比例。",
                    style = FluentTheme.typography.caption
                )
            }

            SettingsSection(
                title = "消息",
                description = "设置消息发送时使用的快捷键。"
            ) {
                CheckBox(
                    checked = useCtrlEnter,
                    label = "使用 Ctrl + Enter 发送消息（Enter 直接换行）",
                    onCheckStateChange = { useCtrlEnter = it }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                disabled = !canSave,
                onClick = { setConfig(pendingConfig) }
            ) {
                Text("保存设置")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Layer(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = FluentTheme.typography.subtitle
            )
            if (description != null) {
                Text(
                    text = description,
                    style = FluentTheme.typography.body.copy(
                        color = FluentTheme.colors.text.text.secondary
                    )
                )
            }
            content()
        }
    }
}