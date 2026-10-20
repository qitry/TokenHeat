package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.anylyralabs.lyraproxy.R
import ui.feice.mochi.nothing.MochiNothingTheme
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.defaultTextStyles

/**
 * 品牌色。
 *
 * 外观走 Nothing 的单色 + 红点体系：黑白灰为骨，红色只留给「正在发生」的状态。
 * 旧的陶土色值仍保留，供少量固定图形（热力阶、图表）使用。
 */
object Claude {
    val Paper = Color(0xFFF7F7F7)
    val PaperDeep = Color(0xFFEDEDED)
    val PaperEdge = Color(0xFFDDDDDD)
    val Ink = Color(0xFF111111)
    val InkSoft = Color(0xFF5C5C5C)
    val InkFaint = Color(0xFF9A9A9A)

    val Clay = Color(0xFFD71921)
    val ClayDeep = Color(0xFFA8141B)
    val ClayWash = Color(0xFFFBE7E8)

    val Moss = Color(0xFF3F7A4A)
    val Rust = Color(0xFFD71921)
    val Amber = Color(0xFF9A6B1F)

    val Night = Color(0xFF000000)
    val NightRaised = Color(0xFF151515)
    val NightEdge = Color(0xFF2A2A2A)
    val NightText = Color(0xFFF2F2F2)
    val NightSoft = Color(0xFFA6A6A6)
    val NightFaint = Color(0xFF6E6E6E)
    val ClayNight = Color(0xFFFF4D55)
}

/** HarmonyOS Sans：正文与标题统一用它，避免系统字体在不同机型上跑偏。 */
private val HarmonySans = FontFamily(
    Font(R.font.harmonyos_sans_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_medium, FontWeight.Medium),
)

private fun harmony(
    size: Float,
    line: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = HarmonySans,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
)

/**
 * 应用字阶。
 *
 * 比 Mochi 默认值整体小一档：默认字阶是按 iOS 大标题排的，放在这种
 * 信息密度较高的工具类界面里会显得松散。这里只改字号 / 行高 / 字体，
 * 形状、动效与交互仍由 mochi 负责。
 */
fun lyraTextStyles(): MochiTextStyles {
    val base = defaultTextStyles()
    return base.copy(
        displayLarge = harmony(30f, 36f, FontWeight.Bold),
        displayMedium = harmony(26f, 32f, FontWeight.Bold),
        displaySmall = harmony(24f, 30f, FontWeight.Bold),
        headlineLarge = harmony(21f, 27f, FontWeight.Medium),
        headlineMedium = harmony(19f, 25f, FontWeight.Medium),
        headlineSmall = harmony(17f, 23f, FontWeight.Medium),
        titleLarge = harmony(16f, 22f, FontWeight.Medium),
        titleMedium = harmony(14f, 20f, FontWeight.Medium),
        titleSmall = harmony(13f, 18f, FontWeight.Medium),
        bodyLarge = harmony(15f, 21f),
        bodyMedium = harmony(13f, 19f),
        bodySmall = harmony(12f, 17f),
        labelLarge = harmony(13f, 18f, FontWeight.Medium),
        labelMedium = harmony(12f, 16f, FontWeight.Medium),
        labelSmall = harmony(11f, 15f, FontWeight.Medium),
        largeTitle = harmony(28f, 34f, FontWeight.Bold),
        navTitle = harmony(16f, 21f, FontWeight.Medium),
        body = harmony(15f, 21f),
        subhead = harmony(13f, 18f),
        footnote = harmony(12f, 16f),
    )
}

/** 页面重音：大号标题。 */
val DisplaySerif: TextStyle
    get() = lyraTextStyles().displayLarge

/** 次级重音：读数用。 */
val ReadoutSerif: TextStyle
    get() = lyraTextStyles().displaySmall

/** 等宽只用于密钥、端口这类需要逐字符比对的短串。 */
val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    lineHeight = 17.sp,
)

/** 当前是否暗色；全局统一用这一处判断，避免各处各写一套。 */
@Composable
fun isClaudeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * 应用主题：Nothing 外观（单色 + 红点）+ mochi 的形状与动效。
 *
 * Nothing 主题只替换配色、字体与语义图标，布局、手势和转场仍是 mochi 的，
 * 所以 HDR 高光、融球、边缘流光这些效果都能直接生效。
 */
@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val styles = remember { lyraTextStyles() }
    MochiNothingTheme(isDark = darkTheme, textStyles = styles) {
        val mochi = MochiTheme.colorScheme
        MaterialTheme(
            colorScheme = mochi.toMaterialScheme(),
            typography = styles.toMaterialTypography(),
            content = content,
        )
    }
}

/** 把 Mochi 的色板映射成 Material3 的 colorScheme，供旧页面过渡期使用。 */
private fun MochiColorScheme.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}

/** Mochi 字阶 → Material3 Typography。 */
private fun MochiTextStyles.toMaterialTypography() = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
