package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import app.anylyralabs.lyraproxy.proto.HubModel

/**
 * 模型目录。
 *
 * 嵌在服务页的分组里，本身不再套滚动容器（外层已经是 LazyColumn）。
 * - 加载中：骨架行
 * - 空：占位说明
 * - 有数据：每行点按复制模型标识，右侧显示倍率 / 免费 / 图片能力
 */
@Composable
fun ModelList(
    models: List<HubModel>,
    onCopyModel: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    if (models.isEmpty()) {
        MochiListSection(modifier = modifier) {
            if (loading) {
                LyraLoadingRow(text = "正在读取模型目录")
            } else {
                LyraEmptyPlaceholder(
                    title = "还没有读取到模型",
                    message = "启动服务后会自动拉取；也可以在上方点「刷新」。",
                )
            }
        }
        return
    }

    val sorted = remember(models) {
        models.sortedWith(compareBy({ it.sortKey }, { it.id }))
    }
    MochiListSection(
        modifier = modifier,
        footer = "点任意一行复制模型标识；倍率为 0 的模型不消耗积分。",
    ) {
        sorted.forEach { model ->
            MochiListRow(
                title = model.name.ifBlank { model.id },
                // 副标题给模型标识，长名字也不会把右侧倍率挤掉
                subtitle = model.name.takeIf { it.isNotBlank() && it != model.id },
                onClick = { onCopyModel(model.id) },
                value = when {
                    model.isFree -> "免费"
                    model.rate.isNotBlank() -> model.rate
                    else -> null
                },
                titleTrailing = {
                    if (model.supportsImages || model.supportsReasoning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (model.supportsImages) {
                                CapabilityChip("图")
                            }
                            if (model.supportsReasoning) {
                                CapabilityChip("思考")
                            }
                        }
                    }
                },
            )
        }
    }
}

/** 能力标签：只占一行小字，不用胶囊底，避免模型行显得拥挤。 */
@Composable
private fun CapabilityChip(text: String) {
    Text(
        text = text,
        style = MochiTheme.textStyles.labelSmall,
        color = MochiTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
