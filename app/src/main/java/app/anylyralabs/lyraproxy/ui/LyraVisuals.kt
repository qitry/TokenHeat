package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiBarGroup
import ui.feice.mochi.basic.MochiBottomToolbar
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiGauge
import ui.feice.mochi.basic.MochiRefreshIndicator
import ui.feice.mochi.basic.MochiRefreshState
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSkeleton
import ui.feice.mochi.basic.MochiTipCard
import ui.feice.mochi.basic.MochiTipPopover
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.mochiRefreshHold
import ui.feice.mochi.basic.rememberMochiRefreshState
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.effect.hdr.MochiEdgeGlow
import ui.feice.mochi.effect.hdr.MochiHdrPalette
import ui.feice.mochi.effect.overscroll.MochiOverscrollEffect
import ui.feice.mochi.effect.overscroll.MochiOverscrollSpec
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.effect.overscroll.rememberMochiOverscrollEffect
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiCompactTitleScaffold
import ui.feice.mochi.navigation.MochiLargeTitleScaffold
import ui.feice.mochi.picker.MochiDate
import ui.feice.mochi.picker.MochiDatePicker
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/*
 * Lyra 的复用视觉件。
 *
 * 这里只做「组合」和「约定」：底层全部是 mochi-compose 的公开组件，不重复实现库里的效果。
 * 页面接入时优先用这些组件，保证 Token 数字、剩余量仪表、占比条、空状态、加载态、下拉刷新、
 * 到期时间选择、提示条、彩边和编辑工具条在各页面之间手感一致。
 *
 * 所有组件都带默认参数，方便单独调用或在 Compose Preview 里试。
 */

/**
 * 滚动数字，用于 Token 总量、积分、请求数等会变化的计数。
 *
 * 直接转发 [MochiCounterText]：每一位数字是真正的滚轮，增大时向前滚、减小时往回滚，
 * 位数变化时高位从下方升起，等宽数字（tnum）保证整串不左右抖。
 *
 * ```kotlin
 * LyraCounter(tokens, style = MochiTheme.textStyles.largeTitle)
 * LyraCounter(requests, format = { "%,d".format(it) })
 * ```
 *
 * @param style 数字字体；默认比正文大一档、半粗，适合卡片主数值
 * @param color 数字颜色；[Color.Unspecified] 时使用当前内容色
 * @param format 数字如何显示（千分位、单位等），数字以外的字符变化时会模糊替换
 */
@Composable
fun LyraCounter(
    value: Long = 0L,
    style: TextStyle = MochiTheme.textStyles.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    format: (Long) -> String = { it.toString() },
) {
    MochiCounterText(
        value = value,
        modifier = modifier,
        color = color,
        style = style,
        format = format,
    )
}

/**
 * 圆形容量仪表，用于 WorkBuddy 剩余积分一类「还剩多少」的原型展示。
 *
 * 直接转发 [MochiGauge]：开口朝下的 270° 弧 + 内侧刻度，中心是剩余值（滚动数字）和单位，
 * 弧的两端可以标 0 / 上限。
 *
 * ```kotlin
 * LyraGauge(value = remaining, max = total, label = "积分")
 * LyraGauge(remaining, total, label = "积分", maxLabel = "$total")
 * ```
 *
 * @param value 当前剩余值，内部会夹在 0..[max] 之间
 * @param max 满额；<= 0 时按 1 处理，避免除零
 * @param label 数字下方显示的单位（「积分」「次」……）
 * @param size 仪表直径；默认 120dp，比库默认值略小，适合放进卡片
 * @param minLabel 弧左端的最小值说明，null 不显示
 * @param maxLabel 弧右端的最大值说明，null 不显示
 * @param format 中心数字如何取整
 */
@Composable
fun LyraGauge(
    value: Float = 0f,
    max: Float = 100f,
    label: String = "积分",
    size: Dp = 120.dp,
    modifier: Modifier = Modifier,
    color: Color = MochiTheme.colorScheme.primary,
    minLabel: String? = null,
    maxLabel: String? = null,
    format: (Float) -> Long = { it.roundToLong() },
) {
    MochiGauge(
        value = value.coerceAtLeast(0f),
        modifier = modifier,
        range = 0f..max.coerceAtLeast(0.0001f),
        size = size,
        label = label,
        minLabel = minLabel,
        maxLabel = maxLabel,
        color = color,
        format = format,
    )
}

/**
 * 横向堆叠占比条（模型 Top 榜），下面一行是「色块 + 名称 + 百分比」图例。
 *
 * 轨道是 [MochiTheme.colorScheme.surfaceVariant]，条高 10dp、胶囊圆角，段与段之间留 2dp 间隙；
 * 数值为 0（或负数）的段自动隐藏，图例也一起隐藏。颜色从 [colors] 循环取，不传则用主题的
 * primary / secondary / tertiary 及其低透明度变体。
 *
 * ```kotlin
 * LyraShareBar(
 *     parts = listOf("claude-sonnet" to 62f, "gpt-5" to 30f, "其它" to 8f),
 * )
 * ```
 *
 * @param parts 名称到数值（相对量，不必先转成百分比）
 * @param colors 自定义色板；不足时循环使用
 * @param trackColor 轨道底色
 * @param barHeight 条高
 * @param gap 段间距
 * @param showLegend 是否显示图例
 */
@Composable
fun LyraShareBar(
    parts: List<Pair<String, Float>> = emptyList(),
    colors: List<Color>? = null,
    modifier: Modifier = Modifier,
    trackColor: Color = MochiTheme.colorScheme.surfaceVariant,
    barHeight: Dp = 10.dp,
    gap: Dp = 2.dp,
    showLegend: Boolean = true,
) {
    val scheme = MochiTheme.colorScheme
    val palette = colors?.takeIf { it.isNotEmpty() } ?: listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.primary.copy(alpha = 0.6f),
        scheme.secondary.copy(alpha = 0.6f),
        scheme.tertiary.copy(alpha = 0.6f),
    )
    val entries = parts.mapIndexedNotNull { index, part ->
        val value = part.second.coerceAtLeast(0f)
        if (value <= 0f) null else Triple(part.first, value, palette[index % palette.size])
    }
    val total = entries.sumOf { it.second.toDouble() }.toFloat()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(MochiCapsule)
                .background(trackColor),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            entries.forEach { (_, value, color) ->
                Box(
                    Modifier
                        .weight(value)
                        .fillMaxHeight()
                        .clip(MochiCapsule)
                        .background(color),
                )
            }
        }
        if (showLegend && total > 0f) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.forEach { (name, value, color) ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(MochiCapsule).background(color))
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MochiTheme.textStyles.footnote,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sharePercentLabel(value / total * 100f),
                            style = MochiTheme.textStyles.footnote,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 占比图例的百分比文案：两位数以上取整，个位数保留一位小数。 */
private fun sharePercentLabel(percent: Float): String =
    if (percent >= 10f) "${percent.roundToInt()}%" else "%.1f%%".format(percent)

/**
 * 空状态占位（搜索无结果、列表为空、请求失败）。
 *
 * 直接转发 [MochiEmptyState]：图标、标题、说明和可选操作整体居中，第一次出现时模糊淡入。
 *
 * ```kotlin
 * LyraEmptyPlaceholder(
 *     title = "还没有记录",
 *     message = "发起一次请求后，这里会出现用量明细。",
 *     icon = { LyraIcon(Icons.Outlined.History, contentDescription = null) },
 * )
 * ```
 *
 * @param icon 图标内容，颜色由 MochiEmptyState 提供（次要文字色）
 * @param action 可选操作，例如「重试」「去添加」
 */
@Composable
fun LyraEmptyPlaceholder(
    title: String = "暂无内容",
    message: String = "",
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    MochiEmptyState(
        title = title,
        modifier = modifier,
        message = message.ifBlank { null },
        icon = icon,
        action = action,
    )
}

/**
 * 列表 / 积分卡片的加载行：左侧转圈，右侧一条按 [text] 长度决定宽度的骨架条。
 *
 * 用 [MochiActivityIndicator] 表示「正在加载」，用 [MochiSkeleton] 的灰块给出列表行占位；
 * 加载完成把 [loading] 设为 false，灰块淡出、文字淡入，位置和大小不跳。
 * 模型列表、积分余额这类「一行正在取数」的场景直接放一行即可。
 *
 * @param text 加载态的文字模板（同时决定骨架条宽度），完成后显示的就是这句话
 * @param loading 是否处于加载中；false 时只显示文字，不再转圈
 */
@Composable
fun LyraLoadingRow(
    text: String = "加载中…",
    modifier: Modifier = Modifier,
    loading: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            MochiActivityIndicator(size = 18.dp)
        }
        MochiSkeleton(
            loading = loading,
            modifier = Modifier.weight(1f),
            shape = MochiCapsule,
        ) {
            Text(
                text = text,
                style = MochiTheme.textStyles.subhead,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 下拉刷新容器，给模型列表 / 记录列表使用。
 *
 * 内部创建 [MochiOverscrollEffect] 和 `rememberMochiRefreshState`，并把转圈固定在顶部；
 * [content] 必须把收到的 [MochiRefreshState] 通过 `Modifier.mochiRefreshHold(refresh)` 交给滚动容器，
 * 把 [MochiOverscrollEffect] 传给 `overscrollEffect`，否则内容不会为转圈让位、也拉不动。
 * Lazy 列表一定要传 [listState]：刷新后在最前面插入的新数据才会出现在视口里。
 *
 * ```kotlin
 * val list = rememberLazyListState()
 * LyraPullRefreshBox(onRefresh = { vm.reload() }, listState = list) { refresh, overscroll ->
 *     LazyColumn(
 *         Modifier.fillMaxSize().mochiRefreshHold(refresh),
 *         state = list,
 *         overscrollEffect = overscroll,
 *         flingBehavior = MochiScrollDefaults.flingBehavior(),
 *     ) { items(models) { ModelRow(it) } }
 * }
 * ```
 *
 * @param listState 列表状态；非 Lazy 内容可以传 null
 * @param content 列表本身；参数依次是刷新状态和越界回弹效果
 */
@Composable
fun LyraPullRefreshBox(
    onRefresh: suspend () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
    listState: LazyListState? = null,
    content: @Composable BoxScope.(MochiRefreshState, MochiOverscrollEffect) -> Unit = { _, _ -> },
) {
    val overscroll = rememberMochiOverscrollEffect()
    val refresh = rememberMochiRefreshState(overscroll, listState, onRefresh)
    Box(modifier) {
        content.invoke(this, refresh, overscroll)
        MochiRefreshIndicator(refresh, Modifier.align(Alignment.TopCenter))
    }
}

/**
 * 到期时间选择结果里的「永久」哨兵值。
 *
 * 项目里的 `expiresAt` 统一是 epoch **秒**，0 表示不设到期时间；这个常量就是给调用方在
 * `onConfirm` 里直接写回字段用的。
 */
const val LyraExpiryPermanent: Long = 0L

/**
 * 到期时间选择对话框：上面切换「指定日期 / 永久」，下面用 [MochiDatePicker] 的年月日滚筒选日期。
 *
 * [initial] 和 [onConfirm] 都是 epoch **秒**（与项目里的 `expiresAt` 一致）；确定时取所选日期
 * 本地时区 23:59:59 的秒数，符合凭证「到这天结束才过期」的直觉。选「永久」时回调
 * [LyraExpiryPermanent]（0）。库的日期选择器只有日期、没有时刻，所以统一取当天最后一刻。
 *
 * ```kotlin
 * var showExpiry by remember { mutableStateOf(false) }
 * LyraExpiryPickerDialog(
 *     visible = showExpiry,
 *     initial = account.expiresAt,          // epoch 秒；0 或 null 打开时选中「永久」
 *     onDismiss = { showExpiry = false },
 *     onConfirm = { seconds -> vm.setExpiry(seconds); showExpiry = false },
 * )
 * ```
 *
 * @param initial 当前到期时间（epoch 秒）；null 或 <= 0 表示永久，打开时默认选中「永久」
 * @param minDate 最早可选日期；默认今天
 * @param maxDate 最晚可选日期
 */
@Composable
fun LyraExpiryPickerDialog(
    visible: Boolean = true,
    initial: Long? = null,
    onDismiss: () -> Unit = {},
    onConfirm: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    minDate: MochiDate = MochiDate.today(),
    maxDate: MochiDate = MochiDate(2100, 12, 31),
) {
    val today = remember { MochiDate.today() }
    val start = remember(initial, minDate, maxDate) {
        val fromEpoch = initial?.takeIf { it > 0L }?.let { epochSecond ->
            val local = Instant.ofEpochSecond(epochSecond).atZone(ZoneId.systemDefault()).toLocalDate()
            MochiDate(local.year, local.monthValue, local.dayOfMonth)
        } ?: today
        fromEpoch.coerceIn(minDate, maxDate)
    }
    var permanent by remember(visible, initial) { mutableStateOf(initial == null || initial <= 0L) }
    var date by remember(visible, initial) { mutableStateOf(start) }

    MochiDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "到期时间",
        modifier = modifier,
        message = if (permanent) "凭证不会自动过期。" else null,
        actions = persistentListOf(
            MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
            MochiDialogAction(
                label = "确定",
                role = MochiDialogActionRole.Primary,
                onClick = {
                    onConfirm(if (permanent) LyraExpiryPermanent else date.endOfDayEpochSecond())
                },
            ),
        ),
    ) {
        MochiSegmentedControl(
            selectedIndex = if (permanent) 1 else 0,
            onSelect = { permanent = it == 1 },
            items = persistentListOf("指定日期", "永久"),
        )
        if (!permanent) {
            MochiDatePicker(
                date = date,
                onDateChange = { date = it },
                modifier = Modifier.fillMaxWidth(),
                min = minDate,
                max = maxDate,
            )
        }
    }
}

/** 所选日期在本地时区的最后一秒（epoch 秒，23:59:59）。 */
private fun MochiDate.endOfDayEpochSecond(): Long =
    java.time.LocalDate.of(year, month, day)
        .atTime(java.time.LocalTime.MAX)
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochSecond()

/**
 * 页内提示条，包装 [MochiTipCard]：左侧图标、标题和说明，右上角关闭。
 *
 * 关闭时整张卡模糊淡出、高度收起，下面的内容被平滑拉上来。关闭动作只回调 [onDismiss]，
 * 「以后不再显示」的记忆由调用方决定；需要页面内自动管理时用 [LyraTipHost]。
 *
 * ```kotlin
 * var showTip by rememberSaveable { mutableStateOf(true) }
 * LyraTip(
 *     title = "提示",
 *     message = "长按账号可以批量编辑。",
 *     visible = showTip,
 *     onDismiss = { showTip = false },
 * )
 * ```
 */
@Composable
fun LyraTip(
    title: String = "提示",
    message: String? = null,
    visible: Boolean = true,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    MochiTipCard(
        title = title,
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        message = message,
        icon = icon,
    )
}

/**
 * 一组页内提示条的宿主：按顺序渲染 [tips]，关掉的提示在当前组合内不再出现，最后渲染 [content]。
 *
 * 关闭状态只活在这个 Host 的组合里（离开页面后重新进入会再次出现）。要跨会话记住「不再提示」，
 * 调用方自行用 SharedPreferences 过滤 [tips] 即可，这里不擅自写持久化。
 *
 * ```kotlin
 * LyraTipHost(
 *     tips = listOf(
 *         LyraTipSpec("edit", "批量管理", "点右上角「编辑」可多选账号。"),
 *         LyraTipSpec("expiry", "到期提醒", "凭证过期前会自动刷新。"),
 *     ),
 * ) {
 *     ModelListContent()
 * }
 * ```
 */
@Composable
fun LyraTipHost(
    tips: List<LyraTipSpec> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var dismissed by remember { mutableStateOf(emptySet<String>()) }
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tips.forEach { tip ->
            LyraTip(
                title = tip.title,
                message = tip.message,
                visible = tip.id !in dismissed,
                onDismiss = { dismissed = dismissed + tip.id },
            )
        }
        content()
    }
}

/** [LyraTipHost] 里的一条提示；[id] 用于在页面内记住它已经被关掉。 */
@Immutable
data class LyraTipSpec(
    val id: String,
    val title: String,
    val message: String? = null,
)

/**
 * 气泡提示，包装 [MochiTipPopover]：内容就是触发按钮，轻点（或长按）从按钮上长出一个带小尖角的提示气泡。
 *
 * [content] 是按钮里的内容（通常是一个图标），想同时保留按钮自己的点击行为可以传 [onClick]。
 * 气泡默认由内部状态控制显隐，点外面、返回键或右上角关闭都会收起。
 *
 * ```kotlin
 * LyraPopoverTip(text = "这里的统计每天 0 点重置") {
 *     LyraIcon(Icons.Outlined.Info, contentDescription = "说明")
 * }
 * ```
 *
 * @param text 气泡正文
 * @param content 触发按钮内容
 * @param title 气泡标题
 * @param icon 气泡里的图标（不是触发按钮的图标）
 * @param onClick 触发按钮自己的点击操作；为 null 时轻点即展开气泡
 */
@Composable
fun LyraPopoverTip(
    text: String = "",
    content: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    title: String = "提示",
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    MochiTipPopover(
        title = title,
        visible = visible,
        onVisibleChange = { visible = it },
        modifier = modifier,
        message = text,
        icon = icon,
        onClick = onClick,
        buttonContent = content,
    )
}

/**
 * 「彩边亮一下」：包装 [MochiEdgeGlow]，[active] 变 true 后只亮 [durationMillis] 毫秒就自动熄灭。
 *
 * 放在页面最外层 Box 的最上面，默认铺满全屏并盖在内容上，不拦截触摸。
 * 同一次 true 只触发一次；要在每次成功时都亮一下，用递增的 key 重建这个组件：
 *
 * ```kotlin
 * var pulseKey by remember { mutableIntStateOf(0) }
 * Box(Modifier.fillMaxSize()) {
 *     Content()
 *     key(pulseKey) { LyraPulseEdgeGlow(active = pulseKey > 0, durationMillis = 1000) }
 * }
 * // 成功回调里：pulseKey++
 * ```
 *
 * 如果只是把 active 在同一次组合里先 false 再 true，状态会被合并，看不到第二次亮起。需要
 * false → true 跨过一次重组才会重新计时。
 *
 * @param active true 时点亮；由 false 变为 true 才开始计时
 * @param durationMillis 亮起持续时间；<= 0 时立即熄灭
 * @param palette 光环配色，默认 Apple Intelligence 风格
 * @param width 柔光从屏幕边往里伸的深度
 * @param line 贴边亮线的粗细
 * @param intensity 0..1 的整体亮度
 */
@Composable
fun LyraPulseEdgeGlow(
    active: Boolean = false,
    durationMillis: Long = 1000L,
    modifier: Modifier = Modifier.fillMaxSize(),
    palette: MochiHdrPalette = MochiHdrPalette.Intelligence,
    width: Dp = 24.dp,
    line: Dp = 3.dp,
    intensity: Float = 1f,
) {
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            lit = true
            delay(durationMillis.coerceAtLeast(0L))
            lit = false
        } else {
            lit = false
        }
    }
    MochiEdgeGlow(
        active = lit,
        modifier = modifier,
        palette = palette,
        width = width,
        line = line,
        intensity = intensity,
    )
}

/**
 * 列表编辑模式的底部工具条：全选、已选计数、删除、完成。
 *
 * 用 [MochiBottomToolbar] + [MochiBarButton]，浮在内容底部、自动带导航栏 inset。
 * 放在页面最外层 Box 里并传 `Modifier.align(Alignment.BottomCenter)`；[editing] 为 false 时不渲染，
 * 不会占位。删除按钮在没选中任何项时禁用，用错误色以示危险操作。
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     ModelList(...)
 *     LyraEditModeBar(
 *         editing = editing,
 *         selectedCount = selected.size,
 *         onDone = { editing = false; selected.clear() },
 *         onSelectAll = { selected = all.toMutableSet() },
 *         onDelete = { confirmDelete = true },
 *         modifier = Modifier.align(Alignment.BottomCenter),
 *     )
 * }
 * ```
 *
 * @param edgeColor 工具条背后向上渐隐的底色，应等于页面背景色
 * @param selectAllLabel 全选按钮文案
 */
@Composable
fun LyraEditModeBar(
    editing: Boolean = false,
    selectedCount: Int = 0,
    onDone: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    edgeColor: Color = MochiDefaults.groupedBackground(),
    selectAllLabel: String = "全选",
) {
    if (!editing) return
    val scheme = MochiTheme.colorScheme
    MochiBottomToolbar(modifier = modifier, edgeColor = edgeColor) {
        MochiBarButton(text = selectAllLabel, onClick = onSelectAll)
        Spacer(Modifier.weight(1f))
        Text(
            text = "已选 $selectedCount 项",
            style = MochiTheme.textStyles.footnote,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        MochiBarButton(
            text = "删除",
            onClick = onDelete,
            enabled = selectedCount > 0,
            colors = MochiButtonDefaults.barColors().copy(content = scheme.error),
        )
        MochiBarButton(text = "完成", onClick = onDone)
    }
}

/**
 * 横向「果冻」一行：内容横向滚动，越界时有 Cresto 回弹，惯性用 mochi 的 iOS 减速曲线。
 *
 * 库的 [ProvideMochiOverscroll] 负责整棵子树的回弹，滚动容器自己传
 * `flingBehavior = MochiScrollDefaults.flingBehavior()`。适合模型快捷筛选、标签页签一类的横排。
 *
 * ```kotlin
 * LyraJellyRow {
 *     models.forEach { LyraFilterChip(it) }
 * }
 * ```
 *
 * @param contentPadding 行两端的留白（在滚动内容内部，会随内容一起滚）
 * @param spacing 子项间距
 * @param spec 回弹参数
 */
@Composable
fun LyraJellyRow(
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    spacing: Dp = 8.dp,
    spec: MochiOverscrollSpec = MochiOverscrollSpec(),
    content: @Composable RowScope.() -> Unit = {},
) {
    ProvideMochiOverscroll(spec) {
        Row(
            modifier
                .horizontalScroll(
                    state = rememberScrollState(),
                    flingBehavior = MochiScrollDefaults.flingBehavior(),
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * 带果冻回弹的纵向 Lazy 列表：整棵子树由 [ProvideMochiOverscroll] 提供越界效果，
 * 列表本身使用 [MochiScrollDefaults.flingBehavior] 的 iOS 惯性。
 *
 * ```kotlin
 * LyraOverscrollList(listState = list) {
 *     items(models, key = { it.id }) { ModelRow(it) }
 * }
 * ```
 *
 * @param listState 列表状态，配合下拉刷新时请与 `rememberMochiRefreshState` 用同一个实例
 * @param contentPadding 列表内边距
 * @param verticalArrangement 子项间距
 * @param spec 回弹参数
 */
@Composable
fun LyraOverscrollList(
    modifier: Modifier = Modifier.fillMaxSize(),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    spec: MochiOverscrollSpec = MochiOverscrollSpec(),
    content: LazyListScope.() -> Unit = {},
) {
    ProvideMochiOverscroll(spec) {
        LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            flingBehavior = MochiScrollDefaults.flingBehavior(),
            content = content,
        )
    }
}

/**
 * 可读性标记：给列表 / 行加上「这棵树已经接好果冻回弹」的语义，本身返回 `this`。
 *
 * 说明：Compose 的越界回弹只能通过 `LocalOverscrollFactory`（即 [ProvideMochiOverscroll]）提供给子树，
 * 惯性则必须作为 `flingBehavior` 参数传给 `scrollable` / `LazyColumn`；两者都无法从一个 Modifier 注入，
 * mochi-compose 也没有等价的 Modifier 扩展（已核对 `effect/overscroll/MochiOverscroll.kt`）。
 * 所以这里不做重复实现，只保留调用点的统一写法；真正生效请使用 [LyraOverscrollList] / [LyraJellyRow]，
 * 或在页面根部包 [ProvideMochiOverscroll] 并显式传 `MochiScrollDefaults.flingBehavior()`。
 */
fun Modifier.lyraOverscroll(): Modifier = this

/**
 * 子页面统一外壳（「UI 设置 / 关于 / 设置」这类二级页）。
 *
 * 默认用 [MochiCompactTitleScaffold]（固定紧凑标题），[largeTitle] 为 true 时换成
 * [MochiLargeTitleScaffold]（大标题跟内容滚动）；左上角统一是 [MochiBackButton]。
 * [actions] 非空时会被包进一个 [MochiBarGroup] 胶囊，所以里面的按钮请用
 * `MochiBarGroupItem`（或宽度相近的顶栏按钮），保持和返回按钮同高。
 *
 * ```kotlin
 * LyraNavSubPage(title = "关于", onBack = { nav.pop() }) {
 *     LyraSection("版本") { MochiListRow("0.2.0") }
 * }
 * ```
 *
 * @param onBack 返回回调；必须显式传入（默认空实现只用于预览）
 * @param subtitle 紧凑顶栏下的小字 / 大标题页的副标题
 * @param onRefresh 可选的页级下拉刷新
 * @param actions 右上角操作，默认不显示
 */
@Composable
fun LyraNavSubPage(
    title: String = "",
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: Boolean = false,
    onRefresh: (suspend () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val navigation: @Composable () -> Unit = { MochiBackButton(onClick = onBack) }
    val groupedActions: (@Composable RowScope.() -> Unit)? = if (actions == null) {
        null
    } else {
        { MochiBarGroup { actions?.invoke(this) } }
    }
    if (largeTitle) {
        MochiLargeTitleScaffold(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigation,
            actions = groupedActions,
            onRefresh = onRefresh,
            content = content,
        )
    } else {
        MochiCompactTitleScaffold(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigation,
            actions = groupedActions,
            onRefresh = onRefresh,
            content = content,
        )
    }
}
