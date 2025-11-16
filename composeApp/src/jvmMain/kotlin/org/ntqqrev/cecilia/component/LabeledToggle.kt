package org.ntqqrev.cecilia.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 可复用的标签切换开关组件，支持在不同状态下显示不同文本
 *
 * @param checked 当前开关状态
 * @param onCheckedChange 状态变化回调
 * @param label 左侧标签文本
 * @param checkedText 开关打开时显示的文本
 * @param uncheckedText 开关关闭时显示的文本
 * @param helperText 可选的辅助说明文本
 * @param modifier 修饰符
 */
@Composable
fun LabeledToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    checkedText: String,
    uncheckedText: String,
    helperText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = if (checked) checkedText else uncheckedText,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }

        if (helperText != null) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
