package app.anylyralabs.lyraproxy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiCheckbox
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogTextField
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.shape.MochiCapsule
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
 * 自上而下：问候与运行状态 → 今日读数与 24 小时分段柱状 → 待办 →
 * 累计四项 → 请求热力墙（按周惰性组合）→ 模型用量 → 签到与积分 → 账号备份。
 *
 * 分组只用 MochiListSection 与 [MochiListDefaults.SectionSpacing] 分层，不画自绘分割线。
 */
@Composable
fun DashboardScreen(
    state: HubState,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onCopyEndpoint: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFromText: () -> Unit,
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
        // 分组之间只靠留白分层，不再画自绘分割线。
        verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
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

        // ---------- 待办 ----------
        item(key = "todo") {
            TodoSection()
        }

        // ---------- 累计四项：读数用滚动数字 ----------
        item(key = "totals") {
            MochiListSection(header = "累计") {
                MochiListRow(
                    title = "累计消耗",
                    trailing = {
                        CounterValue(totalTokens, format = ::formatTokenCount)
                    },
                )
                MochiListRow(
                    title = "请求数",
                    trailing = {
                        CounterValue(calls.size.toLong()) {
                            String.format(Locale.US, "%,d", it)
                        }
                    },
                )
                MochiListRow(
                    title = "输入",
                    trailing = {
                        CounterValue(promptTokens, format = ::formatTokenCount)
                    },
                )
                MochiListRow(
                    title = "输出",
                    trailing = {
                        CounterValue(completionTokens, format = ::formatTokenCount)
                    },
                )
            }
        }

        // ---------- 请求热力墙 ----------
        item(key = "heatmap") {
            HeatmapBlock(calls = calls)
        }

        // ---------- 模型用量 ----------
        item(key = "models") {
            val cs = MochiTheme.colorScheme
            val modelTotal = modelStats.sumOf { it.count }.coerceAtLeast(1)
            val palette = listOf(
                cs.primary,
                cs.secondary,
                cs.tertiary,
                cs.primary.copy(alpha = 0.6f),
                cs.secondary.copy(alpha = 0.6f),
            )
            MochiListSection(
                header = "模型用量",
                footer = "按调用次数排列，最多显示前 5 个模型；右侧为次数占比。",
            ) {
                if (modelStats.isEmpty()) {
                    MochiListRow(
                        title = "还没有调用",
                        subtitle = "客户端连上网关后，这里会按模型累计用量。",
                    )
                } else {
                    // 多色占比条 + 下方逐行百分比，条形和图例指同一组数据。
                    Box(
                        Modifier
                            // 下面的模型行有行首圆点，分隔线从文字处开始才对齐。
                            .mochiListDivider(
                                MochiListDefaults.RowPadding +
                                    MochiListDefaults.LeadingSize +
                                    MochiListDefaults.LeadingGap,
                            )
                            .fillMaxWidth()
                            .padding(
                                start = MochiListDefaults.TextRowPadding,
                                end = MochiListDefaults.TextRowPadding,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                    ) {
                        LyraShareBar(
                            parts = modelStats.map { it.model to it.count.toFloat() },
                            colors = palette,
                            showLegend = false,
                        )
                    }
                    modelStats.forEachIndexed { index, stat ->
                        MochiListRow(
                            title = stat.model,
                            subtitle = "${stat.count} 次 · ${formatTokenCount(stat.tokens)}",
                            value = sharePercent(stat.count * 100f / modelTotal),
                            leading = {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(palette[index % palette.size]),
                                )
                            },
                        )
                    }
                }
                MochiListRow(
                    title = "全部记录",
                    onClick = onNavigateToCalls,
                    chevron = true,
                )
            }
        }

        // ---------- 签到与积分 ----------
        item(key = "credits") {
            Column(verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing)) {
                if (state.provider == GatewayProvider.QODER) {
                    QoderQuotaBlock(
                        usage = state.qoderUsage,
                        error = state.qoderUsageError,
                        busy = state.qoderBusy,
                        onRefresh = onRefreshQoderUsage,
                    )
                } else {
                    val balance = state.balance
                    val remaining = balance?.total ?: 0.0
                    val total = balance?.accounts?.sumOf { it.size } ?: 0.0

                    // 积分：仪表放在分组顶部，下面才是各账号余额。
                    MochiListSection(
                        header = if (checkinSupported) "签到与积分" else "积分",
                        footer = when {
                            balance == null -> "点「刷新积分」读取当前账号余额。"
                            balance.unlimited -> "企业额度不设上限。"
                            balance.cycleEnd.isNotBlank() -> "本期截至 ${balance.cycleEnd}"
                            else -> null
                        },
                    ) {
                        when {
                            balance == null -> MochiListRow(
                                title = "积分",
                                subtitle = if (state.loading == Loading.BALANCE) {
                                    "正在读取余额…"
                                } else {
                                    "尚未读取"
                                },
                            )

                            balance.unlimited -> MochiListRow(
                                title = "剩余积分",
                                value = "不限量",
                            )

                            else -> Box(
                                Modifier
                                    .mochiListDivider(MochiListDefaults.TextRowPadding)
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LyraGauge(
                                    value = remaining.toFloat(),
                                    max = maxOf(total, remaining, 1.0).toFloat(),
                                    label = "积分",
                                    size = 132.dp,
                                    minLabel = "0",
                                    maxLabel = if (total > 0.0) formatCredits(total) else null,
                                )
                            }
                        }

                        balance?.accounts?.forEach { account ->
                            MochiListRow(
                                title = account.name,
                                subtitle = if (account.unlimited) {
                                    "不设上限"
                                } else {
                                    "剩余 ${formatCredits(account.remain)} / ${formatCredits(account.size)}"
                                },
                            )
                        }

                        MochiListRow(
                            title = if (state.loading == Loading.BALANCE) "刷新中…" else "刷新积分",
                            onClick = onRefreshBalance,
                            enabled = state.loading != Loading.BALANCE,
                            chevron = true,
                        )
                    }

                    // 签到动作单独成组，别和余额数字挤在同一张卡片里。
                    if (checkinSupported) {
                        MochiListSection(header = "签到") {
                            if (state.checkedInToday) {
                                MochiListRow(
                                    title = "今天已签到",
                                    subtitle = "明天再来，或为其他账号补签。",
                                )
                            } else {
                                MochiListRow(
                                    title = if (state.loading == Loading.CHECKIN) "签到中…" else "签到",
                                    subtitle = "为当前账号领取今日积分",
                                    onClick = onCheckin,
                                    enabled = state.loading != Loading.CHECKIN,
                                    chevron = true,
                                )
                            }
                            MochiListRow(
                                title = "全部签到",
                                subtitle = "依次为当前版本的全部账号执行",
                                onClick = onCheckinAll,
                                enabled = state.loading != Loading.CHECKIN,
                                chevron = true,
                            )
                        }
                    }
                }
            }
        }

        // ---------- 账号备份 ----------
        item(key = "backup") {
            MochiListSection(
                header = "账号备份",
                footer = "本次会话共 ${summary.calls} 次成功、${summary.failures} 次失败，消耗 ${formatCredits(summary.credits)} 积分。",
            ) {
                MochiListRow(
                    title = "导出备份",
                    subtitle = "生成 .json.gz 文件",
                    onClick = onExportBackup,
                    chevron = true,
                )
                MochiListRow(
                    title = "导入备份文件",
                    subtitle = "从本机选择备份包",
                    onClick = onImportBackup,
                    chevron = true,
                )
                MochiListRow(
                    title = "粘贴备份导入",
                    subtitle = "粘贴凭证或整段备份内容",
                    onClick = onImportFromText,
                    chevron = true,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 待办
// -----------------------------------------------------------------------------

private data class TodoItem(val id: Long, val text: String, val done: Boolean)

/**
 * 待办的保存格式：`id␟done␟text`。用 unit separator 防止文本里的普通分隔符把字段切坏。
 * 数据目前只在内存 / 进程重建后恢复，尚未写入磁盘。
 */
private val TodoListSaver = listSaver<List<TodoItem>, String>(
    save = { items ->
        items.map { "${it.id}\u001F${if (it.done) "1" else "0"}\u001F${it.text}" }
    },
    restore = { saved ->
        saved.mapNotNull { row ->
            val parts = row.split('\u001F', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            TodoItem(id = id, text = parts[2], done = parts[1] == "1")
        }
    },
)

@Composable
private fun TodoSection() {
    var todos by rememberSaveable(stateSaver = TodoListSaver) {
        mutableStateOf<List<TodoItem>>(emptyList())
    }
    var adding by remember { mutableStateOf(false) }

    MochiListSection(
        header = "待办",
        footer = if (todos.isEmpty()) null else "点整行勾选，右侧按钮删除。",
    ) {
        if (todos.isEmpty()) {
            MochiListRow(
                title = "还没有待办",
                subtitle = "把要跟进的事记在这里，进程重建后仍会保留。",
            )
        }
        todos.forEach { todo ->
            TodoRow(
                todo = todo,
                onToggle = {
                    todos = todos.map {
                        if (it.id == todo.id) it.copy(done = !it.done) else it
                    }
                },
                onDelete = {
                    todos = todos.filterNot { it.id == todo.id }
                },
            )
        }
        MochiListRow(
            title = "新增待办",
            onClick = { adding = true },
            chevron = true,
        )
    }

    if (adding) {
        TodoAddDialog(
            onDismiss = { adding = false },
            onAdd = { text ->
                todos = todos + TodoItem(
                    id = System.currentTimeMillis(),
                    text = text,
                    done = false,
                )
                adding = false
            },
        )
    }
}

/**
 * 待办行：行高与行首留白照 MochiListRow 来，勾选后加删除线并降低透明度；
 * 右侧是图标按钮，不再用一行文字按钮占位。
 */
@Composable
private fun TodoRow(
    todo: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MochiTheme.colorScheme
    val dividerInset =
        MochiListDefaults.RowPadding + MochiListDefaults.LeadingSize + MochiListDefaults.LeadingGap
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .mochiListDivider(dividerInset)
            .clickable(onClick = onToggle)
            .heightIn(min = MochiListDefaults.RowMinHeight)
            .padding(
                start = MochiListDefaults.RowPadding,
                end = 10.dp,
                top = MochiListDefaults.RowVerticalPadding,
                bottom = MochiListDefaults.RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(MochiListDefaults.LeadingSize),
            contentAlignment = Alignment.Center,
        ) {
            MochiCheckbox(
                checked = todo.done,
                onCheckedChange = null,
            )
        }
        Spacer(Modifier.width(MochiListDefaults.LeadingGap))
        Text(
            text = todo.text,
            modifier = Modifier.weight(1f),
            style = MochiTheme.textStyles.body.copy(
                textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = cs.onSurface.copy(alpha = if (todo.done) 0.42f else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        MochiIconButton(
            onClick = onDelete,
            style = MochiButtonStyle.Text,
            colors = MochiButtonDefaults.colors(MochiButtonStyle.Text).copy(
                content = cs.error,
                glow = cs.error.copy(alpha = 0.18f),
            ),
            size = 36.dp,
        ) {
            LyraIcon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除待办",
                modifier = Modifier.size(17.dp),
                tint = cs.error,
            )
        }
    }
}

@Composable
private fun TodoAddDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "新增待办",
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "添加",
                role = MochiDialogActionRole.Primary,
                enabled = text.isNotBlank(),
                onClick = { onAdd(text.trim()) },
            ),
        ),
    ) {
        MochiDialogTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "要跟进的事",
        )
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
    val cs = MochiTheme.colorScheme
    Column {
        Text(
            text = getGreetingText(),
            style = MochiTheme.textStyles.displayLarge,
            color = cs.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // 运行状态：LiveDot + 回环地址 + 时长，放进分组卡片里，和下面的读数分开。
        Spacer(Modifier.height(12.dp))
        MochiListSection {
            MochiListRow(
                title = if (running) "网关运行中" else "网关已停止",
                subtitle = "127.0.0.1:$port · 仅接受本机请求",
                leading = { LiveDot(active = running) },
                trailing = {
                    Text(
                        text = uptime,
                        style = MonoSmall,
                        color = if (running) cs.primary else cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
            )
        }

        Spacer(Modifier.height(MochiListDefaults.SectionSpacing))

        // 今日读数 + 24 小时分布放在同一张卡片里：上面是滚动数字，下面是分段柱。
        MochiListSection(
            header = "今日",
            footer = if (todayTokens == 0L) "还没有记录；客户端接入后按本机日志累计。" else null,
        ) {
            MochiListRow(
                title = "Token",
                subtitle = "输入 + 输出",
                trailing = {
                    LyraCounter(
                        value = todayTokens,
                        style = MochiTheme.textStyles.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (todayTokens > 0L) cs.onSurface else cs.onSurfaceVariant,
                        format = ::formatTokenCount,
                    )
                },
            )
            Box(
                Modifier
                    .mochiListDivider(MochiListDefaults.TextRowPadding)
                    .fillMaxWidth()
                    .padding(horizontal = MochiListDefaults.TextRowPadding, vertical = 12.dp),
            ) {
                HourlyBars(hourly = hourly, peak = hourlyPeak)
            }
        }

        Spacer(Modifier.height(MochiListDefaults.SectionSpacing))
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

/**
 * 24 小时分段柱状：surfaceVariant 轨道里排 24 根 primary 胶囊，
 * 高度按峰值归一并做补间，空档保留一条低透明度基线。
 */
@Composable
private fun HourlyBars(hourly: IntArray, peak: Int) {
    val cs = MochiTheme.colorScheme
    val max = peak.coerceAtLeast(1)
    val empty = hourly.all { it == 0 }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "24 小时请求",
                style = MochiTheme.textStyles.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (empty) "暂无请求" else "峰值 $max 次",
                style = MochiTheme.textStyles.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cs.surfaceVariant)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hourly.forEach { count ->
                val ratio = (count.toFloat() / max).coerceIn(0f, 1f)
                val target = (0.06f + 0.94f * ratio).coerceIn(0.06f, 1f)
                val barHeight by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 420),
                    label = "hourlyBarHeight",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(barHeight)
                            .clip(MochiCapsule)
                            .background(
                                if (count == 0) {
                                    cs.primary.copy(alpha = 0.16f)
                                } else {
                                    cs.primary
                                },
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "24 小时前",
                style = MochiTheme.textStyles.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = "现在",
                style = MochiTheme.textStyles.labelSmall,
                color = cs.onSurfaceVariant,
            )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** 列表行尾的滚动读数：字号压到正文大小，颜色跟随次要文字。 */
@Composable
private fun CounterValue(
    value: Long,
    format: (Long) -> String = { it.toString() },
) {
    LyraCounter(
        value = value,
        style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.Medium),
        color = MochiTheme.colorScheme.onSurfaceVariant,
        format = format,
    )
}

/** 模型占比：两位数以上取整，个位数保留一位小数，和占比条图例一致。 */
private fun sharePercent(percent: Float): String =
    if (percent >= 10f) {
        "${percent.roundToInt()}%"
    } else {
        String.format(Locale.US, "%.1f%%", percent)
    }
