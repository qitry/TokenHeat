package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiProgressBar
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme
import app.anylyralabs.lyraproxy.bridge.BridgeStatus
import app.anylyralabs.lyraproxy.bridge.CallRecord
import app.anylyralabs.lyraproxy.bridge.UsageSummary
import app.anylyralabs.lyraproxy.proto.Wire
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页：一台本机网关的账页。
 *
 * 自上而下：问候与运行状态 → 今日读数 → 近 24 小时热度 → 累计四项 →
 * 请求热力墙（按周惰性组合）→ 模型用量 → 签到与积分 → 账号备份。
 *
 * 整页不用卡片，靠细线与留白分段。数字用衬线，是页面上唯一的视觉重音。
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
    onRefreshCalls: () -> Unit = {},
    onRefreshQoderUsage: () -> Unit = {},
) {
    // 运行时长每秒推进；熄火后停表，避免无意义重组。
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.bridgeRunning) {
        while (state.bridgeRunning) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // 首页实时刷新：每 2 秒问一次后端「日志有没有变」。
    // 后端用文件指纹判断，没有新记录时不读盘，因此这个频率开销很低。
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            onRefreshCalls()
        }
    }

    val calls = state.calls
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

    // 近 24 小时逐时计数；只遍历一次，结果作为简单数组交给柱子渲染。
    val hourly = remember(calls, now) {
        val counts = IntArray(24)
        calls.forEach { record ->
            val hoursAgo = ((now - record.timestamp) / 3_600_000L).toInt()
            if (hoursAgo in 0..23) counts[23 - hoursAgo]++
        }
        counts
    }
    val hourlyPeak = remember(hourly) { (hourly.maxOrNull() ?: 0).coerceAtLeast(1) }

    val uptime = remember(state.bridgeRunning, now) {
        if (!state.bridgeRunning || BridgeStatus.startedAt <= 0L) {
            "未运行"
        } else {
            val sec = ((now - BridgeStatus.startedAt) / 1000).coerceAtLeast(0)
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
        }
    }

    val modelStats = remember(calls) {
        calls.groupBy { it.model.ifEmpty { "未知模型" } }
            .map { (model, list) ->
                ModelStat(model, list.size, list.sumOf { it.totalTokens.toLong() })
            }
            .sortedByDescending { it.count }
            .take(5)
    }

    val checkinSupported = state.realm != Wire.Region.GLOBAL

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    ) {
        // ---------- 问候与状态 ----------
        item(key = "hero") {
            HeroBlock(
                running = state.bridgeRunning,
                port = state.port,
                uptime = uptime,
                todayTokens = todayTokens,
                hourly = hourly,
                hourlyPeak = hourlyPeak,
                onStartBridge = onStartBridge,
                onStopBridge = onStopBridge,
                onCopyEndpoint = onCopyEndpoint,
            )
        }

        // ---------- 累计四项 ----------
        item(key = "totals") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Readout(formatTokenCount(totalTokens), "累计消耗", Modifier.weight(1f))
                    Readout("${calls.size}", "请求数", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Readout(formatTokenCount(promptTokens), "输入", Modifier.weight(1f))
                    Readout(formatTokenCount(completionTokens), "输出", Modifier.weight(1f))
                }
            }
        }

        // ---------- 请求热力墙 ----------
        item(key = "heatmap") {
            HeatmapBlock(calls = calls, modifier = Modifier.padding(top = 26.dp))
        }

        // ---------- 模型用量 ----------
        item(key = "models") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "模型用量",
                    subtitle = "按调用次数排列",
                    trailing = {
                        TextAction(text = "全部记录", onClick = onNavigateToCalls)
                    },
                )
                if (modelStats.isEmpty()) {
                    EmptyNote("还没有调用。客户端连上网关后，这里会按模型累计用量。")
                } else {
                    Spacer(Modifier.height(12.dp))
                    val maxCount = modelStats.first().count
                    modelStats.forEach { stat ->
                        ModelStatRow(stat, maxCount)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // ---------- 签到与积分 ----------
        item(key = "credits") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                if (state.provider == GatewayProvider.QODER) {
                    QoderQuotaBlock(
                        usage = state.qoderUsage,
                        error = state.qoderUsageError,
                        busy = state.qoderBusy,
                        onRefresh = onRefreshQoderUsage,
                    )
                } else {
                SectionHeading(
                    title = if (checkinSupported) "签到与积分" else "积分",
                    subtitle = if (checkinSupported) {
                        "长按签到可为当前版本的全部账号依次执行"
                    } else {
                        "国际版不提供签到，这里只显示积分"
                    },
                    trailing = {
                        Text(
                            text = formatCredits(state.balance?.total ?: 0.0),
                            style = MochiTheme.textStyles.titleLarge,
                            color = MochiTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                // 今天已签到就不再提供签到入口，避免重复请求与重复弹窗。
                if (checkinSupported && state.checkedInToday) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag("已签到", tone = TagTone.Positive)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "今天已签到，明天再来",
                            style = MochiTheme.textStyles.bodySmall,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    QuietButton(
                        text = if (state.loading == Loading.BALANCE) "刷新中" else "刷新积分",
                        onClick = onRefreshBalance,
                        enabled = state.loading != Loading.BALANCE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (checkinSupported) {
                            PrimaryButton(
                                text = if (state.loading == Loading.CHECKIN) "签到中" else "签到",
                                onClick = onCheckin,
                                enabled = state.loading != Loading.CHECKIN,
                                modifier = Modifier.weight(1f),
                            )
                            QuietButton(
                                text = "全部签到",
                                onClick = onCheckinAll,
                                enabled = state.loading != Loading.CHECKIN,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            QuietButton(
                                text = if (state.loading == Loading.BALANCE) "刷新中" else "刷新积分",
                                onClick = onRefreshBalance,
                                enabled = state.loading != Loading.BALANCE,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                val balance = state.balance
                if (balance != null && balance.accounts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    balance.accounts.forEach { acc ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = acc.name,
                                style = MochiTheme.textStyles.bodySmall,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatCredits(acc.remain)} / ${formatCredits(acc.size)}",
                                style = MochiTheme.textStyles.bodySmall,
                            )
                        }
                    }
                    if (balance.cycleEnd.isNotBlank()) {
                        Text(
                            text = "本期截至 ${balance.cycleEnd}",
                            style = MochiTheme.textStyles.labelSmall,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                }
            }
        }

        // ---------- 账号备份 ----------
        item(key = "backup") {
            Column(Modifier.padding(top = 26.dp)) {
                Rule()
                SectionHeading(
                    title = "账号备份",
                    subtitle = "导出为 .json.gz，或从备份与凭证字符串恢复",
                    
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("导出", onExportBackup, Modifier.weight(1f))
                    QuietButton("导入", onImportBackup, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本次会话共 ${summary.calls} 次成功、${summary.failures} 次失败，消耗 ${formatCredits(summary.credits)} 积分。",
                    style = MochiTheme.textStyles.labelSmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 问候与今日读数
// -----------------------------------------------------------------------------

@Composable
private fun HeroBlock(
    running: Boolean,
    port: Int,
    uptime: String,
    todayTokens: Long,
    hourly: IntArray,
    hourlyPeak: Int,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onCopyEndpoint: () -> Unit,
) {
    Column {
        Text(
            text = getGreetingText(),
            style = MochiTheme.textStyles.displayLarge,
            color = MochiTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(active = running)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (running) "网关运行中 · 127.0.0.1:$port" else "网关未运行",
                style = MochiTheme.textStyles.bodySmall,
                color = if (running) {
                    MochiTheme.colorScheme.primary
                } else {
                    MochiTheme.colorScheme.onSurfaceVariant
                },
            )
            if (running) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uptime,
                    style = MonoSmall,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = "今日 Token",
            style = MochiTheme.textStyles.labelMedium,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatTokenCount(todayTokens),
            style = MochiTheme.textStyles.displaySmall,
            color = if (todayTokens > 0L) {
                MochiTheme.colorScheme.onSurface
            } else {
                MochiTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )

        // 24 小时热度：24 根柱子，高度按峰值归一，颜色只区分「有/无」。
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            hourly.forEach { count ->
                val ratio = count.toFloat() / hourlyPeak
                val h = (3f + ratio * 27f).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(h)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (count == 0) {
                                MochiTheme.colorScheme.surfaceVariant
                            } else {
                                MochiTheme.colorScheme.primary.copy(
                                    alpha = 0.35f + 0.65f * ratio.coerceAtLeast(0.25f),
                                )
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "24 小时前",
                style = MochiTheme.textStyles.labelSmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "现在",
                style = MochiTheme.textStyles.labelSmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                PrimaryButton("停止服务", onStopBridge, Modifier.weight(1f))
            } else {
                PrimaryButton("启动服务", onStartBridge, Modifier.weight(1f))
            }
            QuietButton("复制地址", onCopyEndpoint, Modifier.weight(1f))
        }
    }
}

// -----------------------------------------------------------------------------
// 请求热力墙
// -----------------------------------------------------------------------------

private enum class HeatSpan(val weeks: Int, val label: String) {
    Q(12, "3 个月"),
    H(26, "半年"),
    Y(52, "一年"),
}

/**
 * 热力墙。
 *
 * 用 LazyRow 按周惰性组合：52 周 × 7 格共 364 个格子，若一次性全部组合，
 * 首帧就要测量三百多个节点。改成惰性后只有可见的十几列参与测量与绘制。
 */
@Composable
private fun HeatmapBlock(calls: List<CallRecord>, modifier: Modifier = Modifier) {
    var span by remember { mutableStateOf(HeatSpan.Y) }

    val endOfWeek = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, Calendar.SATURDAY - get(Calendar.DAY_OF_WEEK))
        }.timeInMillis
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val countsByDate = remember(calls) {
        calls.groupingBy { dateFormat.format(Date(it.timestamp)) }.eachCount()
    }

    // 每周一列，共 span.weeks 列；只在 span 变化时重算。
    val weeks = remember(span, countsByDate, endOfWeek) {
        val cal = Calendar.getInstance().apply { timeInMillis = endOfWeek }
        val list = ArrayList<List<Pair<String, Int>>>(span.weeks)
        for (w in 0 until span.weeks) {
            val days = ArrayList<Pair<String, Int>>(7)
            for (d in 6 downTo 0) {
                val day = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -(w * 7 + (6 - d)))
                }
                val key = dateFormat.format(day.time)
                days.add(0, key to (countsByDate[key] ?: 0))
            }
            list.add(0, days)
        }
        list
    }

    var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val ladder = heatLadder()

    Column(modifier = modifier) {
        Rule()
        SectionHeading(
            title = "请求分布",
            subtitle = "每天有请求就会留下一个格",
            trailing = {
                Text(
                    text = "${calls.size} 次",
                    style = MochiTheme.textStyles.labelMedium,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        // 跨度选择：纯文字，选中项加深并加下划线。
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeatSpan.entries.forEach { option ->
                val active = option == span
                Text(
                    text = option.label,
                    style = MochiTheme.textStyles.labelMedium,
                    color = if (active) {
                        MochiTheme.colorScheme.onSurface
                    } else {
                        MochiTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) {
                                MochiTheme.colorScheme.surfaceVariant
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { span = option }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val gridState = androidx.compose.foundation.lazy.rememberLazyListState()
        // 默认停在最右侧：热力墙读的是「最近」的节奏，一进页面就该看到今天。
        LaunchedEffect(span, weeks.size) {
            if (weeks.isNotEmpty()) gridState.scrollToItem(weeks.lastIndex)
        }
        LazyRow(
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(weeks) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { (date, count) ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ladder[heatLevel(count)])
                                .clickable { selected = date to count },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.let { (date, count) -> "$date · $count 次" }
                    ?: "点格子查看当天，向左滑动看更早",
                style = MochiTheme.textStyles.labelSmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ladder.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(c),
                    )
                }
            }
        }
    }
}

/** 五档热阶：纸 → 陶土，与主色同源。 */
@Composable
private fun heatLadder(): List<Color> {
    val dark = isClaudeDark()
    return if (dark) {
        listOf(
            Color(0xFF30302E),
            Color(0xFF4A342C),
            Color(0xFF7A4A38),
            Color(0xFFB0613F),
            Color(0xFFD97757),
        )
    } else {
        listOf(
            Color(0xFFF0EEE6),
            Color(0xFFE8D3C6),
            Color(0xFFD9A88F),
            Color(0xFFC96442),
            Color(0xFFA84E32),
        )
    }
}

private fun heatLevel(count: Int): Int = when {
    count <= 0 -> 0
    count in 1..4 -> 1
    count in 5..14 -> 2
    count in 15..39 -> 3
    else -> 4
}

// -----------------------------------------------------------------------------
// 模型用量
// -----------------------------------------------------------------------------

private data class ModelStat(val model: String, val count: Int, val tokens: Long)

@Composable
private fun ModelStatRow(stat: ModelStat, maxCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stat.model,
            style = MochiTheme.textStyles.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${stat.count} 次 · ${formatTokenCount(stat.tokens)}",
            style = MochiTheme.textStyles.labelSmall,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(6.dp))
    MochiProgressBar(
        progress = stat.count.toFloat() / maxCount.coerceAtLeast(1),
        height = 3.dp,
    )
}
