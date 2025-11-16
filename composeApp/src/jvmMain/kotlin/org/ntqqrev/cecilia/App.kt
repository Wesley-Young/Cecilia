package org.ntqqrev.cecilia

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ntqqrev.acidify.Bot
import org.ntqqrev.acidify.event.BotOfflineEvent
import org.ntqqrev.cecilia.component.NavigationRail
import org.ntqqrev.cecilia.component.NavigationTab
import org.ntqqrev.cecilia.component.NotoFontFamily
import org.ntqqrev.cecilia.component.panel.*
import org.ntqqrev.cecilia.component.view.CommandPalette
import org.ntqqrev.cecilia.component.view.UnsavedChangesDialog
import org.ntqqrev.cecilia.core.*
import org.ntqqrev.cecilia.core.command.*

@Composable
@Preview
fun App(
    config: CeciliaConfig,
    setConfig: (CeciliaConfig) -> Unit,
    bot: Bot?,
    httpClient: HttpClient,
    conversationManager: ConversationManager? = null,
    loadingError: String? = null,
    onLoginStateChange: ((Boolean, Long) -> Unit)? = null,
    showCommandPalette: Boolean = false,
    onCommandPaletteDismiss: () -> Unit = {}
) {
    MaterialTheme(
        colors = config.theme.colorScheme,
        typography = Typography(
            defaultFontFamily = if (
                System.getProperty("os.name").lowercase().contains("mac") &&
                !config.macUseNotoSansSC
            )
                FontFamily.Default
            else
                NotoFontFamily
        )
    ) {
        val commands = remember {
            listOf(
                RefreshCommand,
                NudgeCommand,
                CardCommand,
                AdminCommand,
                TitleCommand,
                KickCommand,
                MuteCommand,
                MuteAllCommand
            )
        }
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            var offlineReason by remember { mutableStateOf<String?>(null) }
            when {
                loadingError != null -> {
                    // 显示错误界面
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.error
                            )
                            Text(
                                text = loadingError,
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                bot == null -> {
                    // 显示加载界面
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colors.primary
                            )
                            Text(
                                text = "正在初始化...",
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                else -> {
                    // Bot 加载完成
                    var isLoggedIn by remember { mutableStateOf(bot.isLoggedIn) }
                    val contactsState = remember(bot) { ContactsState(bot) }

                    LaunchedEffect(bot) {
                        bot.eventFlow.collect { event ->
                            if (event is BotOfflineEvent) {
                                offlineReason = event.reason
                                isLoggedIn = false
                            }
                        }
                    }

                    // 通知登录状态变化
                    LaunchedEffect(isLoggedIn) {
                        onLoginStateChange?.invoke(isLoggedIn, bot.sessionStore.uin)
                    }

                    LaunchedEffect(isLoggedIn) {
                        if (!isLoggedIn) {
                            contactsState.clear()
                        }
                    }

                    // 提供 Bot 和 ConversationManager 到子组件
                    if (conversationManager != null) {
                        CompositionLocalProvider(
                            LocalConfig provides config,
                            LocalSetConfig provides setConfig,
                            LocalBot provides bot,
                            LocalHttpClient provides httpClient,
                            LocalConversationManager provides conversationManager,
                            LocalContactsState provides contactsState
                        ) {
                            if (isLoggedIn) {
                                // 已登录，显示主界面
                                MainContent()
                            } else {
                                // 未登录，显示登录界面
                                LoginPanel(
                                    onLoginSuccess = { isLoggedIn = true },
                                    onLoginStateChange = onLoginStateChange
                                )
                            }
                        }
                    } else {
                        // ConversationManager 还未初始化，显示加载界面
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    if (showCommandPalette && conversationManager != null) {
                        CommandPalette(
                            bot = bot,
                            httpClient = httpClient,
                            commands = commands,
                            conversationManager = conversationManager,
                            contactsState = contactsState,
                            onDismiss = onCommandPaletteDismiss,
                            onCommandError = { message ->
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )

            offlineReason?.let { reason ->
                AlertDialog(
                    onDismissRequest = { offlineReason = null },
                    title = {
                        Text("已下线")
                    },
                    text = {
                        Text(reason.ifBlank { "Bot 已离线" })
                    },
                    confirmButton = {
                        TextButton(onClick = { offlineReason = null }) {
                            Text("确定")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainContent() {
    var selectedTab by remember { mutableStateOf(NavigationTab.MESSAGES) }
    var targetConversationId by remember { mutableStateOf<String?>(null) }
    val contactsState = LocalContactsState.current

    LaunchedEffect(contactsState) {
        contactsState.ensureInitialized()
    }

    // 设置页面的状态
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var settingsActions by remember { mutableStateOf(SettingsActions()) }

    // 对话框状态
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var pendingTab by remember { mutableStateOf<NavigationTab?>(null) }

    // 显示未保存更改对话框
    if (showUnsavedChangesDialog && pendingTab != null) {
        UnsavedChangesDialog(
            onSave = {
                settingsActions.save()
                selectedTab = pendingTab!!
                showUnsavedChangesDialog = false
                pendingTab = null
            },
            onDiscard = {
                settingsActions.discard()
                selectedTab = pendingTab!!
                showUnsavedChangesDialog = false
                pendingTab = null
            },
            onCancel = {
                showUnsavedChangesDialog = false
                pendingTab = null
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 最左侧：导航栏
        NavigationRail(
            selectedTab = selectedTab,
            onTabSelected = { newTab ->
                // 如果当前在设置页面且有未保存的更改，显示对话框
                if (selectedTab == NavigationTab.SETTINGS && hasUnsavedChanges && newTab != selectedTab) {
                    pendingTab = newTab
                    showUnsavedChangesDialog = true
                } else {
                    selectedTab = newTab
                }
            }
        )

        // 分界线
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )

        // 中间和右侧：根据选择的标签显示不同内容
        when (selectedTab) {
            NavigationTab.MESSAGES -> MessagesPanel(
                initialSelectedConversationId = targetConversationId,
                onConversationSelected = { targetConversationId = null }
            )

            NavigationTab.CONTACTS -> ContactsPanel(
                onOpenConversation = { conversationId ->
                    targetConversationId = conversationId
                    selectedTab = NavigationTab.MESSAGES
                }
            )

            NavigationTab.SETTINGS -> SettingsPanel(
                onUnsavedChangesUpdate = { hasChanges, actions ->
                    hasUnsavedChanges = hasChanges
                    settingsActions = actions
                }
            )
        }
    }
}
