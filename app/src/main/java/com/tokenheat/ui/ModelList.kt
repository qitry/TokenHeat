package com.tokenheat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tokenheat.proto.HubModel

/**
 * Square model family brand logo in Shadcn low-saturation style.
 * Displays authentic AI model marks (OpenAI, Claude, Gemini, DeepSeek, GLM, etc.)
 * with automatic Light/Dark theme adaptation, falling back to a sleek Bot icon for unknown models.
 */
@Composable
fun ModelBadge(model: HubModel, modifier: Modifier = Modifier) {
    ModelBrandBadge(model = model, modifier = modifier, size = 36.dp)
}

/**
 * Filter mode for model catalog.
 */
enum class ModelFilter {
    ALL,
    FREE,
    VISION,
}

/** The catalogue the bridge exposes with search and filter capabilities. */
@Composable
fun ModelList(
    models: List<HubModel>,
    onCopyModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (models.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无模型数据，启动服务后将自动拉取",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ModelFilter.ALL) }

    val filtered = remember(models, searchQuery, activeFilter) {
        val query = searchQuery.trim().lowercase()
        models
            .filter { model ->
                val matchesQuery = query.isEmpty() ||
                    model.name.lowercase().contains(query) ||
                    model.id.lowercase().contains(query) ||
                    model.vendor.lowercase().contains(query)
                val matchesFilter = when (activeFilter) {
                    ModelFilter.ALL -> true
                    ModelFilter.FREE -> model.isFree
                    ModelFilter.VISION -> model.supportsImages
                }
                matchesQuery && matchesFilter
            }
            .sortedWith(compareBy({ it.sortKey }, { it.id }))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Search & Filter controls
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索模型名称、ID 或厂商…", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    Lucide.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Lucide.Close,
                            contentDescription = "清除",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = activeFilter == ModelFilter.ALL,
                onClick = { activeFilter = ModelFilter.ALL },
                label = { Text("全部 (${models.size})", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = activeFilter == ModelFilter.FREE,
                onClick = { activeFilter = ModelFilter.FREE },
                label = { Text("免费 $0", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = activeFilter == ModelFilter.VISION,
                onClick = { activeFilter = ModelFilter.VISION },
                label = { Text("多模态/视觉", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未找到匹配的模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filtered.forEach { model ->
                    ModelRow(model, onCopy = { onCopyModel(model.id) })
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: HubModel, onCopy: () -> Unit) {
    ShadcnCard(onClick = onCopy) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModelBadge(model)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = model.name.ifBlank { model.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = model.id,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (model.isFree) {
                        PillBadge("免费 x0.00", variant = BadgeVariant.Success)
                    } else if (model.rate.isNotBlank()) {
                        PillBadge(model.rate, variant = BadgeVariant.Neutral)
                    }
                }
            }

            val hasCapabilities = model.supportsImages || model.supportsReasoning || model.badges.isNotEmpty()
            if (hasCapabilities) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (model.supportsImages) {
                        CapabilityTag("视觉", Lucide.Image)
                    }
                    if (model.supportsReasoning) {
                        CapabilityTag("深度思考", Lucide.Brain)
                    }
                    model.badges.take(3).forEach { badge ->
                        PillBadge(badge, variant = BadgeVariant.Neutral)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityTag(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
