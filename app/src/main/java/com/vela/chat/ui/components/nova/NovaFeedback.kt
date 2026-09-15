package com.vela.chat.ui.components.nova

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.vela.chat.ui.theme.nova.NovaTokens

/** Skeleton shimmer loop duration; uses the slow motion token for a calm pulse. */
private val ShimmerPeriod: Int = NovaTokens.Motion.slow

/** Lowest alpha the shimmer reaches — keeps placeholders visible during the fade. */
private const val ShimmerMinAlpha = 0.4f

/** Status-dot pulse period, spec'd at ~1.2 s for a calm connection heartbeat. */
private const val StatusDotPulseMs = 1200

/** Peak alpha of the expanding halo ring behind the status dot. */
private const val StatusDotHaloMaxAlpha = 0.5f

/** Avatar circle size in skeleton list rows. */
private val SkeletonAvatarSize: Dp = NovaTokens.Spacing.xl + NovaTokens.Spacing.md

/** Primary text line height in skeleton list rows. */
private val SkeletonLineHeight: Dp = NovaTokens.Spacing.sm + NovaTokens.Spacing.xs

/** Fraction of the row width covered by the primary skeleton line. */
private const val SkeletonPrimaryLineFraction = 0.7f

/** Fraction of the row width covered by the secondary skeleton line. */
private const val SkeletonSecondaryLineFraction = 0.45f

/**
 * Nova loading placeholder: a rounded block that gently pulses between two
 * alphas. Cheap by design — a single alpha animation, no library. Compose
 * skeletons into rows/cards to mock the content that will appear; for list
 * loading prefer [NovaSkeletonList].
 */
@Composable
fun NovaSkeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(NovaTokens.Shape.medium)) {
    val alpha by rememberShimmerAlpha()
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}

/**
 * Full list-loading placeholder: [rows] mock rows (avatar circle + two text
 * lines) pulsing in a shared rhythm. Wrap it in a padded container and show
 * it while the first page loads.
 */
@Composable
fun NovaSkeletonList(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.md),
    ) {
        repeat(rows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.md),
            ) {
                NovaSkeleton(modifier = Modifier.size(SkeletonAvatarSize), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs)) {
                    NovaSkeleton(
                        modifier = Modifier
                            .fillMaxWidth(SkeletonPrimaryLineFraction)
                            .height(SkeletonLineHeight),
                        shape = RoundedCornerShape(NovaTokens.Shape.small),
                    )
                    NovaSkeleton(
                        modifier = Modifier
                            .fillMaxWidth(SkeletonSecondaryLineFraction)
                            .height(SkeletonLineHeight),
                        shape = RoundedCornerShape(NovaTokens.Shape.small),
                    )
                }
            }
        }
    }
}

/**
 * Nova status dot: small colored indicator for connection/streaming state.
 * With [pulsing] it emits a soft expanding halo (~1.2 s loop); with
 * `pulsing = false` it renders as a static dot. The component owns a fixed
 * [NovaTokens.Spacing.lg] footprint so it can sit inside top bars and chips
 * without shifting layout.
 */
@Composable
fun NovaStatusDot(color: Color, pulsing: Boolean = true, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(NovaTokens.Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            val ping by rememberInfiniteTransition(label = "NovaStatusDot").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = StatusDotPulseMs, easing = LinearEasing),
                ),
                label = "NovaStatusDotPing",
            )
            Box(
                modifier = Modifier
                    .size(NovaTokens.Spacing.sm + NovaTokens.Spacing.sm * ping)
                    .background(color.copy(alpha = (1f - ping) * StatusDotHaloMaxAlpha), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(NovaTokens.Spacing.sm)
                .background(color, CircleShape),
        )
    }
}

/** Shared shimmer driver for all Nova skeletons (one alpha, reverse loop). */
@Composable
private fun rememberShimmerAlpha(): State<Float> =
    rememberInfiniteTransition(label = "NovaShimmer").animateFloat(
        initialValue = ShimmerMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ShimmerPeriod, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "NovaShimmerAlpha",
    )
