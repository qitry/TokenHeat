package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiCheckbox
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.theme.MochiTheme
import app.anylyralabs.lyraproxy.bridge.ApiKey

/**
 * 单条自定义密钥。
 *
 * 展示：名称、掩码后的密钥、预算使用情况、模型范围。
 * 操作默认收起，点「管理」再展开，避免列表被按钮塞满。
 */
@Composable
fun ApiKeyItem(
    apiKey: ApiKey,
    onCopy: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClearUsage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    MochiListRow(
        title = apiKey.label,
        subtitle = maskKey(apiKey.key),
        trailing = {
            MochiSwitch(checked = apiKey.enabled, onCheckedChange = onToggle)
        },
    )

    if (apiKey.isUnlimitedBudget) {
        MochiListRow(
            title = "不限预算",
            subtitle = "已用 ${formatCredits(apiKey.usedCredits)} · ${apiKey.requestCount} 次",
        )
    } else {
        val used = apiKey.usedCredits
        val exhausted = used >= apiKey.budget
        MochiListRow(
            title = "预算 ${formatCredits(apiKey.budget)}",
            subtitle = "已用 ${formatCredits(used)} · 剩余 ${formatCredits(apiKey.budgetRemaining)}",
        )
        Box(
            modifier = Modifier
                .mochiListDivider(22.dp)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 10.dp),
        ) {
            MochiProgressBar(
                progress = (used / apiKey.budget).toFloat(),
                height = 3.dp,
                color = if (exhausted) {
                    MochiTheme.colorScheme.error
                } else {
                    MochiTheme.colorScheme.primary
                },
            )
        }
    }

    MochiListRow(
        title = "模型",
        subtitle = if (apiKey.allowedModels.isEmpty()) {
            "全部可用"
        } else {
            apiKey.allowedModels.joinToString("、")
        },
    )

    MochiListRow(
        title = "操作",
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(text = "复制", onClick = onCopy)
                TextAction(text = "编辑", onClick = onEdit)
                TextAction(
                    text = if (expanded) "收起" else "管理",
                    onClick = { expanded = !expanded },
                )
            }
        },
    )
    if (expanded) {
        MochiListRow(
            title = "更多",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction(text = "重置用量", onClick = onClearUsage)
                    TextAction(text = "删除", onClick = onDelete, danger = true)
                }
            },
        )
    }
}

/** 只露出首尾，避免整串密钥在界面上被旁人看到。 */
private fun maskKey(key: String): String =
    if (key.length <= 12) key else "${key.take(8)}…${key.takeLast(4)}"

/**
 * 新建 / 编辑密钥。
 *
 * 模型白名单从上游目录勾选，比手打 ID 可靠；预算留空表示不限。
 */
@Composable
fun ApiKeyEditorDialog(
    existing: ApiKey?,
    availableModels: List<String>,
    onDismiss: () -> Unit,
    onSave: (label: String, allowedModels: List<String>, budget: Double, enabled: Boolean) -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var budgetText by remember {
        mutableStateOf(
            existing?.let { if (it.isUnlimitedBudget) "" else formatCredits(it.budget) } ?: "",
        )
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var selected by remember { mutableStateOf(existing?.allowedModels.orEmpty().toSet()) }
    var restrict by remember { mutableStateOf(existing?.allowedModels?.isNotEmpty() == true) }

    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = if (existing == null) "新建密钥" else "编辑密钥",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "保存",
                role = MochiDialogActionRole.Primary,
                onClick = {
                    onSave(
                        label,
                        if (restrict) selected.toList() else emptyList(),
                        budgetText.toDoubleOrNull() ?: 0.0,
                        enabled,
                    )
                },
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MochiTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "名称，例如：手机客户端",
                singleLine = true,
            )
            MochiTextField(
                value = budgetText,
                onValueChange = { input ->
                    budgetText = input.filter { it.isDigit() || it == '.' }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "积分预算（留空表示不限）",
                supportingText = "累计消耗达到上限后，该密钥的请求会被拒绝",
                singleLine = true,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                MochiCheckbox(
                    checked = restrict,
                    onCheckedChange = { restrict = it },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "限制可用模型",
                    style = MochiTheme.textStyles.bodyMedium,
                    color = MochiTheme.colorScheme.onSurface,
                )
            }

            if (restrict) {
                if (availableModels.isEmpty()) {
                    Text(
                        text = "模型目录为空，请先在服务页刷新模型列表。",
                        style = MochiTheme.textStyles.bodySmall,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        availableModels.forEach { model ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MochiCheckbox(
                                    checked = model in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + model else selected - model
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = model,
                                    style = MochiTheme.textStyles.bodySmall,
                                    color = MochiTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "启用",
                    style = MochiTheme.textStyles.bodyMedium,
                    color = MochiTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MochiSwitch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }
    }
}

/** 新建成功后展示一次完整密钥。 */
@Composable
fun ApiKeyCreatedDialog(
    apiKey: ApiKey,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "密钥已创建",
        message = "把下面两项填进客户端即可。密钥存在本机，之后也能在列表里查看。",
        actions = persistentListOf(
            MochiDialogAction(label = "关闭", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "复制密钥",
                role = MochiDialogActionRole.Primary,
                onClick = onCopy,
            ),
        ),
    ) {
        MochiListSection {
            MochiListRow(
                title = "地址",
                subtitle = "http://127.0.0.1:8765/v1",
            )
            MochiListRow(
                title = "密钥",
                subtitle = apiKey.key,
            )
        }
    }
}

/** 删除确认。 */
@Composable
fun ApiKeyDeleteDialog(
    apiKey: ApiKey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "删除密钥",
        message = "使用「${apiKey.label}」的客户端会立即失去访问权限，此操作不可撤销。",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "删除",
                role = MochiDialogActionRole.Destructive,
                onClick = onConfirm,
            ),
        ),
    )
}
