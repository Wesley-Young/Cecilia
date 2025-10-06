package org.ntqqrev.cecilia.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.ntqqrev.acidify.struct.BotFriendData
import org.ntqqrev.acidify.struct.BotGroupData
import org.ntqqrev.cecilia.LocalBot
import java.awt.Cursor

enum class ContactType {
    FRIENDS,
    GROUPS
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContactsPanel() {
    val bot = LocalBot.current
    var contactType by remember { mutableStateOf(ContactType.FRIENDS) }
    var friends by remember { mutableStateOf<List<BotFriendData>>(emptyList()) }
    var groups by remember { mutableStateOf<List<BotGroupData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFriend by remember { mutableStateOf<BotFriendData?>(null) }
    var selectedGroup by remember { mutableStateOf<BotGroupData?>(null) }
    var leftPanelWidth by remember { mutableStateOf(320.dp) }
    val scope = rememberCoroutineScope()

    // 加载好友和群列表
    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            try {
                friends = bot.fetchFriends()
                groups = bot.fetchGroups()
            } catch (e: Exception) {
                // 加载失败
            } finally {
                isLoading = false
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：好友/群列表
        Box(modifier = Modifier.width(leftPanelWidth)) {
            ContactListPanel(
                contactType = contactType,
                friends = friends,
                groups = groups,
                isLoading = isLoading,
                selectedFriend = selectedFriend,
                selectedGroup = selectedGroup,
                onContactTypeChange = {
                contactType = it
                    selectedFriend = null
                    selectedGroup = null
                },
                onFriendClick = {
                    selectedFriend = it
                    selectedGroup = null
                },
                onGroupClick = {
                    selectedGroup = it
                    selectedFriend = null
                },
                width = leftPanelWidth
            )
        }

        // 可拖拽的分界线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    var dragStartWidth = 0.dp
                    var totalDrag = 0f
                    detectDragGestures(
                        onDragStart = {
                            dragStartWidth = leftPanelWidth
                            totalDrag = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount.x
                            val newWidth = (dragStartWidth + totalDrag.toDp()).coerceIn(200.dp, 600.dp)
                            leftPanelWidth = newWidth
                        }
                    )
                }
                .background(MaterialTheme.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            )
        }

        // 右侧：详细信息
        when {
            selectedFriend != null -> FriendDetailPanel(selectedFriend!!)
            selectedGroup != null -> GroupDetailPanel(selectedGroup!!)
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "选择一个联系人查看详情",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactListPanel(
    contactType: ContactType,
    friends: List<BotFriendData>,
    groups: List<BotGroupData>,
    isLoading: Boolean,
    selectedFriend: BotFriendData?,
    selectedGroup: BotGroupData?,
    onContactTypeChange: (ContactType) -> Unit,
    onFriendClick: (BotFriendData) -> Unit,
    onGroupClick: (BotGroupData) -> Unit,
    width: Dp
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colors.surface)
    ) {
        // 顶部切换栏
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onContactTypeChange(ContactType.FRIENDS) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (contactType == ContactType.FRIENDS)
                            MaterialTheme.colors.primary
                        else MaterialTheme.colors.surface,
                        contentColor = if (contactType == ContactType.FRIENDS)
                            MaterialTheme.colors.onPrimary
                        else MaterialTheme.colors.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("好友")
                }

                Button(
                    onClick = { onContactTypeChange(ContactType.GROUPS) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (contactType == ContactType.GROUPS)
                            MaterialTheme.colors.primary
                        else MaterialTheme.colors.surface,
                        contentColor = if (contactType == ContactType.GROUPS)
                            MaterialTheme.colors.onPrimary
                        else MaterialTheme.colors.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("群聊")
                }
            }
        }

        Divider()

        // 列表内容
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (contactType) {
                ContactType.FRIENDS -> FriendsList(friends, selectedFriend, onFriendClick)
                ContactType.GROUPS -> GroupsList(groups, selectedGroup, onGroupClick)
            }
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<BotFriendData>,
    selectedFriend: BotFriendData?,
    onFriendClick: (BotFriendData) -> Unit
) {
    // 按分组归类并排序
    val friendsByCategory = remember(friends) {
        friends
            .groupBy { it.categoryId to (it.categoryName.ifEmpty { "未分组" }) }
            .toSortedMap(compareBy { it.first })  // 按 categoryId 排序
            .mapValues { (_, friendList) ->
                friendList.sortedBy { it.uin }  // 分组内按 uin 排序
            }
    }

    // 记录每个分组的展开状态，默认只展开 ID=0 的分组
    val expandedCategories = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            friendsByCategory.keys.forEach { (categoryId, _) ->
                put(categoryId, categoryId == 0)  // 只有 ID=0 的分组默认展开
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        friendsByCategory.forEach { (categoryInfo, categoryFriends) ->
            val categoryId = categoryInfo.first
            val categoryName = categoryInfo.second
            val isExpanded = expandedCategories[categoryId] ?: true

            // 分组标题
            item(key = "category_$categoryId") {
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 0f else -90f
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            expandedCategories[categoryId] = !isExpanded 
                        },
                    color = MaterialTheme.colors.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$categoryName (${categoryFriends.size})",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotation),
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // 分组中的好友（只有展开时才显示，带动画）
            if (isExpanded) {
                items(categoryFriends, key = { it.uin }) { friend ->
                    FriendItem(
                        friend = friend,
                        isSelected = selectedFriend?.uin == friend.uin,
                        onClick = { onFriendClick(friend) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendItem(
    friend: BotFriendData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bot = LocalBot.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f)
        else MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                uin = friend.uin,
                size = 48.dp,
                isGroup = false
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.remark.ifEmpty { friend.nickname },
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend.remark.isNotEmpty() && friend.nickname.isNotEmpty()) {
                    Text(
                        text = friend.nickname,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsList(
    groups: List<BotGroupData>,
    selectedGroup: BotGroupData?,
    onGroupClick: (BotGroupData) -> Unit
) {
    // 按 uin 升序排序
    val sortedGroups = remember(groups) {
        groups.sortedBy { it.uin }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sortedGroups, key = { it.uin }) { group ->
            GroupItem(
                group = group,
                isSelected = selectedGroup?.uin == group.uin,
                onClick = { onGroupClick(group) }
            )
        }
    }
}

@Composable
private fun GroupItem(
    group: BotGroupData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bot = LocalBot.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f)
        else MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                uin = group.uin,
                size = 48.dp,
                isGroup = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.memberCount} 人",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun FriendDetailPanel(friend: BotFriendData) {
    val bot = LocalBot.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarImage(
            uin = friend.uin,
            size = 160.dp,
            isGroup = false,
            quality = 640  // 使用高清头像
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = friend.remark.ifEmpty { friend.nickname },
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        if (friend.remark.isNotEmpty()) {
            Text(
                text = "昵称: ${friend.nickname}",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow("QQ号", friend.uin.toString())
            InfoRow("UID", friend.uid)
            if (friend.qid.isNotEmpty()) {
                InfoRow("QID", friend.qid)
            }
            InfoRow("分组", friend.categoryName)

            // 性别和年龄
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
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Column {
                    Text(
                        text = "个性签名",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = friend.bio,
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDetailPanel(group: BotGroupData) {
    val bot = LocalBot.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarImage(
            uin = group.uin,
            size = 160.dp,
            isGroup = true,
            quality = 640  // 使用高清头像
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = group.name,
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow("群号", group.uin.toString())
            InfoRow("成员数", "${group.memberCount} / ${group.capacity}")

            val percentage = if (group.capacity > 0) {
                ((group.memberCount.toFloat() / group.capacity) * 100).toInt()
            } else 0

            // 成员占用率进度条
            Column {
                Text(
                    text = "成员占用率",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = percentage / 100f,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.primary
                    )
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
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
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Medium
        )
    }
}
