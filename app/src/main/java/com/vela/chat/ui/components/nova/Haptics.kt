package com.vela.chat.ui.components.nova

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vela.chat.ui.theme.nova.LocalHapticsEnabled

/**
 * Chosen haptics API:
 * - `rememberHaptics()` — the standard entry point. Reads
 *   [LocalHapticsEnabled] (provided by `NovaTheme` from
 *   `AppSettings.hapticsEnabled`), so screens honor the user preference with
 *   zero wiring. Use this everywhere in Nova screens and the chat surface.
 * - `rememberHapticFeedback(enabled: Boolean)` — escape hatch for callers that
 *   hold settings outside the theme (previews, special flows).
 * Both return a stable `(HapticFeedbackType) -> Unit` lambda that is a no-op
 * while haptics are disabled, so call sites never branch on the setting.
 */

/**
 * Returns a haptic callback honoring [enabled]; a no-op when disabled.
 * The returned lambda is remembered, so it can be captured safely.
 */
@Composable
fun rememberHapticFeedback(enabled: Boolean): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { type: HapticFeedbackType -> if (enabled) haptics.performHapticFeedback(type) }
    }
}

/**
 * Returns a haptic callback honoring [LocalHapticsEnabled] (theme-provided
 * `AppSettings.hapticsEnabled`). Same contract as the [enabled] overload.
 */
@Composable
fun rememberHapticFeedback(): (HapticFeedbackType) -> Unit =
    rememberHapticFeedback(enabled = LocalHapticsEnabled.current)

/**
 * Preferred haptics entry point for screens and the chat surface: reads
 * [LocalHapticsEnabled] and returns a remembered no-op-when-disabled callback.
 *
 * Example: `val haptics = rememberHaptics(); haptics(HapticFeedbackType.LongPress)`
 */
@Composable
fun rememberHaptics(): (HapticFeedbackType) -> Unit =
    rememberHapticFeedback(enabled = LocalHapticsEnabled.current)
