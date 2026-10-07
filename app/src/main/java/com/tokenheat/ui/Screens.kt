package com.tokenheat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokenheat.bridge.CallRecord
import com.tokenheat.bridge.UsageSummary
import com.tokenheat.data.CheckinItem
import com.tokenheat.data.QLogin
import com.tokenheat.proto.Credential
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.Wire
import java.util.Locale

// -----------------------------------------------------------------------------
// Credential Screen & Unified Provider Management
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialScreen(
    state: HubState,
    onSwitchRealm: (Wire.Region) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onLogin: (Wire.Region) -> Unit,
    onLogout: () -> Unit,
    onOpenDetails: () -> Unit,
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
) {
    val currentProvider = state.provider

    // Resolve accounts and selection based on provider
    val (savedAccounts, activeId) = when (currentProvider) {
        Provider.WORKBUDDY -> Pair(state.accounts[state.realm].orEmpty(), state.activeAccountId)
        Provider.ZCODE -> Pair(state.zcodeAccounts, state.zcodeActiveId)
        Provider.ZEN -> Pair(state.zenAccounts, state.zenActiveId)
        Provider.QODER_CN, Provider.QODER_GLOBAL, Provider.ANTIGRAVITY -> Pair(
            state.slotAccounts[currentProvider].orEmpty(),
            state.slotActiveId[currentProvider]
        )
    }

    val active = state.credential

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Horizontal Scrollable Provider Pill Selector
        item {
            ProviderSelectorRow(
                selected = currentProvider,
                state = state,
                onSelect = onSwitchProvider,
            )
        }

        // 2. Sub-realm selector for WorkBuddy
        if (currentProvider == Provider.WORKBUDDY) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wire.Region.entries.forEach { region ->
                        val count = state.accounts[region].orEmpty().size
                        val isSelected = state.realm == region
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSwitchRealm(region) },
                            label = {
                                Text(
                                    if (count > 0) "${realmName(region)} ($count)" else realmName(region),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        // 3. Active Credential Hero Card
        item {
            ActiveCredentialCard(
                provider = currentProvider,
                active = active,
                expiryText = state.expiryText,
                realm = state.realm,
                onClick = onOpenDetails,
            )
        }

        // 4. Saved Accounts Pool Section
        item {
            SectionHeader(
                title = "轮训账号池",
                subtitle = if (savedAccounts.isNotEmpty()) "共 ${savedAccounts.size} 个账号在列" else "暂无已保存账号",
            )
        }

        if (savedAccounts.isNotEmpty()) {
            items(savedAccounts, key = { it.id }) { account ->
                val isActive = account.id == activeId
                UnifiedAccountRow(
                    label = account.label,
                    uid = account.uid,
                    isActive = isActive,
                    enabled = !account.disabled,
                    onSelect = {
                        when (currentProvider) {
                            Provider.WORKBUDDY -> onSwitchAccount(account.id)
                            Provider.ZCODE -> onSwitchZcodeAccount(account.id)
                            Provider.ZEN -> onSwitchZenAccount(account.id)
                            Provider.QODER_CN, Provider.QODER_GLOBAL, Provider.ANTIGRAVITY ->
                                onSwitchSlotAccount(currentProvider, account.id)
                        }
                    },
                    onToggleEnabled = { onToggleAccount(account) },
                    onDelete = {
                        when (currentProvider) {
                            Provider.WORKBUDDY -> onDeleteAccount(account.id)
                            Provider.ZCODE -> onDeleteZcodeAccount(account.id)
                            Provider.ZEN -> onDeleteZenAccount(account.id)
                            Provider.QODER_CN, Provider.QODER_GLOBAL, Provider.ANTIGRAVITY ->
                                onDeleteSlotAccount(currentProvider, account.id)
                        }
                    },
                )
            }
        }

        // 5. Action Buttons (Login / Add Key / Logout)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when (currentProvider) {
                            Provider.WORKBUDDY -> onLogin(state.realm)
                            Provider.ZCODE -> onLoginZcode()
                            Provider.ZEN -> onShowZenKeyDialog()
                            Provider.QODER_CN -> onLoginQoder(QLogin.QRegion.CN)
                            Provider.QODER_GLOBAL -> onLoginQoder(QLogin.QRegion.GLOBAL)
                            Provider.ANTIGRAVITY -> onLoginAG()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    val btnText = when (currentProvider) {
                        Provider.WORKBUDDY -> if (active == null) "登录 ${realmName(state.realm)}" else "添加 ${realmName(state.realm)} 账号"
                        Provider.ZCODE -> if (active == null) "登录 ZCode" else "添加 ZCode 账号"
                        Provider.ZEN -> if (active == null) "添加 Zen API Key" else "添加新的 Key"
                        Provider.QODER_CN -> if (active == null) "登录 Qoder 国内版" else "添加 Qoder 国内版账号"
                        Provider.QODER_GLOBAL -> if (active == null) "登录 Qoder 国际版" else "添加 Qoder 国际版账号"
                        Provider.ANTIGRAVITY -> if (active == null) "登录 Antigravity" else "添加 Antigravity 账号"
                    }
                    Text(btnText, fontWeight = FontWeight.Medium)
                }

                if (state.hasAnyCredential) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            "清空全部已保存凭据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // 6. Subdued Note Card
        item {
            val noteText = when (currentProvider) {
                Provider.WORKBUDDY -> "走官方 CLI 的 OAuth 流程，登录后可获取接口调用的 API Token。国内版与国际版是两套独立账号体系，各自支持多账号参与轮训。"
                Provider.ZCODE -> "走 ZCode 官方 CLI 的 OAuth 流程，登录后自动兑换长期有效的 API Key，调用走 api.z.ai 标准 OpenAI 接口，支持多账号轮训。"
                Provider.ZEN -> "在 opencode.ai 控制台复制 Zen API Key 粘贴即可（免费模型调用 $0，按量模型扣除余额）。Key 仅存储于本地，支持多 Key 轮训。"
                Provider.QODER_CN, Provider.QODER_GLOBAL -> "走 Qoder 官方设备码授权流程，浏览器确认后自动完成登录。支持添加多账号参与轮训与用量互补。"
                Provider.ANTIGRAVITY -> "走 Google 官方 OAuth 授权（与 Antigravity 插件同款流程），浏览器授权后自动回调回填。支持多账号轮训。"
            }

            ShadcnCard(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }

    if (state.showZenKeyDialog) {
        ZenKeyDialog(
            onDismiss = onDismissZenKeyDialog,
            onConfirm = onConfirmZenKey,
        )
    }
}

@Composable
private fun ProviderSelectorRow(
    selected: Provider,
    state: HubState,
    onSelect: (Provider) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Provider.entries.forEach { provider ->
            val count = when (provider) {
                Provider.WORKBUDDY -> state.accounts.values.sumOf { it.size }
                Provider.ZCODE -> state.zcodeAccounts.size
                Provider.ZEN -> state.zenAccounts.size
                Provider.QODER_CN, Provider.QODER_GLOBAL, Provider.ANTIGRAVITY ->
                    state.slotAccounts[provider]?.size ?: 0
            }
            val isSelected = selected == provider
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(provider) },
                label = {
                    Text(
                        if (count > 0) "${provider.label} ($count)" else provider.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun ActiveCredentialCard(
    provider: Provider,
    active: Credential?,
    expiryText: String,
    realm: Wire.Region,
    onClick: () -> Unit,
) {
    ShadcnCard(onClick = onClick) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(active = active != null)
                Spacer(Modifier.width(8.dp))
                val title = if (provider == Provider.WORKBUDDY) {
                    "当前凭证 · ${realmName(realm)}"
                } else {
                    "当前凭证 · ${provider.label}"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (active != null) {
                    PillBadge("活跃", variant = BadgeVariant.Success)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "查看详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (active == null) {
                Text(
                    text = "当前平台尚未关联任何有效账号，请点击下方按钮登录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "主体标识",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = active.nickname.ifBlank { active.uid.take(16).ifBlank { "已连接" } },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "有效期限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (provider == Provider.ZCODE || provider == Provider.ZEN) {
                                "长期有效"
                            } else {
                                expiryText.ifBlank { "已生效" }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "点击此卡片可查看完整 API Key / Token 与调试信息",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedAccountRow(
    label: String,
    uid: String,
    isActive: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    ShadcnCard(
        onClick = onSelect,
        border = if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(
                active = enabled,
                activeColor = if (isActive) ZincColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                inactiveColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        PillBadge("当前主号", variant = BadgeVariant.Success)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (uid.isNotBlank()) {
                        "${uid.take(12)} · ${if (enabled) "参与轮训" else "已暂停轮训"}"
                    } else {
                        if (enabled) "参与轮训" else "已暂停轮训"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleEnabled, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (enabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (enabled) "暂停轮训" else "恢复轮训",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除账号",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bridge Screen & Dashboard
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
) {
    var portText by remember(state.port) { mutableStateOf(state.port.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Service Dashboard Header
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(active = state.bridgeRunning)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "本地 API 服务",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (state.bridgeRunning) "运行中 · 127.0.0.1:${state.port}" else "已停止服务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.bridgeRunning,
                            onCheckedChange = { checked ->
                                if (checked) onStart(portText.toIntOrNull() ?: 8765) else onStop()
                            },
                        )
                    }

                    if (state.bridgeRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    // Port configuration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit) },
                            label = { Text("服务监听端口", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        if (state.bridgeRunning) {
                            OutlinedButton(
                                onClick = onCopyEndpoint,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text("复制地址")
                            }
                        }
                    }

                    // Endpoint & Key preview
                    CodeField(
                        label = "接口基础地址 (Base URL)",
                        value = "http://127.0.0.1:${state.port}/v1",
                        onCopy = onCopyEndpoint,
                    )
                    CodeField(
                        label = "认证密钥 (API Key)",
                        value = state.secret,
                        onCopy = { onCopyModel(state.secret) },
                    )
                }
            }
        }

        // 2. Battery & Notification Warning Banners
        if (!state.batteryExempt || !state.notificationsAllowed) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.batteryExempt) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, ZincColors.Warning.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                .clickable(onClick = onRequestBatteryExemption),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = ZincColors.Warning,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "建议关闭系统电池优化，避免应用在后台休眠时中断服务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "去设置",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (!state.notificationsAllowed) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, ZincColors.Danger.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                .clickable(onClick = onRequestNotifications),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = ZincColors.Danger,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "前台服务通知权限未开启，可能导致系统后台回收",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "开启",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Keep-Alive Overlay Settings Card
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "悬浮窗保活（防系统冻结）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        PillBadge(
                            text = if (state.overlayAllowed) "已启用" else "未启用",
                            variant = if (state.overlayAllowed) BadgeVariant.Success else BadgeVariant.Neutral,
                        )
                    }
                    Text(
                        text = "通过屏幕边缘常驻微型状态面板，使应用维持可见前台级别，防止系统进程深度休眠。无需 root。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!state.overlayAllowed) {
                        Button(
                            onClick = onRequestOverlay,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text("授予悬浮窗权限")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "面板不透明度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${(state.overlayOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Slider(
                            value = state.overlayOpacity,
                            onValueChange = onOverlayOpacity,
                            valueRange = 0.15f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("锁定面板位置", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "开启后不可随意拖拽，防止误触",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.overlayLocked,
                                onCheckedChange = onOverlayLocked,
                            )
                        }
                    }
                }
            }
        }

        // 4. Client Integration Guide Shortcut
        item {
            ShadcnCard(onClick = onShowHelp) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "如何接入客户端？",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "查看 NextChat、OpenCat、Cherry Studio 等接入示例与配置文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 5. Model Catalog Section Header & Refresh
        item {
            SectionHeader(
                title = "可用模型目录",
                subtitle = "点击任意模型可一键复制模型标识",
                action = {
                    OutlinedButton(
                        onClick = onRefreshModels,
                        enabled = state.loading != Loading.MODELS,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        if (state.loading == Loading.MODELS) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("获取中", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("刷新", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
        }

        // 6. Model List with Search & Filtering
        item {
            ModelList(
                models = state.models,
                onCopyModel = onCopyModel,
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Rewards & Quota Screens
// -----------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RewardsScreen(
    state: HubState,
    onCheckin: () -> Unit,
    onCheckinAll: () -> Unit,
    onRefreshBalance: () -> Unit,
) {
    if (state.provider == Provider.ZCODE) {
        QuotaRewardsScreen(
            state = state,
            quota = state.quotas[Provider.ZCODE].orEmpty(),
            title = "Coding Plan 订阅额度",
            note = "说明：ZCode 配额源自 Coding Plan 订阅，按订阅周期重置。用尽后可添加新账号轮训。",
            onRefreshBalance = onRefreshBalance,
        )
        return
    }
    if (state.provider == Provider.ZEN) {
        QuotaRewardsScreen(
            state = state,
            quota = state.quotas[Provider.ZEN].orEmpty(),
            title = "Zen 账单与配额",
            note = "说明：Zen 按量计费，所有标记免费模型为 $0；具体余额与调用账单以 opencode.ai 网页控制台为准。",
            onRefreshBalance = onRefreshBalance,
        )
        return
    }
    if (state.provider == Provider.QODER_CN || state.provider == Provider.QODER_GLOBAL) {
        QuotaRewardsScreen(
            state = state,
            quota = state.quotas[state.provider].orEmpty(),
            title = "Qoder 订阅与配额",
            note = "说明：Qoder 剩余配额源自订阅周期，用尽后可添加多账号参与轮训与调用互备。",
            onRefreshBalance = onRefreshBalance,
        )
        return
    }
    if (state.provider == Provider.ANTIGRAVITY) {
        QuotaRewardsScreen(
            state = state,
            quota = state.quotas[state.provider].orEmpty(),
            title = "Antigravity 账号配额",
            note = "说明：Antigravity 按 Google 账号模型配额计费，建议添加多账号降低触发 429 速率限制的概率。",
            onRefreshBalance = onRefreshBalance,
        )
        return
    }

    // WorkBuddy Rewards & Balance
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Daily Checkin Card
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = "每日签到领积分",
                        subtitle = "点击签到当前账号，长按可对该版本下全部账号批量签到",
                    )
                    Button(
                        onClick = onCheckin,
                        enabled = state.loading != Loading.CHECKIN,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = onCheckin,
                                onLongClick = onCheckinAll,
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (state.loading == Loading.CHECKIN) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("签到进行中…")
                        } else {
                            Text("立即签到 (长按全部)", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Balance Card
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "账户余额",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = onRefreshBalance,
                            enabled = state.loading != Loading.BALANCE,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            if (state.loading == Loading.BALANCE) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("刷新中", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("刷新", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    val balance = state.balance
                    when {
                        state.loading == Loading.BALANCE && balance == null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        balance == null -> {
                            Text(
                                "点击“刷新”查询可用积分余额",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        balance.accounts.isEmpty() -> {
                            Text(
                                "当前账号未查询到套餐积分数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "可用积分合计",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = balance.total.toLong().toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                balance.accounts.forEach { acc ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = acc.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = "${acc.remain.toLong()} / ${acc.size.toLong()}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (balance.cycleEnd.isNotBlank()) {
                                Text(
                                    text = "结算周期截止：${balance.cycleEnd}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaRewardsScreen(
    state: HubState,
    quota: String,
    title: String,
    note: String,
    onRefreshBalance: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = onRefreshBalance,
                            enabled = state.loading != Loading.BALANCE,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            if (state.loading == Loading.BALANCE) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("刷新中", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("刷新", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (state.loading == Loading.BALANCE && quota.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (quota.isBlank()) {
                        Text(
                            text = "点击“刷新”拉取额度信息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = quota,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            ShadcnCard(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Calls Screen & Usage Statistics
// -----------------------------------------------------------------------------

@Composable
fun CallsScreen(state: HubState, onClear: () -> Unit, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            kotlinx.coroutines.delay(2000L)
        }
    }

    val summary = UsageSummary.of(state.calls)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Metrics Grid Card
        item {
            ShadcnCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "网关用量概览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.calls.isNotEmpty()) {
                            TextButton(
                                onClick = onClear,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "清空记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    // Stat Grid (2x2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            label = "请求数 (成功 / 失败)",
                            value = "${summary.calls} / ${summary.failures}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "消耗积分",
                            value = formatCredits(summary.credits),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            label = "输入 Tokens",
                            value = summary.promptTokens.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "输出 Tokens",
                            value = summary.completionTokens.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 2. Call Stream List
        if (state.calls.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无调用日志。当客户端通过本地代理发起请求后，记录将实时显示在此处。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item {
                SectionHeader(
                    title = "调用流水历史",
                    subtitle = "最近 ${state.calls.size} 次请求（倒序）",
                )
            }

            items(state.calls.reversed(), key = { it.timestamp.toString() + it.model }) { record ->
                UnifiedCallRow(record)
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun UnifiedCallRow(record: CallRecord) {
    val isFailed = record.outcome == CallRecord.Outcome.FAILED
    ShadcnCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillBadge(
                    text = if (isFailed) "FAIL" else "200 OK",
                    variant = if (isFailed) BadgeVariant.Danger else BadgeVariant.Success,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = record.model.ifEmpty { "(未知模型)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (record.accountLabel.isNotBlank()) {
                Text(
                    text = "服务账号：${record.accountLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isFailed) {
                Text(
                    text = "异常：${record.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${record.promptTokens} in / ${record.completionTokens} out",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "积分: ${formatCredits(record.credits)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun formatCredits(value: Double): String {
    if (value == 0.0) return "0"
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

// -----------------------------------------------------------------------------
// Dialogs & Drawers
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailDrawer(
    state: HubState,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val cred = state.credential
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        if (cred == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无活跃凭据", style = MaterialTheme.typography.bodyMedium)
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "凭证详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            when (cred.provider) {
                Provider.ZEN -> {
                    item { DetailItem("账号体系", "Zen", onCopy) }
                    item { DetailItem("备注/昵称", cred.nickname.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("来源", cred.source, onCopy) }
                    item { DetailItem("有效期", "长期有效（API Key）", onCopy) }
                    item { DetailItem("API Key", cred.apiKey.ifBlank { "(无)" }, onCopy) }
                }
                Provider.ZCODE -> {
                    item { DetailItem("账号体系", "ZCode", onCopy) }
                    item { DetailItem("备注/昵称", cred.nickname.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("UID", cred.uid.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("来源", cred.source, onCopy) }
                    item { DetailItem("有效期", "长期有效（API Key）", onCopy) }
                    item { DetailItem("API Key", cred.apiKey.ifBlank { "(无)" }, onCopy) }
                }
                Provider.QODER_CN, Provider.QODER_GLOBAL -> {
                    item { DetailItem("账号体系", cred.provider.label, onCopy) }
                    item { DetailItem("昵称", cred.nickname.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("UID", cred.uid.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("有效期", state.expiryText.ifBlank { "未知" }, onCopy) }
                    item { DetailItem("Access Token", cred.accessToken.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("Refresh Token", cred.refreshToken.ifBlank { "(无)" }, onCopy) }
                }
                Provider.ANTIGRAVITY -> {
                    item { DetailItem("账号体系", "Antigravity", onCopy) }
                    item { DetailItem("邮箱/UID", cred.nickname.ifBlank { cred.uid.ifBlank { "(无)" } }, onCopy) }
                    item { DetailItem("Cloud 项目", cred.projectId.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("有效期", state.expiryText.ifBlank { "未知" }, onCopy) }
                    item { DetailItem("Access Token", cred.accessToken.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("Refresh Token", cred.refreshToken.ifBlank { "(无)" }, onCopy) }
                }
                else -> {
                    item { DetailItem("版本", realmName(Wire.regionOf(cred.domain)), onCopy) }
                    item { DetailItem("昵称", cred.nickname.ifBlank { "(无)" }, onCopy) }
                    item { DetailItem("UID", cred.uid, onCopy) }
                    item { DetailItem("Domain", cred.domain, onCopy) }
                    item { DetailItem("来源", cred.source, onCopy) }
                    item { DetailItem("有效期", state.expiryText.ifBlank { "未知" }, onCopy) }
                    item { DetailItem("Access Token", cred.accessToken, onCopy) }
                    item { DetailItem("Refresh Token", cred.refreshToken.ifBlank { "(无)" }, onCopy) }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "提示：点击各字段即可复制到剪贴板。敏感凭证请妥善保管，勿随意分享。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, onCopy: (String, String) -> Unit) {
    ShadcnCard(onClick = { onCopy(label, value) }) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "复制",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun HelpDialog(state: HubState, onDismiss: () -> Unit, onCopyEndpoint: () -> Unit) {
    val endpoint = "http://127.0.0.1:${state.port}/v1"
    val snippet = """
        {
          "providers": {
            "tokenheat": {
              "baseUrl": "$endpoint",
              "api": "openai-completions",
              "apiKey": "${state.secret}"
            }
          }
        }
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("客户端接入指引", fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        "TokenHeat 提供标准 OpenAI 兼容接口，任何支持自定义 API Host 的客户端均可直接接入。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    CodeField(label = "Base URL", value = endpoint, onCopy = onCopyEndpoint)
                }
                item {
                    CodeField(label = "API Key", value = state.secret, onCopy = onCopyEndpoint)
                }
                item {
                    Text("配置文件示例", style = MaterialTheme.typography.labelSmall)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCopyEndpoint,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("复制地址") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
fun ZenKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (key: String, label: String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Zen API Key", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "在 opencode.ai 控制台复制 Key 后粘贴至下方。Key 仅保存在本机私有存储中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备注标签（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(key, label) },
                enabled = key.isNotBlank(),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun CheckinDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("签到结果", fontWeight = FontWeight.SemiBold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("确定") }
        },
    )
}

@Composable
fun CheckinDialog(
    message: String,
    items: List<CheckinItem>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.isEmpty()) "签到结果" else "批量签到执行明细", fontWeight = FontWeight.SemiBold) },
        text = {
            if (items.isEmpty()) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(message, style = MaterialTheme.typography.bodyMedium) }
                    items(items, key = { it.label }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (entry.ok) Icons.Outlined.Check else Icons.Outlined.Close,
                                contentDescription = null,
                                tint = if (entry.ok) ZincColors.Success else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    entry.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("完成") }
        },
    )
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录", fontWeight = FontWeight.SemiBold) },
        text = { Text("确认清空所有已保存的平台凭证与轮训配置？此操作无法撤销。", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("确认清空") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

internal fun realmName(region: Wire.Region): String =
    if (region == Wire.Region.GLOBAL) "国际版" else "国内版"
