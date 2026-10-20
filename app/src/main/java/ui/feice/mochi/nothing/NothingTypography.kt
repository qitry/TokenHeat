// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0

package ui.feice.mochi.nothing

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import app.anylyralabs.lyraproxy.R
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.defaultTextStyles

/** 正文用 HarmonyOS Sans（本 App 自带），显示字体交给系统衬线。 */
internal val NothingBodyFamily = FontFamily(
    Font(R.font.harmonyos_sans_regular, FontWeight.Normal),
    Font(R.font.harmonyos_sans_medium, FontWeight.Medium),
)
private val DisplayFamily = FontFamily.Serif
private val DotFamily = FontFamily(Font(R.font.nothing_dot))

/**
 * Replace families only. Size, weight, line height and platform font scaling remain the caller's.
 * @param serifDisplay titles (large / nav title, display, headline) use the system serif; false keeps them in HarmonyOS Sans
 */
public fun nothingTextStyles(base: MochiTextStyles = defaultTextStyles(), serifDisplay: Boolean = true): MochiTextStyles {
    // System serif supplies a matching Latin/CJK family through the device's font fallback.
    fun TextStyle.displayFamily(): TextStyle = copy(fontFamily = if (serifDisplay) DisplayFamily else NothingBodyFamily)
    return nothingStyles(base) { it.displayFamily() }
}

private inline fun nothingStyles(base: MochiTextStyles, display: (TextStyle) -> TextStyle): MochiTextStyles = base.copy(
    displayLarge = base.displayLarge.let(display),
    displayMedium = base.displayMedium.let(display),
    displaySmall = base.displaySmall.let(display),
    headlineLarge = base.headlineLarge.let(display),
    headlineMedium = base.headlineMedium.let(display),
    headlineSmall = base.headlineSmall.let(display),
    titleLarge = base.titleLarge.copy(fontFamily = NothingBodyFamily),
    titleMedium = base.titleMedium.copy(fontFamily = NothingBodyFamily),
    titleSmall = base.titleSmall.copy(fontFamily = NothingBodyFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = NothingBodyFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = NothingBodyFamily),
    bodySmall = base.bodySmall.copy(fontFamily = NothingBodyFamily),
    labelLarge = base.labelLarge.copy(fontFamily = NothingBodyFamily),
    labelMedium = base.labelMedium.copy(fontFamily = NothingBodyFamily),
    labelSmall = base.labelSmall.copy(fontFamily = NothingBodyFamily),
    largeTitle = base.largeTitle.let(display),
    // The compact title is the collapsed large title, so it keeps the same serif family.
    navTitle = base.navTitle.let(display),
    body = base.body.copy(fontFamily = NothingBodyFamily),
    subhead = base.subhead.copy(fontFamily = NothingBodyFamily),
    footnote = base.footnote.copy(fontFamily = NothingBodyFamily),
)

/** Opt-in for standalone numeric displays and short Latin labels, never body paragraphs. */
public fun nothingDotTextStyle(base: TextStyle): TextStyle = base.copy(fontFamily = DotFamily, fontSynthesis = FontSynthesis.None)
