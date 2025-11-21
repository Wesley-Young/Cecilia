package org.ntqqrev.cecilia.view

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Icon
import io.github.composefluent.component.SideNav
import io.github.composefluent.component.SideNavItem
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Chat
import io.github.composefluent.icons.regular.People
import io.github.composefluent.icons.regular.Settings
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
            expanded = expanded,
            onExpandStateChange = { expanded = it },
            modifier = Modifier.fillMaxHeight(),
            title = {
                Text("$botNickname (${bot.uin})")
            }
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
            Layer(
                modifier = Modifier.fillMaxHeight(),
                elevation = 8.dp
            ) {
                Crossfade(targetState = mainViewState) { state ->
                    when (state) {
                        MainViewState.Chat -> ChatView()
                        MainViewState.Contacts -> {}
                        MainViewState.Settings -> SettingsView()
                    }
                }
            }
        }
    }
}