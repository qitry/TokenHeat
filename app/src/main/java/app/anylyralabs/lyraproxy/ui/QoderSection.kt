package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anylyralabs.lyraproxy.qoder.QoderAccount
import app.anylyralabs.lyraproxy.qoder.QoderQuota
import app.anylyralabs.lyraproxy.qoder.QoderUsage

/**
 * Qoder 国际版账号区。
 *
 * 与 WorkBuddy 的账号区对等：账号列表、切换、删除、额度概览都在这里，
 * 不再保留「测试连接」这类调试入口——功能本身可用时，诊断按钮只会添乱。
 */
@Composable
fun QoderSection(
    account: QoderAccount?,
    accounts: List<QoderAccount>,
    quota: QoderUsage?,
    quotaError: String,
    busy: Boolean,
    status: String,
    onLogin: () -> Unit,
    onPatLogin: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onOpenDetail: () -> Unit = {},
    onRequestLogout: () -> Unit = {},
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Qoder 国际版",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (account != null) {
                Tag("已登录", tone = TagTone.Positive)
            }
        }
        Spacer(Modifier.height(6.dp))

        if (account == null) {
            Text(
                text = "走 Qoder 官方 CLI 的浏览器授权，或粘贴 Personal Access Token。登录后即可使用模型并接入网关。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = if (busy) "等待授权" else "浏览器登录",
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(
                    text = "粘贴 PAT",
                    onClick = onPatLogin,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                text = account.name.ifBlank { account.email.ifBlank { "Qoder 账号" } },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenDetail)
                    .padding(vertical = 2.dp),
            )
            if (account.email.isNotBlank()) {
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(if (account.source == "pat") "PAT" else "浏览器授权")
                    if (account.expiresAt > 0) {
                        append(" · 到期 ")
                        append(
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(account.expiresAt)),
                        )
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 额度概览：与首页同一套数据，这里给一个更紧凑的版本。
            Spacer(Modifier.height(12.dp))
            when {
                quotaError.isNotBlank() -> Text(
                    text = quotaError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                quota == null || !quota.hasAny -> Text(
                    text = "正在读取额度…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    quota.userQuota?.let { QuotaLine("用户额度", it) }
                    quota.addOnQuota?.let { QuotaLine("加赠额度", it) }
                    quota.orgQuota?.let { QuotaLine("组织资源包", it) }
                }
            }

            // 多账号：只有一个账号时不显示列表，避免页面像调试面板。
            if (accounts.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "账号",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                accounts.forEach { item ->
                    val isActive = item.id == account.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitchAccount(item.id) }
                            .padding(vertical = 7.dp),
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
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name.ifBlank { item.email.ifBlank { item.id } },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.email.isNotBlank()) {
                                Text(
                                    text = item.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isActive) {
                            Tag("使用中", tone = TagTone.Active)
                            Spacer(Modifier.width(8.dp))
                        }
                        TextAction(text = "删除", danger = true) { onDeleteAccount(item.id) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(
                    text = "添加账号",
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(
                    text = "凭证详情",
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            TextAction(text = "退出 Qoder 账号", onClick = onRequestLogout, danger = true)
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 紧凑额度行：剩余 / 总量，附一条细进度。 */
@Composable
private fun QuotaLine(label: String, quota: QoderQuota) {
    val unit = quota.unit
    val remaining = quota.remaining
    val total = quota.total
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    remaining != null && total != null -> "${trimNumber(remaining)} / ${trimNumber(total)} $unit"
                    remaining != null -> "剩余 ${trimNumber(remaining)} $unit"
                    total != null -> "共 ${trimNumber(total)} $unit"
                    else -> "未知"
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        quota.fraction?.let { fraction ->
            Spacer(Modifier.height(4.dp))
            ThinBar(fraction = fraction)
        }
    }
}

/**
 * Qoder 额度（首页使用）。
 *
 * 与账号区的紧凑版本不同，首页给完整的三类额度和到期时间。
 */
@Composable
fun QoderQuotaBlock(
    usage: QoderUsage?,
    error: String,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Qoder 额度",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextAction(
                text = if (busy) "刷新中" else "刷新",
                onClick = onRefresh,
            )
        }
        Spacer(Modifier.height(10.dp))

        when {
            error.isNotBlank() -> Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            usage == null -> Text(
                text = "点「刷新」读取额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !usage.hasAny -> Text(
                text = "当前账号没有返回额度信息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                usage.userQuota?.let { QuotaLine("用户额度", it) }
                usage.addOnQuota?.let { QuotaLine("加赠额度", it) }
                usage.orgQuota?.let { QuotaLine("组织资源包", it) }
                if (usage.expiresAt > 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "额度到期 " + java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(usage.expiresAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 去掉多余的小数，让 12.00 显示成 12。 */
private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)

/** 粘贴 Qoder Personal Access Token。 */
@Composable
fun QoderPatDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴 Qoder PAT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "在 qoder.com/account/integrations 生成令牌，形如 pt-...。令牌只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Personal Access Token") },
                    placeholder = { Text("pt-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text("登录")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 浏览器授权等待提示。 */
@Composable
fun QoderLoginDialog(
    authUrl: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在浏览器完成授权") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已打开浏览器。完成登录后这个窗口会自动关闭；若浏览器没有弹出，请手动打开下面的地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = authUrl,
                    style = MonoSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制链接") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}


/** Qoder 凭证详情：与 WorkBuddy 的抽屉对齐，所有字段可点按复制。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QoderDetailDrawer(
    account: QoderAccount?,
    onDismiss: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (account == null) {
            Text("尚无 Qoder 账号", modifier = Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        val rows = listOf(
            "账号" to account.name.ifBlank { "未命名" },
            "邮箱" to account.email.ifBlank { "无" },
            "UID" to account.id,
            "来源" to if (account.source == "pat") "Personal Access Token" else "浏览器授权",
            "有效期" to if (account.expiresAt > 0) {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(account.expiresAt))
            } else {
                "未知"
            },
            "机器标识" to account.machineId,
            "Access Token" to account.accessToken,
            "Refresh Token" to account.refreshToken.ifBlank { "无" },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            item {
                SectionHeading(
                    title = "Qoder 凭证",
                    subtitle = "点任意一行复制该字段",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Rule()
            }
            items(rows) { (label, value) ->
                FieldRow(
                    label = label,
                    value = value,
                    mono = label == "UID" || label.contains("Token") || label == "机器标识",
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

/** 退出 Qoder：清除全部已保存账号。 */
@Composable
fun QoderLogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出 Qoder") },
        text = { Text("将清除本机保存的全部 Qoder 账号，需要重新登录才能继续使用。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("退出", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
