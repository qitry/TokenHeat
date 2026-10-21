package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nothing 风格的基础视觉：黑白灰为主，红色只承担强调与错误。
 *
 * 这里不引入任何新依赖：配色是 Material3 的 ColorScheme，形状是自绘 Path，
 * 卡片只是 background + clip + shadow 的组合。
 */
object LyraColors {
    object Light {
        val Background = Color(0xFFF2F2F2)
        val Surface = Color(0xFFFFFFFF)
        val TextPrimary = Color(0xFF151515)
        val TextSecondary = Color(0xFF666666)
        val Outline = Color(0xFFD7D7D7)
        val Accent = Color(0xFFD71921)
    }

    object Dark {
        val Background = Color(0xFF000000)
        val Surface = Color(0xFF1C1C1C)
        val TextPrimary = Color(0xFFF2F2F2)
        val TextSecondary = Color(0xFFA8A8A8)
        val Outline = Color(0xFF424242)
        val Accent = Color(0xFFFF4D55)
    }
}

private val LightNothingScheme = lightColorScheme().copy(
    primary = LyraColors.Light.Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE7E8),
    onPrimaryContainer = Color(0xFF7A0D12),
    secondary = LyraColors.Light.TextSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = LyraColors.Light.TextPrimary,
    tertiary = Color(0xFF3E6B4F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1ECE4),
    onTertiaryContainer = Color(0xFF1E3A29),
    background = LyraColors.Light.Background,
    onBackground = LyraColors.Light.TextPrimary,
    surface = LyraColors.Light.Surface,
    onSurface = LyraColors.Light.TextPrimary,
    surfaceVariant = Color(0xFFE9E9E9),
    onSurfaceVariant = LyraColors.Light.TextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LyraColors.Light.Surface,
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE3E3E3),
    outline = LyraColors.Light.Outline,
    outlineVariant = LyraColors.Light.Outline,
    error = LyraColors.Light.Accent,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE7E8),
    onErrorContainer = Color(0xFF7A0D12),
    inverseSurface = LyraColors.Light.TextPrimary,
    inverseOnSurface = LyraColors.Light.Background,
    inversePrimary = LyraColors.Dark.Accent,
    scrim = Color(0xFF000000),
)

private val DarkNothingScheme = darkColorScheme().copy(
    primary = LyraColors.Dark.Accent,
    onPrimary = Color(0xFF2A0507),
    primaryContainer = Color(0xFF3A1114),
    onPrimaryContainer = Color(0xFFFFD9DB),
    secondary = LyraColors.Dark.TextSecondary,
    onSecondary = Color(0xFF101010),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = LyraColors.Dark.TextPrimary,
    tertiary = Color(0xFF8FBF9F),
    onTertiary = Color(0xFF0C1B12),
    tertiaryContainer = Color(0xFF1B3224),
    onTertiaryContainer = Color(0xFFCDE8D5),
    background = LyraColors.Dark.Background,
    onBackground = LyraColors.Dark.TextPrimary,
    surface = LyraColors.Dark.Surface,
    onSurface = LyraColors.Dark.TextPrimary,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = LyraColors.Dark.TextSecondary,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = LyraColors.Dark.Surface,
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF303030),
    outline = LyraColors.Dark.Outline,
    outlineVariant = LyraColors.Dark.Outline,
    error = LyraColors.Dark.Accent,
    onError = Color(0xFF2A0507),
    errorContainer = Color(0xFF3A1114),
    onErrorContainer = Color(0xFFFFD9DB),
    inverseSurface = LyraColors.Dark.TextPrimary,
    inverseOnSurface = Color(0xFF101010),
    inversePrimary = LyraColors.Light.Accent,
    scrim = Color(0xFF000000),
)

/**
 * Nothing 主题。
 *
 * `dark` 由调用方传入，保持与 [ClaudeTheme] 的跟随系统逻辑一致。
 * 字体仍沿用 ClaudeTypography：只换颜色，不改字号与排版。
 */
@Composable
fun NothingTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkNothingScheme else LightNothingScheme,
        typography = ClaudeTypography,
        content = content,
    )
}

/**
 * G2 连续圆角。
 *
 * 普通 [androidx.compose.foundation.shape.RoundedCornerShape] 在直线与圆弧的接点处
 * 曲率会从 0 突跳到 1/r，看起来像「贴上去的圆角」。这里用 n = 4 的超椭圆
 * （|x/r|^4 + |y/r|^4 = 1）构造四分之一角：参数化为
 *
 *     x = r · sign(cos t) · sqrt(|cos t|)
 *     y = r · sign(sin t) · sqrt(|sin t|)
 *
 * 超椭圆在轴端点处的曲率正好为 0，因此与直边的连接是曲率连续的（G2），
 * 半径从 0 平滑过渡到最大值，而不是突然出现一段圆弧。
 *
 * 每个角采样 14 段直线。对手机屏幕上的圆角尺寸来说，折线误差远小于 1px。
 */
fun SmoothRoundedCornerShape(radius: Dp): Shape = G2RoundedShape(radius)

private class G2RoundedShape(private val radius: Dp) : Shape {
    private var cachedSize: Size = Size.Unspecified
    private var cachedRadius: Float = -1f
    private var cachedPath: Path? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        val r = with(density) { radius.toPx() }
            .coerceIn(0f, min(size.width, size.height) / 2f)
        if (r <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        val cached = cachedPath
        if (cached != null && cachedSize == size && cachedRadius == r) {
            return Outline.Generic(cached)
        }

        val path = buildPath(size, r)
        cachedPath = path
        cachedSize = size
        cachedRadius = r
        return Outline.Generic(path)
    }

    private fun buildPath(size: Size, r: Float): Path = Path().apply {
        val steps = 14

        fun lineToSuperellipse(cx: Float, cy: Float, angle: Double) {
            val x = cx + r * signedSqrt(cos(angle))
            val y = cy + r * signedSqrt(sin(angle))
            lineTo(x, y)
        }

        // 上边
        moveTo(r, 0f)
        lineTo(size.width - r, 0f)

        // 右上角：上 → 右
        for (i in 1..steps) {
            lineToSuperellipse(size.width - r, r, -PI / 2 + (PI / 2) * i / steps)
        }
        // 右边
        lineTo(size.width, size.height - r)

        // 右下角：右 → 下
        for (i in 1..steps) {
            lineToSuperellipse(size.width - r, size.height - r, (PI / 2) * i / steps)
        }
        // 下边
        lineTo(r, size.height)

        // 左下角：下 → 左
        for (i in 1..steps) {
            lineToSuperellipse(r, size.height - r, PI / 2 + (PI / 2) * i / steps)
        }
        // 左边
        lineTo(0f, r)

        // 左上角：左 → 上
        for (i in 1..steps) {
            lineToSuperellipse(r, r, PI + (PI / 2) * i / steps)
        }
        close()
    }
}

/** sign(v) · sqrt(|v|)，即 n = 4 超椭圆的参数化分量。 */
private fun signedSqrt(value: Double): Float {
    val root = sqrt(abs(value)).toFloat()
    return if (value < 0) -root else root
}

/**
 * 卡片：轻微阴影 + 平滑圆角 + 主题表面色。
 *
 * 阴影很浅，只在浅色模式下把卡片从 #F2F2F2 背景上托起来；
 * 深色模式下阴影几乎不可见，靠表面色 #1C1C1C 与留白区分层级。
 */
@Composable
fun Modifier.lyraCard(shape: Shape = SmoothRoundedCornerShape(18.dp)): Modifier =
    this
        .shadow(elevation = 1.5.dp, shape = shape, clip = false)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
