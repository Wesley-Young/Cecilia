package org.ntqqrev.cecilia.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import org.ntqqrev.cecilia.util.isValidUrl

@Composable
fun ValidatorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
    validation: ValidationResult = ValidationResult.Valid
) {
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            header = header,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = placeholder
        )
        if (!validation.isValid && !validation.message.isNullOrBlank()) {
            Text(
                text = validation.message,
                style = FluentTheme.typography.caption,
                color = FluentTheme.colors.system.critical
            )
        }
    }
}

data class ValidationResult(val isValid: Boolean, val message: String? = null) {
    companion object {
        val Valid = ValidationResult(true, null)
    }
}

fun validateOptionalHttpUrl(text: String): ValidationResult {
    if (text.isEmpty()) return ValidationResult.Valid
    return if (text.isValidUrl()) {
        ValidationResult.Valid
    } else {
        ValidationResult(false, "请输入有效的 http(s) 代理地址，或留空。")
    }
}

fun validateRequiredHttpUrl(text: String): ValidationResult {
    return if (text.isNotEmpty() && text.isValidUrl()) {
        ValidationResult.Valid
    } else {
        ValidationResult(false, "请输入有效的 http(s) 地址。")
    }
}
