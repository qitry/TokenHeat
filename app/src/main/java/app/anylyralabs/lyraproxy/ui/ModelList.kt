package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anylyralabs.lyraproxy.proto.HubModel

/**
 * 模型目录。
 *
 * 用 Column 展开而不是嵌套 LazyColumn：外层已经是 LazyColumn，
 * 再套一层可滚动列表会触发无限高度测量。条目本身很轻，直接铺开没有性能问题。
 */
@Composable
fun ModelList(
    models: List<HubModel>,
    onCopyModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (models.isEmpty()) {
        Text(
            text = "还没有读取到模型。启动服务后会自动拉取。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val sorted = models.sortedWith(compareBy({ it.sortKey }, { it.id }))
    Column(modifier = modifier.fillMaxWidth()) {
        sorted.forEach { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyModel(model.id) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.name.ifBlank { model.id },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                if (model.isFree) {
                    Tag("免费", tone = TagTone.Positive)
                    Spacer(Modifier.width(8.dp))
                } else if (model.rate.isNotBlank()) {
                    Text(
                        text = model.rate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (model.supportsImages) {
                    Text(
                        text = "图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
