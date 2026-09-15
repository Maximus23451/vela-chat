package com.vela.chat.ui.theme.nova

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.vela.chat.data.settings.ThemePreset

/**
 * Nova palette machinery: seed-based M3 scheme generation plus the 15 named
 * theme presets. Moved verbatim from `ui/theme/Color.kt` so the whole color
 * system lives under `ui/theme/nova/`; `ui/theme/Color.kt` re-exports the
 * public entry points for source compatibility.
 */

/**
 * Builds a Material 3 [ColorScheme] from a single accent seed. Neutral roles
 * are taken from hand-tuned baselines; the primary/secondary/tertiary families
 * are derived from the seed by shifting HSL lightness to the canonical M3 tones.
 */
fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    fun tone(target: Int): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed.toArgb(), hsl)
        hsl[2] = target / 100f
        return Color(ColorUtils.HSLToColor(hsl))
    }

    return if (dark) {
        darkColorScheme(
            primary = tone(80),
            onPrimary = tone(20),
            primaryContainer = tone(30),
            onPrimaryContainer = tone(90),
            secondary = tone(70),
            onSecondary = tone(20),
            secondaryContainer = tone(28),
            onSecondaryContainer = tone(90),
            tertiary = tone(82),
            onTertiary = tone(22),
            tertiaryContainer = tone(34),
            onTertiaryContainer = tone(92),
            background = Color(0xFF131318),
            onBackground = Color(0xFFE5E1E9),
            surface = Color(0xFF131318),
            onSurface = Color(0xFFE5E1E9),
            surfaceVariant = Color(0xFF47464F),
            onSurfaceVariant = Color(0xFFC9C5D0),
            surfaceContainerLowest = Color(0xFF0E0E13),
            surfaceContainerLow = Color(0xFF1B1B21),
            surfaceContainer = Color(0xFF1F1F25),
            surfaceContainerHigh = Color(0xFF2A2930),
            surfaceContainerHighest = Color(0xFF35343B),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF47464F),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = tone(40),
            onPrimary = Color.White,
            primaryContainer = tone(90),
            onPrimaryContainer = tone(10),
            secondary = tone(40),
            onSecondary = Color.White,
            secondaryContainer = tone(90),
            onSecondaryContainer = tone(10),
            tertiary = tone(38),
            onTertiary = Color.White,
            tertiaryContainer = tone(92),
            onTertiaryContainer = tone(8),
            background = Color(0xFFFCF8FF),
            onBackground = Color(0xFF1B1B21),
            surface = Color(0xFFFCF8FF),
            onSurface = Color(0xFF1B1B21),
            surfaceVariant = Color(0xFFE4E1EC),
            onSurfaceVariant = Color(0xFF47464F),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF6F2FA),
            surfaceContainer = Color(0xFFF0ECF4),
            surfaceContainerHigh = Color(0xFFEAE7EF),
            surfaceContainerHighest = Color(0xFFE5E1E9),
            outline = Color(0xFF78767F),
            outlineVariant = Color(0xFFC9C5D0),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}

/** Push background/surface roles to true black for OLED panels. */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF101013),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E22),
)

/** Maps a [ThemePreset] to its Material 3 [ColorScheme]; [seedColor] is only used by [ThemePreset.MATERIAL_YOU]. */
fun schemeForPreset(preset: ThemePreset, dark: Boolean, seedColor: Color): ColorScheme {
    return when (preset) {
        ThemePreset.MATERIAL_YOU -> schemeFromSeed(seedColor, dark)
        ThemePreset.MIDNIGHT_BLUE -> if (dark) midnightDark() else midnightLight()
        ThemePreset.OPENWEBUI -> if (dark) openWebUiDark() else openWebUiLight()
        ThemePreset.CHATGPT -> if (dark) chatGptDark() else chatGptLight()
        ThemePreset.CLAUDE -> if (dark) claudeDark() else claudeLight()
        ThemePreset.NORD -> if (dark) nordDark() else nordLight()
        ThemePreset.DRACULA -> if (dark) draculaDark() else draculaLight()
        ThemePreset.CATPPUCCIN -> if (dark) catppuccinDark() else catppuccinLight()
        ThemePreset.SOLARIZED_DARK -> if (dark) solarizedDark() else solarizedLight()
        ThemePreset.HIGH_CONTRAST -> if (dark) highContrastDark() else highContrastLight()
        ThemePreset.GRUVBOX -> if (dark) gruvboxDark() else gruvboxLight()
        ThemePreset.TOKYO_NIGHT -> if (dark) tokyoNightDark() else tokyoNightLight()
        ThemePreset.ROSE_PINE -> if (dark) rosePineDark() else rosePineLight()
        ThemePreset.ONE_DARK -> if (dark) oneDarkDark() else oneDarkLight()
        ThemePreset.MONOKAI -> if (dark) monokaiDark() else monokaiLight()
    }
}

// ---- Presets Implementation ----

private fun midnightDark() = darkColorScheme(
    primary = Color(0xFF00E8FC),
    onPrimary = Color(0xFF00353A),
    primaryContainer = Color(0xFF004E55),
    onPrimaryContainer = Color(0xFFBEFAFF),
    secondary = Color(0xFF1B6CF3),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0A1128),
    surface = Color(0xFF0C1836),
    onSurface = Color(0xFFE5EAF5),
    surfaceVariant = Color(0xFF1B2A4A),
    onSurfaceVariant = Color(0xFFBCC5D5),
    outline = Color(0xFF8893A6)
)

private fun midnightLight() = lightColorScheme(
    primary = Color(0xFF1B6CF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E2FF),
    onPrimaryContainer = Color(0xFF001A40),
    secondary = Color(0xFF00E8FC),
    onSecondary = Color(0xFF00353A),
    background = Color(0xFFF0F4F8),
    surface = Color.White,
    onSurface = Color(0xFF101F30),
    surfaceVariant = Color(0xFFE0E8F0),
    onSurfaceVariant = Color(0xFF404F60),
    outline = Color(0xFF707F90)
)

private fun openWebUiDark() = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF64748B)
)

private fun openWebUiLight() = lightColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFF94A3B8)
)

private fun chatGptDark() = darkColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E6E56),
    onPrimaryContainer = Color(0xFFD2F4EB),
    secondary = Color(0xFFACACBE),
    onSecondary = Color(0xFF212121),
    background = Color(0xFF212121),
    surface = Color(0xFF2F2F2F),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF404041),
    onSurfaceVariant = Color(0xFFC2C2D6),
    outline = Color(0xFF8E8EA0)
)

private fun chatGptLight() = lightColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F4EB),
    onPrimaryContainer = Color(0xFF0B5844),
    secondary = Color(0xFF6E6E80),
    onSecondary = Color.White,
    background = Color.White,
    surface = Color(0xFFF7F7F8),
    onSurface = Color(0xFF2D3748),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF6E6E80),
    outline = Color(0xFFACACBE)
)

private fun claudeDark() = darkColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFF3E8FF),
    onSecondary = Color(0xFF191816),
    background = Color(0xFF191816),
    surface = Color(0xFF22211F),
    onSurface = Color(0xFFF5F2EB),
    surfaceVariant = Color(0xFF383633),
    onSurfaceVariant = Color(0xFFCDC8BF),
    outline = Color(0xFF999389)
)

private fun claudeLight() = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF191816),
    onSecondary = Color.White,
    background = Color(0xFFFBF0DF),
    surface = Color(0xFFF9E8D2),
    onSurface = Color(0xFF191816),
    surfaceVariant = Color(0xFFEADFC9),
    onSurfaceVariant = Color(0xFF5C5850),
    outline = Color(0xFF8C8578)
)

private fun nordDark() = darkColorScheme(
    primary = Color(0xFF88C0D0),
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF434C5E),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF8FBCBB),
    onSecondary = Color(0xFF2E3440),
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFD8DEE9),
    surfaceVariant = Color(0xFF4C566A),
    onSurfaceVariant = Color(0xFFE5E9F0),
    outline = Color(0xFFD8DEE9)
)

private fun nordLight() = lightColorScheme(
    primary = Color(0xFF5E81AC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8DEE9),
    onPrimaryContainer = Color(0xFF2E3440),
    secondary = Color(0xFF81A1C1),
    onSecondary = Color(0xFF2E3440),
    background = Color(0xFFECEFF4),
    surface = Color(0xFFE5E9F0),
    onSurface = Color(0xFF2E3440),
    surfaceVariant = Color(0xFFD8DEE9),
    onSurfaceVariant = Color(0xFF4C566A),
    outline = Color(0xFF88C0D0)
)

private fun draculaDark() = darkColorScheme(
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF282A36),
    primaryContainer = Color(0xFF44475A),
    onPrimaryContainer = Color(0xFFF8F8F2),
    secondary = Color(0xFFFF79C6),
    onSecondary = Color(0xFF282A36),
    background = Color(0xFF282A36),
    surface = Color(0xFF44475A),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF6272A4),
    onSurfaceVariant = Color(0xFFF8F8F2),
    outline = Color(0xFF6272A4)
)

private fun draculaLight() = lightColorScheme(
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF282A36),
    primaryContainer = Color(0xFFEAF8FB),
    onPrimaryContainer = Color(0xFF282A36),
    secondary = Color(0xFFFF79C6),
    onSecondary = Color(0xFF282A36),
    background = Color(0xFFF8F8F0),
    surface = Color(0xFFE6E6D8),
    onSurface = Color(0xFF282A36),
    surfaceVariant = Color(0xFFD0D0C0),
    onSurfaceVariant = Color(0xFF44475A),
    outline = Color(0xFF6272A4)
)

private fun catppuccinDark() = darkColorScheme(
    primary = Color(0xFFCBA6F7),
    onPrimary = Color(0xFF1E1E2E),
    primaryContainer = Color(0xFF313244),
    onPrimaryContainer = Color(0xFFCDD6F4),
    secondary = Color(0xFF89B4FA),
    onSecondary = Color(0xFF1E1E2E),
    background = Color(0xFF1E1E2E),
    surface = Color(0xFF252538),
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF45475A),
    onSurfaceVariant = Color(0xFFBAC2DE),
    outline = Color(0xFF585B70)
)

private fun catppuccinLight() = lightColorScheme(
    primary = Color(0xFF8839EF),
    onPrimary = Color(0xFFEFF1F5),
    primaryContainer = Color(0xFFCCD0DA),
    onPrimaryContainer = Color(0xFF4C4F69),
    secondary = Color(0xFF1E66F5),
    onSecondary = Color(0xFFEFF1F5),
    background = Color(0xFFEFF1F5),
    surface = Color(0xFFE6E9EF),
    onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFCCD0DA),
    onSurfaceVariant = Color(0xFF5C5F77),
    outline = Color(0xFF9C86BC)
)

private fun solarizedDark() = darkColorScheme(
    primary = Color(0xFF2AA198),
    onPrimary = Color(0xFF002B36),
    primaryContainer = Color(0xFF073642),
    onPrimaryContainer = Color(0xFF93A1A1),
    secondary = Color(0xFF268BD2),
    onSecondary = Color(0xFF002B36),
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    onSurface = Color(0xFF93A1A1),
    surfaceVariant = Color(0xFF586E75),
    onSurfaceVariant = Color(0xFF93A1A1),
    outline = Color(0xFF586E75)
)

private fun solarizedLight() = lightColorScheme(
    primary = Color(0xFFB58900),
    onPrimary = Color(0xFFFDF6E3),
    primaryContainer = Color(0xFFEEE8D5),
    onPrimaryContainer = Color(0xFF586E75),
    secondary = Color(0xFF2AA198),
    onSecondary = Color(0xFFFDF6E3),
    background = Color(0xFFFDF6E3),
    surface = Color(0xFFEEE8D5),
    onSurface = Color(0xFF586E75),
    surfaceVariant = Color(0xFF93A1A1),
    onSurfaceVariant = Color(0xFF586E75),
    outline = Color(0xFF93A1A1)
)

private fun highContrastDark() = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFFFFFFF),
    outline = Color(0xFFFFFFFF)
)

private fun highContrastLight() = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEEEEE),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFDDDDDD),
    onSurfaceVariant = Color(0xFF000000),
    outline = Color(0xFF000000)
)

// ---- Gruvbox ----
private fun gruvboxDark() = darkColorScheme(
    primary = Color(0xFFFABD2F), onPrimary = Color(0xFF282828),
    primaryContainer = Color(0xFF504945), onPrimaryContainer = Color(0xFFFBF1C7),
    secondary = Color(0xFF8EC07C), onSecondary = Color(0xFF282828),
    background = Color(0xFF282828), surface = Color(0xFF32302F), onSurface = Color(0xFFEBDBB2),
    surfaceVariant = Color(0xFF3C3836), onSurfaceVariant = Color(0xFFD5C4A1), outline = Color(0xFF928374),
)
private fun gruvboxLight() = lightColorScheme(
    primary = Color(0xFFB57614), onPrimary = Color(0xFFFBF1C7),
    primaryContainer = Color(0xFFEBDBB2), onPrimaryContainer = Color(0xFF3C3836),
    secondary = Color(0xFF427B58), onSecondary = Color(0xFFFBF1C7),
    background = Color(0xFFFBF1C7), surface = Color(0xFFF2E5BC), onSurface = Color(0xFF3C3836),
    surfaceVariant = Color(0xFFEBDBB2), onSurfaceVariant = Color(0xFF504945), outline = Color(0xFF928374),
)

// ---- Tokyo Night ----
private fun tokyoNightDark() = darkColorScheme(
    primary = Color(0xFF7AA2F7), onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF2A2E42), onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7), onSecondary = Color(0xFF1A1B26),
    background = Color(0xFF1A1B26), surface = Color(0xFF24283B), onSurface = Color(0xFFA9B1D6),
    surfaceVariant = Color(0xFF2F344D), onSurfaceVariant = Color(0xFF9AA5CE), outline = Color(0xFF565F89),
)
private fun tokyoNightLight() = lightColorScheme(
    primary = Color(0xFF2E7DE9), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7C5E3), onPrimaryContainer = Color(0xFF343B58),
    secondary = Color(0xFF9854F1), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFE1E2E7), surface = Color(0xFFD5D6DB), onSurface = Color(0xFF343B58),
    surfaceVariant = Color(0xFFC4C8DA), onSurfaceVariant = Color(0xFF4C505E), outline = Color(0xFF6A6F87),
)

// ---- Rosé Pine ----
private fun rosePineDark() = darkColorScheme(
    primary = Color(0xFFEBBCBA), onPrimary = Color(0xFF191724),
    primaryContainer = Color(0xFF403D52), onPrimaryContainer = Color(0xFFE0DEF4),
    secondary = Color(0xFFC4A7E7), onSecondary = Color(0xFF191724),
    background = Color(0xFF191724), surface = Color(0xFF1F1D2E), onSurface = Color(0xFFE0DEF4),
    surfaceVariant = Color(0xFF26233A), onSurfaceVariant = Color(0xFF908CAA), outline = Color(0xFF6E6A86),
)
private fun rosePineLight() = lightColorScheme(
    primary = Color(0xFFB4637A), onPrimary = Color(0xFFFFFAF3),
    primaryContainer = Color(0xFFF2E9E1), onPrimaryContainer = Color(0xFF575279),
    secondary = Color(0xFF907AA9), onSecondary = Color(0xFFFFFAF3),
    background = Color(0xFFFAF4ED), surface = Color(0xFFFFFAF3), onSurface = Color(0xFF575279),
    surfaceVariant = Color(0xFFF2E9E1), onSurfaceVariant = Color(0xFF797593), outline = Color(0xFF9893A5),
)

// ---- One Dark (Atom) ----
private fun oneDarkDark() = darkColorScheme(
    primary = Color(0xFF61AFEF), onPrimary = Color(0xFF282C34),
    primaryContainer = Color(0xFF3B4048), onPrimaryContainer = Color(0xFFABB2BF),
    secondary = Color(0xFFC678DD), onSecondary = Color(0xFF282C34),
    background = Color(0xFF282C34), surface = Color(0xFF21252B), onSurface = Color(0xFFABB2BF),
    surfaceVariant = Color(0xFF3E4451), onSurfaceVariant = Color(0xFF9DA5B4), outline = Color(0xFF5C6370),
)
private fun oneDarkLight() = lightColorScheme(
    primary = Color(0xFF4078F2), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0DCFB), onPrimaryContainer = Color(0xFF383A42),
    secondary = Color(0xFFA626A4), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF383A42),
    surfaceVariant = Color(0xFFE5E5E6), onSurfaceVariant = Color(0xFF696C77), outline = Color(0xFFA0A1A7),
)

// ---- Monokai ----
private fun monokaiDark() = darkColorScheme(
    primary = Color(0xFFA6E22E), onPrimary = Color(0xFF272822),
    primaryContainer = Color(0xFF49483E), onPrimaryContainer = Color(0xFFF8F8F2),
    secondary = Color(0xFFFD971F), onSecondary = Color(0xFF272822),
    background = Color(0xFF272822), surface = Color(0xFF2D2E27), onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF3E3D32), onSurfaceVariant = Color(0xFFCFCFC2), outline = Color(0xFF75715E),
)
private fun monokaiLight() = lightColorScheme(
    primary = Color(0xFFE6004C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE4EC), onPrimaryContainer = Color(0xFF4A0E26),
    secondary = Color(0xFFF57C00), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF272822),
    surfaceVariant = Color(0xFFECECEC), onSurfaceVariant = Color(0xFF5A594F), outline = Color(0xFF9E9E94),
)
