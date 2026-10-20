package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anylyralabs.lyraproxy.bridge.ApiKey
import app.anylyralabs.lyraproxy.proto.Wire
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabBarDefaults
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiTabBarScrollBehavior
import ui.feice.mochi.effect.hdr.MochiEdgeGlow
import ui.feice.mochi.effect.hdr.MochiHdrPalette
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.navigation.MochiPeerTransition
import ui.feice.mochi.navigation.MochiPeerTransitionSpec
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.navigation.mochiScrollEdgeFade
import ui.feice.mochi.nothing.NothingColors
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/** 底部标签栏：图标 + 文字，交给 Mochi 的胶囊栏做选中块滑动与触感。 */
private val LyraTabs = persistentListOf(
    MochiTabItem(
        label = HubTab.Dashboard.label,
        icon = { tint -> LyraIcon(Icons.Outlined.Home, contentDescription = null, tint = tint) },
        selectedIcon = { tint -> LyraIcon(Icons.Filled.Home, contentDescription = null, tint = tint) },
    ),
    MochiTabItem(
        label = HubTab.Credential.label,
        icon = { tint -> LyraIcon(Icons.Outlined.Person, contentDescription = null, tint = tint) },
        selectedIcon = { tint -> LyraIcon(Icons.Filled.Person, contentDescription = null, tint = tint) },
    ),
    MochiTabItem(
        label = HubTab.Bridge.label,
        icon = { tint -> LyraIcon(Icons.Outlined.Settings, contentDescription = null, tint = tint) },
        selectedIcon = { tint -> LyraIcon(Icons.Filled.Settings, contentDescription = null, tint = tint) },
    ),
    MochiTabItem(
        label = HubTab.Calls.label,
        icon = { tint -> LyraIcon(Icons.Outlined.History, contentDescription = null, tint = tint) },
        selectedIcon = { tint -> LyraIcon(Icons.Filled.History, contentDescription = null, tint = tint) },
    ),
    MochiTabItem(
        label = HubTab.Theme.label,
        icon = { tint -> LyraIcon(Icons.Outlined.Palette, contentDescription = null, tint = tint) },
        selectedIcon = { tint -> LyraIcon(Icons.Filled.Palette, contentDescription = null, tint = tint) },
    ),
)

private val HubPages = listOf(0, 1, 2, 3, 4)

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
    onQoderLogin: () -> Unit = {},
    onQoderPatLogin: () -> Unit = {},
    onQoderRefreshModels: () -> Unit = {},
    onQoderRefreshUsage: () -> Unit = {},
    onQoderLogout: () -> Unit = {},
    onQoderSwitchAccount: (String) -> Unit = {},
    onQoderDeleteAccount: (String) -> Unit = {},
    onQoderOpenDetail: () -> Unit = {},
    onDismissQoderDetail: () -> Unit = {},
    onQoderRequestLogout: () -> Unit = {},
    onQoderConfirmLogout: () -> Unit = {},
    onDismissQoderLogout: () -> Unit = {},
    onCopyQoderField: (String, String) -> Unit = { _, _ -> },
    onSwitchProvider: (GatewayProvider) -> Unit = {},
    onForwardReasoning: (Boolean) -> Unit = {},
    onQoderPatConfirm: (String) -> Unit = {},
    onDismissQoderPat: () -> Unit = {},
    onDismissQoderLogin: () -> Unit = {},
    onCopyQoderUrl: () -> Unit = {},
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
    val appearance = LocalLyraAppearance.current
    val scrollBehavior = rememberMochiTabBarScrollBehavior()

    // 总开关打开时点亮一圈 3 秒的边缘流光（日落配色）。
    var edgeGlow by remember { mutableStateOf(false) }
    LaunchedEffect(state.bridgeRunning) {
        if (state.bridgeRunning) {
            edgeGlow = true
            delay(3_000)
            edgeGlow = false
        } else {
            edgeGlow = false
        }
    }

    ProvideMochiOverscroll {
        Box(Modifier.fillMaxSize().background(MochiDefaults.groupedBackground())) {
            // 切换标签用同层级过渡的景深样式：前一页往后退、淡出，后一页从后面往前、淡入。
            // 不保留已经切走的页：记录页和概览页各自有轮询，留在组合里会一直跑。
            MochiPeerTransition(
                targetState = tab,
                spec = MochiPeerTransitionSpec.Depth,
                pages = HubPages,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                onPageChange = {
                    tab = it
                    scrollBehavior.expand()
                },
                retainPages = false,
            ) { page ->
                when (page) {
                    0 -> HubPage(HubTab.Dashboard.label) {
                        DashboardScreen(
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
                            onRefreshCalls = onRefreshCalls,
                            onRefreshQoderUsage = onQoderRefreshUsage,
                        )
                    }

                    1 -> HubPage(HubTab.Credential.label) {
                        CredentialScreen(
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
                            onQoderLogin = onQoderLogin,
                            onQoderPatLogin = onQoderPatLogin,
                            onQoderLogout = onQoderLogout,
                            onQoderSwitchAccount = onQoderSwitchAccount,
                            onQoderDeleteAccount = onQoderDeleteAccount,
                            onQoderOpenDetail = onQoderOpenDetail,
                            onQoderRequestLogout = onQoderRequestLogout,
                            onSwitchProvider = onSwitchProvider,
                        )
                    }

                    2 -> HubPage(HubTab.Bridge.label) {
                        BridgeScreen(
                            state = state,
                            onStart = onStartBridge,
                            onStop = onStopBridge,
                            onCopyEndpoint = onCopyEndpoint,
                            onShowHelp = onShowHelp,
                            onCopyModel = onCopyModel,
                            onRequestNotifications = onRequestNotifications,
                            onRefreshModels = if (state.provider == GatewayProvider.QODER) {
                                onQoderRefreshModels
                            } else {
                                onRefreshModels
                            },
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
                            onForwardReasoning = onForwardReasoning,
                        )
                    }

                    3 -> HubPage(HubTab.Calls.label) {
                        CallsScreen(state, onClearCalls, onRefreshCalls)
                    }

                    else -> ThemeTab()
                }
            }

            // 滚动边缘效果：标签栏后面垫一层底色，朝内容那侧渐隐。
            Spacer(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            LyraBottomBarClearance,
                    )
                    .mochiScrollEdgeFade(MochiDefaults.groupedBackground(), bottom = true, extent = 56.dp),
            )

            MochiTabBar(
                selectedIndex = tab,
                colors = if (appearance?.style == LyraStyle.Nothing) {
                    MochiTabBarDefaults.colors(selectedContent = NothingColors.Accent)
                } else {
                    MochiTabBarDefaults.colors()
                },
                onSelect = {
                    tab = it
                    scrollBehavior.expand()
                },
                items = LyraTabs,
                // 只有图标 + 选中项横排标签，和 demo 首页一致。
                behavior = MochiTabBarDefaults.iconOnlyBehavior(selectedLabelOnly = true),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                scrollBehavior = scrollBehavior,
                onReselect = { scrollBehavior.expand() },
                // 附件条：常驻显示当前模型、供应商与网关开关
                accessory = { inline ->
                    LyraAccessory(
                        inline = inline,
                        provider = state.provider.label,
                        model = state.calls
                            .maxByOrNull { it.timestamp }
                            ?.model
                            ?.takeIf { it.isNotBlank() }
                            ?: "自动路由",
                        running = state.bridgeRunning,
                        onToggle = { checked ->
                            if (checked) onStartBridge(state.port) else onStopBridge()
                        },
                    )
                },
            )

            MochiEdgeGlow(
                active = edgeGlow,
                modifier = Modifier.fillMaxSize(),
                palette = MochiHdrPalette.Sunset,
                width = 24.dp,
                line = 3.dp,
                speed = 3f,
                intensity = 1f,
            )
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
        if (state.showQoderDetail) {
            QoderDetailDrawer(
                account = state.qoderAccount,
                onDismiss = onDismissQoderDetail,
                onCopy = onCopyQoderField,
            )
        }
        if (state.showQoderLogoutConfirm) {
            QoderLogoutDialog(
                onConfirm = onQoderConfirmLogout,
                onDismiss = onDismissQoderLogout,
            )
        }
        if (state.showPasteImport) {
            PasteImportDialog(
                onDismiss = onDismissPasteImport,
                onConfirm = onConfirmPasteImport,
            )
        }
        if (state.qoderShowPatDialog) {
            QoderPatDialog(
                onDismiss = onDismissQoderPat,
                onConfirm = onQoderPatConfirm,
            )
        }
        if (state.qoderLoginUrl.isNotBlank()) {
            QoderLoginDialog(
                authUrl = state.qoderLoginUrl,
                onCopy = onCopyQoderUrl,
                onDismiss = onDismissQoderLogin,
            )
        }
        if (state.creatingApiKey || state.editingApiKey != null) {
            ApiKeyEditorDialog(
                existing = state.editingApiKey,
                availableModels = if (state.provider == GatewayProvider.QODER) {
                    state.qoderModels.map { it.id }
                } else {
                    state.models.map { it.id }
                },
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
 * 已有页面还没有自己的顶栏：这里给它们一个统一的 Mochi 紧凑顶栏，
 * 内容下移，底部给浮起的标签栏和附件条让出空间。
 */
@Composable
private fun HubPage(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MochiTitleBar(title)
        Box(
            Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(bottom = LyraBottomBarClearance)
                .navigationBarsPadding(),
        ) {
            content()
        }
    }
}

/**
 * 标签栏上方的附件条：当前模型 + 供应商 + 网关开关。
 *
 * 收进标签栏那一行时（[inline]）只留模型名和开关，避免把两行文字挤进
 * 胶囊高度；展开时左侧补一个供应商首字母方块，和胶囊圆头同心。
 */
@Composable
private fun LyraAccessory(
    inline: Boolean,
    provider: String,
    model: String,
    running: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MochiTheme.colorScheme
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = if (inline) 18.dp else 8.dp, end = if (inline) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!inline) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(cs.primaryContainer, MochiCapsule),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.take(1),
                    style = MochiTheme.textStyles.labelLarge,
                    color = cs.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = model,
                style = MochiTheme.textStyles.labelLarge,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!inline) {
                Text(
                    text = provider,
                    style = MochiTheme.textStyles.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        MochiSwitch(checked = running, onCheckedChange = onToggle)
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
