package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiTextStyles
import androidx.compose.runtime.CompositionLocalProvider
import ui.feice.mochi.theme.LocalMochiControlPalette
import ui.feice.mochi.theme.MochiControlPalette
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.defaultTextStyles
import ui.feice.mochi.theme.lightColorScheme

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
fun isClaudeDark(): Boolean = MochiTheme.colorScheme.isDark

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
    val scheme = remember(darkTheme) { nothingScheme(darkTheme) }
    // Nothing 的控件外观：白色拇指、中性轨道、灰色行尾箭头。
    val controls = remember(darkTheme) {
        if (darkTheme) {
            MochiControlPalette(
                thumb = Color.White,
                inactiveTrack = Color(0xFF666666),
                sliderThumb = Color.White,
                listChevron = Color(0xFFA8A8A8),
            )
        } else {
            MochiControlPalette(thumb = Color.White, listChevron = Color(0xFF666666))
        }
    }
    CompositionLocalProvider(LocalMochiControlPalette provides controls) {
        MochiTheme(colors = scheme, textStyles = styles, content = content)
    }
}

/** Nothing 外观：黑白灰 + 红点，从 Mochi 的默认色阶派生后逐项覆盖。 */
private fun nothingScheme(isDark: Boolean): MochiColorScheme = if (isDark) {
    darkColorScheme().copy(
        primary = Color(0xFFD71921),
        onPrimary = Color.White,
        inversePrimary = Color(0xFF9F1017),
        primaryContainer = Color(0xFF353535),
        onPrimaryContainer = Color(0xFFF2F2F2),
        secondary = Color(0xFFD0D0D0),
        onSecondary = Color(0xFF151515),
        secondaryContainer = Color(0xFF303030),
        onSecondaryContainer = Color(0xFFE5E5E5),
        tertiary = Color(0xFFFFB3B7),
        onTertiary = Color(0xFF680A10),
        background = Color.Black,
        onBackground = Color(0xFFF2F2F2),
        surface = Color(0xFF1C1C1C),
        onSurface = Color(0xFFF2F2F2),
        surfaceVariant = Color(0xFF353535),
        onSurfaceVariant = Color(0xFFA8A8A8),
        surfaceTint = Color.Transparent,
        outline = Color(0xFF888888),
        outlineVariant = Color(0xFF424242),
        error = Color(0xFFFF6B6B),
        onError = Color.Black,
        scrim = Color.Black,
        surfaceBright = Color(0xFF353535),
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF141414),
        surfaceContainer = Color(0xFF1C1C1C),
        surfaceContainerHigh = Color(0xFF282828),
        surfaceContainerHighest = Color(0xFF353535),
    )
} else {
    lightColorScheme().copy(
        primary = Claude.Clay,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4E4),
        onPrimaryContainer = Color(0xFF1C1C1C),
        secondary = Color(0xFF454545),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9E9E9),
        onSecondaryContainer = Color(0xFF252525),
        tertiary = Claude.Clay,
        onTertiary = Color.White,
        background = Color(0xFFF2F2F2),
        onBackground = Color(0xFF151515),
        surface = Color(0xFFF2F2F2),
        onSurface = Color(0xFF151515),
        surfaceVariant = Color(0xFFE4E4E4),
        onSurfaceVariant = Color(0xFF666666),
        surfaceTint = Color.Transparent,
        outline = Color(0xFF777777),
        outlineVariant = Color(0xFFD7D7D7),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFE4E4E4),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F8F8),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFE9E9E9),
        surfaceContainerHighest = Color(0xFFE0E0E0),
    )
}
