package com.vela.chat.ui.chat.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.AttachmentType
import com.vela.chat.ui.chat.ChatUiState
import com.vela.chat.ui.components.nova.GlassSurface
import com.vela.chat.ui.components.nova.NovaFilledChip
import com.vela.chat.ui.components.nova.rememberHaptics
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.util.SpeechToTextController
import com.vela.chat.util.rememberSpeechToText

/** Max composer text height (~6 lines) before it scrolls internally. */
private val COMPOSER_MAX_HEIGHT = 160.dp

/** Temperature quick-chip slider bounds (matches the parameters screen). */
private const val TEMPERATURE_MIN = 0f
private const val TEMPERATURE_MAX = 2f

/**
 * Floating Nova composer: auto-resizing text field (max ~6 lines, then internal
 * scroll) inside a 28dp glass surface, with attachment, prompt-library,
 * temperature quick-chip, voice dictation, and an animated send button.
 * Send-on-enter follows the user's setting.
 */
@Composable
fun InputBar(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: (android.net.Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSelectPrompt: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var showPromptLibrary by remember { mutableStateOf(false) }
    var showTemperature by remember { mutableStateOf(false) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(onAttach) }

    // Floating above the nav area: side margins + nav-bar/IME insets on the shell.
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.sm)
            .navigationBarsPadding()
            .imePadding(),
        cornerRadius = NovaTokens.Shape.large,
    ) {
        Column(Modifier.padding(NovaTokens.Spacing.sm)) {
            if (state.attachments.isNotEmpty() || state.isProcessingAttachment) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = NovaTokens.Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
                ) {
                    items(state.attachments, key = { it.id }) { attachment ->
                        AttachmentChip(attachment) { onRemoveAttachment(attachment.id) }
                    }
                    if (state.isProcessingAttachment) {
                        item {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                        }
                    }
                }
            }

            ComposerField(
                state = state,
                onInputChange = onInputChange,
                onSend = { haptics(HapticFeedbackType.LongPress); onSend() },
            )

            Row(
                Modifier.fillMaxWidth().padding(top = NovaTokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerAction(Icons.Rounded.AttachFile, "Attach file") {
                    attachmentPicker.launch(arrayOf("image/*", "application/pdf", "text/*"))
                }
                ComposerAction(Icons.AutoMirrored.Rounded.LibraryBooks, "Prompt library") {
                    showPromptLibrary = true
                }
                TemperatureChip(
                    temperature = state.params.temperature,
                    expanded = showTemperature,
                    onExpandedChange = { showTemperature = it },
                    onCommit = onTemperatureChange,
                )
                Spacer(Modifier.weight(1f))
                if (!state.isGenerating) {
                    MicButton(state = state, onInputChange = onInputChange)
                    Spacer(Modifier.width(NovaTokens.Spacing.sm))
                }
                SendButton(
                    state = state,
                    onSend = { haptics(HapticFeedbackType.LongPress); onSend() },
                    onStop = onStop,
                )
            }
        }
    }

    if (showPromptLibrary) {
        PromptLibrarySheet(
            prompts = state.savedPrompts,
            onSelect = onSelectPrompt,
            onDismiss = { showPromptLibrary = false },
        )
    }
}

/** The auto-resizing text input with send-on-enter support. */
@Composable
private fun ComposerField(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    TextField(
        value = state.input,
        onValueChange = onInputChange,
        modifier = Modifier.fillMaxWidth().heightIn(max = COMPOSER_MAX_HEIGHT),
        placeholder = { Text("Message…") },
        textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
        maxLines = COMPOSER_MAX_LINES,
        shape = RoundedCornerShape(NovaTokens.Shape.large),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = if (state.sendOnEnter) ImeAction.Send else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onSend = { if (state.canSend) onSend() }),
    )
}

/** Max visible lines before the composer scrolls internally. */
private const val COMPOSER_MAX_LINES = 6

/**
 * Voice dictation button: requests permission on first use, starts/stops the
 * recognizer, and appends recognized text to the composer draft.
 */
@Composable
private fun MicButton(state: ChatUiState, onInputChange: (String) -> Unit) {
    val context = LocalContext.current
    val speech = rememberSpeechToText()
    var voiceBase by remember { mutableStateOf("") }

    fun applyVoice(spoken: String) {
        val separator = if (voiceBase.isBlank()) "" else " "
        onInputChange((voiceBase + separator + spoken).trimStart())
    }

    fun beginVoice() {
        voiceBase = state.input
        speech.start(
            languageTag = state.ttsLanguage,
            onPartial = ::applyVoice,
            onFinal = ::applyVoice,
            onError = { msg ->
                if (!SpeechToTextController.isBenignError(msg)) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginVoice()
        else Toast.makeText(context, "Microphone permission is needed for voice input.", Toast.LENGTH_SHORT).show()
    }

    ComposerAction(
        icon = Icons.Rounded.Mic,
        description = if (speech.isListening) "Stop listening" else "Voice input",
        tint = if (speech.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        when {
            speech.isListening -> speech.stop()
            speech.hasPermission() -> beginVoice()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Send / stop button with a scale+fade animation as it becomes actionable. */
@Composable
private fun SendButton(
    state: ChatUiState,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    if (state.isGenerating) {
        ComposerAction(
            icon = Icons.Rounded.Stop,
            description = "Stop",
            container = MaterialTheme.colorScheme.error,
            tint = MaterialTheme.colorScheme.onError,
            onClick = onStop,
        )
        return
    }

    val sendScale by animateFloatAsState(
        targetValue = if (state.canSend) 1f else 0.7f,
        animationSpec = tween(durationMillis = NovaTokens.Motion.fast),
        label = "sendScale",
    )
    val sendAlpha by animateFloatAsState(
        targetValue = if (state.canSend) 1f else 0.55f,
        animationSpec = tween(durationMillis = NovaTokens.Motion.fast),
        label = "sendAlpha",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = sendScale
                scaleY = sendScale
                alpha = sendAlpha
            }
            .size(SEND_BUTTON_SIZE)
            .clip(CircleShape)
            .background(if (state.canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = state.canSend, onClick = onSend),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = "Send",
            tint = if (state.canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val SEND_BUTTON_SIZE = 44.dp

/** Temperature quick-chip: opens a slider popup bound to the chat's params. */
@Composable
private fun TemperatureChip(
    temperature: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var pending by remember(expanded) { mutableFloatStateOf(temperature) }

    Box {
        NovaFilledChip(
            selected = expanded,
            onClick = { onExpandedChange(!expanded) },
            label = "Temp %.1f".format(temperature),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onCommit(pending); onExpandedChange(false) }) {
            Column(Modifier.padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.xs)) {
                Text(
                    "Temperature %.1f".format(pending),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = pending,
                    onValueChange = { pending = it },
                    onValueChangeFinished = { onCommit(pending) },
                    valueRange = TEMPERATURE_MIN..TEMPERATURE_MAX,
                )
            }
        }
    }
}

/** Small round icon button used across the composer toolbar. */
@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    container: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 10.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (attachment.type == AttachmentType.IMAGE) Icons.Rounded.Image else Icons.Rounded.Description,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                attachment.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(96.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(12.dp))
        }
    }
}
