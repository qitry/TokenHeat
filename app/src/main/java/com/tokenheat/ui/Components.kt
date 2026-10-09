package com.tokenheat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class BadgeVariant {
    Neutral,
    Success,
    Warning,
    Danger,
    Primary,
}

@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Neutral,
) {
    val (bg, content, border) = when (variant) {
        BadgeVariant.Success -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            ZincColors.Success,
            ZincColors.Success.copy(alpha = 0.35f)
        )
        BadgeVariant.Warning -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            ZincColors.Warning,
            ZincColors.Warning.copy(alpha = 0.35f)
        )
        BadgeVariant.Danger -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            ZincColors.Danger,
            ZincColors.Danger.copy(alpha = 0.35f)
        )
        BadgeVariant.Primary -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            Color.Transparent
        )
        BadgeVariant.Neutral -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outlineVariant
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

@Composable
fun StatusDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = ZincColors.Success,
    inactiveColor: Color = MaterialTheme.colorScheme.outline,
    size: Dp = 8.dp,
) {
    if (active) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(size + 4.dp)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(inactiveColor)
        )
    }
}

@Composable
fun ShadcnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = SquircleCornerShape(FlCorner.Md.dp),
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = SquircleCornerShape(FlCorner.Md.dp),
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun CodeField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                .clickable(onClick = onCopy),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Lucide.Copy,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Continuous curvature Superellipse / Squircle Shape.
 * Formula: |x/a|^n + |y/b|^n = 1
 * - Exponent n = 5: Specially crafted for all icons, avatars, and badges.
 * - Exponent n = 6: Specially crafted for containers, cards, buttons, dialogs, and inputs.
 */
class SuperellipseShape(
    val exponent: Float = 6f,
    val cornerRadius: Dp? = null,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return Outline.Generic(path)

        val r = cornerRadius?.let { with(density) { it.toPx() } }
            ?.coerceAtMost(minOf(width, height) / 2f)

        if (r == null || r >= minOf(width, height) / 2f - 0.5f) {
            // Full Superellipse (for badges, square icons, capsules)
            val cx = width / 2f
            val cy = height / 2f
            val a = width / 2f
            val b = height / 2f
            val p = 2.0 / exponent
            val steps = 64
            for (i in 0 until steps) {
                val theta = (i.toDouble() / steps) * 2.0 * Math.PI
                val cosT = Math.cos(theta)
                val sinT = Math.sin(theta)
                val x = cx + a * Math.signum(cosT).toFloat() * Math.pow(Math.abs(cosT), p).toFloat()
                val y = cy + b * Math.signum(sinT).toFloat() * Math.pow(Math.abs(sinT), p).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        } else {
            // Corner-based continuous curvature superellipse
            val p = 2.0 / exponent
            val steps = 14
            // 1. Top edge to top-right corner
            path.moveTo(r, 0f)
            path.lineTo(width - r, 0f)
            for (i in 0..steps) {
                val theta = (Math.PI / 2.0) * (1.0 - i.toDouble() / steps)
                val cosT = Math.cos(theta)
                val sinT = Math.sin(theta)
                val x = width - r + r * Math.pow(cosT, p).toFloat()
                val y = r - r * Math.pow(sinT, p).toFloat()
                path.lineTo(x, y)
            }
            // 2. Right edge to bottom-right corner
            path.lineTo(width, height - r)
            for (i in 0..steps) {
                val theta = (Math.PI / 2.0) * (i.toDouble() / steps)
                val cosT = Math.cos(theta)
                val sinT = Math.sin(theta)
                val x = width - r + r * Math.pow(cosT, p).toFloat()
                val y = height - r + r * Math.pow(sinT, p).toFloat()
                path.lineTo(x, y)
            }
            // 3. Bottom edge to bottom-left corner
            path.lineTo(r, height)
            for (i in 0..steps) {
                val theta = (Math.PI / 2.0) * (1.0 - i.toDouble() / steps)
                val cosT = Math.cos(theta)
                val sinT = Math.sin(theta)
                val x = r - r * Math.pow(cosT, p).toFloat()
                val y = height - r + r * Math.pow(sinT, p).toFloat()
                path.lineTo(x, y)
            }
            // 4. Left edge to top-left corner
            path.lineTo(0f, r)
            for (i in 0..steps) {
                val theta = (Math.PI / 2.0) * (i.toDouble() / steps)
                val cosT = Math.cos(theta)
                val sinT = Math.sin(theta)
                val x = r - r * Math.pow(cosT, p).toFloat()
                val y = r - r * Math.pow(sinT, p).toFloat()
                path.lineTo(x, y)
            }
            path.close()
        }
        return Outline.Generic(path)
    }
}

/** Icon Superellipse Shape (Exponent n = 5) */
val IconSquircleShape = SuperellipseShape(exponent = 5f)

/** Continuous Curvature Container Corner Shape (Exponent n = 6) */
fun SquircleCornerShape(cornerRadius: Dp = 12.dp) = SuperellipseShape(exponent = 6f, cornerRadius = cornerRadius)

/** Continuous Curvature Pill Shape (Exponent n = 6) */
val SquirclePillShape = SuperellipseShape(exponent = 6f)

// -----------------------------------------------------------------------------
// FlClash 目标模式基础组件（对标 lib/widgets/card.dart + scaffold.dart +
// views/dashboard/widgets/start_button.dart）。命名以 Fl 开头，避免与现有
// Shadcn 系组件冲突，迁移完成后 Shadcn* 可整体退役。
// -----------------------------------------------------------------------------

/** 对标 FlClash `Info`：卡片/分组的标题行数据。 */
data class FlInfo(val label: String)

/** 对标 FlClash `InfoHeader`：titleSmall + onSurfaceVariant 的分组标题行。 */
@Composable
fun FlInfoHeader(
    info: FlInfo,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = info.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** 对标 FlClash `CommonCardType`。 */
enum class FlCardType { Plain, Filled }

/**
 * 对标 FlClash `CommonCard`：
 * - Plain 背 surfaceContainerLow，Filled 背 surfaceContainerHigh；
 * - 边框 surfaceContainerHighest，选中态描边 primary、底 secondaryContainer；
 * - 形状统一超椭圆 md=16。
 */
@Composable
fun FlCommonCard(
    modifier: Modifier = Modifier,
    type: FlCardType = FlCardType.Plain,
    isSelected: Boolean = false,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    info: FlInfo? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        isError -> scheme.errorContainer
        isSelected -> scheme.secondaryContainer
        type == FlCardType.Filled -> scheme.surfaceContainerHigh
        else -> scheme.surfaceContainerLow
    }
    val borderColor = when {
        isError -> scheme.error.copy(alpha = 0.6f)
        isSelected -> scheme.primary
        else -> scheme.surfaceContainerHighest
    }
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            if (info != null) {
                FlInfoHeader(info = info)
                Spacer(Modifier.size(8.dp))
            }
            content()
        }
    }
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = SquircleCornerShape(FlCorner.Md.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = if (isError) scheme.error else scheme.onSurface,
            disabledContainerColor = container,
            disabledContentColor = scheme.onSurface,
        ),
    ) { body() }
}

/**
 * 对标 FlClash `SettingsBlock`：标题 + 整块 surfaceContainer 设置组。
 */
@Composable
fun FlSettingsBlock(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlInfoHeader(
            info = FlInfo(title),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Surface(
            shape = SquircleCornerShape(FlCorner.Md.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.padding(8.dp)) { content() }
        }
    }
}

/** 顶栏动作：对标 FlClash `IconButtonData`，超量时收进溢出菜单。 */
data class FlBarAction(
    val icon: @Composable () -> Unit,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * 对标 FlClash `CommonScaffold` 精简版：标题 + 最多 3 个顶栏按钮（多余进溢出菜单）
 * + 顶部线性加载条 + FAB。body 默认 16dp 页面边距。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlScaffold(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<FlBarAction> = emptyList(),
    isLoading: Boolean = false,
    floatingActionButton: @Composable (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    var overflowOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shown = actions.take(3)
    val overflow = actions.drop(3)
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        shown.forEach { a ->
                            IconButton(onClick = a.onClick) { a.icon() }
                        }
                        if (overflow.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { overflowOpen = true }) {
                                    Icon(
                                        imageVector = Lucide.MoreVertical,
                                        contentDescription = "更多",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowOpen,
                                    onDismissRequest = { overflowOpen = false },
                                ) {
                                    overflow.forEach { a ->
                                        DropdownMenuItem(
                                            text = { Text(a.description) },
                                            leadingIcon = a.icon,
                                            onClick = { overflowOpen = false; a.onClick() },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = { floatingActionButton?.invoke() },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
        ) { body() }
    }
}

/**
 * 对标 FlClash `StartButton`：56dp 高扩展式 FAB，未运行时只露图标，
 * 运行中展开显示计时文本。
 */
@Composable
fun FlStartButton(
    running: Boolean,
    runTimeText: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onToggle,
        modifier = modifier,
        shape = SquirclePillShape,
        containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.primary,
        contentColor = if (running) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (running) Lucide.Square else Lucide.Play,
                contentDescription = if (running) "停止" else "启动",
                modifier = Modifier.size(24.dp),
            )
            if (running) {
                Text(
                    text = runTimeText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

