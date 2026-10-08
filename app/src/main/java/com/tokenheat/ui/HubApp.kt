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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                topBar = {
                    if (tab != HubTab.Chat.ordinal) {
                        TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TokenHeat",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                        )
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
 * Shadcn/iOS-inspired floating pill bottom navigation bar.
 */
@Composable
private fun FloatingNavigationBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HubTab.entries.forEachIndexed { index, item ->
                    val isSelected = currentTab == index
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelectTab(index) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn() + expandHorizontally(),
                            ) {
                                Row {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
