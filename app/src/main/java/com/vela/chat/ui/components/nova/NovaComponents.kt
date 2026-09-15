package com.vela.chat.ui.components.nova

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vela.chat.ui.theme.nova.LocalNovaColors
import com.vela.chat.ui.theme.nova.NovaTokens

/** Minimum interactive height for Nova rows (Material touch-target guidance with comfortable padding). */
private val NovaRowMinHeight: Dp = NovaTokens.Spacing.xl + NovaTokens.Spacing.lg

/** Leading icon container size inside Nova rows. */
private val NovaRowIconContainer: Dp = NovaTokens.Spacing.xl + NovaTokens.Spacing.sm

/** Empty-state icon circle diameter. */
private val EmptyStateIconContainer: Dp = NovaTokens.Spacing.xl * 3

/** Empty-state icon glyph size inside the glass circle. */
private val EmptyStateIconSize: Dp = NovaTokens.Spacing.xl + NovaTokens.Spacing.sm

/** Glass hairline width, mirrors [GlassSurface]. */
private val HairlineWidth: Dp = 1.dp

/** Content description used for the conventional back-navigation icon slot. */
private const val BackContentDescription = "Back"

/**
 * Nova top bar: M3 center-aligned app bar with an optional second-line
 * subtitle. Transparent background so the Nova gradient (and any glass chrome
 * beneath) shows through; pair with [NovaStatusDot] in [actions] for
 * streaming/connection indicators.
 *
 * The [navigationIcon] slot is the conventional back affordance; it is only
 * rendered when both the icon and [onNavigationClick] are supplied.
 */
@Composable
fun NovaTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(imageVector = navigationIcon, contentDescription = BackContentDescription)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * Nova empty state: a glass icon medallion over a centered title/subtitle and
 * an optional tonal action button. Use for empty lists, failed loads and
 * "nothing here yet" moments. Renders nothing extra when [actionLabel] or
 * [onAction] is omitted.
 */
@Composable
fun NovaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val nova = LocalNovaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NovaTokens.Spacing.lg, vertical = NovaTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(EmptyStateIconContainer)
                .clip(CircleShape)
                .background(nova.glassSurface)
                .padding(NovaTokens.Spacing.xs)
                .border(HairlineWidth, nova.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * Nova section header: uppercase micro-label that opens a settings/content
 * group. Use one per group; do not make it interactive.
 */
@Composable
fun NovaSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = NovaTokens.Spacing.md,
            end = NovaTokens.Spacing.md,
            top = NovaTokens.Spacing.lg,
            bottom = NovaTokens.Spacing.sm,
        ),
    )
}

/**
 * Nova settings row: title with optional subtitle, optional leading icon in a
 * tonal container and an optional trailing slot (chevron, value label, switch
 * use [NovaSwitchRow] instead). When [onClick] is supplied the whole row is
 * clickable; otherwise it renders as a static, informative row.
 */
@Composable
fun NovaSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NovaTokens.Shape.small))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = NovaRowMinHeight)
            .padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.md),
    ) {
        if (icon != null) {
            RowIcon(icon)
        }
        RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * Nova switch row: a [NovaSettingsRow]-styled row bound to a boolean. The
 * whole row is toggleable (with the Switch semantics role) and the embedded
 * [Switch] mirrors state; its own click handling is disabled so the row is a
 * single touch target.
 */
@Composable
fun NovaSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NovaTokens.Shape.small))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = NovaRowMinHeight)
            .padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.md),
    ) {
        if (icon != null) {
            RowIcon(icon)
        }
        RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Nova slider row: label + monospaced value label above a full-width
 * [Slider]. Fully state-hoisted — [value] comes from the caller and
 * [onValueChange] reports drags. [valueLabel] is preformatted by the caller
 * (e.g. "0.7" or "24 s").
 */
@Composable
fun NovaSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

/**
 * Nova filled chip: a pill toggle for compact multi-choice filters. Selection
 * animates between the primary fill and a neutral container over
 * [NovaTokens.Motion.fast]. Fully state-hoisted via [selected].
 */
@Composable
fun NovaFilledChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(NovaTokens.Motion.fast, easing = NovaTokens.Motion.standard),
        label = "NovaChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(NovaTokens.Motion.fast, easing = NovaTokens.Motion.standard),
        label = "NovaChipContent",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(NovaTokens.Shape.extraLarge),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.sm),
        )
    }
}

/** Tonal rounded container for a row's leading icon. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(NovaRowIconContainer)
            .clip(RoundedCornerShape(NovaTokens.Shape.small))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Title/subtitle stack shared by the settings rows. */
@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
