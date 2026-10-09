package com.tokenheat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokenheat.data.QLogin
import com.tokenheat.mcp.McpServerConfig
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.Wire

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp(
    state: HubState,
    onStartBridge: (Int) -> Unit,
    onStopBridge: () -> Unit,
    onLogin: (Wire.Region) -> Unit,
    onLogout: () -> Unit,
    onClearCalls: () -> Unit,
    onRefreshCalls: () -> Unit,
    onSwitchRealm: (Wire.Region) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSwitchProvider: (Provider) -> Unit,
    onLoginZcode: () -> Unit,
    onSwitchZcodeAccount: (String) -> Unit,
    onDeleteZcodeAccount: (String) -> Unit,
    onToggleAccount: (SavedAccount) -> Unit,
    onSwitchZenAccount: (String) -> Unit,
    onDeleteZenAccount: (String) -> Unit,
    onShowZenKeyDialog: () -> Unit,
    onDismissZenKeyDialog: () -> Unit,
    onConfirmZenKey: (key: String, label: String) -> Unit,
    onLoginQoder: (QLogin.QRegion) -> Unit,
    onSwitchSlotAccount: (Provider, String) -> Unit,
    onDeleteSlotAccount: (Provider, String) -> Unit,
    onLoginAG: () -> Unit,
    onOpenCredentialDetails: () -> Unit,
    onDismissCredentialDetails: () -> Unit,
    onCopyField: (String, String) -> Unit,
    onCheckin: () -> Unit,
    onCheckinAll: () -> Unit,
    onRefreshBalance: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onCopyModel: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onRefreshModels: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOverlayOpacity: (Float) -> Unit,
    onOverlayLocked: (Boolean) -> Unit,
    onDismissCheckin: () -> Unit,
    showHelp: Boolean,
    onShowHelp: () -> Unit,
    onDismissHelp: () -> Unit,
    onConfirmLogout: () -> Unit = {},
    onDismissLogout: () -> Unit = {},
    onOpenAuthUrl: (String) -> Unit = {},
    onCancelLogin: () -> Unit = {},
    onDismissLoginDialog: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onTabShown: (HubTab) -> Unit = {},
    onSendMessage: (String, List<ChatAttachment>) -> Unit = { _, _ -> },
    onStopStreaming: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onSelectChatModel: (String) -> Unit = {},
    onRetryChatMessage: () -> Unit = {},
    onUpdateThinkingEffort: (ThinkingEffort) -> Unit = {},
    onUpdateExaApiKey: (String) -> Unit = {},
    onToggleMcpEnabled: (Boolean) -> Unit = {},
    onAddMcpServer: (McpServerConfig) -> Unit = {},
    onDeleteMcpServer: (String) -> Unit = {},
    onToggleMcpServer: (String, Boolean) -> Unit = { _, _ -> },
    onPickImage: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    onClearAllConversations: () -> Unit = {},
    onTogglePinConversation: (String) -> Unit = {},
    onToggleArchiveConversation: (String) -> Unit = {},
    onDeleteChatMessage: (String) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    var isChatDetailOpen by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { onTabShown(HubTab.entries[tab]) }

    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }

    TokenHeatTheme(darkTheme = dark) {
        val background = MaterialTheme.colorScheme.background
        Surface(color = background, contentColor = contentColorFor(background)) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (tab != HubTab.Chat.ordinal) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = getGreetingText(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    PillBadge(
                                        text = if (state.bridgeRunning) "运行中 :${state.port}" else "已停用",
                                        variant = if (state.bridgeRunning) BadgeVariant.Success else BadgeVariant.Neutral,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { dark = !dark }) {
                                    Icon(
                                        imageVector = if (dark) Lucide.Sun else Lucide.Moon,
                                        contentDescription = if (dark) "浅色模式" else "深色模式",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                },
                bottomBar = {
                    if (tab != HubTab.Chat.ordinal || !isChatDetailOpen) {
                        FloatingNavigationBar(
                            currentTab = tab,
                            onSelectTab = { tab = it },
                            showStartTrailing = tab == HubTab.Dashboard.ordinal,
                            bridgeRunning = state.bridgeRunning,
                            onToggleBridge = {
                                if (state.bridgeRunning) onStopBridge()
                                else onStartBridge(state.port)
                            },
                        )
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = if (tab == HubTab.Chat.ordinal && isChatDetailOpen) 0.dp else padding.calculateBottomPadding(),
                        ),
                ) {
                    when (HubTab.entries[tab]) {
                        HubTab.Dashboard -> DashboardScreen(
                            state = state,
                            onStartBridge = { onStartBridge(state.port) },
                            onStopBridge = onStopBridge,
                            onCopyEndpoint = onCopyEndpoint,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onShowHelp = onShowHelp,
                            onNavigateToCalls = { tab = HubTab.Calls.ordinal },
                        )
                        HubTab.Credential -> CredentialScreen(
                            state = state,
                            onSwitchRealm = onSwitchRealm,
                            onSwitchAccount = onSwitchAccount,
                            onDeleteAccount = onDeleteAccount,
                            onLogin = onLogin,
                            onLogout = onLogout,
                            onOpenDetails = onOpenCredentialDetails,
                            onSwitchProvider = onSwitchProvider,
                            onLoginZcode = onLoginZcode,
                            onSwitchZcodeAccount = onSwitchZcodeAccount,
                            onDeleteZcodeAccount = onDeleteZcodeAccount,
                            onToggleAccount = onToggleAccount,
                            onSwitchZenAccount = onSwitchZenAccount,
                            onDeleteZenAccount = onDeleteZenAccount,
                            onShowZenKeyDialog = onShowZenKeyDialog,
                            onDismissZenKeyDialog = onDismissZenKeyDialog,
                            onConfirmZenKey = onConfirmZenKey,
                            onLoginQoder = onLoginQoder,
                            onSwitchSlotAccount = onSwitchSlotAccount,
                            onDeleteSlotAccount = onDeleteSlotAccount,
                            onLoginAG = onLoginAG,
                            onOpenAuthUrl = onOpenAuthUrl,
                            onCancelLogin = onCancelLogin,
                            onCopyField = onCopyField,
                            onCheckin = onCheckin,
                            onCheckinAll = onCheckinAll,
                            onRefreshBalance = onRefreshBalance,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                        )
                        HubTab.Chat -> ChatScreen(
                            state = state,
                            onSendMessage = onSendMessage,
                            onStopStreaming = onStopStreaming,
                            onClearMessages = onClearMessages,
                            onSelectModel = onSelectChatModel,
                            onStartBridge = { onStartBridge(state.port) },
                            onRefreshModels = onRefreshModels,
                            onCopyText = onCopyField,
                            onRetryLastMessage = onRetryChatMessage,
                            onUpdateThinkingEffort = onUpdateThinkingEffort,
                            onUpdateExaApiKey = onUpdateExaApiKey,
                            onToggleMcpEnabled = onToggleMcpEnabled,
                            onAddMcpServer = onAddMcpServer,
                            onDeleteMcpServer = onDeleteMcpServer,
                            onToggleMcpServer = onToggleMcpServer,
                            onPickImage = onPickImage,
                            onPickFile = onPickFile,
                            onRemoveAttachment = onRemoveAttachment,
                            onSelectConversation = onSelectConversation,
                            onNewConversation = onNewConversation,
                            onRenameConversation = onRenameConversation,
                            onDeleteConversation = onDeleteConversation,
                            onClearAllConversations = onClearAllConversations,
                            onTogglePinConversation = onTogglePinConversation,
                            onToggleArchiveConversation = onToggleArchiveConversation,
                            onDeleteChatMessage = onDeleteChatMessage,
                            onChatDetailStateChanged = { isChatDetailOpen = it },
                            isDarkTheme = dark,
                            onToggleDarkTheme = { dark = !dark },
                        )
                        HubTab.Bridge -> BridgeScreen(
                            state = state,
                            onStart = onStartBridge,
                            onStop = onStopBridge,
                            onCopyEndpoint = onCopyEndpoint,
                            onShowHelp = onShowHelp,
                            onCopyModel = onCopyModel,
                            onRequestNotifications = onRequestNotifications,
                            onRefreshModels = onRefreshModels,
                            onRequestBatteryExemption = onRequestBatteryExemption,
                            onRequestOverlay = onRequestOverlay,
                            onOverlayOpacity = onOverlayOpacity,
                            onOverlayLocked = onOverlayLocked,
                        )
                        HubTab.Calls -> CallsScreen(state, onClearCalls, onRefreshCalls)
                    }
                }
            }
        }

        if (state.checkinMessage.isNotBlank()) {
            CheckinDialog(
                message = state.checkinMessage,
                items = state.checkinItems,
                onDismiss = onDismissCheckin,
            )
        }
        if (state.showCredentialDrawer) {
            CredentialDetailDrawer(
                state = state,
                onDismiss = onDismissCredentialDetails,
                onCopy = onCopyField,
            )
        }
        if (showHelp) {
            HelpDialog(
                state = state,
                onDismiss = onDismissHelp,
                onCopyEndpoint = onCopyEndpoint,
            )
        }
        if (state.showLogoutConfirm) {
            LogoutDialog(
                onConfirm = onConfirmLogout,
                onDismiss = onDismissLogout,
            )
        }
        if (state.loginFlow != null) {
            LoginProgressDialog(
                flow = state.loginFlow,
                onCopyUrl = { onCopyField("授权链接", it) },
                onOpenBrowser = onOpenAuthUrl,
                onCancel = onCancelLogin,
                onDismiss = onDismissLoginDialog,
            )
        }
    }
}

/**
 * Dynamic friendly greeting text based on local clock time.
 */
private fun getGreetingText(): String {
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val timeVal = hour + minute / 60.0
    return when {
        timeVal < 6.0 -> "凌晨好，少熬夜身体好~"
        timeVal < 11.5 -> "早上好，记得吃早餐哦~"
        timeVal < 14.0 -> "中午好，需要干点什么？"
        timeVal < 18.5 -> "下午好，来杯咖啡？"
        else -> "工作劳累，记得休息。"
    }
}

/**
 * FlClash 目标模式的悬浮导航坞（对标 lib/widgets/navigation_dock.dart）：
 * - 全胶囊超椭圆底座，色 surfaceContainer，边距 21dp，.bar 高 62dp；
 * - 图标 24dp + 10sp 标签常显，选中态 secondaryContainer 镜片；
 * - Dashboard 页右侧挂圆形启停键（对标 docked StartButton 槽位）。
 */
@Composable
private fun FloatingNavigationBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showStartTrailing: Boolean = false,
    bridgeRunning: Boolean = false,
    onToggleBridge: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 21.dp, end = 21.dp, top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = SquirclePillShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 8.dp,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier
                        .height(64.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HubTab.entries.forEachIndexed { index, item ->
                        val isSelected = currentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(SquirclePillShape)
                                .clickable { onSelectTab(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = SquirclePillShape,
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showStartTrailing) {
                Surface(
                    shape = IconSquircleShape,
                    color = if (bridgeRunning) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (bridgeRunning) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(IconSquircleShape)
                        .clickable(onClick = onToggleBridge),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (bridgeRunning) Lucide.Square else Lucide.Play,
                            contentDescription = if (bridgeRunning) "停止服务" else "启动服务",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
