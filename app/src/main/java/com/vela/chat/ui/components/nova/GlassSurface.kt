package com.vela.chat.ui.components.nova

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vela.chat.ui.theme.nova.LocalNovaColors
import com.vela.chat.ui.theme.nova.NovaColorScheme
import com.vela.chat.ui.theme.nova.NovaTokens

/** Glass hairline border width, shared by all translucent chrome. */
private val HairlineWidth: Dp = 1.dp

/** How much the glass wash fades toward the bottom (1.0 = no fade). */
private const val GlassSheenFade = 0.6f

/**
 * Nova glass card: a rounded translucent surface with a hairline border that
 * reads as frosted glass over the app's background gradient. On API 31+ the
 * decorative wash is softened with a blur [android.graphics.RenderEffect]; on
 * older devices the same wash renders unblurred — always graceful, never a
 * crash.
 *
 * Intended for elevated chrome (headers, hero cards, empty states, sheets,
 * dialogs). Do NOT use it per message bubble or inside dense lazy lists: the
 * blur layer is cheap only when instances are few — message bubbles must stay
 * plain translucent surfaces (see docs/UI_GUIDELINES.md).
 *
 * The surface adds no padding; callers control inner spacing inside [content].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = NovaTokens.Shape.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nova = LocalNovaColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier.clip(shape)) {
        // Decorative frosted wash (blurred on API 31+, plain gradient below).
        Box(
            modifier = Modifier
                .matchParentSize()
                .frosted(NovaTokens.Blur.glassRadius)
                .background(glassWash(nova), shape),
        )
        // Content sizes the surface; the wash and border adapt to it.
        Column(modifier = Modifier, content = content)
        // Hairline drawn last so wash/content never hide the glass border.
        Box(modifier = Modifier.matchParentSize().border(HairlineWidth, nova.glassBorder, shape))
    }
}

/**
 * Box-content variant of [GlassSurface] for callers that need to place
 * positioned (non-Column) content on the glass: same wash, border and
 * graceful-blur behavior without the Column contract.
 */
@Composable
fun GlassSurfaceBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = NovaTokens.Shape.large,
    content: @Composable BoxScope.() -> Unit,
) {
    val nova = LocalNovaColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .frosted(NovaTokens.Blur.glassRadius)
                .background(glassWash(nova), shape),
        )
        // Content sizes the surface.
        Box(modifier = Modifier, content = content)
        Box(modifier = Modifier.matchParentSize().border(HairlineWidth, nova.glassBorder, shape))
    }
}

/** Vertical glass wash: the translucent fill fading slightly toward the bottom edge. */
private fun glassWash(nova: NovaColorScheme): Brush = Brush.verticalGradient(
    listOf(
        nova.glassSurface,
        nova.glassSurface.copy(alpha = nova.glassSurface.alpha * GlassSheenFade),
    ),
)

/**
 * Applies the subtle glass blur to the decorated layer only — never to content.
 * The framework [android.graphics.RenderEffect] is created exclusively inside
 * the SDK-int guarded branch, so pre-S devices never touch API-31 classes.
 */
private fun Modifier.frosted(radius: Dp): Modifier = graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blurPx = radius.toPx()
        renderEffect = android.graphics.RenderEffect
            .createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
            .asComposeRenderEffect()
    }
}
