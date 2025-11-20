package org.ntqqrev.cecilia.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.composefluent.background.Mica
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import org.ntqqrev.acidify.Bot
import org.ntqqrev.cecilia.core.Config
import org.ntqqrev.cecilia.core.LocalBot

@Composable
fun App(
    config: Config,
    bot: Bot?,
    showConfigInitDialog: () -> Unit
) {
    var isLoggedIn by remember { mutableStateOf(bot?.isLoggedIn ?: false) }

    Mica(Modifier.fillMaxSize()) {
        if (bot == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProgressRing()
                    Text("正在初始化")
                }
            }
        } else {
            CompositionLocalProvider(
                LocalBot provides bot
            ) {
                if (!isLoggedIn) {
                    LoginView(
                        showConfigInitDialog
                    )
                }
            }
        }
    }
}