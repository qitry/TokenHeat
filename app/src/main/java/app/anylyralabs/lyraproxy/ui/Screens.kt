package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiAlign
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogTextField
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListColors
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.list.MochiListStatusChip
import ui.feice.mochi.theme.MochiTheme
import app.anylyralabs.lyraproxy.bridge.ApiKey
import app.anylyralabs.lyraproxy.bridge.CallRecord
import app.anylyralabs.lyraproxy.bridge.KeepAliveUtils
import app.anylyralabs.lyraproxy.bridge.UsageSummary
import app.anylyralabs.lyraproxy.data.CheckinItem
import app.anylyralabs.lyraproxy.proto.Wire

// -----------------------------------------------------------------------------
// 凭证
// -----------------------------------------------------------------------------

@Composable
fun CredentialScreen(
    state: HubState,
    onSwitchRealm: (Wire.Region) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onLogin: (Wire.Region) -> Unit,
    onLogout: () -> Unit,
    onOpenDetails: () -> Unit,
    onQoderLogin: () -> Unit = {},
    onQoderPatLogin: () -> Unit = {},
    onQoderLogout: () -> Unit = {},
    onQoderSwitchAccount: (String) -> Unit = {},
    onQoderDeleteAccount: (String) -> Unit = {},
    onQoderOpenDetail: () -> Unit = {},
    onQoderRequestLogout: () -> Unit = {},
    onSwitchProvider: (GatewayProvider) -> Unit = {},
) {
    val region = state.realm
    val regionLabel = realmName(region)
    val active = state.credential
    val saved = state.accounts[region].orEmpty()
    var realmMenuOpen by remember { mutableStateOf(false) }
    val realmItems = remember(state.accounts) {
        Wire.Region.entries.map {
            MochiItem("${realmName(it)} · ${state.accounts[it].orEmpty().size} 个账号")
        }.toImmutableList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
    ) {
        // 供应商选择：WorkBuddy 与 Qoder 是两套完全独立的账号体系，
        // 不能混在同一个「版本」切换里，所以先选供应商，再展示对应内容。
        item(key = "provider") {
            MochiSegmentedControl(
                selectedIndex = GatewayProvider.entries.indexOf(state.provider).coerceAtLeast(0),
                onSelect = { onSwitchProvider(GatewayProvider.entries[it]) },
                items = ProviderLabels,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.provider == GatewayProvider.QODER) {
            item(key = "qoder") {
                QoderSection(
                    account = state.qoderAccount,
                    accounts = state.qoderAccounts,
                    quota = state.qoderUsage,
                    quotaError = state.qoderUsageError,
                    busy = state.qoderBusy,
                    status = state.qoderStatus,
                    onLogin = onQoderLogin,
                    onPatLogin = onQoderPatLogin,
                    onLogout = onQoderLogout,
                    onSwitchAccount = onQoderSwitchAccount,
                    onDeleteAccount = onQoderDeleteAccount,
                    onOpenDetail = onQoderOpenDetail,
                    onRequestLogout = onQoderRequestLogout,
                )
            }
        } else {

            // 当前账号
            item(key = "current") {
                MochiListSection(header = regionLabel) {
                    if (active == null) {
                        MochiListRow(
                            title = "$regionLabel 还没有登录",
                            subtitle = "登录后即可通过本机网关调用模型",
                        )
                    } else {
                        MochiListRow(
                            title = active.nickname.ifBlank { active.uid.take(10) },
                            subtitle = "有效期至 ${state.expiryText.ifBlank { "未知" }}",
                        )
                    }
                    MochiListButton("凭证详情", onOpenDetails)
                    if (state.hasAnyCredential) {
                        MochiListButton("退出登录", onLogout, destructive = true)
                    }
                }
            }

            // 版本切换：国内版 / 国际版是两套账号，用下拉菜单切换，省掉并排分段。
            item(key = "realm") {
                MochiListSection(
                    header = "版本",
                    footer = "两个版本各自保存账号，切换后立即使用对应版本的凭证。",
                ) {
                    MochiListRow(
                        title = "当前版本",
                        subtitle = "国内版 / 国际版",
                        trailing = {
                            MochiDropdown(
                                expanded = realmMenuOpen,
                                onExpandedChange = { realmMenuOpen = it },
                                items = realmItems,
                                onItemClick = { index ->
                                    onSwitchRealm(Wire.Region.entries[index])
                                    realmMenuOpen = false
                                },
                                selectedIndex = Wire.Region.entries.indexOf(region).coerceAtLeast(0),
                                behavior = MochiDropdownDefaults.behavior(align = MochiAlign.End),
                                buttonMinHeight = 34.dp,
                                buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "$regionLabel · ${saved.size} 个账号",
                                    style = MochiTheme.textStyles.labelLarge,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(6.dp))
                                LyraIcon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }

            // 账号列表：与 Qoder 的账号行保持同一套排版。
            if (saved.isNotEmpty()) {
                item(key = "accounts") {
                    MochiListSection(header = "$regionLabel 的账号 · ${saved.size}") {
                        saved.forEach { account ->
                            val isActive = account.id == state.activeAccountId
                            AccountLine(
                                label = account.label,
                                uid = account.uid,
                                isActive = isActive,
                                onSelect = { onSwitchAccount(account.id) },
                                onDelete = { onDeleteAccount(account.id) },
                            )
                        }
                    }
                }
            }

            // 登录
            item(key = "actions") {
                MochiListSection {
                    MochiListRow(
                        title = if (active == null) "登录 $regionLabel" else "添加 $regionLabel 账号",
                        onClick = { onLogin(region) },
                        chevron = true,
                    )
                }
            }

            // 说明
            item(key = "note") {
                Text(
                    text = "登录走官方 CLI 的 OAuth 流程，需要在本机浏览器完成授权。国内版与国际版是两套独立账号体系，各自可添加多个账号并在其间切换。",
                    style = MochiTheme.textStyles.bodySmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MochiListDefaults.TextRowPadding),
                )
            }
        }
    }
}

private val ProviderLabels = persistentListOf("WorkBuddy", "Qoder")

/** 账号行：一行文字 + 右侧状态；选中态靠一个小圆点，不用整行换色。 */
@Composable
private fun AccountLine(
    label: String,
    uid: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    MochiListRow(
        title = label,
        subtitle = if (uid.isNotBlank()) uid.take(10) else null,
        leading = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MochiTheme.colorScheme.primary
                        } else {
                            MochiTheme.colorScheme.outline
                        },
                    ),
            )
        },
        onClick = onSelect,
        checked = isActive,
        titleTrailing = {
            if (isActive) {
                Tag("使用中", tone = TagTone.Active)
            }
        },
        trailing = {
            TextAction(text = "删除", onClick = onDelete, danger = true)
        },
    )
}

/** 凭证详情抽屉：全部字段可点按复制。 */
@Composable
fun CredentialDetailDrawer(
    state: HubState,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val sheetState = rememberMochiSheetState(initial = MochiSheetDetent.Large)
    LaunchedEffect(Unit) { sheetState.show() }

    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = "凭证详情")
        MochiSheetContent {
            val cred = state.credential
            if (cred == null) {
                Text(
                    text = "尚无凭证",
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
                return@MochiSheetContent
            }
            Text(
                text = "点任意一行复制该字段",
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
            val rows = listOf(
                "版本" to realmName(Wire.regionOf(cred.domain)),
                "昵称" to cred.nickname.ifBlank { "无" },
                "UID" to cred.uid,
                "域名" to cred.domain,
                "来源" to cred.source,
                "有效期" to state.expiryText.ifBlank { "未知" },
                "Access Token" to cred.accessToken,
                "Refresh Token" to cred.refreshToken.ifBlank { "无" },
            )
            MochiListSection {
                rows.forEach { (label, value) ->
                    MochiListRow(
                        title = label,
                        subtitle = value,
                        onClick = { onCopy(label, value) },
                    )
                }
            }
            Text(
                text = "Token 等同于登录凭据，分享后对方可以你的身份调用接口。",
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 服务
// -----------------------------------------------------------------------------

@Composable
fun BridgeScreen(
    state: HubState,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onShowHelp: () -> Unit,
    onCopyModel: (String) -> Unit,
    onRequestNotifications: () -> Unit,
    onRefreshModels: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestOverlay: () -> Unit,
    onOverlayOpacity: (Float) -> Unit,
    onOverlayLocked: (Boolean) -> Unit,
    onOverlayEnabled: (Boolean) -> Unit = {},
    onOverlayMovable: (Boolean) -> Unit = {},
    onOverlayPassthrough: (Boolean) -> Unit = {},
    onCopyApiKey: (String) -> Unit = {},
    onToggleApiKeyEnabled: (ApiKey, Boolean) -> Unit = { _, _ -> },
    onEditApiKey: (ApiKey) -> Unit = {},
    onDeleteApiKey: (ApiKey) -> Unit = {},
    onCreateApiKey: () -> Unit = {},
    onClearApiKeyUsage: (ApiKey) -> Unit = {},
    onForwardReasoning: (Boolean) -> Unit = {},
) {
    var portText by remember(state.port) { mutableStateOf(state.port.toString()) }
    var showPortDialog by remember { mutableStateOf(false) }
    var modeMenuOpen by remember { mutableStateOf(false) }

    // 模型目录跟随供应商：切到 Qoder 时展示 Qoder 的目录，避免两套模型混在一起。
    val qoderMode = state.provider == GatewayProvider.QODER
    val shownModels = if (qoderMode) state.qoderModels else state.models
    val modelsBusy = if (qoderMode) state.qoderBusy else state.loading == Loading.MODELS
    val sortedModels = remember(shownModels) {
        shownModels.sortedWith(compareBy({ it.sortKey }, { it.id }))
    }

    // 当前监听端口与「下次启动」的草稿分开显示，避免复制地址和端口行对不上。
    val pendingPort = portText.takeIf { it != state.port.toString() }
    val modeIndex = when {
        state.overlayPassthrough -> 2
        state.overlayLocked -> 1
        else -> 0
    }
    val modeLabel = OverlayModeLabels[modeIndex]
    val modeHint = when (modeIndex) {
        1 -> "能点击折叠，不能拖动"
        2 -> "不响应任何触摸，事件交给下层应用"
        else -> "能移动位置，点一下折叠"
    }
    val modeItems = remember { OverlayModeLabels.map { MochiItem(it) }.toImmutableList() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            // 服务状态
            item(key = "status") {
                MochiListSection {
                    MochiListRow(
                        title = if (state.bridgeRunning) "运行中" else "已停止",
                        subtitle = "本机回环地址 127.0.0.1:${state.port}，仅接受本机请求。",
                        leading = { LiveDot(active = state.bridgeRunning) },
                        trailing = {
                            MochiSwitch(
                                checked = state.bridgeRunning,
                                onCheckedChange = { checked ->
                                    if (checked) onStart(portText.toIntOrNull() ?: 8765) else onStop()
                                },
                            )
                        },
                    )
                    if (!state.notificationsAllowed) {
                        MochiListRow(
                            title = "通知权限未开启",
                            subtitle = "系统可能回收后台服务",
                            onClick = onRequestNotifications,
                            destructive = true,
                            trailing = { TextAction(text = "开启", onClick = onRequestNotifications) },
                        )
                    }
                    if (!state.batteryExempt) {
                        MochiListRow(
                            title = "电池优化未关闭",
                            subtitle = "后台进程可能被系统限制",
                            onClick = onRequestBatteryExemption,
                            trailing = { TextAction(text = "去设置", onClick = onRequestBatteryExemption) },
                        )
                    }
                }
            }

            // 接入信息：键在左、值在右，值统一等宽；地址、密钥、端口三行同属一张卡片。
            item(key = "endpoint") {
                MochiListSection(
                    header = "接入信息",
                    footer = "点地址行可直接复制；端口修改在下次启动服务时生效。",
                ) {
                    MochiListRow(
                        title = "地址",
                        onClick = onCopyEndpoint,
                        trailing = { MonoValue("http://127.0.0.1:${state.port}/v1") },
                    )
                    MochiListRow(
                        title = "密钥",
                        trailing = { MonoValue(state.secret) },
                    )
                    MochiListRow(
                        title = "端口",
                        subtitle = pendingPort?.let { "下次启动使用 $it" },
                        onClick = { showPortDialog = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonoValue(state.port.toString())
                                Spacer(Modifier.width(6.dp))
                                MochiIconButton(
                                    onClick = { showPortDialog = true },
                                    size = 30.dp,
                                ) {
                                    LyraIcon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "编辑端口",
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        },
                    )
                    MochiListRow(
                        title = "复制地址",
                        onClick = onCopyEndpoint,
                        trailing = { TextAction(text = "复制", onClick = onCopyEndpoint) },
                    )
                    MochiListRow(
                        title = "接入帮助",
                        onClick = onShowHelp,
                        chevron = true,
                    )
                }
            }

            // 保活悬浮窗：开关、不透明度、交互方式都收在同一张卡片里。
            item(key = "overlay") {
                MochiListSection(
                    header = "保活悬浮窗",
                    footer = "切到后台时显示状态，让系统保持本应用可见。",
                ) {
                    MochiListRow(
                        title = "显示悬浮窗",
                        subtitle = if (state.overlayAllowed) {
                            "切到后台时显示状态面板"
                        } else {
                            "需要「显示在其他应用上层」权限"
                        },
                        trailing = {
                            MochiSwitch(
                                checked = state.overlayEnabled && state.overlayAllowed,
                                onCheckedChange = { checked ->
                                    if (checked && !state.overlayAllowed) {
                                        onRequestOverlay()
                                    } else {
                                        onOverlayEnabled(checked)
                                    }
                                },
                            )
                        },
                    )
                    if (!state.overlayAllowed) {
                        MochiListRow(
                            title = "授予悬浮窗权限",
                            onClick = onRequestOverlay,
                            destructive = true,
                            chevron = true,
                        )
                    }
                    val overlayActive = state.overlayEnabled && state.overlayAllowed
                    MochiListSliderRow(
                        value = state.overlayOpacity,
                        onValueChange = onOverlayOpacity,
                        valueRange = 0.15f..1f,
                        enabled = overlayActive,
                        leading = {
                            Text(
                                text = "不透明度",
                                style = MochiTheme.textStyles.body,
                                maxLines = 1,
                            )
                        },
                        trailing = {
                            Text(
                                text = "${(state.overlayOpacity * 100).toInt()}%",
                                style = MonoSmall,
                            )
                        },
                    )
                    MochiListRow(
                        title = "交互方式",
                        subtitle = modeHint,
                        trailing = {
                            MochiDropdown(
                                expanded = modeMenuOpen,
                                onExpandedChange = { modeMenuOpen = it },
                                items = modeItems,
                                onItemClick = { index ->
                                    when (index) {
                                        0 -> {
                                            onOverlayPassthrough(false)
                                            onOverlayMovable(true)
                                            onOverlayLocked(false)
                                        }
                                        1 -> {
                                            onOverlayPassthrough(false)
                                            onOverlayLocked(true)
                                            onOverlayMovable(false)
                                        }
                                        else -> onOverlayPassthrough(true)
                                    }
                                    modeMenuOpen = false
                                },
                                selectedIndex = modeIndex,
                                enabled = overlayActive,
                                behavior = MochiDropdownDefaults.behavior(align = MochiAlign.End),
                                buttonMinHeight = 34.dp,
                                buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = MochiTheme.textStyles.labelLarge,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(6.dp))
                                LyraIcon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                    )
                }
            }

            // 后台保活
            item(key = "keepalive") {
                MochiListSection(
                    header = "后台保活",
                    footer = KeepAliveUtils.romHint(),
                ) {
                    MochiListRow(
                        title = "电池优化",
                        subtitle = if (state.batteryExempt) {
                            "已忽略电池优化，后台限制较少"
                        } else {
                            "未忽略电池优化，系统可能限制或清理进程"
                        },
                        value = if (state.batteryExempt) "已忽略" else "未忽略",
                        onClick = onRequestBatteryExemption,
                        chevron = true,
                    )
                    MochiListRow(
                        title = "通知",
                        subtitle = if (state.notificationsAllowed) "已允许" else "未允许",
                        value = if (state.notificationsAllowed) "已开启" else "未开启",
                        onClick = onRequestNotifications,
                        chevron = true,
                    )
                }
            }

            // 自定义密钥
            item(key = "apikeys") {
                MochiListSection(
                    header = "自定义密钥",
                    footer = "为客户端分别签发，可限制模型与预算",
                ) {
                    if (state.apiKeys.isEmpty()) {
                        MochiListRow(
                            title = "使用共享密钥",
                            subtitle = "当前所有客户端共用上面那条共享密钥，无法区分来源，也无法单独限额。",
                        )
                    } else {
                        state.apiKeys.forEach { key ->
                            ApiKeyItem(
                                apiKey = key,
                                onCopy = { onCopyApiKey(key.key) },
                                onToggle = { enabled -> onToggleApiKeyEnabled(key, enabled) },
                                onEdit = { onEditApiKey(key) },
                                onDelete = { onDeleteApiKey(key) },
                                onClearUsage = { onClearApiKeyUsage(key) },
                            )
                        }
                    }
                    MochiListButton("新建密钥", onCreateApiKey)
                }
            }

            // 网关行为
            if (state.provider == GatewayProvider.QODER) {
                item(key = "gateway-behavior") {
                    MochiListSection(
                        header = "网关行为",
                        footer = "控制转发给客户端的字段",
                    ) {
                        MochiListRow(
                            title = "转发思考过程",
                            subtitle = "关闭后只转发正文，兼容不支持 reasoning_content 的客户端",
                            trailing = {
                                MochiSwitch(
                                    checked = state.forwardReasoning,
                                    onCheckedChange = onForwardReasoning,
                                )
                            },
                        )
                    }
                }
            }

            // 模型目录
            item(key = "models") {
                MochiListSection(
                    header = "可用模型",
                    footer = "点模型名复制标识",
                ) {
                    if (sortedModels.isEmpty()) {
                        MochiListRow(
                            title = "还没有读取到模型",
                            subtitle = "启动服务后会自动拉取。",
                        )
                    } else {
                        sortedModels.forEach { model ->
                            MochiListRow(
                                title = model.name.ifBlank { model.id },
                                onClick = { onCopyModel(model.id) },
                                titleTrailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (model.isFree) {
                                            Tag("免费", tone = TagTone.Positive)
                                        } else if (model.rate.isNotBlank()) {
                                            Text(
                                                text = model.rate,
                                                style = MochiTheme.textStyles.labelSmall,
                                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        if (model.supportsImages) {
                                            Text(
                                                text = "图",
                                                style = MochiTheme.textStyles.labelSmall,
                                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    MochiListRow(
                        title = if (modelsBusy) "正在刷新目录" else "刷新目录",
                        onClick = if (modelsBusy) null else onRefreshModels,
                        chevron = !modelsBusy,
                        loading = modelsBusy,
                    )
                }
            }
        }

        if (showPortDialog) {
            PortDialog(
                current = portText,
                onDismiss = { showPortDialog = false },
                onSave = { value ->
                    portText = value
                    showPortDialog = false
                },
            )
        }
    }
}

private val OverlayModeLabels = persistentListOf("可拖动", "锁定", "完全穿透")

/** 接入信息里的值：等宽、右对齐，长地址单行省略。 */
@Composable
private fun MonoValue(text: String) {
    Text(
        text = text,
        style = MonoSmall.copy(textAlign = TextAlign.End),
        color = MochiTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 端口编辑：只接受数字，保存后作为下一次启动的端口。 */
@Composable
private fun PortDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..65535

    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "网关端口",
        message = "可选 1–65535；修改在下次启动服务时生效。",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "保存",
                role = MochiDialogActionRole.Primary,
                enabled = valid,
                onClick = { parsed?.let { onSave(it.toString()) } },
            ),
        ),
    ) {
        MochiDialogTextField(
            value = text,
            onValueChange = { input -> text = input.filter(Char::isDigit).take(5) },
            placeholder = "8765",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// -----------------------------------------------------------------------------
// 调用记录
// -----------------------------------------------------------------------------

@Composable
fun CallsScreen(state: HubState, onClear: () -> Unit, onRefresh: () -> Unit) {
    // 每批 200 条，滚动到底继续追加，避免一次组合全部历史记录。
    val pageSize = 200
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            kotlinx.coroutines.delay(3000L)
        }
    }

    val summary = remember(state.calls) { UsageSummary.of(state.calls) }
    var visibleCount by remember(state.calls.size) {
        mutableIntStateOf(minOf(pageSize, state.calls.size))
    }
    val visible = remember(state.calls, visibleCount) {
        state.calls.asReversed().take(visibleCount)
    }
    val listColors = MochiListDefaults.colors()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
    ) {
        item(key = "summary") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "调用记录",
                        style = MochiTheme.textStyles.displayMedium,
                        color = MochiTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.calls.isNotEmpty()) {
                        TextAction(text = "清空", onClick = onClear, danger = true)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "共 ${state.calls.size} 次，按时间倒序",
                    style = MochiTheme.textStyles.bodySmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                MochiListSection {
                    MochiListRow(title = "成功", value = "${summary.calls}")
                    MochiListRow(title = "失败", value = "${summary.failures}")
                    MochiListRow(title = "积分", value = formatCredits(summary.credits))
                    MochiListRow(title = "输入", value = formatTokenCount(summary.promptTokens.toLong()))
                    MochiListRow(title = "输出", value = formatTokenCount(summary.completionTokens.toLong()))
                    MochiListRow(title = "合计", value = formatTokenCount(summary.totalTokens.toLong()))
                }
            }
        }

        if (state.calls.isEmpty()) {
            item(key = "empty") {
                MochiListSection(modifier = Modifier.padding(top = MochiListDefaults.SectionSpacing)) {
                    MochiListRow(
                        title = "还没有记录",
                        subtitle = "客户端通过网关发起请求后，每一次调用都会记在这里。",
                    )
                }
            }
        } else {
            item(key = "records-header") {
                MochiListSectionHeader(
                    text = "明细",
                    modifier = Modifier.padding(top = MochiListDefaults.SectionSpacing),
                )
            }
            itemsIndexed(
                items = visible,
                // 记录只会在尾部追加；用绝对位置做 key，新增记录时已有行的 key 保持不变。
                key = { index, _ -> "call-${state.calls.lastIndex - index}" },
            ) { index, record ->
                // 所有记录共享一张连续卡片：首行圆上角、末行圆下角，中间行保持直角。
                val absoluteIndex = state.calls.lastIndex - index
                FusedCallLine(
                    record = record,
                    first = index == 0,
                    last = absoluteIndex == 0,
                    colors = listColors,
                )
            }
            if (visibleCount < state.calls.size) {
                item(key = "more") {
                    LaunchedEffect(visibleCount) {
                        visibleCount = (visibleCount + pageSize).coerceAtMost(state.calls.size)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = MochiListDefaults.SectionCorner,
                                    bottomEnd = MochiListDefaults.SectionCorner,
                                ),
                            )
                            .background(listColors.container),
                    ) {
                        // 上一行已经画过底部分隔线，这里不再重复画，避免两条线叠成 2dp。
                        Text(
                            text = "继续加载…",
                            style = MochiTheme.textStyles.labelSmall,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 融合明细行：所有记录在同一张卡片里，只有首行和真正的末行带圆角。
 * 每行仍是独立的 MochiListRow，分隔线由这里补，避免每行一张卡片。
 */
@Composable
private fun FusedCallLine(
    record: CallRecord,
    first: Boolean,
    last: Boolean,
    colors: MochiListColors,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) MochiListDefaults.SectionCorner else 0.dp,
        topEnd = if (first) MochiListDefaults.SectionCorner else 0.dp,
        bottomStart = if (last) MochiListDefaults.SectionCorner else 0.dp,
        bottomEnd = if (last) MochiListDefaults.SectionCorner else 0.dp,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.container),
    ) {
        CallLine(record)
        if (!last) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = MochiListDefaults.TextRowPadding)
                    .height(1.dp)
                    .background(MochiTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun CallLine(record: CallRecord) {
    val failed = record.outcome == CallRecord.Outcome.FAILED
    MochiListRow(
        title = record.model.ifEmpty { "未知模型" },
        subtitle = if (failed) {
            record.detail
        } else {
            "${record.promptTokens} 入 · ${record.completionTokens} 出 · ${formatCredits(record.credits)} 积分"
        },
        value = record.timeText,
        status = if (failed) {
            {
                MochiListStatusChip(
                    text = "失败",
                    containerColor = MochiTheme.colorScheme.errorContainer,
                    contentColor = MochiTheme.colorScheme.onErrorContainer,
                )
            }
        } else {
            null
        },
    )
}

// -----------------------------------------------------------------------------
// 对话框
// -----------------------------------------------------------------------------

@Composable
fun CheckinDialog(
    message: String,
    items: List<CheckinItem>,
    onDismiss: () -> Unit,
) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = if (items.isEmpty()) "签到结果" else "批量签到结果",
        message = if (items.isEmpty()) message else null,
        actions = persistentListOf(
            MochiDialogAction(label = "好", role = MochiDialogActionRole.Primary),
        ),
    ) {
        if (items.isNotEmpty()) {
            Text(
                text = message,
                style = MochiTheme.textStyles.bodyMedium,
                color = MochiTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { entry ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (entry.ok) {
                                        Claude.Moss
                                    } else {
                                        MochiTheme.colorScheme.error
                                    },
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = entry.label,
                                style = MochiTheme.textStyles.bodyMedium,
                                color = MochiTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = entry.message,
                                style = MochiTheme.textStyles.labelSmall,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 粘贴导入：接受备份包、账号数组或单个凭证对象。 */
@Composable
fun PasteImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "粘贴凭证",
        message = "粘贴从「凭证详情」复制的 JSON，或整段备份内容。",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "导入",
                role = MochiDialogActionRole.Primary,
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text) },
            ),
        ),
    ) {
        MochiTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "{\"accessToken\":\"...\"}",
            singleLine = false,
            maxLines = 10,
        )
    }
}

@Composable
fun HelpDialog(state: HubState, onDismiss: () -> Unit, onCopyEndpoint: () -> Unit) {
    val endpoint = "http://127.0.0.1:${state.port}/v1"
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "接入方式",
        message = "网关提供 OpenAI 兼容接口，任何支持自定义 baseURL 的客户端都能接入。",
        actions = persistentListOf(
            MochiDialogAction(label = "关闭", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "复制地址",
                role = MochiDialogActionRole.Primary,
                onClick = onCopyEndpoint,
            ),
        ),
    ) {
        MochiListSection {
            MochiListRow(
                title = "地址",
                subtitle = endpoint,
                onClick = onCopyEndpoint,
            )
            MochiListRow(
                title = "密钥",
                subtitle = state.secret,
            )
        }
        Text(
            text = "在客户端的「自定义 OpenAI 接口」里填入上面两项即可。模型名可以在服务页的模型目录里点按复制。",
            style = MochiTheme.textStyles.bodySmall,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "倍率为 0 的模型不消耗积分。",
            style = MochiTheme.textStyles.bodySmall,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "退出登录",
        message = "将清除国内版与国际版已保存的全部凭证，需要重新登录才能使用。",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "退出",
                role = MochiDialogActionRole.Destructive,
                onClick = onConfirm,
            ),
        ),
    )
}

/** 版本显示名。 */
internal fun realmName(region: Wire.Region): String =
    if (region == Wire.Region.GLOBAL) "国际版" else "国内版"
