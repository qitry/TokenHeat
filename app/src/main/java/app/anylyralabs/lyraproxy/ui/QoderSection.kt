package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anylyralabs.lyraproxy.qoder.QoderAccount
import app.anylyralabs.lyraproxy.qoder.QoderProbe
import app.anylyralabs.lyraproxy.qoder.QoderQuota
import app.anylyralabs.lyraproxy.qoder.QoderUsage

/**
 * Qoder 国际版账号区。
 *
 * 独立于 WorkBuddy 的区域选择：它是另一套账号体系，
 * 共用「国内版/国际版」切换只会让用户以为两者能互换。
 */
@Composable
fun QoderSection(
    account: QoderAccount?,
    modelCount: Int,
    busy: Boolean,
    status: String,
    onLogin: () -> Unit,
    onPatLogin: () -> Unit,
    onTest: () -> Unit,
    onRefreshModels: () -> Unit,
    onLogout: () -> Unit,
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
                text = "走 Qoder 官方 CLI 的浏览器授权，或粘贴 Personal Access Token。登录后可以读取模型目录并接入网关。",
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
                    append("来源 ").append(if (account.source == "pat") "PAT" else "浏览器授权")
                    append(" · 模型 ").append(modelCount).append(" 个")
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
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(
                    text = "测试连接",
                    onClick = onTest,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(
                    text = "刷新模型",
                    onClick = onRefreshModels,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextAction(text = "退出 Qoder", onClick = onLogout, danger = true)
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

/**
 * Qoder 额度。
 *
 * 与 WorkBuddy 的余额卡分开：这里的单位由上游给出，且分为用户额度、
 * 加赠额度与组织资源包三类，不强行折算成同一个数字。
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
                usage.userQuota?.let { QuotaRow("用户额度", it) }
                usage.addOnQuota?.let { QuotaRow("加赠额度", it) }
                usage.orgQuota?.let { QuotaRow("组织资源包", it) }
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

@Composable
private fun QuotaRow(label: String, quota: QoderQuota) {
    val unit = quota.unit
    val remaining = quota.remaining
    val total = quota.total
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    remaining != null && total != null -> "${trim(remaining)} / ${trim(total)} $unit"
                    remaining != null -> "剩余 ${trim(remaining)} $unit"
                    total != null -> "共 ${trim(total)} $unit"
                    else -> "未知"
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        quota.fraction?.let { fraction ->
            Spacer(Modifier.height(5.dp))
            ThinBar(fraction = fraction)
        }
    }
}

/** 去掉多余的小数，让 12.00 显示成 12。 */
private fun trim(value: Double): String =
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

/** 连通性测试结果：原样展示状态码与响应片段。 */
@Composable
fun QoderProbeDialog(
    probe: QoderProbe,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (probe.ok) "连接正常" else "连接失败") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(probe.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = probe.detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
