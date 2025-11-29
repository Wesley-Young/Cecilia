package org.ntqqrev.cecilia.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text

@Composable
fun ConfigInitDialog(
    visible: Boolean,
    initialSignApiUrl: String = "",
    initialSignApiHttpProxy: String = "",
    onConfirm: (String, String) -> Unit,
    onDismissRequest: () -> Unit,
    isRefining: Boolean = false
) {
    var signApiUrl by remember { mutableStateOf(initialSignApiUrl) }
    var signApiHttpProxy by remember { mutableStateOf(initialSignApiHttpProxy) }

    val trimmedUrl = signApiUrl.trim()
    val trimmedProxy = signApiHttpProxy.trim()

    val urlValidation = remember(trimmedUrl) { validateRequiredHttpUrl(trimmedUrl) }
    val proxyValidation = remember(trimmedProxy) { validateOptionalHttpUrl(trimmedProxy) }
    val isFormValid = urlValidation.isValid && proxyValidation.isValid

    ContentDialog(
        title = "配置签名服务",
        visible = visible,
        primaryButtonText = "保存",
        closeButtonText = if (isRefining) "取消" else null,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isRefining) {
                    Text(
                        text = "修改签名服务或代理需要重启应用后才会生效，点击保存后应用将会自动退出。"
                    )
                } else {
                    Text(
                        text = "首次启动需要提供 signApiUrl，完成后将写入 config.json。"
                    )
                }
                ValidatorTextField(
                    value = signApiUrl,
                    onValueChange = { signApiUrl = it },
                    header = { Text("签名服务地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    validation = urlValidation
                )
                ValidatorTextField(
                    value = signApiHttpProxy,
                    onValueChange = { signApiHttpProxy = it },
                    modifier = Modifier.fillMaxWidth(),
                    header = { Text("HTTP 代理（可选）") },
                    singleLine = true,
                    validation = proxyValidation
                )
            }
        },
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> {
                    if (isFormValid) {
                        onConfirm(trimmedUrl, trimmedProxy)
                    }
                }

                ContentDialogButton.Close -> onDismissRequest()
                else -> {} // Unexpected
            }
        }
    )
}
