package org.ntqqrev.cecilia.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Icon
import io.github.composefluent.component.ListItem
import io.github.composefluent.component.Text
import io.github.composefluent.component.TopNav
import io.github.composefluent.component.TopNavItem
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Group
import io.github.composefluent.icons.regular.Person
import org.ntqqrev.acidify.entity.BotFriend
import org.ntqqrev.acidify.entity.BotGroup
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.component.DraggableDivider
import org.ntqqrev.cecilia.core.LocalBot

@OptIn(ExperimentalFluentApi::class)
@Composable
fun ContactsView() {
    val bot = LocalBot.current
    var leftPanelWidth by remember { mutableStateOf(260.dp) }
    var selectedIndex by remember { mutableStateOf(0) }
    var friends by remember { mutableStateOf<List<BotFriend>>(emptyList()) }
    var groups by remember { mutableStateOf<List<BotGroup>>(emptyList()) }
    var selectedFriend by remember { mutableStateOf<BotFriend?>(null) }
    var selectedGroup by remember { mutableStateOf<BotGroup?>(null) }

    LaunchedEffect(bot) {
        runCatching {
            val allFriends = bot.getFriends()
            val allGroups = bot.getGroups()
            friends = allFriends.sortedBy { friend ->
                friend.remark.ifEmpty { friend.nickname }
            }
            groups = allGroups.sortedBy { group -> group.name }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(leftPanelWidth)) {
            TopNav(
                expanded = false,
                onExpandedChanged = {},
            ) {
                item {
                    TopNavItem(
                        selected = selectedIndex == 0,
                        onClick = { selectedIndex = 0 },
                        text = { Text("好友") },
                        icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) }
                    )
                }
                item {
                    TopNavItem(
                        selected = selectedIndex == 1,
                        onClick = { selectedIndex = 1 },
                        text = { Text("群聊") },
                        icon = { Icon(imageVector = Icons.Default.Group, contentDescription = null) }
                    )
                }
            }

            when (selectedIndex) {
                0 -> ContactList(
                    items = friends,
                    selected = selectedFriend,
                    onSelected = {
                        selectedFriend = it
                        selectedGroup = null
                    }
                )

                1 -> ContactList(
                    items = groups,
                    selected = selectedGroup,
                    onSelected = {
                        selectedGroup = it
                        selectedFriend = null
                    }
                )
            }
        }

        DraggableDivider(
            currentWidth = leftPanelWidth,
            onWidthChange = { leftPanelWidth = it },
            showDivider = false,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // TODO: contact info view
        }
    }
}

@Composable
private inline fun <reified T> ContactList(
    items: List<T>,
    selected: T?,
    noinline onSelected: (T) -> Unit,
) {
    ContactList(
        items = items,
        selected = selected,
        isGroup = T::class == BotGroup::class,
        onSelected = onSelected,
    )
}

@Composable
private fun <T> ContactList(
    items: List<T>,
    selected: T?,
    isGroup: Boolean,
    onSelected: (T) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxHeight()
            .padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    val displayName = when (item) {
                        is BotFriend -> item.remark.ifEmpty { item.nickname }
                        is BotGroup -> item.name
                        else -> ""
                    }
                    val uin = when (item) {
                        is BotFriend -> item.uin
                        is BotGroup -> item.uin
                        else -> 0L
                    }

                    ListItem(
                        selected = selected == item,
                        onSelectedChanged = { onSelected(item) },
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarImage(
                                    uin = uin,
                                    size = 32.dp,
                                    isGroup = isGroup,
                                    quality = 100
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = displayName,
                                    style = FluentTheme.typography.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState)
            )
        }
    }
}