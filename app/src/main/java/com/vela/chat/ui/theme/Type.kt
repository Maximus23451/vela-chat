package com.vela.chat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

private val Default = Typography()

val VelaTypography = Typography(
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(lineHeight = 24.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Monospace style used by code blocks and inline code. */
val MonoTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

/**
 * Returns [VelaTypography] with every text style scaled by [scale], honoring
 * the user's font-size preference (1.0f = unchanged). Font and line sizes that
 * are unspecified are left alone.
 */
fun scaledVelaTypography(scale: Float): Typography {
    if (scale == 1.0f) return VelaTypography

    fun TextStyle.scaled(): TextStyle {
        val newFont = if (fontSize.isSpecified) fontSize * scale else fontSize
        val newLineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight
        return copy(fontSize = newFont, lineHeight = newLineHeight)
    }

    val base = VelaTypography
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}
