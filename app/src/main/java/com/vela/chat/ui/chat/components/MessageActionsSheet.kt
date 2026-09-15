package com.vela.chat.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.theme.nova.NovaTokens

/** Common target languages offered by the translate action (kept short on purpose). */
val COMMON_TRANSLATION_LANGUAGES = listOf(
    "English", "Spanish", "French", "German", "Italian", "Portuguese",
    "Dutch", "Russian", "Chinese", "Japanese", "Korean", "Arabic",
    "Hindi", "Turkish", "Polish", "Ukrainian", "Indonesian", "Vietnamese",
)

/**
 * Bottom-sheet action menu for a message (long-press on a bubble): copy, edit,
 * delete, plus assistant-only retry/continue/summarize/translate and the
 * favorite/pin flag toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    isLastAssistant: Boolean,
    ttsEnabled: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onSummarize: () -> Unit,
    onTranslate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onSpeak: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAssistant = message.role == Role.ASSISTANT

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = NovaTokens.Spacing.lg),
        ) {
            NovaSectionHeader(text = "Message actions")

            SheetActionRow(
                icon = Icons.Rounded.ContentCopy,
                label = "Copy",
                onClick = { onDismiss(); onCopy() },
            )
            SheetActionRow(
                icon = Icons.Rounded.Edit,
                label = "Edit",
                onClick = { onDismiss(); onEdit() },
            )
            SheetActionRow(
                icon = Icons.Rounded.Delete,
                label = "Delete",
                onClick = { onDismiss(); onDelete() },
            )
            if (isAssistant) {
                SheetActionRow(
                    icon = Icons.Rounded.Refresh,
                    label = "Retry",
                    onClick = { onDismiss(); onRetry() },
                )
                if (isLastAssistant) {
                    SheetActionRow(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Continue generating",
                        onClick = { onDismiss(); onContinue() },
                    )
                }
                SheetActionRow(
                    icon = Icons.Rounded.Summarize,
                    label = "Summarize conversation",
                    onClick = { onDismiss(); onSummarize() },
                )
                SheetActionRow(
                    icon = Icons.Rounded.Translate,
                    label = "Translate…",
                    onClick = { onDismiss(); onTranslate() },
                )
                if (ttsEnabled && onSpeak != null) {
                    SheetActionRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Read aloud",
                        onClick = { onDismiss(); onSpeak() },
                    )
                }
            }
            SheetActionRow(
                icon = if (message.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                label = if (message.favorite) "Remove favorite" else "Add to favorites",
                onClick = { onDismiss(); onToggleFavorite() },
            )
            SheetActionRow(
                icon = if (message.pinned) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark,
                label = if (message.pinned) "Unpin" else "Pin",
                onClick = { onDismiss(); onTogglePin() },
            )
        }
    }
}

/**
 * Small chooser for the translate target language. Rendered on top of the
 * (already dismissed) actions sheet so only one modal layer is active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageChooserDialog(
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
            ) {
                COMMON_TRANSLATION_LANGUAGES.forEach { language ->
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onPick(language) }
                            .padding(horizontal = NovaTokens.Spacing.sm, vertical = NovaTokens.Spacing.xs),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NovaTokens.Spacing.lg, vertical = NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = NovaTokens.Spacing.md),
        )
    }
}
