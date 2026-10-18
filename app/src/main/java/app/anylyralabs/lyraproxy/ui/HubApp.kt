package app.anylyralabs.lyraproxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.anylyralabs.lyraproxy.bridge.ApiKey
import app.anylyralabs.lyraproxy.proto.Wire

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
    onOverlayPassthrough: (Boolean) -> Unit,
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
    LaunchedEffect(tab) { onTabShown(HubTab.entries[tab]) }
    var dark by remember { mutableStateOf(false) }

    // 状态栏图标跟随应用内的亮暗，否则暗色模式下黑色图标看不见。
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(dark) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    ClaudeTheme(darkTheme = dark) {
        val background = MaterialTheme.colorScheme.background
        Surface(color = background, contentColor = contentColorFor(background)) {
            Scaffold(
                // 交给顶栏与底栏各自避让系统栏：Scaffold 若同时消费 insets，
                // 会与栏内的 statusBarsPadding / navigationBarsPadding 叠加成双倍留白。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    AppHeader(
                        running = state.bridgeRunning,
                        dark = dark,
                        onToggleDark = { dark = !dark },
                    )
                },
                bottomBar = {
                    BottomTabs(current = tab, onSelect = { tab = it })
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
                            onOverlayPassthrough = onOverlayPassthrough,
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
 * 顶栏：左侧一个轻字标，右侧只有亮暗切换。
 * 运行状态放到页面正文里说，顶栏不重复。
 */
@Composable
private fun AppHeader(running: Boolean, dark: Boolean, onToggleDark: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 顶栏自己避让状态栏：Scaffold 只提供 padding，不会替 topBar 处理。
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lyra",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "Proxy",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            if (running) {
                LiveDot(active = true, size = 6.dp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleDark),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (dark) "浅色" else "深色",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * 底栏：纯文字导航，不用图标。
 * 四个入口字数不多，文字比图标更直接，也省掉 4 个图标的绘制。
 */
@Composable
private fun BottomTabs(current: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HubTab.entries.forEach { item ->
                val selected = current == item.ordinal
                val color by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tabColor",
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(item.ordinal) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = color,
                    )
                    Spacer(Modifier.height(3.dp))
                    // 指示条固定宽度，只切换颜色：若宽度随选中变化，
                    // 文字会被挤动，切换标签时整行会抖一下。
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Dynamic friendly greeting text based on local clock time.
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
