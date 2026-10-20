package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import app.anylyralabs.lyraproxy.proto.HubModel

/**
 * 模型目录。
 *
 * 使用 MochiListSection 的分组卡片，每行点按复制模型标识。
 */
@Composable
fun ModelList(
    models: List<HubModel>,
    onCopyModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (models.isEmpty()) {
        MochiListSection(modifier = modifier) {
            MochiListRow(
                title = "还没有读取到模型",
                subtitle = "启动服务后会自动拉取。",
            )
        }
        return
    }

    val sorted = models.sortedWith(compareBy({ it.sortKey }, { it.id }))
    MochiListSection(modifier = modifier) {
        sorted.forEach { model ->
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
                            Spacer(Modifier.width(2.dp))
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
}
