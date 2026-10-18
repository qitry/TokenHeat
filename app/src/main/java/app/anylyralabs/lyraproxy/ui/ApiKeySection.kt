package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anylyralabs.lyraproxy.bridge.ApiKey

/**
 * 自定义 API Key 管理卡。
 *
 * 每条密钥可以限制「只允许使用的模型」与「积分预算」，
 * 网关在放行前会同时校验两者，并在每次成功调用后累加消耗。
 */
@Composable
fun ApiKeySection(
    keys: List<ApiKey>,
    onCopyKey: (String) -> Unit,
    onToggleEnabled: (ApiKey, Boolean) -> Unit,
    onEdit: (ApiKey) -> Unit,
    onDelete: (ApiKey) -> Unit,
    onCreate: () -> Unit,
    onClearUsage: (ApiKey) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "自定义 API Key",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "为不同客户端签发独立密钥，可限制可用模型与积分预算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onCreate) { Text("新建") }
            }

            if (keys.isEmpty()) {
                Text(
                    text = "还没有自定义密钥。当前所有客户端共用网关的共享密钥，无法区分来源，也无法限制额度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                keys.forEach { key ->
                    ApiKeyRow(
                        apiKey = key,
                        onCopyKey = { onCopyKey(key.key) },
                        onToggleEnabled = { enabled -> onToggleEnabled(key, enabled) },
                        onEdit = { onEdit(key) },
                        onDelete = { onDelete(key) },
                        onClearUsage = { onClearUsage(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyRow(
    apiKey: ApiKey,
    onCopyKey: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClearUsage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = apiKey.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = maskKey(apiKey.key),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = apiKey.enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            // 预算进度：仅在设了预算时出现，避免无意义的进度条。
            if (!apiKey.isUnlimitedBudget) {
                val fraction = if (apiKey.budget > 0) {
                    (apiKey.usedCredits / apiKey.budget).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                BudgetBar(
                    fraction = fraction,
                    exhausted = apiKey.usedCredits >= apiKey.budget,
                )
                Text(
                    text = "预算 ${formatCredits(apiKey.budget)}，已用 ${formatCredits(apiKey.usedCredits)}，剩余 ${formatCredits(apiKey.budgetRemaining)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (apiKey.usedCredits >= apiKey.budget) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                Text(
                    text = "不限预算 · 已用 ${formatCredits(apiKey.usedCredits)} · ${apiKey.requestCount} 次调用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = if (apiKey.allowedModels.isEmpty()) {
                    "可用模型：全部"
                } else {
                    "可用模型：${apiKey.allowedModels.joinToString("、")}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyKey,
                    modifier = Modifier.weight(1f),
                ) { Text("复制") }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) { Text("编辑") }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f),
                ) { Text(if (expanded) "收起" else "更多") }
            }

            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClearUsage, modifier = Modifier.weight(1f)) {
                        Text("重置用量")
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetBar(fraction: Float, exhausted: Boolean) {
    val track = MaterialTheme.colorScheme.surface
    val fill = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(fill),
        )
    }
}

/** 只露出前后几位，避免整串密钥在界面上被旁人看到。 */
private fun maskKey(key: String): String {
    if (key.length <= 12) return key
    return "${key.take(8)}…${key.takeLast(4)}"
}

/**
 * 新建 / 编辑密钥。
 *
 * 模型白名单用多选列表呈现：从上游目录里勾选，比让用户手打模型 ID 可靠。
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
    var selectedModels by remember { mutableStateOf(existing?.allowedModels.orEmpty().toSet()) }
    var restrictModels by remember { mutableStateOf(existing?.allowedModels?.isNotEmpty() == true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建 API Key" else "编辑 API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        // 只接受数字与一个小数点，避免非法输入导致解析失败。
                        budgetText = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("积分预算") },
                    placeholder = { Text("留空表示不限制") },
                    supportingText = { Text("累计消耗达到上限后，该密钥的请求会被拒绝") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = restrictModels,
                        onCheckedChange = { restrictModels = it },
                    )
                    Text(
                        text = "限制可用模型",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (restrictModels) {
                    if (availableModels.isEmpty()) {
                        Text(
                            text = "模型目录为空，请先在「API 平台」页刷新模型列表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier = Modifier.height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            availableModels.take(30).forEach { model ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = model in selectedModels,
                                        onCheckedChange = { checked ->
                                            selectedModels = if (checked) {
                                                selectedModels + model
                                            } else {
                                                selectedModels - model
                                            }
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
                        text = "启用该密钥",
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
                        if (restrictModels) selectedModels.toList() else emptyList(),
                        budgetText.toDoubleOrNull() ?: 0.0,
                        enabled,
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 新建成功后展示完整密钥，供用户复制一次。 */
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "请立即复制保存。密钥只在本地存储，之后也可以随时在列表里查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey.key,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "客户端填写：\nBase URL: http://127.0.0.1:8765/v1\nAPI Key: ${apiKey.key}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            Button(onClick = onCopy) { Text("复制密钥") }
        },
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
        text = { Text("删除后，使用「${apiKey.label}」的客户端将立即无法访问网关。此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
