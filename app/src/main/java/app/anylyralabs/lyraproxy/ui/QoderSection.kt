package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme
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
    MochiListSection(
        header = "Qoder 国际版",
        footer = status.takeIf { it.isNotBlank() },
    ) {
        if (account == null) {
            MochiListRow(
                title = "未登录",
                subtitle = "走 Qoder 官方 CLI 的浏览器授权，或粘贴 Personal Access Token。登录后即可使用模型并接入网关。",
            )
            MochiListRow(
                title = if (busy) "等待授权" else "浏览器登录",
                onClick = onLogin,
                enabled = !busy,
                chevron = true,
            )
            MochiListRow(
                title = "粘贴 PAT",
                onClick = onPatLogin,
                enabled = !busy,
                chevron = true,
            )
        } else {
            MochiListRow(
                title = account.name.ifBlank { account.email.ifBlank { "Qoder 账号" } },
                subtitle = listOfNotNull(
                    account.email.takeIf { it.isNotBlank() },
                    buildString {
                        append(if (account.source == "pat") "PAT" else "浏览器授权")
                        if (account.expiresAt > 0) {
                            append(" · 到期 ")
                            append(
                                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(account.expiresAt)),
                            )
                        }
                    },
                ).joinToString(" · "),
                onClick = onOpenDetail,
                chevron = true,
                titleTrailing = {
                    Tag("已登录", tone = TagTone.Positive)
                },
            )

            // 额度概览：与首页同一套数据，这里给一个更紧凑的版本。
            when {
                quotaError.isNotBlank() -> MochiListRow(
                    title = "额度读取失败",
                    subtitle = quotaError,
                    destructive = true,
                )
                quota == null || !quota.hasAny -> MochiListRow(
                    title = "额度",
                    subtitle = "正在读取额度…",
                )
                else -> {
                    quota.userQuota?.let { QuotaLine("用户额度", it) }
                    quota.addOnQuota?.let { QuotaLine("加赠额度", it) }
                    quota.orgQuota?.let { QuotaLine("组织资源包", it) }
                }
            }

            // 多账号：只有一个账号时不显示列表，避免页面像调试面板。
            if (accounts.size > 1) {
                MochiListRow(
                    title = "账号",
                    subtitle = "共 ${accounts.size} 个",
                )
                accounts.forEach { item ->
                    val isActive = item.id == account.id
                    MochiListRow(
                        title = item.name.ifBlank { item.email.ifBlank { item.id } },
                        subtitle = item.email.takeIf { it.isNotBlank() },
                        checked = isActive,
                        onClick = { onSwitchAccount(item.id) },
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
                        titleTrailing = {
                            if (isActive) {
                                Tag("使用中", tone = TagTone.Active)
                            }
                        },
                        trailing = {
                            TextAction(text = "删除", onClick = { onDeleteAccount(item.id) }, danger = true)
                        },
                    )
                }
            }

            MochiListRow(
                title = "添加账号",
                onClick = onLogin,
                enabled = !busy,
                chevron = true,
            )
            MochiListRow(
                title = "凭证详情",
                onClick = onOpenDetail,
                chevron = true,
            )
            MochiListRow(
                title = "退出 Qoder 账号",
                onClick = onRequestLogout,
                destructive = true,
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

    MochiListRow(
        title = label,
        value = when {
            remaining != null && total != null -> "${trimNumber(remaining)} / ${trimNumber(total)} $unit"
            remaining != null -> "剩余 ${trimNumber(remaining)} $unit"
            total != null -> "共 ${trimNumber(total)} $unit"
            else -> "未知"
        },
    )

    quota.fraction?.let { fraction ->
        Box(
            modifier = Modifier
                .mochiListDivider(22.dp)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 10.dp),
        ) {
            MochiProgressBar(
                progress = fraction,
                height = 3.dp,
            )
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
                style = MochiTheme.textStyles.titleLarge,
                color = MochiTheme.colorScheme.onSurface,
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
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.error,
            )
            usage == null -> Text(
                text = "点「刷新」读取额度。",
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
            !usage.hasAny -> Text(
                text = "当前账号没有返回额度信息。",
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
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
                        style = MochiTheme.textStyles.labelSmall,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
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
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "粘贴 Qoder PAT",
        message = "在 qoder.com/account/integrations 生成令牌，形如 pt-...。令牌只保存在本机。",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "登录",
                role = MochiDialogActionRole.Primary,
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ),
        ),
    ) {
        MochiTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "pt-...",
            singleLine = true,
        )
    }
}

/** 浏览器授权等待提示。 */
@Composable
fun QoderLoginDialog(
    authUrl: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "在浏览器完成授权",
        message = "已打开浏览器。完成登录后这个窗口会自动关闭；若浏览器没有弹出，请手动打开下面的地址。",
        actions = persistentListOf(
            MochiDialogAction(label = "关闭", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "复制链接",
                role = MochiDialogActionRole.Primary,
                onClick = onCopy,
            ),
        ),
    ) {
        Text(
            text = authUrl,
            style = MonoSmall,
            color = MochiTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Qoder 凭证详情：与 WorkBuddy 的抽屉对齐，所有字段可点按复制。 */
@Composable
fun QoderDetailDrawer(
    account: QoderAccount?,
    onDismiss: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val sheetState = rememberMochiSheetState(initial = MochiSheetDetent.Large)
    LaunchedEffect(Unit) { sheetState.show() }

    MochiSheet(state = sheetState, onDismiss = onDismiss) {
        MochiSheetHeader(title = "Qoder 凭证")
        MochiSheetContent {
            if (account == null) {
                Text(
                    text = "尚无 Qoder 账号",
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

/** 退出 Qoder：清除全部已保存账号。 */
@Composable
fun QoderLogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "退出 Qoder",
        message = "将清除本机保存的全部 Qoder 账号，需要重新登录才能继续使用。",
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
