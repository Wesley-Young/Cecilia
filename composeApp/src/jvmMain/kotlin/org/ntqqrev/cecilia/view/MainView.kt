package org.ntqqrev.cecilia.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.*
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Chat
import io.github.composefluent.icons.regular.People
import io.github.composefluent.icons.regular.Settings
import org.ntqqrev.cecilia.component.AvatarImage
import org.ntqqrev.cecilia.core.LocalBot

enum class MainViewState(
    val displayName: String,
    val icon: ImageVector
) {
    Chat(
        "聊天",
        Icons.Default.Chat
    ),
    Contacts(
        "联系人",
        Icons.Default.People
    ),
    Settings(
        "设置",
        Icons.Default.Settings
    ),
}

@OptIn(ExperimentalFluentApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainView() {
    val bot = LocalBot.current
    var mainViewState by remember { mutableStateOf(MainViewState.Chat) }
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    var botNickname by remember { mutableStateOf("") }

    LaunchedEffect(bot) {
        val profile = bot.fetchUserInfoByUin(bot.uin)
        botNickname = profile.nickname.ifEmpty { bot.uin.toString() }
    }

    Row(Modifier.fillMaxWidth()) {
        SideNav(
            indicatorState = rememberIndicatorState(),
            expanded = expanded,
            onExpandStateChange = { expanded = it },
            modifier = Modifier.fillMaxHeight()
                .padding(top = 6.dp),
            autoSuggestionBox = null,
            header = {
                SideNavHeaderArea(
                    title = { Text("$botNickname (${bot.uin})") },
                    backButton = {},
                    expandButton = {
                        NavigationDefaults.ExpandedButton(
                            onClick = { expanded = !expanded },
                            icon = {
                                AvatarImage(
                                    uin = bot.uin,
                                    size = 32.dp,
                                )
                            }
                        )
                    }
                )
            },
        ) {
            MainViewState.entries.forEachIndexed { idx, state ->
                SideNavItem(
                    selected = selectedIndex == idx,
                    onClick = {
                        selectedIndex = idx
                        mainViewState = state
                    },
                    icon = {
                        Icon(imageVector = state.icon, contentDescription = null)
                    }
                ) {
                    Text(state.displayName)
                }
            }
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxHeight()
        ) {
            Box(
                if (mainViewState == MainViewState.Chat) Modifier
                else Modifier.size(0.dp)
            ) {
                ChatView()
            }
            Box(
                if (mainViewState == MainViewState.Contacts) Modifier
                else Modifier.size(0.dp)
            ) {
                ContactsView()
            }
            Box(
                if (mainViewState == MainViewState.Settings) Modifier
                else Modifier.size(0.dp)
            ) {
                SettingsView()
            }
        }
    }
}