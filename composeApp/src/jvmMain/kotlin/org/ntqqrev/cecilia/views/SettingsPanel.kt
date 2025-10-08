package org.ntqqrev.cecilia.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ntqqrev.acidify.util.log.LogLevel
import org.ntqqrev.cecilia.components.DropdownTextField
import org.ntqqrev.cecilia.components.LabeledToggle
import org.ntqqrev.cecilia.components.ValidatedTextField
import org.ntqqrev.cecilia.utils.LocalConfig
import org.ntqqrev.cecilia.utils.LocalSetConfig

data class SettingsActions(
    val save: () -> Unit = {},
    val discard: () -> Unit = {}
)

@Composable
fun SettingsPanel(
    onUnsavedChangesUpdate: (hasChanges: Boolean, actions: SettingsActions) -> Unit = { _, _ -> }
) {
    val config = LocalConfig.current
    val setConfig = LocalSetConfig.current

    var signApiUrl by remember { mutableStateOf(config.signApiUrl) }
    var signApiHttpProxy by remember { mutableStateOf(config.signApiHttpProxy) }
    var minLogLevel by remember { mutableStateOf(config.minLogLevel) }
    var displayScale by remember { mutableStateOf(config.displayScale.toString()) }
    var useCtrlEnterToSend by remember { mutableStateOf(config.useCtrlEnterToSend) }

    // 校验状态
    val isSignApiUrlValid = signApiUrl.isNotBlank() && isValidUrl(signApiUrl)
    val isSignApiHttpProxyValid = signApiHttpProxy.isBlank() || isValidUrl(signApiHttpProxy)
    val displayScaleFloat = displayScale.toFloatOrNull()
    val isDisplayScaleValid = displayScaleFloat != null && displayScaleFloat in 0.5f..3.0f

    val isFormValid = isSignApiUrlValid && isSignApiHttpProxyValid && isDisplayScaleValid

    // 检测是否有未保存的更改
    val hasUnsavedChanges = signApiUrl != config.signApiUrl ||
            signApiHttpProxy != config.signApiHttpProxy ||
            minLogLevel != config.minLogLevel ||
            displayScaleFloat != config.displayScale ||
            useCtrlEnterToSend != config.useCtrlEnterToSend

    // 保存配置的函数
    val saveConfig = {
        if (isFormValid) {
            setConfig(
                config.copy(
                    signApiUrl = signApiUrl,
                    signApiHttpProxy = signApiHttpProxy,
                    minLogLevel = minLogLevel,
                    displayScale = displayScaleFloat,
                    useCtrlEnterToSend = useCtrlEnterToSend
                )
            )
        }
    }

    // 丢弃更改的函数
    val discardChanges = {
        signApiUrl = config.signApiUrl
        signApiHttpProxy = config.signApiHttpProxy
        minLogLevel = config.minLogLevel
        displayScale = config.displayScale.toString()
        useCtrlEnterToSend = config.useCtrlEnterToSend
    }

    // 通知父组件是否有未保存的更改以及操作
    LaunchedEffect(hasUnsavedChanges, isFormValid) {
        onUnsavedChangesUpdate(
            hasUnsavedChanges && isFormValid,
            SettingsActions(
                save = saveConfig,
                discard = discardChanges
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.4f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth(),
                elevation = 4.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.h5,
                        color = MaterialTheme.colors.onSurface
                    )

                    Divider()

                    // 签名API地址
                    SettingSection(title = "签名服务") {
                        ValidatedTextField(
                            value = signApiUrl,
                            onValueChange = { signApiUrl = it },
                            label = "签名API地址",
                            isValid = isSignApiUrlValid,
                            errorMessage = "请输入有效的URL地址",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ValidatedTextField(
                            value = signApiHttpProxy,
                            onValueChange = { signApiHttpProxy = it },
                            label = "HTTP代理（可选）",
                            placeholder = "例如: http://127.0.0.1:7890",
                            isValid = isSignApiHttpProxyValid,
                            errorMessage = "请输入有效的代理URL地址",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider()

                    // 日志级别
                    SettingSection(title = "日志设置") {
                        DropdownTextField(
                            value = minLogLevel,
                            onValueChange = { minLogLevel = it },
                            options = LogLevel.entries,
                            label = "最小日志级别",
                            helperText = "设置最小显示的日志级别。级别从低到高: VERBOSE < DEBUG < INFO < WARN < ERROR",
                            displayText = { it.name },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider()

                    // 显示缩放
                    SettingSection(title = "界面设置") {
                        ValidatedTextField(
                            value = displayScale,
                            onValueChange = { displayScale = it },
                            label = "显示缩放比例",
                            isValid = isDisplayScaleValid,
                            errorMessage = "请输入有效的数字（范围：0.5 到 3.0）",
                            helperText = "修改界面缩放比例（例如：1.0, 1.25, 1.5）",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider()

                    // 消息发送设置
                    SettingSection(title = "消息设置") {
                        LabeledToggle(
                            checked = useCtrlEnterToSend,
                            onCheckedChange = { useCtrlEnterToSend = it },
                            label = "发送消息快捷键",
                            checkedText = "使用 ⌘/Ctrl + Enter 发送消息，Enter 换行",
                            uncheckedText = "使用 Enter 发送消息，⌘/Ctrl + Enter 换行",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Divider()

                    // 保存按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = saveConfig,
                            enabled = isFormValid && hasUnsavedChanges,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                                disabledBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                            )
                        ) {
                            Text("保存设置")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "注意：某些设置需要重启应用后才能生效。",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        content()
    }
}

private fun isValidUrl(url: String): Boolean {
    return try {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return false

        // 检查是否以 http:// 或 https:// 开头
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return false
        }

        // 简单的URL格式检查
        val urlPattern = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")
        urlPattern.matches(trimmedUrl)
    } catch (e: Exception) {
        false
    }
}
