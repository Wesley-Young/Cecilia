package org.ntqqrev.cecilia.components

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
    onConfirm: (String) -> Unit
) {
    var signApiUrl by remember { mutableStateOf(initialSignApiUrl) }
    val trimmedUrl = signApiUrl.trim()
    val isValid = trimmedUrl.isNotEmpty() && trimmedUrl.isValidUrl()

    AlertDialog(
        onDismissRequest = {}, // 禁止在首次启动时关闭对话框
        title = { Text("配置签名服务") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "首次启动需要提供 signApiUrl，完成后将写入 config.json。",
                    style = MaterialTheme.typography.body2
                )
                OutlinedTextField(
                    value = signApiUrl,
                    onValueChange = { signApiUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("签名 API 地址") },
                    singleLine = true,
                    isError = !isValid
                )
                if (!isValid) {
                    Text(
                        text = "请输入有效的 http(s) 地址。",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmedUrl) },
                enabled = isValid
            ) {
                Text("保存")
            }
        }
    )
}

private fun String.isValidUrl(): Boolean {
    return try {
        if (isEmpty()) return false
        if (!startsWith("http://") && !startsWith("https://")) return false
        val urlPattern = Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")
        urlPattern.matches(this)
    } catch (e: Exception) {
        false
    }
}
