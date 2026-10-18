package app.anylyralabs.lyraproxy.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

// -----------------------------------------------------------------------------
// 首页专用的小型组件。
//
// 这些组件刻意只服务于首页，主题色一律取自当前 MaterialTheme，
// 不引入第二套设计令牌，页面其余部分保持原样。
// -----------------------------------------------------------------------------

/** 是否暗色；与界面其余部分保持一致的判定方式。 */
@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * 热阶 0..4：灰烬 → 活炭 → 余烬 → 焰心。
 * 暗色用暖炭到亮橙，亮色用暖灰到深赭，保证两种主题下都能读出强度差。
 */
private val HeatLadderDark = listOf(
    Color(0xFF2B2622),
    Color(0xFF55341F),
    Color(0xFF8F4A20),
    Color(0xFFC96A2B),
    Color(0xFFF0A468),
)

private val HeatLadderLight = listOf(
    Color(0xFFECE6DD),
    Color(0xFFE5C7A4),
    Color(0xFFD89C5D),
    Color(0xFFBC6A2E),
    Color(0xFF8F3D10),
)

/** 当前主题下的五档热阶取色。 */
@Composable
fun HeatLadder(): List<Color> = if (isDarkSurface()) HeatLadderDark else HeatLadderLight

/** 请求次数 → 热阶档位（0..4）。热力墙、火花条、热度榜共用同一映射。 */
fun heatIndex(count: Int): Int = when {
    count <= 0 -> 0
    count in 1..4 -> 1
    count in 5..14 -> 2
    count in 15..39 -> 3
    else -> 4
}

/** 状态点：运行时脉动，静默时为空心环。 */
@Composable
fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    if (active) {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val scale by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "statusPulseScale",
        )
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(size + 4.dp)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
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
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
        )
    }
}

/** 水平热度条：浅槽 + 主色填充。 */
@Composable
fun HeatBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Surface(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 单格读数：标签在上、数值在下。 */
@Composable
fun ReadoutCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 读数表内部的竖向分格线。 */
@Composable
fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 摘要计数：数值在上、标签在下，居中排列。 */
@Composable
fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分段选择芯片，用于热力墙的周数切换。 */
@Composable
fun SpanChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val isDark = isDarkSurface()
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        border = if (selected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

/** Token 数量缩写：B/M/K，保证大数也能塞进一格。 */
fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", count / 1_000_000_000.0)
    count >= 1_000_000L -> String.format(Locale.US, "%.2fM", count / 1_000_000.0)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

/**
 * 模型徽章：首字母 + 家族语义色，n=5 连续曲率圆角。
 * 首页热度榜使用；不依赖任何外部图标资源。
 */
@Composable
fun ModelBadge(modelId: String, size: Dp = 30.dp) {
    val isDark = isDarkSurface()
    val tint = modelTint(modelId)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = if (isDark) 0.24f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = modelId.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) tint.copy(alpha = 0.95f) else tint,
        )
    }
}

/** 按模型家族给一个稳定的低饱和标识色。 */
private fun modelTint(modelId: String): Color {
    val id = modelId.lowercase()
    return when {
        id.contains("claude") || id.contains("anthropic") -> Color(0xFFC97A4A)
        id.contains("deepseek") -> Color(0xFF5A6FA8)
        id.contains("gemini") || id.contains("google") -> Color(0xFF5A7FA8)
        id.contains("glm") || id.contains("zhipu") -> Color(0xFF6E8A5E)
        id.contains("qwen") || id.contains("tongyi") -> Color(0xFF8A6E9E)
        id.contains("hunyuan") || id.contains("hy3") -> Color(0xFF4F7A9B)
        id.contains("doubao") -> Color(0xFF8C6440)
        id.contains("kimi") || id.contains("moonshot") -> Color(0xFF7A6BA0)
        id.contains("llama") || id.contains("meta") -> Color(0xFF5D7F9E)
        id.contains("gpt") || id.contains("openai") -> Color(0xFF4E8C74)
        else -> Color(0xFF7A7164)
    }
}
