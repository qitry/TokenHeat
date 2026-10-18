package app.anylyralabs.lyraproxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
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
import app.anylyralabs.lyraproxy.bridge.ApiKey
import app.anylyralabs.lyraproxy.data.Login
import app.anylyralabs.lyraproxy.proto.Wire

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onOverlayEnabled: (Boolean) -> Unit,
    onOverlayMovable: (Boolean) -> Unit,
    onCopyApiKey: (String) -> Unit,
    onToggleApiKeyEnabled: (ApiKey, Boolean) -> Unit,
    onEditApiKey: (ApiKey) -> Unit,
    onCreateApiKey: () -> Unit,
    onDeleteApiKey: (ApiKey) -> Unit,
    onClearApiKeyUsage: (ApiKey) -> Unit,
    onSaveApiKey: (label: String, allowedModels: List<String>, budget: Double, enabled: Boolean) -> Unit,
    onDismissApiKeyEditor: () -> Unit,
    onDismissApiKeyCreated: () -> Unit,
    onConfirmDeleteApiKey: (ApiKey) -> Unit,
    onDismissDeleteApiKey: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFromText: () -> Unit,
    onDismissPasteImport: () -> Unit,
    onConfirmPasteImport: (String) -> Unit,
    onDismissCheckin: () -> Unit,
    showHelp: Boolean,
    onShowHelp: () -> Unit,
    onDismissHelp: () -> Unit,
    onTabShown: (HubTab) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(HubTab.Dashboard.ordinal) }
    // Reporting the visible tab lets the host reload that page's data on entry,
    // which is what keeps the call history current without a restart.
    LaunchedEffect(tab) { onTabShown(HubTab.entries[tab]) }
    var dark by remember { mutableStateOf(false) }

    MaterialExpressiveTheme(
        colorScheme = if (dark) darkColorScheme() else expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
    ) {
        val background = MaterialTheme.colorScheme.background
        Surface(color = background, contentColor = contentColorFor(background)) {
            Scaffold(
                // 取消 Scaffold 自带的 insets，避免键盘弹起时内容被重复顶起。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Text("LyraProxy", fontWeight = FontWeight.SemiBold)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    FloatingNavigationBar(
                        currentTab = tab,
                        onSelectTab = { tab = it },
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
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
                            onNavigateToCalls = { tab = HubTab.Calls.ordinal },
                            onCheckin = onCheckin,
                            onCheckinAll = onCheckinAll,
                            onRefreshBalance = onRefreshBalance,
                        )
                        HubTab.Credential -> CredentialScreen(
                            state = state,
                            onSwitchRealm = onSwitchRealm,
                            onSwitchAccount = onSwitchAccount,
                            onDeleteAccount = onDeleteAccount,
                            onLogin = onLogin,
                            onLogout = onLogout,
                            onOpenDetails = onOpenCredentialDetails,
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onImportFromText = onImportFromText,
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
                            onOverlayEnabled = onOverlayEnabled,
                            onOverlayMovable = onOverlayMovable,
                            onCopyApiKey = onCopyApiKey,
                            onToggleApiKeyEnabled = onToggleApiKeyEnabled,
                            onEditApiKey = onEditApiKey,
                            onDeleteApiKey = onDeleteApiKey,
                            onCreateApiKey = onCreateApiKey,
                            onClearApiKeyUsage = onClearApiKeyUsage,
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
        if (state.showPasteImport) {
            PasteImportDialog(
                onDismiss = onDismissPasteImport,
                onConfirm = onConfirmPasteImport,
            )
        }
        // 新建 / 编辑密钥
        if (state.creatingApiKey || state.editingApiKey != null) {
            ApiKeyEditorDialog(
                existing = state.editingApiKey,
                availableModels = state.models.map { it.id },
                onDismiss = onDismissApiKeyEditor,
                onSave = onSaveApiKey,
            )
        }
        state.justCreatedApiKey?.let { created ->
            ApiKeyCreatedDialog(
                apiKey = created,
                onCopy = { onCopyApiKey(created.key) },
                onDismiss = onDismissApiKeyCreated,
            )
        }
        state.deletingApiKey?.let { target ->
            ApiKeyDeleteDialog(
                apiKey = target,
                onConfirm = { onConfirmDeleteApiKey(target) },
                onDismiss = onDismissDeleteApiKey,
            )
        }
        if (showHelp) {
            HelpDialog(
                state = state,
                onDismiss = onDismissHelp,
                onCopyEndpoint = onCopyEndpoint,
            )
        }
    }
}

/**
 * Dynamic friendly greeting text based on local clock time.
 * Rendered inside the Dashboard hero readout.
 */
internal fun getGreetingText(): String {
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val timeVal = hour + minute / 60.0
    return when {
        timeVal < 6.0 -> "凌晨好，少熬夜身体好"
        timeVal < 11.5 -> "早上好，记得吃早餐"
        timeVal < 14.0 -> "中午好，需要干点什么"
        timeVal < 18.5 -> "下午好，来杯咖啡"
        else -> "工作劳累，记得休息"
    }
}

/**
 * 悬浮药丸底栏。
 *
 * 选中态用实底药丸 + 反色图标文字，其余只显示图标，避免五个文字标签挤在一起；
 * 底部额外留出导航栏高度，避开系统手势条。
 */
@Composable
private fun FloatingNavigationBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    // 积分已并入首页，底栏只展示四个真实页面。
    val visibleTabs = HubTab.entries

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visibleTabs.forEach { item ->
                    val index = item.ordinal
                    val selected = currentTab == index
                    val horizontal by animateDpAsState(
                        targetValue = if (selected) 14.dp else 12.dp,
                        label = "navHorizontalPadding",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "navContentColor",
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onSelectTab(index) },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = horizontal, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor,
                            )
                            if (selected) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
