package com.tokenheat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tokenheat.bridge.BridgeStatus
import com.tokenheat.bridge.CallRecord
import com.tokenheat.proto.HubModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Modern metrics & activity dashboard screen (Zinc low-saturation theme).
 * Features live uptime counter, token throughput counters, GitHub-like activity heatmap,
 * top model distribution, and quick action controls.
 */
@Composable
fun DashboardScreen(
    state: HubState,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onShowHelp: () -> Unit,
    onNavigateToCalls: () -> Unit,
) {
    // Ticker for real-time uptime display
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.bridgeRunning) {
        while (state.bridgeRunning) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val calls = state.calls
    val totalRequests = calls.size
    val successCount = calls.count { it.outcome == CallRecord.Outcome.OK }
    val successRate = if (totalRequests > 0) (successCount * 100f / totalRequests) else 100f

    val promptTokens = calls.sumOf { it.promptTokens.toLong() }
    val completionTokens = calls.sumOf { it.completionTokens.toLong() }
    val totalTokens = promptTokens + completionTokens

    // Calculate today's token consumption
    val startOfDay = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayTokens = remember(calls, startOfDay) {
        calls.filter { it.timestamp >= startOfDay }.sumOf { it.totalTokens.toLong() }
    }

    // Uptime text formatting
    val uptimeText = remember(state.bridgeRunning, BridgeStatus.startedAt, now) {
        if (!state.bridgeRunning || BridgeStatus.startedAt <= 0L) {
            "未运行"
        } else {
            val diffSec = ((now - BridgeStatus.startedAt) / 1000).coerceAtLeast(0)
            val hours = diffSec / 3600
            val minutes = (diffSec % 3600) / 60
            val seconds = diffSec % 60
            if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}分 ${seconds}秒"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Gateway Status & Core Hardware Metrics Card (FlClash service_status 目标位)
        item {
            FlCommonCard(info = FlInfo("网关服务")) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(active = state.bridgeRunning)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "TokenHeat 本地网关",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (state.bridgeRunning) "运行端口 127.0.0.1:${state.port}" else "网关服务当前已停止",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PillBadge(
                            text = if (state.bridgeRunning) "服务中" else "已离线",
                            variant = if (state.bridgeRunning) BadgeVariant.Success else BadgeVariant.Neutral,
                        )
                    }

                    // Key stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricSmallBox(
                            label = "运行启动时长",
                            value = uptimeText,
                            modifier = Modifier.weight(1f),
                        )
                        MetricSmallBox(
                            label = "累计服务请求",
                            value = "$totalRequests 次",
                            modifier = Modifier.weight(1f),
                        )
                        MetricSmallBox(
                            label = "调用成功率",
                            value = String.format(Locale.getDefault(), "%.1f%%", successRate),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Quick Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.bridgeRunning) {
                            OutlinedButton(
                                onClick = onStopBridge,
                                shape = SquirclePillShape,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            ) {
                                Icon(Lucide.Square, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text("停止服务", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Button(
                                onClick = onStartBridge,
                                shape = SquirclePillShape,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("启动服务", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = onCopyEndpoint,
                            shape = SquirclePillShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(Lucide.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制端点", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 2. Token Throughput Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = "Token 吞吐量统计",
                    subtitle = "精准统计自上游真实用量回传的 Token 消耗",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TokenStatCard(
                        title = "总 Token 消耗",
                        count = totalTokens,
                        note = "全周期累计",
                        modifier = Modifier.weight(1f),
                        highlight = true,
                    )
                    TokenStatCard(
                        title = "今日产生 Token",
                        count = todayTokens,
                        note = "自今日 00:00",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TokenStatCard(
                        title = "输入 Prompt Tokens",
                        count = promptTokens,
                        note = "提示词开销",
                        modifier = Modifier.weight(1f),
                    )
                    TokenStatCard(
                        title = "生成 Completion Tokens",
                        count = completionTokens,
                        note = "模型输出回复",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 3. GitHub-style Request Activity Heatmap Wall
        item {
            FlCommonCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActivityHeatmap(calls = calls)
                }
            }
        }

        // 4. Top Models Distribution
        item {
            FlCommonCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "热门模型请求分布",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "查看明细",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onNavigateToCalls),
                        )
                    }

                    val modelStats = remember(calls) {
                        calls.groupBy { it.model }
                            .map { (model, list) ->
                                ModelCallStat(
                                    model = model,
                                    count = list.size,
                                    tokens = list.sumOf { it.totalTokens.toLong() },
                                )
                            }
                            .sortedByDescending { it.count }
                            .take(5)
                    }

                    if (modelStats.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无调用记录，发起请求后将在此汇总排行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            modelStats.forEach { stat ->
                                TopModelRow(stat = stat, maxCount = modelStats.first().count)
                            }
                        }
                    }
                }
            }
        }

        // 5. Account Bundle Backup & Quick Tools
        item {
            FlCommonCard(info = FlInfo("数据包与便捷运维")) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "支持将全量供应商账号加密压缩为 .json.gz 离线文件，便于在不同设备间迁移互备。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onExportBackup,
                            shape = SquirclePillShape,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导出账号包", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onImportBackup,
                            shape = SquirclePillShape,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入账号包", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Activity Heatmap (GitHub Contribution Calendar)
// -----------------------------------------------------------------------------

enum class HeatmapSpan(val weeks: Int, val label: String) {
    WEEKS_12(12, "3 个月"),
    WEEKS_26(26, "半年"),
    WEEKS_52(52, "全年 (52周)"),
}

@Composable
fun ActivityHeatmap(calls: List<CallRecord>) {
    var span by remember { mutableStateOf(HeatmapSpan.WEEKS_52) }

    // Align to the coming Saturday so the grid ends neatly on today's week
    val calendar = Calendar.getInstance()
    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    calendar.add(Calendar.DAY_OF_YEAR, Calendar.SATURDAY - currentDayOfWeek)
    val endDate = calendar.timeInMillis

    // Group call counts by date format yyyy-MM-dd
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val countsByDate = remember(calls) {
        calls.groupingBy { dateFormat.format(Date(it.timestamp)) }.eachCount()
    }

    // Build span.weeks columns, each with 7 rows (Sunday to Saturday)
    val weeks = remember(calls, endDate, span) {
        val cal = Calendar.getInstance().apply { timeInMillis = endDate }
        val weekList = mutableListOf<List<DayHeat>>()
        for (w in 0 until span.weeks) {
            val daysInWeek = mutableListOf<DayHeat>()
            for (d in 6 downTo 0) {
                val calDay = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -(w * 7 + (6 - d)))
                }
                val dateStr = dateFormat.format(calDay.time)
                val count = countsByDate[dateStr] ?: 0
                daysInWeek.add(0, DayHeat(dateStr, count))
            }
            weekList.add(0, daysInWeek)
        }
        weekList
    }

    var selectedDay by remember { mutableStateOf<DayHeat?>(null) }
    val scrollState = rememberScrollState()

    // Automatically scroll to the right (most recent days) on launch or span change
    LaunchedEffect(span, weeks.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // 5-level GitHub heat levels (level 0 to level 4)
    // Dark: level 0 is dark gray, higher levels become brighter emerald
    // Light: level 0 is light gray, higher levels become richer/darker emerald
    val colors = remember(isDark) {
        if (isDark) {
            listOf(
                Color(0xFF161B22), // 0: 暗灰底色
                Color(0xFF0E4429), // 1: 低暗幽绿
                Color(0xFF006D32), // 2: 中暗深绿
                Color(0xFF26A641), // 3: 鲜明翠绿
                Color(0xFF39D353), // 4: 最高亮荧光绿
            )
        } else {
            listOf(
                Color(0xFFEBEDF0), // 0: 浅灰底色
                Color(0xFF9BE9A8), // 1: 浅亮草绿
                Color(0xFF40C463), // 2: 明朗中绿
                Color(0xFF30A14E), // 3: 浓郁翠绿
                Color(0xFF216E39), // 4: 最深浓墨绿
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header with Span Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "请求热力活跃墙",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "近 ${span.weeks} 周 (${span.weeks * 7} 天) 请求活动分布全景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillBadge(
                text = span.label,
                variant = BadgeVariant.Primary,
            )
        }

        // Span Selector Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeatmapSpan.entries.forEach { s ->
                val isSelected = s == span
                FilterChip(
                    selected = isSelected,
                    onClick = { span = s },
                    label = { Text(s.label, style = MaterialTheme.typography.labelSmall) },
                    shape = SquircleCornerShape(FlCorner.Sm.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        // Scrollable Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val color = when {
                                day.count == 0 -> colors[0]
                                day.count in 1..4 -> colors[1]
                                day.count in 5..14 -> colors[2]
                                day.count in 15..39 -> colors[3]
                                else -> colors[4]
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                                    .clickable { selectedDay = day },
                            )
                        }
                    }
                }
            }
        }

        // Legend & Selected Day Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedDay?.let { "${it.date}：${it.count} 次请求" } ?: "点击单元格查看当天请求次数（可向左滑动回溯）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c),
                    )
                }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class DayHeat(val date: String, val count: Int)

// -----------------------------------------------------------------------------
// Sub-components
// -----------------------------------------------------------------------------

@Composable
private fun MetricSmallBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(SquircleCornerShape(FlCorner.Sm.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TokenStatCard(
    title: String,
    count: Long,
    note: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    FlCommonCard(
        modifier = modifier,
        type = if (highlight) FlCardType.Filled else FlCardType.Plain,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTokenCount(count),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTokenCount(count: Long): String {
    return when {
        count >= 1_000_000_000L -> String.format(Locale.getDefault(), "%.2fB", count / 1_000_000_000.0)
        count >= 1_000_000L -> String.format(Locale.getDefault(), "%.2fM", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private data class ModelCallStat(val model: String, val count: Int, val tokens: Long)

@Composable
private fun TopModelRow(stat: ModelCallStat, maxCount: Int) {
    val modelProxy = remember(stat.model) {
        HubModel(id = stat.model, name = stat.model)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelBrandBadge(model = modelProxy, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stat.model,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val fraction = if (maxCount > 0) (stat.count.toFloat() / maxCount) else 0f
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stat.count} 次",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${formatTokenCount(stat.tokens)} tok",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
