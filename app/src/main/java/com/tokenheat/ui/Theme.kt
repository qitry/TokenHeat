package com.tokenheat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ShadcnDarkScheme else ShadcnLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
