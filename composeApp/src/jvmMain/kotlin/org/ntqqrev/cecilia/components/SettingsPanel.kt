package org.ntqqrev.cecilia.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "设置功能开发中...",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

