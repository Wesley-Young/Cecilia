package org.ntqqrev.cecilia.component.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SignApiSetupDialog(
    initialSignApiUrl: String,
    initialSignApiHttpProxy: String,
    onConfirm: (String, String) -> Unit,
    onDismissRequest: (() -> Unit)? = null,
    showRestartReminder: Boolean = false
) {
    var signApiUrl by remember { mutableStateOf(initialSignApiUrl) }
    var signApiHttpProxy by remember { mutableStateOf(initialSignApiHttpProxy) }
    val trimmedUrl = signApiUrl.trim()
    val trimmedProxy = signApiHttpProxy.trim()
    val isUrlValid = trimmedUrl.isNotEmpty() && trimmedUrl.isValidUrl()
    val isProxyValid = trimmedProxy.isEmpty() || trimmedProxy.isValidUrl()
    val isFormValid = isUrlValid && isProxyValid

    AlertDialog(
        onDismissRequest = {
            onDismissRequest?.invoke()
        }, // 未提供回调时保持首次启动行为
        title = { Text("配置签名服务") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showRestartReminder) {
                    Text(
                        text = "修改签名服务或代理需要重启应用后才会生效，点击保存后应用将会自动退出。",
                        style = MaterialTheme.typography.body2
                    )
                } else {
                    Text(
                        text = "首次启动需要提供 signApiUrl，完成后将写入 config.json。",
                        style = MaterialTheme.typography.body2
                    )
                }
                OutlinedTextField(
                    value = signApiUrl,
                    onValueChange = { signApiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("签名 API 地址") },
                    singleLine = true,
                    isError = !isUrlValid
                )
                if (!isUrlValid) {
                    Text(
                        text = "请输入有效的 http(s) 地址。",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
                OutlinedTextField(
                    value = signApiHttpProxy,
                    onValueChange = { signApiHttpProxy = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTP 代理（可选）") },
                    placeholder = { Text("例如 http://127.0.0.1:7890") },
                    singleLine = true,
                    isError = !isProxyValid
                )
                if (!isProxyValid) {
                    Text(
                        text = "请输入有效的 http(s) 代理地址，或留空。",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmedUrl, trimmedProxy) },
                enabled = isFormValid
            ) {
                Text("保存")
            }
        },
        dismissButton = onDismissRequest?.let { dismiss ->
            {
                TextButton(onClick = dismiss) { Text("取消") }
            }
        }
    )
}

private fun String.isValidUrl(): Boolean =
    runCatching {
        if (isEmpty()) return@runCatching false
        if (!startsWith("http://") && !startsWith("https://")) return@runCatching false
        val urlPattern = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")
        urlPattern.matches(this)
    }.getOrElse { false }
