// Portions derived from mochi-compose example (Apache-2.0).
// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.mochiAnimateHeight
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.shape.MochiConcentricContainer
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/** 首页内容底部给浮起的标签栏让出的高度（标签栏 + 附件条 + 外边距）。 */
internal val LyraBottomBarClearance = 152.dp

/** 分组容器的圆角（= MochiTheme.shapes.extraLarge） */
internal val LyraSectionCorner = 28.dp

/**
 * 分组：小标题 + 圆角容器。演示页的内容都放在分组里。
 * @param footer 容器下方的补充说明
 */
@Composable
internal fun LyraSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    padding: Dp = 16.dp,
    container: Boolean = true,
    animateHeight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) MochiListSectionHeader(title)
        if (container) {
            LyraCard(padding = padding, animateHeight = animateHeight, content = content)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
        if (footer != null) MochiListSectionFooter(footer)
    }
}

/** 设置项直接复用列表布局；通用卡片的 padding 不能代替列表行与首末留白。 */
@Composable
internal fun LyraSettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable LyraSettingsScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scope = remember(maxWidth) { LyraSettingsScope(maxWidth) }
        MochiListSection(header = title, footer = footer) { scope.content() }
    }
}

/** 行布局模式由作用域显式传递，避免控件依赖隐式环境或在通用卡片里叠加行内边距。 */
internal class LyraSettingsScope(private val cardWidth: Dp) {
    @Composable
    fun <T> toggles(label: String, options: List<Pair<String, T>>, value: T, onChange: (T) -> Unit) {
        LyraToggles(label, options, value, rowWidth = cardWidth, onChange = onChange)
    }
}

/**
 * iOS 设置式的滑块分组（像「亮度」）：调的是什么写在卡片外的小标题里，当前数值跟在小标题右边；
 * 卡片里只有一行滑块，行高和普通行一样。
 */
@Composable
internal fun LyraSliderSection(
    label: String,
    value: String,
    current: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    origin: Float? = null,
    enabled: Boolean = true,
    gel: Float = 0.6f,
    footer: String? = null,
) {
    val secondary = MochiListDefaults.colors().secondary
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MochiListDefaults.TextRowPadding)
                .padding(bottom = MochiListDefaults.HeaderGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MochiTheme.textStyles.navTitle,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MochiTheme.textStyles.navTitle.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = secondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        MochiListSection(footer = footer) {
            MochiListSliderRow(current, onChange, valueRange = valueRange, steps = steps, enabled = enabled, origin = origin, gel = gel)
        }
    }
}

/** 给标题保留实际字宽，控件只能用剩下的宽度，不能把标题挤进圆角或挤没。 */
@Composable
private fun settingsControlWidth(label: String, cardWidth: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) { measurer.measure(label, MochiTheme.textStyles.body).size.width.toDp() }
    return (cardWidth - MochiListDefaults.TextRowPadding * 2 - labelWidth - 8.dp).coerceAtLeast(0.dp)
}

/** 自定义内容舞台；设置行使用 [LyraSettingsSection]，不在这里复制列表的布局算法。 */
@Composable
internal fun LyraCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    animateHeight: Boolean = true,
    corner: Dp = LyraSectionCorner,
    shape: Shape = MochiTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    MochiConcentricContainer(corner, padding) {
        Column(
            modifier
                .fillMaxWidth()
                .background(MochiDefaults.groupedContainer(), shape)
                .mochiAnimateHeight(shape, enabled = animateHeight)
                // 没有内边距 = 内容铺满卡片；自带底色时由卡片统一裁进自己的形状。
                .then(if (padding == 0.dp) Modifier.clip(shape) else Modifier)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun <T> LyraToggles(
    label: String,
    options: List<Pair<String, T>>,
    value: T,
    rowWidth: Dp = Dp.Unspecified,
    onChange: (T) -> Unit,
) {
    val labels = remember(options) { options.map { it.first }.toImmutableList() }
    val control: @Composable (Modifier) -> Unit = { modifier ->
        MochiSegmentedControl(
            selectedIndex = options.indexOfFirst { it.second == value }.coerceAtLeast(0),
            onSelect = { onChange(options[it].second) },
            items = labels,
            modifier = modifier,
            behavior = ToggleBehavior,
        )
    }
    if (rowWidth.isSpecified) {
        val maxWidth = settingsControlWidth(label, rowWidth)
        ui.feice.mochi.list.MochiListRow(label, trailing = { control(Modifier.widthIn(max = maxWidth)) })
    } else {
        // 舞台里夹着的参数不再重复套卡片，但文字仍和分组标题对齐。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MochiListDefaults.TextRowPadding - HeaderPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MochiTheme.textStyles.body, maxLines = 1)
            Spacer(Modifier.weight(1f))
            control(Modifier)
        }
    }
}

private val ToggleBehavior = MochiSegmentedDefaults.behavior(height = 32.dp, segmentPadding = 12.dp)

private val HeaderPadding = 16.dp
