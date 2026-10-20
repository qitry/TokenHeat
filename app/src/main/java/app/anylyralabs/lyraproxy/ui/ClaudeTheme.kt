package app.anylyralabs.lyraproxy.ui

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.anylyralabs.lyraproxy.R
import ui.feice.mochi.effect.content.MochiThemeRevealState
import ui.feice.mochi.nothing.NothingIcons
import ui.feice.mochi.nothing.nothingColorScheme
import ui.feice.mochi.nothing.nothingControlPalette
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.DefaultKeyColor
import ui.feice.mochi.theme.LocalMochiControlPalette
import ui.feice.mochi.theme.LocalMochiIconProvider
import ui.feice.mochi.theme.MochiGraphics
import ui.feice.mochi.theme.MochiGraphicsLevel
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.ThemeController
import ui.feice.mochi.theme.animateMochiColorScheme
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

/** 外观流派：Mochi 用种子色生成整套配色，Nothing 用官方的黑白灰 + 红点。 */
internal enum class LyraStyle { Mochi, Nothing }

/** 正文与标题的 HarmonyOS Sans：避免系统字体在不同机型上跑偏。 */
private val HarmonySans = FontFamily(
    Font(R.font.harmonyos_sans_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_medium, FontWeight.Medium),
)

/** 显示字体：系统衬线（宋体），和 Nothing 的衬线标题规则一致。 */
private val DisplayFamily = FontFamily.Serif

private fun harmony(
    size: Float,
    line: Float,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
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
 *
 * @param serifDisplay 大标题、导航标题、display / headline 用系统衬线；正文始终 HarmonyOS Sans
 */
fun lyraTextStyles(serifDisplay: Boolean = true): MochiTextStyles {
    fun TextStyle.displayFamily(): TextStyle = copy(fontFamily = if (serifDisplay) DisplayFamily else HarmonySans)
    return defaultTextStyles().copy(
        displayLarge = harmony(30f, 36f, FontWeight.Bold).displayFamily(),
        displayMedium = harmony(26f, 32f, FontWeight.Bold).displayFamily(),
        displaySmall = harmony(24f, 30f, FontWeight.Bold).displayFamily(),
        headlineLarge = harmony(21f, 27f, FontWeight.Medium).displayFamily(),
        headlineMedium = harmony(19f, 25f, FontWeight.Medium).displayFamily(),
        headlineSmall = harmony(17f, 23f, FontWeight.Medium).displayFamily(),
        titleLarge = harmony(16f, 22f, FontWeight.Medium),
        titleMedium = harmony(14f, 20f, FontWeight.Medium),
        titleSmall = harmony(13f, 18f, FontWeight.Medium),
        bodyLarge = harmony(15f, 21f),
        bodyMedium = harmony(13f, 19f),
        bodySmall = harmony(12f, 17f),
        labelLarge = harmony(13f, 18f, FontWeight.Medium),
        labelMedium = harmony(12f, 16f, FontWeight.Medium),
        labelSmall = harmony(11f, 15f, FontWeight.Medium),
        largeTitle = harmony(28f, 34f, FontWeight.Bold).displayFamily(),
        navTitle = harmony(16f, 21f, FontWeight.Medium).displayFamily(),
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
 * 外观设置：风格、明暗、衬线、点阵图标、图形档位，以及 Mochi 的种子色 / 取色算法 / 对比度。
 *
 * 持久化思路照抄 demo 的 DemoAppearance：属性都是可观察状态，theme 侧用
 * `snapshotFlow` 把每次改动写回 SharedPreferences。
 */
@Stable
internal class LyraAppearance(private val prefs: SharedPreferences) {
    // 新装默认 Nothing 外观
    var style by mutableStateOf(enumValue(prefs.getString("style", null), LyraStyle.Nothing))
    var mode by mutableStateOf(enumValue(prefs.getString("mode", null), ColorSchemeMode.System))
    var serif by mutableStateOf(prefs.getBoolean("serif", true))
    var dotIcons by mutableStateOf(prefs.getBoolean("dotIcons", false))

    /** 实验性 HDR 高光；默认沿用 App 原来的行为（开）。 */
    private var hdrState by mutableStateOf(prefs.getBoolean("hdr", true))

    var hdr: Boolean
        get() = hdrState
        set(value) {
            hdrState = value
            MochiHdr.enabled = value
        }

    /** 整套换装（主题、明暗）的揭开转场，MainActivity 在最外层接上。 */
    var reveal: MochiThemeRevealState? = null

    val controller = ThemeController(
        enumValue(prefs.getString("mochiMode", null), ColorSchemeMode.MonetSystem),
        Color(prefs.getInt("mochiSeed", DefaultKeyColor.toArgb())),
        enumValue(prefs.getString("mochiPalette", null), PaletteStyle.TonalSpot),
        Double.fromBits(prefs.getLong("mochiContrastBits", 0L)),
    )

    init {
        // 图形档位是库的全局状态，这里只负责存取；以前存过的「低」档按「中」处理。
        val saved = prefs.getString("graphics", null)
        MochiGraphics.level = if (saved == "Low") MochiGraphicsLevel.Mid else enumValue(saved, MochiGraphicsLevel.High)
        MochiHdr.enabled = hdr
    }

    /** 当前外观下的明暗（系统取色也算跟随系统）。 */
    fun isDark(systemDark: Boolean): Boolean =
        if (style == LyraStyle.Nothing) mode.isDark(systemDark) else controller.colorSchemeMode.isDark(systemDark)

    fun snapshot(): List<Any> = listOf(
        style,
        mode,
        serif,
        dotIcons,
        hdr,
        MochiGraphics.level,
        controller.colorSchemeMode,
        controller.keyColor,
        controller.paletteStyle,
        controller.contrastLevel,
    )

    fun persist() {
        prefs.edit()
            .putString("style", style.name)
            .putString("mode", mode.name)
            .putBoolean("serif", serif)
            .putBoolean("dotIcons", dotIcons)
            .putBoolean("hdr", hdr)
            .putString("graphics", MochiGraphics.level.name)
            .putString("mochiMode", controller.colorSchemeMode.name)
            .putInt("mochiSeed", controller.keyColor.toArgb())
            .putString("mochiPalette", controller.paletteStyle.name)
            .putLong("mochiContrastBits", controller.contrastLevel.toBits())
            .apply()
    }
}

internal fun ColorSchemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> systemDark
    ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
    ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
}

private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

internal val LocalLyraAppearance = staticCompositionLocalOf<LyraAppearance?> { null }

@Composable
internal fun rememberLyraAppearance(): LyraAppearance {
    val context = LocalContext.current
    val appearance = remember(context) {
        LyraAppearance(context.getSharedPreferences("lyra_appearance", 0))
    }
    LaunchedEffect(appearance) {
        snapshotFlow { appearance.snapshot() }.collect { appearance.persist() }
    }
    return appearance
}

/**
 * 应用主题：Nothing 外观（单色 + 红点）或 Mochi 的种子色配色，加上 mochi 的形状与动效。
 *
 * Nothing 主题只替换配色、字体与语义图标，布局、手势和转场仍是 mochi 的，
 * 所以 HDR 高光、融球、边缘流光这些效果都能直接生效。
 *
 * @param darkTheme 没有 [LocalLyraAppearance] 时的明暗回退；正常运行时由外观设置决定
 */
@Composable
fun ClaudeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appearance = LocalLyraAppearance.current
    val nothing = appearance == null || appearance.style == LyraStyle.Nothing
    val dark = appearance?.isDark(darkTheme) ?: darkTheme
    // 换种子色等连续变化走配色插值；揭开转场进行中一步到位（洞里露出来的必须已经是最终颜色）
    val revealing = appearance?.reveal?.isRevealing == true
    val target = if (nothing) nothingColorScheme(dark) else appearance!!.controller.currentColorScheme()
    val colors = animateMochiColorScheme(target, animate = !revealing)
    val serif = appearance?.serif ?: true
    val textStyles = remember(serif) { lyraTextStyles(serifDisplay = serif) }
    CompositionLocalProvider(
        LocalMochiControlPalette provides if (nothing) nothingControlPalette(dark) else null,
        LocalMochiIconProvider provides if (nothing && appearance?.dotIcons == true) NothingIcons else null,
    ) {
        // Keep the content at one call site so switching appearance retains navigation and scroll state.
        MochiTheme(colors = colors, textStyles = textStyles, content = content)
    }
}
