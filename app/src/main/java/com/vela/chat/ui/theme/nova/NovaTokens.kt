package com.vela.chat.ui.theme.nova

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nova design-language tokens: the single source of truth for shape, spacing,
 * motion, elevation and glassmorphism constants. Components must reference
 * these values instead of hard-coded numbers so the visual language stays
 * consistent and retunable in one place.
 */
object NovaTokens {

    /**
     * Corner radii. Nova is a soft-cornered language: chrome and cards sit at
     * [large] (28dp), while small controls (rows, inputs) use the smaller steps.
     */
    object Shape {
        /** Tiny anchors: checkboxes, badges, inline chips. */
        val extraSmall: Dp = 12.dp

        /** Small controls: setting rows, text fields, list tiles. */
        val small: Dp = 16.dp

        /** Medium surfaces: skeletons, secondary cards, dropdowns. */
        val medium: Dp = 20.dp

        /** Primary Nova radius: cards, glass surfaces, sheets, dialogs. */
        val large: Dp = 28.dp

        /** Extra-large fills: full-bleed sheets, pill-shaped chips/buttons. */
        val extraLarge: Dp = 32.dp
    }

    /** Spacing scale. All layout padding/gaps must come from these steps. */
    object Spacing {
        /** Hairline gaps: icon-to-label nudges, inline separators. */
        val xs: Dp = 4.dp

        /** Tight rhythm: inside rows, between related controls. */
        val sm: Dp = 8.dp

        /** Default rhythm: screen margins, card padding, list gaps. */
        val md: Dp = 16.dp

        /** Section spacing: between groups, hero paddings. */
        val lg: Dp = 24.dp

        /** Page-level spacing: empty states, top-level breathing room. */
        val xl: Dp = 32.dp
    }

    /**
     * Motion durations (in milliseconds) and easing curves. Navigation uses
     * [normal] + [emphasized]; micro feedback (color/alpha) uses [fast];
     * celebratory or full-screen reveals may use [slow].
     */
    object Motion {
        /** Micro-feedback duration: color swaps, chip selection, ripples. */
        const val fast = 150

        /** Standard transition duration: navigation, content swaps. */
        const val normal = 250

        /** Long duration: oversized reveals and decorative loops. */
        const val slow = 400

        /**
         * Material "standard" easing (cubic-bezier 0.4, 0, 0.2, 1). The default
         * for bidirectional movement where both endpoints matter.
         */
        val standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        /**
         * Material "emphasized" easing (cubic-bezier 0.2, 0, 0, 1). Strong
         * acceleration followed by a long settle — use for navigation and
         * large-element transitions.
         */
        val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /**
         * Material "decelerate" easing (cubic-bezier 0, 0, 0.2, 1). Fast entry,
         * gentle landing — pair with fades for incoming content.
         */
        val decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    }

    /**
     * Elevation levels (dp). Nova keeps chrome nearly flat: depth is expressed
     * with glass translucency and borders first, shadows second.
     */
    object Elevation {
        /** Base plane — content on the background. */
        val level0: Dp = 0.dp

        /** Resting raised chrome: glass cards, top bars. */
        val level1: Dp = 1.dp

        /** Actively hovered/dragged surfaces, menus. */
        val level3: Dp = 3.dp

        /** Dialogs and floating overlays. */
        val level6: Dp = 6.dp
    }

    /**
     * Glassmorphism recipe. [glassAlpha]/[glassBorderAlpha] are the canonical
     * alphas for translucent surfaces in dark themes; the light-theme glass
     * constants live in the [NovaColorScheme] builder. Use the tokens instead
     * of hand-picked alphas when layering custom glass.
     */
    object Blur {
        /** Blur radius (dp) applied to the decorative wash of [GlassSurface]-style chrome on API 31+. */
        val glassRadius: Dp = 20.dp

        /** Canonical alpha for dark-theme glass fills. */
        const val glassAlpha = 0.12f

        /** Canonical alpha for glass hairline borders. */
        const val glassBorderAlpha = 0.2f
    }
}
