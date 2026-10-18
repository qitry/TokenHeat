package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = apiKey.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = maskKey(apiKey.key),
                        style = MonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = apiKey.enabled, onCheckedChange = onToggle)
            }

            if (apiKey.isUnlimitedBudget) {
                Text(
                    text = "不限预算 · 已用 ${formatCredits(apiKey.usedCredits)} · ${apiKey.requestCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val used = apiKey.usedCredits
                val exhausted = used >= apiKey.budget
                ThinBar(
                    fraction = (used / apiKey.budget).toFloat(),
                    fill = if (exhausted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "预算 ${formatCredits(apiKey.budget)} · 已用 ${formatCredits(used)} · 剩余 ${formatCredits(apiKey.budgetRemaining)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exhausted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = if (apiKey.allowedModels.isEmpty()) {
                    "模型：全部可用"
                } else {
                    "模型：${apiKey.allowedModels.joinToString("、")}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextAction(text = "复制", onClick = onCopy)
                TextAction(text = "编辑", onClick = onEdit)
                TextAction(if (expanded) "收起" else "管理") { expanded = !expanded }
            }

            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextAction(text = "重置用量", onClick = onClearUsage)
                    TextAction(text = "删除", onClick = onDelete, danger = true)
                }
            }
        }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建密钥" else "编辑密钥") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：手机客户端") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { input ->
                        budgetText = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("积分预算") },
                    placeholder = { Text("留空表示不限") },
                    supportingText = { Text("累计消耗达到上限后，该密钥的请求会被拒绝") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = restrict, onCheckedChange = { restrict = it })
                    Text("限制可用模型", style = MaterialTheme.typography.bodyMedium)
                }

                if (restrict) {
                    if (availableModels.isEmpty()) {
                        Text(
                            text = "模型目录为空，请先在服务页刷新模型列表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                        ) {
                            availableModels.forEach { model ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = model in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + model else selected - model
                                        },
                                    )
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        label,
                        if (restrict) selected.toList() else emptyList(),
                        budgetText.toDoubleOrNull() ?: 0.0,
                        enabled,
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 新建成功后展示一次完整密钥。 */
@Composable
fun ApiKeyCreatedDialog(
    apiKey: ApiKey,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("密钥已创建") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "把下面两项填进客户端即可。密钥存在本机，之后也能在列表里查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldRow("地址", "http://127.0.0.1:8765/v1", mono = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                FieldRow("密钥", apiKey.key, mono = true)
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制密钥") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 删除确认。 */
@Composable
fun ApiKeyDeleteDialog(
    apiKey: ApiKey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除密钥") },
        text = { Text("使用「${apiKey.label}」的客户端会立即失去访问权限，此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
