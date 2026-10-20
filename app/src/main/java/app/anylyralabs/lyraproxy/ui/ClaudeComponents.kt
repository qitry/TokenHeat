package app.anylyralabs.lyraproxy.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.theme.MochiTheme

// -----------------------------------------------------------------------------
// 原子组件。
//
// 设计约定：不用卡片。内容直接落在纸面上，靠间距与一条细线分段。
// 这既是视觉选择，也省掉了大量 Surface/Border/shadow 的测量与绘制开销。
// -----------------------------------------------------------------------------

/**
 * 细线：唯一的分段手段。
 *
 * 自带下方留白：它是分组的起点，紧接其后的标题或标签需要一点呼吸空间，
 * 否则文字会贴在线上。上方的间距由各分组的外层 padding 提供。
 */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color),
        )
    }
}

/**
 * 小节标签。
 *
 * 自带上方留白：它通常紧跟在一条分隔线之后，若不留空隙，
 * 文字会贴在线上，显得拥挤。
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 14.dp),
    )
}

/** 标题 + 说明的两行结构，用于每个分组的开头；自带上方留白。 */
@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 按钮统一高度。主次按钮并排时必须同高，否则描边与内边距的差异会让它们错位。 */
private val BUTTON_HEIGHT = 46.dp

/**
 * 主按钮：实底陶土。
 * 这是页面上最强的操作，一屏最多一个。
 *
 * 高度固定为 [BUTTON_HEIGHT]，与 [QuietButton] 完全一致：
 * 两者经常并排出现，若各自用内边距撑高，差 1~2dp 就会明显错位。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    MochiButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = MochiButtonStyle.Filled,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = MochiTheme.textStyles.labelLarge,
            maxLines = 1,
        )
    }
}

/** 次按钮：浅纸底 + 细描边，明确可点，但不抢主操作。 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    MochiButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (danger) MochiButtonStyle.Text else MochiButtonStyle.Tonal,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MochiTheme.textStyles.labelLarge,
            color = if (danger) MaterialTheme.colorScheme.error else LocalContentColor.current,
            maxLines = 1,
        )
    }
}

/**
 * 纯文字操作，用于「清空」「重置」这类低频动作。
 *
 * 注意参数顺序：动作函数放在最后，这样 `TextAction("删除", onDelete)` 与
 * `TextAction("管理") { ... }` 两种写法都能正确绑定。
 */
@Composable
fun TextAction(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    MochiButton(
        onClick = onClick,
        modifier = modifier,
        style = MochiButtonStyle.Text,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        minHeight = 32.dp,
    ) {
        Text(
            text = text,
            style = MochiTheme.textStyles.labelMedium,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 状态点：运行时缓慢呼吸。
 *
 * 呼吸用 1400ms 的往返动画，幅度很小——它表示「活着」，不该抢注意力。
 */
@Composable
fun LiveDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp,
) {
    if (active) {
        val transition = rememberInfiniteTransition(label = "liveDot")
        val scale by transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "liveDotScale",
        )
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(size + 5.dp)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)),
        )
    }
}

/** 读数：数字在上、标签在下，数字用衬线，是页面唯一的数据重音。 */
@Composable
fun Readout(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 横向细进度条，用于预算与占比。无动画，避免无谓的每帧重绘。 */
@Composable
fun ThinBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(fill),
        )
    }
}

/** 短标签：不用胶囊，只用一行小字，避免页面上到处是圆角容器。 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.Neutral) {
    // 语义色一律取主题色，暗色模式下才不会因为固定深色而看不清。
    val color = when (tone) {
        TagTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        TagTone.Active -> MaterialTheme.colorScheme.primary
        TagTone.Danger -> MaterialTheme.colorScheme.error
        TagTone.Positive -> MaterialTheme.colorScheme.tertiary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.09f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

enum class TagTone { Neutral, Active, Danger, Positive }

/** 空状态：一句说明 + 一个可选动作，语气平实，不抒情。 */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

/** 键值行：左标签右值，值可点按复制。 */
@Composable
fun FieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = if (mono) MonoSmall else MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 加载指示：一行文字，不用动画组件，避免持续重绘。 */
@Composable
fun InlineLoading(text: String = "读取中") {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Token 数量缩写。 */
fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", count / 1_000_000_000.0)
    count >= 1_000_000L -> String.format(Locale.US, "%.2fM", count / 1_000_000.0)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

/** 积分缩写；整数不显示小数。 */
fun formatCredits(value: Double): String {
    if (value == 0.0) return "0"
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}
