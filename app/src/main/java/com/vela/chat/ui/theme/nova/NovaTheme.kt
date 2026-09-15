package com.vela.chat.ui.theme.nova

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.ThemeMode
import com.vela.chat.data.settings.ThemePreset
import com.vela.chat.ui.theme.scaledVelaTypography

/**
 * Whether haptic feedback is enabled (mirrors `AppSettings.hapticsEnabled`).
 * Provided by [NovaTheme]; defaults to `true` outside the theme. Read this via
 * [com.vela.chat.ui.components.nova.rememberHaptics] instead of directly.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Material [Shapes] built entirely from [NovaTokens.Shape]. */
private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(NovaTokens.Shape.extraSmall),
    small = RoundedCornerShape(NovaTokens.Shape.small),
    medium = RoundedCornerShape(NovaTokens.Shape.medium),
    large = RoundedCornerShape(NovaTokens.Shape.large),
    extraLarge = RoundedCornerShape(NovaTokens.Shape.extraLarge),
)

/**
 * The Nova theme: resolves light/dark from [AppSettings.themeMode], builds the
 * [NovaColorScheme] from the selected preset (or Material You dynamic color on
 * Android 12+ when enabled), applies AMOLED black, the scaled Nova typography,
 * and Nova shapes, then provides [LocalNovaColors] and [LocalHapticsEnabled]
 * and draws the subtle vertical background gradient behind [content].
 *
 * `VelaTheme` is a thin wrapper around this function that additionally provides
 * the legacy `LocalChatDensity`/`LocalBubbleStyle` locals; both entry points
 * provide the full Nova environment.
 */
@Composable
fun NovaTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val isAmoled = dark && settings.amoledBlack
    val context = LocalContext.current
    val preset = if (dark) settings.darkThemePreset else settings.lightThemePreset
    val canUseDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = preset == ThemePreset.MATERIAL_YOU && settings.dynamicColor && canUseDynamic

    val nova: NovaColorScheme = if (useDynamicColor) {
        val dynamic: ColorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        novaColorScheme(material = dynamic, dark = dark, isAmoled = isAmoled)
    } else {
        novaColorScheme(settings = settings, dark = dark, isAmoled = isAmoled)
    }
    val typography = remember(settings.fontSizeScale) { scaledVelaTypography(settings.fontSizeScale) }

    CompositionLocalProvider(
        LocalNovaColors provides nova,
        LocalHapticsEnabled provides settings.hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = nova.material,
            typography = typography,
            shapes = NovaShapes,
        ) {
            // Subtle vertical gradient behind all content; opaque screens cover
            // it harmlessly, transparent scaffolds let the glass treatment show.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(nova.backgroundGradient()),
            ) {
                content()
            }
        }
    }
}
