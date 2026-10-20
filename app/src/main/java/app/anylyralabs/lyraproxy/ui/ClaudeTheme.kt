package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.ThemeController

/**
 * 品牌色板。
 *
 * 主题由 mochi-compose 生成：这里只提供种子色（陶土）和取色算法，
 * 明暗两套色阶全部交给 MD3 Expressive 的 tonal palette 推导。
 * 旧的纸墨色值保留给少数需要固定颜色的地方（图表、热力阶）。
 */
object Claude {
    val Paper = Color(0xFFFAF9F5)
    val PaperDeep = Color(0xFFF0EEE6)
    val PaperEdge = Color(0xFFE6E3DA)
    val Ink = Color(0xFF1F1E1D)
    val InkSoft = Color(0xFF6B6862)
    val InkFaint = Color(0xFF9C978D)

    val Clay = Color(0xFFC96442)
    val ClayDeep = Color(0xFFA84E32)
    val ClayWash = Color(0xFFF6E8E1)

    val Moss = Color(0xFF5C7A52)
    val Rust = Color(0xFFA8382B)
    val Amber = Color(0xFF9A6B1F)

    val Night = Color(0xFF262624)
    val NightRaised = Color(0xFF30302E)
    val NightEdge = Color(0xFF3E3E3B)
    val NightText = Color(0xFFF5F4EE)
    val NightSoft = Color(0xFFB4B0A8)
    val NightFaint = Color(0xFF807D76)
    val ClayNight = Color(0xFFD97757)
}

/** 页面重音：Mochi 的 display 级字阶。 */
val DisplaySerif: TextStyle
    get() = defaultMochiTextStyles.displayLarge

/** 次级重音：读数用，比问候小一号。 */
val ReadoutSerif: TextStyle
    get() = defaultMochiTextStyles.displaySmall

/** 等宽只用于密钥、端口这类需要逐字符比对的短串。 */
val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    lineHeight = 17.sp,
)

private val defaultMochiTextStyles: MochiTextStyles =
    ui.feice.mochi.theme.defaultTextStyles()

/** 当前是否暗色；全局统一用这一处判断，避免各处各写一套。 */
@Composable
fun isClaudeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * 应用主题：MochiTheme 生成配色 / 字阶 / 形状，MaterialTheme 再把它桥接给
 * 尚未迁移到 Mochi 组件的页面，保证两套组件读到的是同一份色板。
 */
@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val controller = remember {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.System,
            keyColor = Claude.Clay,
            paletteStyle = PaletteStyle.TonalSpot,
        )
    }
    // 用户手动切换明暗时，把模式锁到对应的一侧；否则跟随系统。
    SideEffect {
        controller.colorSchemeMode =
            if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
    }

    MochiTheme(controller = controller) {
        val mochi = MochiTheme.colorScheme
        val styles = MochiTheme.textStyles
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
