package org.ntqqrev.cecilia.view

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.filled.Group
import io.github.composefluent.icons.filled.Person
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
            val allFriends = bot.getFriends(true)
            val allGroups = bot.getGroups(true)
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
                        icon = {
                            Icon(
                                imageVector = if (selectedIndex == 0) Icons.Filled.Person else Icons.Regular.Person,
                                contentDescription = null
                            )
                        }
                    )
                }
                item {
                    TopNavItem(
                        selected = selectedIndex == 1,
                        onClick = { selectedIndex = 1 },
                        text = { Text("群聊") },
                        icon = {
                            Icon(
                                imageVector = if (selectedIndex == 1) Icons.Filled.Group else Icons.Regular.Group,
                                contentDescription = null
                            )
                        }
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
        ) {
            when {
                selectedFriend != null -> {
                    FriendInfoView(friend = selectedFriend!!)
                }

                selectedGroup != null -> {
                    GroupInfoView(group = selectedGroup!!)
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "选择一个联系人查看详情",
                            style = FluentTheme.typography.bodyLarge,
                            color = FluentTheme.colors.text.text.secondary
                        )
                    }
                }
            }
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

@Composable
private fun FriendInfoView(friend: BotFriend) {
    Layer(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                AvatarImage(
                    uin = friend.uin,
                    size = 160.dp,
                    isGroup = false,
                    quality = 640
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = friend.remark.ifEmpty { friend.nickname },
                style = FluentTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            if (friend.remark.isNotEmpty()) {
                Text(
                    text = "备注：${friend.nickname}",
                    style = FluentTheme.typography.body,
                    color = FluentTheme.colors.text.text.secondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow("QQ 号", friend.uin.toString())
                if (friend.qid.isNotEmpty()) {
                    InfoRow("QID", friend.qid)
                }
                InfoRow("分组", friend.categoryName)
                val genderText = when (friend.gender.name) {
                    "MALE" -> "男"
                    "FEMALE" -> "女"
                    else -> "未知"
                }
                if (friend.age > 0) {
                    InfoRow("年龄", friend.age.toString())
                }
                InfoRow("性别", genderText)

                if (friend.bio.isNotEmpty()) {
                    Box(
                        Modifier.height(1.dp)
                            .fillMaxWidth()
                            .background(FluentTheme.colors.stroke.divider.default)
                    )
                    Column {
                        Text(
                            text = "个性签名",
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = friend.bio,
                            style = FluentTheme.typography.body,
                            color = FluentTheme.colors.text.text.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoView(group: BotGroup) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TODO：${group.name}",
            style = FluentTheme.typography.body,
            color = FluentTheme.colors.text.text.secondary
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = FluentTheme.typography.caption,
            color = FluentTheme.colors.text.text.secondary
        )
        Text(
            text = value,
            style = FluentTheme.typography.body,
            fontWeight = FontWeight.Medium
        )
    }
}