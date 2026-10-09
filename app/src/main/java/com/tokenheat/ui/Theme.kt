package com.tokenheat.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object ZincColors {
    val Zinc50 = Color(0xFFFAFAFA)
    val Zinc100 = Color(0xFFF4F4F5)
    val Zinc200 = Color(0xFFE4E4E7)
    val Zinc300 = Color(0xFFD4D4D8)
    val Zinc400 = Color(0xFFA1A1AA)
    val Zinc500 = Color(0xFF71717A)
    val Zinc600 = Color(0xFF52525B)
    val Zinc700 = Color(0xFF3F3F46)
    val Zinc800 = Color(0xFF27272A)
    val Zinc900 = Color(0xFF18181B)
    val Zinc950 = Color(0xFF09090B)

    // Subdued status accents
    val Success = Color(0xFF059669)
    val SuccessBgLight = Color(0xFFECFDF5)
    val SuccessBgDark = Color(0xFF064E3B)

    val Warning = Color(0xFFD97706)
    val WarningBgLight = Color(0xFFFFFBEB)
    val WarningBgDark = Color(0xFF451A03)

    val Danger = Color(0xFFDC2626)
    val DangerBgLight = Color(0xFFFEF2F2)
    val DangerBgDark = Color(0xFF450A0A)
}

val ShadcnLightScheme = lightColorScheme(
    primary = ZincColors.Zinc900,
    onPrimary = Color.White,
    primaryContainer = ZincColors.Zinc100,
    onPrimaryContainer = ZincColors.Zinc900,
    secondary = ZincColors.Zinc700,
    onSecondary = Color.White,
    secondaryContainer = ZincColors.Zinc200,
    onSecondaryContainer = ZincColors.Zinc900,
    tertiary = ZincColors.Zinc600,
    onTertiary = Color.White,
    tertiaryContainer = ZincColors.Zinc100,
    onTertiaryContainer = ZincColors.Zinc900,
    background = ZincColors.Zinc50,
    onBackground = ZincColors.Zinc900,
    surface = Color.White,
    onSurface = ZincColors.Zinc900,
    surfaceVariant = ZincColors.Zinc100,
    onSurfaceVariant = ZincColors.Zinc500,
    outline = ZincColors.Zinc300,
    outlineVariant = ZincColors.Zinc200,
    error = ZincColors.Danger,
    onError = Color.White,
    errorContainer = ZincColors.DangerBgLight,
    onErrorContainer = ZincColors.Danger,
)

val ShadcnDarkScheme = darkColorScheme(
    primary = ZincColors.Zinc50,
    onPrimary = ZincColors.Zinc950,
    primaryContainer = ZincColors.Zinc800,
    onPrimaryContainer = ZincColors.Zinc50,
    secondary = ZincColors.Zinc400,
    onSecondary = ZincColors.Zinc950,
    secondaryContainer = ZincColors.Zinc800,
    onSecondaryContainer = ZincColors.Zinc100,
    tertiary = ZincColors.Zinc500,
    onTertiary = ZincColors.Zinc50,
    tertiaryContainer = ZincColors.Zinc900,
    onTertiaryContainer = ZincColors.Zinc100,
    background = ZincColors.Zinc950,
    onBackground = ZincColors.Zinc50,
    surface = ZincColors.Zinc900,
    onSurface = ZincColors.Zinc50,
    surfaceVariant = ZincColors.Zinc800,
    onSurfaceVariant = ZincColors.Zinc400,
    outline = ZincColors.Zinc700,
    outlineVariant = ZincColors.Zinc800,
    error = Color(0xFFF87171),
    onError = ZincColors.Zinc950,
    errorContainer = ZincColors.DangerBgDark,
    onErrorContainer = Color(0xFFFCA5A5),
)

@Composable
fun TokenHeatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ShadcnDarkScheme
        else -> ShadcnLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * FlClash 目标模式的设计 token（对标 lib/common/shape.dart）：
 * - 全部圆角走超椭圆连续曲率，禁止 Stadium/RoundedRectangle；
 * - 卡片 md=16，弹窗 xxl=28，输入框 md=16，按钮全胶囊；
 * - 容器色阶走 M3 surfaceContainerLow → Highest，选中态 secondaryContainer。
 */
object FlCorner {
    const val Xs = 4
    const val Sm = 8
    const val Md = 16
    const val Lg = 20
    const val Xl = 24
    const val Xxl = 28
}

/**
 * FlClash 形状映射（M3 `Shapes()` 只接受 `CornerBasedShape`，超椭圆无法注入
 * 主题，只能在各组件 `shape =` 处显式传入）：
 * - 卡片/容器 `SquircleCornerShape(Md)`，弹窗 `Xxl`，输入框 `Md`，
 *   小芯片 `Sm`，按钮 `SquirclePillShape`。
 */
