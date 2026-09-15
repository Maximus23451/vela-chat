package com.vela.chat.ui.theme.nova

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.vela.chat.data.settings.AppSettings

/**
 * Nova semantic color scheme. Wraps the full Material 3 [ColorScheme] (kept in
 * [material] so every existing `MaterialTheme.colorScheme` consumer keeps
 * working) and adds the Nova-specific slots for glassmorphism, the background
 * gradient and status colors. Use [LocalNovaColors.current] to read it.
 */
data class NovaColorScheme(
    /** The complete Material 3 palette this scheme is built on. */
    val material: ColorScheme,

    /** Pre-alpha'd translucent fill for glass surfaces ([GlassSurface], chrome cards). */
    val glassSurface: Color,

    /** Pre-alpha'd hairline color for glass borders. */
    val glassBorder: Color,

    /** Top color of the subtle full-screen vertical gradient behind content. */
    val gradientStart: Color,

    /** Bottom color of the subtle full-screen vertical gradient behind content. */
    val gradientEnd: Color,

    /** Accent for streaming/generation indicators (pulsing dots, "generating" chips). */
    val streamIndicator: Color,

    /** Positive status (connection healthy, export finished, saved). */
    val success: Color,

    /** Cautionary status (degraded connection, approaching limits). */
    val warning: Color,

    /** Destructive/error status (mirrors `material.error` for non-M3 slots). */
    val danger: Color,
)

/** Convenience accessors mirroring the Material 3 palette. */
val NovaColorScheme.primary: Color get() = material.primary
val NovaColorScheme.onPrimary: Color get() = material.onPrimary
val NovaColorScheme.surface: Color get() = material.surface
val NovaColorScheme.onSurface: Color get() = material.onSurface
val NovaColorScheme.onSurfaceVariant: Color get() = material.onSurfaceVariant

/**
 * Provides the active [NovaColorScheme]; supplied by [NovaTheme]. The default
 * is the light, Violet-seed scheme so previews and code outside the theme
 * still render sensibly.
 */
val LocalNovaColors = staticCompositionLocalOf { novaColorScheme(AppSettings(), dark = false, isAmoled = false) }

/** Alpha of light-theme glass fills (frosted white over the background gradient). */
private const val GlassAlphaLight = 0.62f

/** Alpha of AMOLED glass fills — softer than dark glass to avoid banding on true black. */
private const val GlassAlphaAmoled = 0.08f

/** How strongly [NovaColorScheme.primary] tints the bottom of the light gradient. */
private const val GradientTintLight = 0.08f

/** How strongly [NovaColorScheme.primary] tints the bottom of the dark gradient. */
private const val GradientTintDark = 0.12f

private val SuccessLight = Color(0xFF2E7D32)
private val SuccessDark = Color(0xFF81C784)
private val WarningLight = Color(0xFFB26A00)
private val WarningDark = Color(0xFFFFB74D)
private val DangerLight = Color(0xFFBA1A1A)
private val DangerDark = Color(0xFFFFB4AB)

/**
 * Builds a [NovaColorScheme] from the user's settings, mapping the selected
 * [com.vela.chat.data.settings.ThemePreset] (per light/dark mode) plus the
 * custom/named accent seed into the Nova slots. Dynamic color cannot be
 * resolved here (it needs a composable context); for that path [NovaTheme]
 * builds the material scheme first and calls the [ColorScheme] overload.
 */
fun novaColorScheme(settings: AppSettings, dark: Boolean, isAmoled: Boolean): NovaColorScheme {
    val preset = if (dark) settings.darkThemePreset else settings.lightThemePreset
    val customSeed = settings.customAccentColor?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }
    val seed = customSeed ?: Color(settings.accentColor.seed)
    return novaColorScheme(
        material = schemeForPreset(preset, dark, seed),
        dark = dark,
        isAmoled = isAmoled,
    )
}

/**
 * Builds a [NovaColorScheme] from an already-resolved Material 3 [ColorScheme]
 * (either a preset scheme or a dynamic-color scheme) and derives the Nova
 * slots: glass fills/borders from surface translucency, gradient stops tinted
 * with the primary, and fixed semantic status colors. With [isAmoled] the
 * material surfaces are pushed to true black first.
 */
fun novaColorScheme(material: ColorScheme, dark: Boolean, isAmoled: Boolean): NovaColorScheme {
    val base = if (isAmoled) material.toAmoled() else material
    val glassAlpha = when {
        isAmoled -> GlassAlphaAmoled
        dark -> NovaTokens.Blur.glassAlpha
        else -> GlassAlphaLight
    }
    return NovaColorScheme(
        material = base,
        glassSurface = Color.White.copy(alpha = glassAlpha),
        glassBorder = (if (dark) Color.White else Color.Black).copy(alpha = NovaTokens.Blur.glassBorderAlpha),
        gradientStart = base.background,
        gradientEnd = lerp(base.background, base.primary, if (dark) GradientTintDark else GradientTintLight),
        streamIndicator = base.primary,
        success = if (dark) SuccessDark else SuccessLight,
        warning = if (dark) WarningDark else WarningLight,
        danger = if (dark) DangerDark else DangerLight,
    )
}

/**
 * Vertical background gradient for the whole app: [NovaColorScheme.gradientStart]
 * at the top to [NovaColorScheme.gradientEnd] at the bottom. Screens that want
 * the glass treatment draw themselves transparently on top of this.
 */
fun NovaColorScheme.backgroundGradient(): Brush = Brush.verticalGradient(listOf(gradientStart, gradientEnd))
