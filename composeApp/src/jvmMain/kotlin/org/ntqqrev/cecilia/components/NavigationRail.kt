package org.ntqqrev.cecilia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class NavigationTab {
    MESSAGES,
    CONTACTS,
    SETTINGS
}

@Composable
fun NavigationRail(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colors.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        NavigationRailItem(
            icon = Icons.AutoMirrored.Filled.Message,
            label = "消息",
            selected = selectedTab == NavigationTab.MESSAGES,
            onClick = { onTabSelected(NavigationTab.MESSAGES) }
        )

        NavigationRailItem(
            icon = Icons.Default.People,
            label = "联系人",
            selected = selectedTab == NavigationTab.CONTACTS,
            onClick = { onTabSelected(NavigationTab.CONTACTS) }
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationRailItem(
            icon = Icons.Default.Settings,
            label = "设置",
            selected = selectedTab == NavigationTab.SETTINGS,
            onClick = { onTabSelected(NavigationTab.SETTINGS) }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NavigationRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colors.onPrimary
            else MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

