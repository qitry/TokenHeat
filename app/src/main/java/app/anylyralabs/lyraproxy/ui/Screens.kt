package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
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
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onImportFromText: () -> Unit = {},
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
    ) {
        // 供应商选择：WorkBuddy 与 Qoder 是两套完全独立的账号体系，
        // 不能混在同一个「版本」切换里，所以先选供应商，再展示对应内容。
        item(key = "provider") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GatewayProvider.entries.forEach { item ->
                        SegmentTab(
                            label = item.label,
                            selected = state.provider == item,
                            onClick = { onSwitchProvider(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
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

            // 版本切换
            item(key = "realm") {
                Column(Modifier.padding(top = 26.dp)) {
                    MochiListSection(header = "版本") {
                        Wire.Region.entries.forEach { item ->
                            val count = state.accounts[item].orEmpty().size
                            MochiListRow(
                                title = realmName(item),
                                subtitle = if (count > 0) "$count 个账号" else null,
                                checked = region == item,
                                onClick = { onSwitchRealm(item) },
                            )
                        }
                    }
                }
            }

            // 账号列表
            if (saved.isNotEmpty()) {
                item(key = "accounts") {
                    Column(Modifier.padding(top = 26.dp)) {
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
            }

            // 登录与迁移
            item(key = "actions") {
                Column(Modifier.padding(top = 26.dp)) {
                    MochiListSection {
                        MochiListRow(
                            title = if (active == null) "登录 $regionLabel" else "添加 $regionLabel 账号",
                            onClick = { onLogin(region) },
                            chevron = true,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    MochiListSection(
                        header = "账号迁移",
                        footer = "导出为 .json.gz 备份，或在换机后粘贴凭证直接恢复。导入按账号合并，重复导入只刷新。",
                    ) {
                        MochiListButton("导出", onExportBackup)
                        MochiListButton("导入文件", onImportBackup)
                        MochiListButton("粘贴凭证导入", onImportFromText)
                    }
                }
            }

            // 说明
            item(key = "note") {
                Text(
                    text = "登录走官方 CLI 的 OAuth 流程，需要在本机浏览器完成授权。国内版与国际版是两套独立账号体系，各自可添加多个账号并在其间切换。",
                    style = MochiTheme.textStyles.bodySmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

/** 分段切换：两个互斥选项，用底色区分选中，不用描边。 */
@Composable
private fun SegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MochiButton(
        onClick = onClick,
        modifier = modifier,
        style = if (selected) MochiButtonStyle.Filled else MochiButtonStyle.Tonal,
        minHeight = 38.dp,
    ) {
        Text(
            text = label,
            style = MochiTheme.textStyles.labelLarge,
            maxLines = 1,
        )
    }
}

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
    // 模型目录跟随供应商：切到 Qoder 时展示 Qoder 的目录，避免两套模型混在一起。
    val qoderMode = state.provider == GatewayProvider.QODER
    val shownModels = if (qoderMode) state.qoderModels else state.models
    val modelsBusy = if (qoderMode) state.qoderBusy else state.loading == Loading.MODELS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
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
                    NoticeLine(
                        text = "通知权限未开启，系统可能回收后台服务",
                        action = "开启",
                        onClick = onRequestNotifications,
                        danger = true,
                    )
                }
                if (!state.batteryExempt) {
                    NoticeLine(
                        text = "电池优化未关闭",
                        action = "去设置",
                        onClick = onRequestBatteryExemption,
                    )
                }
            }
        }

        // 端口与接入信息
        item(key = "endpoint") {
            Column(Modifier.padding(top = 26.dp)) {
                MochiListSection(header = "接入信息") {
                    MochiListTextFieldRow(
                        value = portText,
                        onValueChange = { input -> portText = input.filter(Char::isDigit) },
                        label = "端口",
                        placeholder = "8765",
                        singleLine = true,
                    )
                    MochiListRow(
                        title = "地址",
                        subtitle = "http://127.0.0.1:${state.port}/v1",
                        onClick = onCopyEndpoint,
                    )
                    MochiListRow(
                        title = "密钥",
                        subtitle = state.secret,
                        onClick = onCopyEndpoint,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MochiListSection {
                    MochiListButton("复制地址", onCopyEndpoint)
                    MochiListButton("接入帮助", onShowHelp)
                }
            }
        }

        // 悬浮窗
        item(key = "overlay") {
            Column(Modifier.padding(top = 26.dp)) {
                MochiListSection(
                    header = "保活悬浮窗",
                    footer = "切到后台时显示状态，让系统保持本应用可见",
                ) {
                    MochiListRow(
                        title = "显示悬浮窗",
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
                        NoticeLine(
                            text = "未授予「显示在其他应用上层」权限",
                            action = "授权",
                            onClick = onRequestOverlay,
                            danger = true,
                        )
                    }
                }

                if (state.overlayEnabled && state.overlayAllowed) {
                    Spacer(Modifier.height(20.dp))
                    MochiListSection(
                        header = "不透明度 ${(state.overlayOpacity * 100).toInt()}%",
                    ) {
                        Box(
                            modifier = Modifier
                                .mochiListDivider(22.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            MochiSlider(
                                value = state.overlayOpacity,
                                onValueChange = onOverlayOpacity,
                                valueRange = 0.15f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    MochiListSection(header = "交互方式") {
                        val passthrough = state.overlayPassthrough
                        OverlayMode(
                            title = "可拖动",
                            hint = "能移动位置，点一下折叠",
                            selected = !passthrough && state.overlayMovable && !state.overlayLocked,
                            onSelect = {
                                onOverlayPassthrough(false)
                                onOverlayMovable(true)
                                onOverlayLocked(false)
                            },
                        )
                        OverlayMode(
                            title = "锁定",
                            hint = "能点击折叠，不能拖动",
                            selected = !passthrough && state.overlayLocked,
                            onSelect = {
                                onOverlayPassthrough(false)
                                onOverlayLocked(true)
                                onOverlayMovable(false)
                            },
                        )
                        OverlayMode(
                            title = "完全穿透",
                            hint = "不响应任何触摸，事件交给下层应用",
                            selected = passthrough,
                            onSelect = { onOverlayPassthrough(true) },
                        )
                    }
                }
            }
        }

        // 后台保活
        item(key = "keepalive") {
            MochiListSection(
                header = "后台保活",
                footer = KeepAliveUtils.romHint(),
            ) {
                MochiListRow(
                    title = if (state.batteryExempt) "已忽略电池优化" else "忽略电池优化",
                    subtitle = if (state.batteryExempt) {
                        "已忽略电池优化，后台限制较少"
                    } else {
                        "未忽略电池优化，系统可能限制或清理进程"
                    },
                    onClick = onRequestBatteryExemption,
                )
                MochiListRow(
                    title = if (state.notificationsAllowed) "通知已开启" else "开启通知",
                    onClick = onRequestNotifications,
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
                Column(Modifier.padding(top = 26.dp)) {
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
        }

        // 模型目录
        item(key = "models") {
            Column(Modifier.padding(top = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "可用模型",
                            style = MochiTheme.textStyles.titleLarge,
                            color = MochiTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "点模型名复制标识",
                            style = MochiTheme.textStyles.bodySmall,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextAction(
                        text = if (modelsBusy) "获取中" else "刷新",
                        onClick = onRefreshModels,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (modelsBusy && shownModels.isEmpty()) {
                    Text(
                        text = "正在读取模型目录",
                        style = MochiTheme.textStyles.labelSmall,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                } else {
                    ModelList(
                        models = shownModels,
                        onCopyModel = onCopyModel,
                    )
                }
            }
        }
    }
}

/** 保活悬浮窗的三种交互方式，单选。 */
@Composable
private fun OverlayMode(
    title: String,
    hint: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    MochiListRow(
        title = title,
        subtitle = hint,
        checked = selected,
        onClick = onSelect,
    )
}

/** 一行提示 + 行内动作。 */
@Composable
private fun NoticeLine(
    text: String,
    action: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = MochiTheme.colorScheme
    val contentColor = if (danger) colors.onErrorContainer else colors.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (danger) colors.errorContainer else colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MochiTheme.textStyles.bodySmall,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MochiTheme.textStyles.labelMedium,
            color = if (danger) colors.onErrorContainer else colors.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
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
                Column(Modifier.padding(top = 26.dp)) {
                    MochiListSection {
                        MochiListRow(
                            title = "还没有记录",
                            subtitle = "客户端通过网关发起请求后，每一次调用都会记在这里。",
                        )
                    }
                }
            }
        } else {
            item(key = "records") {
                Column(Modifier.padding(top = 26.dp)) {
                    MochiListSection(header = "明细", animateHeight = false) {
                        visible.forEach { record ->
                            CallLine(record)
                        }
                    }
                }
            }
            if (visibleCount < state.calls.size) {
                item(key = "more") {
                    LaunchedEffect(visibleCount) {
                        visibleCount = (visibleCount + pageSize).coerceAtMost(state.calls.size)
                    }
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
        titleTrailing = {
            if (failed) {
                Tag("失败", tone = TagTone.Danger)
            }
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
