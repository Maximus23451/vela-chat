package com.vela.chat.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.AttachmentType
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import com.vela.chat.ui.chat.ChatUiState
import com.vela.chat.ui.chat.StreamingState
import com.vela.chat.ui.components.markdown.MarkdownText
import com.vela.chat.ui.components.nova.GlassSurface
import com.vela.chat.ui.components.nova.rememberHaptics
import com.vela.chat.ui.theme.LocalBubbleStyle
import com.vela.chat.ui.theme.LocalChatDensity
import com.vela.chat.ui.theme.nova.NovaTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun getBubbleShape(isUser: Boolean, style: BubbleStyle): RoundedCornerShape {
    return when (style) {
        BubbleStyle.ROUNDED -> if (isUser) RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp) else RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
        BubbleStyle.SEMI_ROUNDED -> if (isUser) RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp) else RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
        BubbleStyle.SHARP -> RoundedCornerShape(SHARP_BUBBLE_RADIUS)
    }
}

private fun getGeneralShape(style: BubbleStyle): RoundedCornerShape {
    return when (style) {
        BubbleStyle.ROUNDED -> RoundedCornerShape(12.dp)
        BubbleStyle.SEMI_ROUNDED -> RoundedCornerShape(6.dp)
        BubbleStyle.SHARP -> RoundedCornerShape(SHARP_BUBBLE_RADIUS)
    }
}

/** Corner radius used for the sharp bubble style (kept from the original design). */
private val SHARP_BUBBLE_RADIUS = 2.dp

/** Drag distance past which a horizontal swipe triggers reply (right) / delete (left). */
private val SWIPE_ACTION_THRESHOLD = 72.dp

/** Max horizontal displacement of a bubble during a swipe gesture. */
private val SWIPE_MAX_DRAG = 120.dp

/** Max bubble width (readability: ~10 words per line on phones). */
private val BUBBLE_MAX_WIDTH = 320.dp

private val messageTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Stable LazyColumn key of the "load older" row. */
private const val LOAD_OLDER_KEY = "load-older"

/**
 * The chat transcript: windowed messages with a "load older" row, Nova glass
 * assistant bubbles, swipe gestures (right = quote into composer, left = delete
 * with undo via the caller), long-press action sheet, and multi-select mode.
 */
@Composable
fun MessageList(
    state: ChatUiState,
    listState: LazyListState = rememberLazyListState(),
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: () -> Unit,
    onSummarize: () -> Unit,
    onTranslateTo: (Message, String) -> Unit,
    onSpeak: (String) -> Unit,
    onQuote: (Message) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onLoadOlder: () -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalChatDensity.current
    val outerPadding = when (density) {
        ChatDensity.COMPACT -> 4.dp
        ChatDensity.COZY -> 12.dp
        ChatDensity.ROOMY -> 20.dp
    }
    val clipboard = LocalClipboardManager.current
    var actionsTarget by remember { mutableStateOf<Message?>(null) }
    var translateTarget by remember { mutableStateOf<Message?>(null) }
    val lastAssistantId = state.messages.lastOrNull { it.role == Role.ASSISTANT }?.id

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = outerPadding),
    ) {
        if (state.hasMoreMessages) {
            item(key = LOAD_OLDER_KEY) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadOlder) {
                        Icon(Icons.Rounded.ExpandLess, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Load older messages")
                    }
                }
            }
        }

        items(state.messages, key = { it.id }) { message ->
            MessageItem(
                message = message,
                renderMarkdown = state.renderMarkdown,
                showTimestamps = state.showTimestamps,
                showTokenUsage = state.showTokenUsage,
                selectionMode = state.selectionMode,
                selected = message.id in state.selectedMessageIds,
                onOpenActions = { actionsTarget = message },
                onToggleSelection = { onToggleSelection(message.id) },
                onDelete = onDelete,
                onQuote = onQuote,
            )
        }

        state.streaming?.let { streaming ->
            item(key = "streaming") {
                StreamingMessage(streaming = streaming)
            }
        }
    }

    actionsTarget?.let { target ->
        MessageActionsSheet(
            message = target,
            isLastAssistant = target.id == lastAssistantId,
            ttsEnabled = state.ttsEnabled,
            onDismiss = { actionsTarget = null },
            onCopy = { clipboard.setText(AnnotatedString(target.content)) },
            onEdit = { onEdit(target) },
            onDelete = { onDelete(target) },
            onRetry = { onRegenerate(target.id) },
            onContinue = onContinue,
            onSummarize = onSummarize,
            onTranslate = { translateTarget = target },
            onToggleFavorite = { onToggleFavorite(target.id, !target.favorite) },
            onTogglePin = { onTogglePin(target.id, !target.pinned) },
            onSpeak = { onSpeak(target.content) },
        )
    }

    translateTarget?.let { target ->
        LanguageChooserDialog(
            title = "Translate to…",
            onPick = { language ->
                onTranslateTo(target, language)
                translateTarget = null
            },
            onDismiss = { translateTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: Message,
    renderMarkdown: Boolean,
    showTimestamps: Boolean,
    showTokenUsage: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpenActions: () -> Unit,
    onToggleSelection: () -> Unit,
    onDelete: (Message) -> Unit,
    onQuote: (Message) -> Unit,
) {
    val density = LocalChatDensity.current
    val bubbleStyle = LocalBubbleStyle.current

    val itemHorizontalPadding = when (density) {
        ChatDensity.COMPACT -> 8.dp
        ChatDensity.COZY -> 12.dp
        ChatDensity.ROOMY -> 16.dp
    }
    val itemVerticalPadding = when (density) {
        ChatDensity.COMPACT -> 3.dp
        ChatDensity.COZY -> 6.dp
        ChatDensity.ROOMY -> 10.dp
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding),
    ) {
        if (selectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                MessageBody(
                    message = message,
                    renderMarkdown = renderMarkdown,
                    showTimestamps = showTimestamps,
                    showTokenUsage = showTokenUsage,
                    bubbleStyle = bubbleStyle,
                    onTap = onToggleSelection,
                    onLongPress = onToggleSelection,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            SwipeableMessageRow(
                onReply = { onQuote(message) },
                onDelete = { onDelete(message) },
            ) {
                MessageBody(
                    message = message,
                    renderMarkdown = renderMarkdown,
                    showTimestamps = showTimestamps,
                    showTokenUsage = showTokenUsage,
                    bubbleStyle = bubbleStyle,
                    onTap = {},
                    onLongPress = onOpenActions,
                )
            }
        }
    }
}

/** The bubble content of one message, minus selection/swipe chrome. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBody(
    message: Message,
    renderMarkdown: Boolean,
    showTimestamps: Boolean,
    showTokenUsage: Boolean,
    bubbleStyle: BubbleStyle,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val alignment = if (message.role == Role.USER) Alignment.End else Alignment.Start

    Column(modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (message.attachments.isNotEmpty()) {
            AttachmentStrip(message.attachments, alignment, bubbleStyle)
        }

        val bubbleInteraction = Modifier.combinedClickable(
            onClick = onTap,
            onLongClick = {
                haptics(HapticFeedbackType.LongPress)
                onLongPress()
            },
        )

        if (message.role == Role.USER) {
            if (message.content.isNotBlank()) {
                Box(
                    Modifier
                        .widthIn(max = BUBBLE_MAX_WIDTH)
                        .clip(getBubbleShape(isUser = true, style = bubbleStyle))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .then(bubbleInteraction)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        message.content,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistantAvatar()
                Spacer(Modifier.width(8.dp))
                Text(
                    message.model ?: "Assistant",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MessageFlags(message)
            }
            Spacer(Modifier.size(6.dp))

            GlassSurface(
                modifier = Modifier
                    .clip(getGeneralShape(bubbleStyle))
                    .then(bubbleInteraction),
                cornerRadius = assistantGlassRadius(bubbleStyle),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    message.reasoning?.takeIf { it.isNotBlank() }?.let {
                        ReasoningSection(it, style = bubbleStyle)
                    }
                    if (message.isError) {
                        ErrorContent(message.content, bubbleStyle)
                    } else if (renderMarkdown) {
                        MarkdownText(message.content)
                    } else {
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (showTokenUsage && message.completionTokens > 0 && message.tokensPerSecond != null) {
                Text(
                    text = "%.1f tok/s".format(message.tokensPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                )
            }
        }

        if (showTimestamps) MessageTimestamp(message.createdAt)
    }
}

/** Subtle favorite / pin glyphs shown on flagged messages. */
@Composable
private fun MessageFlags(message: Message) {
    if (message.favorite) {
        FlagIcon(Icons.Rounded.Star, "Favorite message")
    }
    if (message.pinned) {
        FlagIcon(Icons.Rounded.Bookmark, "Pinned message")
    }
}

@Composable
private fun FlagIcon(icon: ImageVector, description: String) {
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier.padding(start = 4.dp).size(13.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** Glass corner radius for assistant bubbles, honoring the user's bubble style. */
private fun assistantGlassRadius(style: BubbleStyle): Dp = when (style) {
    BubbleStyle.ROUNDED -> NovaTokens.Shape.large
    BubbleStyle.SEMI_ROUNDED -> SEMI_ROUNDED_GLASS_RADIUS
    BubbleStyle.SHARP -> SHARP_BUBBLE_RADIUS
}

/** Corner radius for the semi-rounded bubble style on glass surfaces. */
private val SEMI_ROUNDED_GLASS_RADIUS = 12.dp

/**
 * Horizontal swipe shell for message rows: swipe right reveals the quote/reply
 * action, swipe left reveals delete (the caller shows the undo snackbar).
 * Implemented with [detectHorizontalDragGestures] + an [Animatable] offset — no
 * external gesture library, and vertical list scrolling is unaffected because
 * only horizontal drags past the touch slop are claimed.
 */
@Composable
private fun SwipeableMessageRow(
    onReply: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val offsetX = remember { Animatable(0f) }
    val thresholdPx = with(density) { SWIPE_ACTION_THRESHOLD.toPx() }
    val maxDragPx = with(density) { SWIPE_MAX_DRAG.toPx() }
    val scheme = MaterialTheme.colorScheme
    val current = offsetX.value

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        // Gesture hints revealed behind the dragged bubble.
        Row(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(getGeneralShape(LocalBubbleStyle.current))
                    .background(if (current > 0f) scheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (current > 0f) SwipeHint(Icons.AutoMirrored.Rounded.Reply, "Reply", Alignment.CenterStart)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(getGeneralShape(LocalBubbleStyle.current))
                    .background(if (current < 0f) scheme.errorContainer else Color.Transparent),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (current < 0f) SwipeHint(Icons.Rounded.Delete, "Delete", Alignment.CenterEnd)
            }
        }

        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offsetX.value
                            when {
                                value <= -thresholdPx -> {
                                    haptics(HapticFeedbackType.LongPress)
                                    onDelete()
                                }
                                value >= thresholdPx -> {
                                    haptics(HapticFeedbackType.LongPress)
                                    onReply()
                                }
                            }
                            scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onHorizontalDrag = { _, amount ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount).coerceIn(-maxDragPx, maxDragPx))
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeHint(icon: ImageVector, label: String, alignment: Alignment) {
    Row(
        Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (alignment == Alignment.CenterEnd) Text(label, style = MaterialTheme.typography.labelMedium)
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        if (alignment == Alignment.CenterStart) Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageTimestamp(createdAt: Long) {
    Text(
        text = messageTimeFormatter.format(Date(createdAt)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun StreamingMessage(streaming: StreamingState) {
    val density = LocalChatDensity.current
    val bubbleStyle = LocalBubbleStyle.current

    val itemHorizontalPadding = when (density) {
        ChatDensity.COMPACT -> 8.dp
        ChatDensity.COZY -> 12.dp
        ChatDensity.ROOMY -> 16.dp
    }
    val itemVerticalPadding = when (density) {
        ChatDensity.COMPACT -> 3.dp
        ChatDensity.COZY -> 6.dp
        ChatDensity.ROOMY -> 10.dp
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
            Text("Assistant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(6.dp))

        GlassSurface(cornerRadius = assistantGlassRadius(bubbleStyle)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                streaming.reasoning.takeIf { it.isNotBlank() }?.let {
                    ReasoningSection(it, initiallyExpanded = true, isThinking = streaming.content.isBlank(), style = bubbleStyle)
                }

                if (streaming.content.isBlank() && streaming.reasoning.isBlank()) {
                    TypingIndicator()
                } else {
                    // Render the live stream as plain text — re-parsing Markdown on every
                    // update is expensive. The finalized message renders full Markdown.
                    Text(streaming.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ReasoningSection(
    text: String,
    initiallyExpanded: Boolean = false,
    isThinking: Boolean = false,
    style: BubbleStyle,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(getGeneralShape(style))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = if (isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            if (isThinking) {
                // Pulsing "Thinking…" while the model emits reasoning but no answer yet.
                val transition = rememberInfiniteTransition(label = "thinking")
                val pulse by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "pulse",
                )
                Text(
                    "Thinking…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = pulse),
                )
            } else {
                Text("Reasoning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(expanded) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun ErrorContent(text: String, style: BubbleStyle) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(getGeneralShape(style))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("✦", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    alignment: Alignment.Horizontal = Alignment.Start,
    style: BubbleStyle,
) {
    Column(horizontalAlignment = alignment, modifier = Modifier.padding(bottom = 6.dp)) {
        attachments.forEach { att ->
            Row(
                Modifier
                    .padding(vertical = 2.dp)
                    .clip(getGeneralShape(style))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (att.type == AttachmentType.IMAGE) Icons.Rounded.Image else Icons.Rounded.Description,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(att.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
    }
}
