package app.anylyralabs.lyraproxy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 视觉语言：暖纸账本。
 *
 * LyraProxy 是一台跑在本机的网关，界面要像一张摊开的账页：底色是温的纸，
 * 文字是墨，只有一个陶土色在说话——它只表示「活的」东西：正在运行、
 * 正在计费、有数据在流动。其余一切都靠字号与留白分层，不用描边和阴影。
 *
 * 衬线体只出现在两处：问候语与今日读数。这是整页唯一的视觉重音，
 * 别处一律无衬线，避免字体互相抢戏。
 */
object Claude {

    // 纸与墨（亮色）
    val Paper = Color(0xFFFAF9F5)
    val PaperDeep = Color(0xFFF0EEE6)
    val PaperEdge = Color(0xFFE6E3DA)
    val Ink = Color(0xFF1F1E1D)
    val InkSoft = Color(0xFF6B6862)
    val InkFaint = Color(0xFF9C978D)

    // 陶土：唯一的强调色
    val Clay = Color(0xFFC96442)
    val ClayDeep = Color(0xFFA84E32)
    val ClayWash = Color(0xFFF6E8E1)

    // 语义（低饱和，只在状态里出现）
    val Moss = Color(0xFF5C7A52)
    val Rust = Color(0xFFA8382B)
    val Amber = Color(0xFF9A6B1F)

    // 夜（暗色）
    val Night = Color(0xFF262624)
    val NightRaised = Color(0xFF30302E)
    val NightEdge = Color(0xFF3E3E3B)
    val NightText = Color(0xFFF5F4EE)
    val NightSoft = Color(0xFFB4B0A8)
    val NightFaint = Color(0xFF807D76)
    val ClayNight = Color(0xFFD97757)
}

// -----------------------------------------------------------------------------
// 字体：衬线只给「问候」与「今日读数」，其余无衬线。
// -----------------------------------------------------------------------------

/** 页面重音：大号衬线，行距放松，接近印刷物的呼吸感。 */
val DisplaySerif = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.6).sp,
)

/** 次级重音：读数用，比问候小一号，仍是衬线。 */
val ReadoutSerif = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 50.sp,
    letterSpacing = (-1).sp,
)

/** 等宽只用于密钥、端口这类需要逐字符比对的短串。 */
val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 11.5.sp,
    lineHeight = 17.sp,
)

private val ClaudeTypography = Typography(
    displayLarge = DisplaySerif,
    displayMedium = DisplaySerif.copy(fontSize = 27.sp, lineHeight = 34.sp),
    displaySmall = ReadoutSerif.copy(fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val LightScheme = lightColorScheme(
    primary = Claude.Clay,
    onPrimary = Color(0xFFFFFBF8),
    primaryContainer = Claude.ClayWash,
    onPrimaryContainer = Claude.ClayDeep,
    secondary = Claude.InkSoft,
    onSecondary = Claude.Paper,
    secondaryContainer = Claude.PaperDeep,
    onSecondaryContainer = Claude.Ink,
    tertiary = Claude.Moss,
    onTertiary = Claude.Paper,
    background = Claude.Paper,
    onBackground = Claude.Ink,
    surface = Claude.Paper,
    onSurface = Claude.Ink,
    surfaceVariant = Claude.PaperDeep,
    onSurfaceVariant = Claude.InkSoft,
    surfaceContainerLow = Claude.Paper,
    surfaceContainerHigh = Claude.PaperDeep,
    outline = Claude.PaperEdge,
    outlineVariant = Claude.PaperEdge,
    error = Claude.Rust,
    onError = Color(0xFFFFFBF8),
    errorContainer = Color(0xFFF7E4E0),
    onErrorContainer = Claude.Rust,
    scrim = Claude.Ink,
)

private val DarkScheme = darkColorScheme(
    primary = Claude.ClayNight,
    onPrimary = Color(0xFF241512),
    primaryContainer = Color(0xFF3A2A24),
    onPrimaryContainer = Color(0xFFF0C6B5),
    secondary = Claude.NightSoft,
    onSecondary = Claude.Night,
    secondaryContainer = Claude.NightRaised,
    onSecondaryContainer = Claude.NightText,
    tertiary = Color(0xFF9DB68F),
    onTertiary = Claude.Night,
    background = Claude.Night,
    onBackground = Claude.NightText,
    surface = Claude.Night,
    onSurface = Claude.NightText,
    surfaceVariant = Claude.NightRaised,
    onSurfaceVariant = Claude.NightSoft,
    surfaceContainerLow = Claude.Night,
    surfaceContainerHigh = Claude.NightRaised,
    outline = Claude.NightEdge,
    outlineVariant = Claude.NightEdge,
    error = Color(0xFFE39B8F),
    onError = Claude.Night,
    errorContainer = Color(0xFF3D2320),
    onErrorContainer = Color(0xFFF0BCB3),
    scrim = Color(0xFF000000),
)

/** 当前是否暗色；全局统一用这一处判断，避免各处各写一套。 */
@Composable
fun isClaudeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ClaudeTypography,
        content = content,
    )
}
