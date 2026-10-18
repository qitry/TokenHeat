package com.wbhub.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wbhub.app.bridge.BridgeStatus
import com.wbhub.app.bridge.CallRecord
import com.wbhub.app.bridge.UsageSummary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页数据大屏：本机 AI 网关的实时计量面板。
 *
 * 结构（自上而下）：
 * 1. Hero —— 问候、点火状态、今日 Token 读数、24 小时热度条、启动/停止/复制端点；
 * 2. 四格读数 —— 累计总消耗 / 请求 / 输入 / 输出；
 * 3. 请求热力墙 —— 12/26/52 周可切换；
 * 4. 模型热度榜 —— 按调用次数 Top 5；
 * 5. 每日签到与余额（原「积分」页，合并至此）；
 * 6. 网关用量汇总 —— 积分消耗；
 * 7. 账号数据包 —— 导出 / 导入。
 */
@Composable
fun DashboardScreen(
    state: HubState,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onCheckin: () -> Unit,
    onCheckinAll: () -> Unit,
    onRefreshBalance: () -> Unit,
) {
    // 运行时长每秒推进；熄火时停表，避免无谓重组。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.bridgeRunning) {
        while (state.bridgeRunning) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val calls = state.calls
    val totalRequests = calls.size
    val promptTokens = calls.sumOf { it.promptTokens.toLong() }
    val completionTokens = calls.sumOf { it.completionTokens.toLong() }
    val totalTokens = promptTokens + completionTokens
    val summary = remember(calls) { UsageSummary.of(calls) }

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

    // 近 24 小时逐时请求计数。
    val hourlyCounts = remember(calls, now) {
        val counts = IntArray(24)
        calls.forEach { record ->
            val hoursAgo = ((now - record.timestamp) / 3_600_000L).toInt()
            if (hoursAgo in 0..23) counts[23 - hoursAgo]++
        }
        counts
    }

    val uptimeText = remember(state.bridgeRunning, now) {
        if (!state.bridgeRunning || BridgeStatus.startedAt <= 0L) {
            "未运行"
        } else {
            val diffSec = ((now - BridgeStatus.startedAt) / 1000).coerceAtLeast(0)
            val hours = diffSec / 3600
            val minutes = (diffSec % 3600) / 60
            val seconds = diffSec % 60
            if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- 1. Hero ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = getGreetingText(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(active = state.bridgeRunning)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = if (state.bridgeRunning) {
                                        "网关运行中 · 127.0.0.1:${state.port}"
                                    } else {
                                        "网关未运行 · 数据静默中"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.bridgeRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (state.bridgeRunning) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "运行时长",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = uptimeText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // 今日热量大读数
                    Column {
                        Text(
                            text = "今日 Token 消耗",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatTokenCount(todayTokens),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (todayTokens > 0L) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }

                    SparkBars(hourlyCounts)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (state.bridgeRunning) {
                            OutlinedButton(
                                onClick = onStopBridge,
                                modifier = Modifier.weight(1f),
                            ) { Text("停止") }
                        } else {
                            Button(
                                onClick = onStartBridge,
                                modifier = Modifier.weight(1f),
                            ) { Text("启动服务") }
                        }
                        FilledTonalButton(
                            onClick = onCopyEndpoint,
                            modifier = Modifier.weight(1f),
                        ) { Text("复制端点") }
                    }
                }
            }
        }

        // ---------- 2. 四格读数 ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        ReadoutCell("累计总消耗", formatTokenCount(totalTokens), Modifier.weight(1f))
                        VerticalHairline()
                        ReadoutCell("累计请求", "$totalRequests 次", Modifier.weight(1f))
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    ) {
                        ReadoutCell("累计输入", formatTokenCount(promptTokens), Modifier.weight(1f))
                        VerticalHairline()
                        ReadoutCell("累计输出", formatTokenCount(completionTokens), Modifier.weight(1f))
                    }
                }
            }
        }

        // ---------- 3. 请求热力墙 ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActivityHeatmap(calls = calls)
                }
            }
        }

        // ---------- 4. 模型热度榜 ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "模型热度榜",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "按调用次数排列的前 5 个模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "查看流水",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onNavigateToCalls),
                        )
                    }

                    val modelStats = remember(calls) {
                        calls.groupBy { it.model.ifEmpty { "(未知模型)" } }
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
                        Text(
                            text = "暂无调用记录，客户端发起请求后将在此累计热度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            modelStats.forEach { stat ->
                                TopModelRow(stat = stat, maxCount = modelStats.first().count)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 5. 签到与余额（原积分页） ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "每日签到",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "长按可为本版本全部账号依次签到",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatCredits(state.balance?.total ?: 0.0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Button(
                            onClick = onCheckin,
                            enabled = state.loading != Loading.CHECKIN,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (state.loading == Loading.CHECKIN) "签到中…" else "签到")
                        }
                        FilledTonalButton(
                            onClick = onCheckinAll,
                            enabled = state.loading != Loading.CHECKIN,
                            modifier = Modifier.weight(1f),
                        ) { Text("全部签到") }
                        OutlinedButton(
                            onClick = onRefreshBalance,
                            enabled = state.loading != Loading.BALANCE,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (state.loading == Loading.BALANCE) "刷新中" else "刷新") }
                    }

                    val balance = state.balance
                    when {
                        balance == null -> Text(
                            text = "点「刷新」查询余额",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        balance.accounts.isEmpty() -> Text(
                            text = "暂无套餐数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            balance.accounts.forEach { acc ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${formatCredits(acc.remain)} / ${formatCredits(acc.size)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            if (balance.cycleEnd.isNotBlank()) {
                                Text(
                                    text = "周期截止：${balance.cycleEnd}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 6. 用量汇总 ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "网关用量",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("成功", summary.calls.toString())
                        StatCell("失败", summary.failures.toString())
                        StatCell("消耗积分", formatCredits(summary.credits))
                    }
                }
            }
        }

        // ---------- 7. 账号数据包 ----------
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(
                            text = "账号数据包",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "将全部账号导出为 .json.gz 离线文件，可跨设备迁移或粘贴凭证字符串导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        OutlinedButton(
                            onClick = onExportBackup,
                            modifier = Modifier.weight(1f),
                        ) { Text("导出账号包") }
                        OutlinedButton(
                            onClick = onImportBackup,
                            modifier = Modifier.weight(1f),
                        ) { Text("导入账号包") }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 24 小时火花条
// -----------------------------------------------------------------------------

@Composable
private fun SparkBars(hourlyCounts: IntArray) {
    val ladder = HeatLadder()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "近 24 小时请求热度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "逐时",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            hourlyCounts.forEach { count ->
                val level = heatIndex(count)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4 + level * 7.5f).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ladder[level]),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 请求热力墙
// -----------------------------------------------------------------------------

enum class HeatmapSpan(val weeks: Int, val label: String) {
    WEEKS_12(12, "3 个月"),
    WEEKS_26(26, "半年"),
    WEEKS_52(52, "全年 52 周"),
}

@Composable
fun ActivityHeatmap(calls: List<CallRecord>) {
    var span by remember { mutableStateOf(HeatmapSpan.WEEKS_52) }

    // 对齐到本周六，使网格正好结束在当周。
    val calendar = Calendar.getInstance()
    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    calendar.add(Calendar.DAY_OF_YEAR, Calendar.SATURDAY - currentDayOfWeek)
    val endDate = calendar.timeInMillis

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val countsByDate = remember(calls) {
        calls.groupingBy { dateFormat.format(Date(it.timestamp)) }.eachCount()
    }

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
                daysInWeek.add(0, DayHeat(dateStr, countsByDate[dateStr] ?: 0))
            }
            weekList.add(0, daysInWeek)
        }
        weekList
    }

    var selectedDay by remember { mutableStateOf<DayHeat?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(span, weeks.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val ladder = HeatLadder()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "请求热力墙",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "近 ${span.weeks} 周，每天一次请求都会积攒一份热量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${span.label} · ${calls.size} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeatmapSpan.entries.forEach { s ->
                SpanChip(
                    label = s.label,
                    selected = s == span,
                    onClick = { span = s },
                )
            }
        }

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
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(ladder[heatIndex(day.count)])
                                    .clickable { selectedDay = day },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedDay?.let { "${it.date} · ${it.count} 次请求" }
                    ?: "点选单元格回看当天请求次数，可向左滑动翻阅",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ladder.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c),
                    )
                }
            }
        }
    }
}

private data class DayHeat(val date: String, val count: Int)

// -----------------------------------------------------------------------------
// 模型热度榜
// -----------------------------------------------------------------------------

private data class ModelCallStat(val model: String, val count: Int, val tokens: Long)

@Composable
private fun TopModelRow(stat: ModelCallStat, maxCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelBadge(modelId = stat.model, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stat.model,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            HeatBar(
                fraction = if (maxCount > 0) stat.count.toFloat() / maxCount else 0f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stat.count} 次",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${formatTokenCount(stat.tokens)} tok",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
