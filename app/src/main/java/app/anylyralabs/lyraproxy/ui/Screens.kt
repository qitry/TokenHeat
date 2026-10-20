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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            Column {
                SectionLabel(regionLabel)
                Spacer(Modifier.height(8.dp))
                if (active == null) {
                    Text(
                        text = "$regionLabel 还没有登录",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = active.nickname.ifBlank { active.uid.take(10) },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "有效期至 ${state.expiryText.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("凭证详情", onOpenDetails, Modifier.weight(1f))
                    if (state.hasAnyCredential) {
                        QuietButton("退出登录", onLogout, Modifier.weight(1f), danger = true)
                    }
                }
            }
        }

        // 版本切换
        item(key = "realm") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionLabel("版本")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wire.Region.entries.forEach { item ->
                        val count = state.accounts[item].orEmpty().size
                        SegmentTab(
                            label = if (count > 0) "${realmName(item)} $count" else realmName(item),
                            selected = region == item,
                            onClick = { onSwitchRealm(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 账号列表
        if (saved.isNotEmpty()) {
            item(key = "accounts-head") {
                Column(Modifier.padding(top = 22.dp)) {
                    SectionLabel("$regionLabel 的账号 · ${saved.size}")
                }
            }
            items(saved, key = { it.id }) { account ->
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

        // 登录与迁移
        item(key = "actions") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (active == null) "登录 $regionLabel" else "添加 $regionLabel 账号",
                    onClick = { onLogin(region) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                SectionLabel("账号迁移")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "导出为 .json.gz 备份，或在换机后粘贴凭证直接恢复。导入按账号合并，重复导入只刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("导出", onExportBackup, Modifier.weight(1f))
                    QuietButton("导入文件", onImportBackup, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                QuietButton("粘贴凭证导入", onImportFromText, Modifier.fillMaxWidth())
            }
        }

        // 说明
        item(key = "note") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "登录走官方 CLI 的 OAuth 流程，需要在本机浏览器完成授权。国内版与国际版是两套独立账号体系，各自可添加多个账号并在其间切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uid.isNotBlank()) {
                    Text(
                        text = uid.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isActive) {
                Tag("使用中", tone = TagTone.Active)
                Spacer(Modifier.width(10.dp))
            }
            TextAction(text = "删除", onClick = onDelete, danger = true)
        }
    }
}

/** 凭证详情抽屉：全部字段可点按复制。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailDrawer(
    state: HubState,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val cred = state.credential
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (cred == null) {
            Text("尚无凭证", modifier = Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            item {
                SectionHeading(
                    title = "凭证详情",
                    subtitle = "点任意一行复制该字段",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Rule()
            }
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
            items(rows) { (label, value) ->
                FieldRow(
                    label = label,
                    value = value,
                    mono = label.contains("Token") || label == "UID",
                    onClick = { onCopy(label, value) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Token 等同于登录凭据，分享后对方可以你的身份调用接口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(active = state.bridgeRunning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.bridgeRunning) "运行中" else "已停止",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.bridgeRunning,
                        onCheckedChange = { checked ->
                            if (checked) onStart(portText.toIntOrNull() ?: 8765) else onStop()
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本机回环地址 127.0.0.1:${state.port}，仅接受本机请求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!state.notificationsAllowed) {
                    Spacer(Modifier.height(10.dp))
                    NoticeLine(
                        text = "通知权限未开启，系统可能回收后台服务",
                        action = "开启",
                        onClick = onRequestNotifications,
                        danger = true,
                    )
                }
                if (!state.batteryExempt) {
                    Spacer(Modifier.height(8.dp))
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
                Rule()
                SectionLabel("接入信息")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                FieldRow("地址", "http://127.0.0.1:${state.port}/v1", mono = true, onClick = onCopyEndpoint)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FieldRow("密钥", state.secret, mono = true, onClick = onCopyEndpoint)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("复制地址", onCopyEndpoint, Modifier.weight(1f))
                    QuietButton("接入帮助", onShowHelp, Modifier.weight(1f))
                }
            }
        }

        // 悬浮窗
        item(key = "overlay") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "保活悬浮窗",
                    subtitle = "切到后台时显示状态，让系统保持本应用可见",
                    trailing = {
                        Switch(
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
                    Spacer(Modifier.height(10.dp))
                    NoticeLine(
                        text = "未授予「显示在其他应用上层」权限",
                        action = "授权",
                        onClick = onRequestOverlay,
                        danger = true,
                    )
                }

                if (state.overlayEnabled && state.overlayAllowed) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "不透明度 ${(state.overlayOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = state.overlayOpacity,
                        onValueChange = onOverlayOpacity,
                        valueRange = 0.15f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(6.dp))
                    SectionLabel("交互方式")
                    Spacer(Modifier.height(4.dp))
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

        // 后台保活
        item(key = "keepalive") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "后台保活",
                    subtitle = if (state.batteryExempt) {
                        "已忽略电池优化，后台限制较少"
                    } else {
                        "未忽略电池优化，系统可能限制或清理进程"
                    },
                    
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton(
                        text = if (state.batteryExempt) "已豁免" else "忽略电池优化",
                        onClick = onRequestBatteryExemption,
                        modifier = Modifier.weight(1f),
                    )
                    QuietButton(
                        text = if (state.notificationsAllowed) "通知已开启" else "开启通知",
                        onClick = onRequestNotifications,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = KeepAliveUtils.romHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 自定义密钥
        item(key = "apikeys") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "自定义密钥",
                    subtitle = "为客户端分别签发，可限制模型与预算",
                    trailing = {
                        TextAction(text = "新建", onClick = onCreateApiKey)
                    },
                )
                Spacer(Modifier.height(12.dp))
                if (state.apiKeys.isEmpty()) {
                    Text(
                        text = "当前所有客户端共用上面那条共享密钥，无法区分来源，也无法单独限额。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.apiKeys.forEachIndexed { index, key ->
                        ApiKeyItem(
                            apiKey = key,
                            onCopy = { onCopyApiKey(key.key) },
                            onToggle = { enabled -> onToggleApiKeyEnabled(key, enabled) },
                            onEdit = { onEditApiKey(key) },
                            onDelete = { onDeleteApiKey(key) },
                            onClearUsage = { onClearApiKeyUsage(key) },
                        )
                        if (index < state.apiKeys.lastIndex) {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // 网关行为
        if (state.provider == GatewayProvider.QODER) {
            item(key = "gateway-behavior") {
                Column(Modifier.padding(top = 26.dp)) {
                    Rule()
                    SectionHeading(
                        title = "网关行为",
                        subtitle = "控制转发给客户端的字段",
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("转发思考过程", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "reasoning_content 不是 OpenAI 标准字段，部分客户端会解析失败；默认关闭",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.forwardReasoning,
                            onCheckedChange = onForwardReasoning,
                        )
                    }
                }
            }
        }

        // 模型目录
        item(key = "models-head") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "可用模型",
                    subtitle = "点模型名复制标识",
                    trailing = {
                        TextAction(
                            text = if (modelsBusy) "获取中" else "刷新",
                            onClick = onRefreshModels,
                        )
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        item(key = "models") {
            if (modelsBusy && shownModels.isEmpty()) {
                InlineLoading("正在读取模型目录")
            } else {
                ModelList(
                    models = shownModels,
                    onCopyModel = onCopyModel,
                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行提示 + 行内动作。 */
@Composable
private fun NoticeLine(
    text: String,
    action: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (danger) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (danger) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (danger) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
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
    // 每批 200 条，滚动到底继续追加；配合 LazyColumn 只组合可见项。
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
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.calls.isNotEmpty()) {
                        TextAction(text = "清空", onClick = onClear, danger = true)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "共 ${state.calls.size} 次，按时间倒序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    Readout("${summary.calls}", "成功", Modifier.weight(1f))
                    Readout("${summary.failures}", "失败", Modifier.weight(1f))
                    Readout(formatCredits(summary.credits), "积分", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Readout(formatTokenCount(summary.promptTokens.toLong()), "输入", Modifier.weight(1f))
                    Readout(formatTokenCount(summary.completionTokens.toLong()), "输出", Modifier.weight(1f))
                    Readout(formatTokenCount(summary.totalTokens.toLong()), "合计", Modifier.weight(1f))
                }
            }
        }

        if (state.calls.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(top = 26.dp)) {
                    Rule()
                    EmptyNote("还没有记录。客户端通过网关发起请求后，每一次调用都会记在这里。")
                }
            }
        } else {
            item(key = "head") {
                Column(Modifier.padding(top = 26.dp)) {
                    Rule()
                    SectionLabel("明细")
                }
            }
            items(visible, key = { "${it.timestamp}-${it.model}-${it.detail.hashCode()}" }) { record ->
                CallLine(record)
            }
            if (visibleCount < state.calls.size) {
                item(key = "more") {
                    LaunchedEffect(visibleCount) {
                        visibleCount = (visibleCount + pageSize).coerceAtMost(state.calls.size)
                    }
                    Text(
                        text = "继续加载…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLine(record: CallRecord) {
    val failed = record.outcome == CallRecord.Outcome.FAILED
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.padding(vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.model.ifEmpty { "未知模型" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (failed) {
                    Tag("失败", tone = TagTone.Danger)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = record.timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (failed) {
                    record.detail
                } else {
                    "${record.promptTokens} 入 · ${record.completionTokens} 出 · ${formatCredits(record.credits)} 积分"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.isEmpty()) "签到结果" else "批量签到结果") },
        text = {
            if (items.isEmpty()) {
                Text(message)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(message, style = MaterialTheme.typography.bodyMedium) }
                    items(items, key = { it.label }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (entry.ok) {
                                            Claude.Moss
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    entry.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
    )
}

/** 粘贴导入：接受备份包、账号数组或单个凭证对象。 */
@Composable
fun PasteImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴凭证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "粘贴从「凭证详情」复制的 JSON，或整段备份内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("内容") },
                    placeholder = { Text("{\"accessToken\":\"...\"}") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun HelpDialog(state: HubState, onDismiss: () -> Unit, onCopyEndpoint: () -> Unit) {
    val endpoint = "http://127.0.0.1:${state.port}/v1"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("接入方式") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        text = "网关提供 OpenAI 兼容接口，任何支持自定义 baseURL 的客户端都能接入。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldRow("地址", endpoint, mono = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        FieldRow("密钥", state.secret, mono = true)
                    }
                }
                item {
                    Text(
                        text = "在客户端的「自定义 OpenAI 接口」里填入上面两项即可。模型名可以在服务页的模型目录里点按复制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Text(
                        text = "倍率为 0 的模型不消耗积分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCopyEndpoint() }) { Text("复制地址") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("将清除国内版与国际版已保存的全部凭证，需要重新登录才能使用。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("退出", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 版本显示名。 */
internal fun realmName(region: Wire.Region): String =
    if (region == Wire.Region.GLOBAL) "国际版" else "国内版"
