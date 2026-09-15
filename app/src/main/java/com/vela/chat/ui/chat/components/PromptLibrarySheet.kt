package com.vela.chat.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.util.PromptVariables

/**
 * Prompt-library sheet for the composer: lists saved prompts and inserts the
 * chosen one into the composer. Prompts containing `{{variables}}` open a value
 * collection dialog first ([PromptVariables.extract]/[PromptVariables.render]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibrarySheet(
    prompts: List<SavedPrompt>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var variableTarget by remember { mutableStateOf<SavedPrompt?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = NovaTokens.Spacing.lg),
        ) {
            NovaSectionHeader(text = "Prompt library")

            if (prompts.isEmpty()) {
                Text(
                    text = "No saved prompts yet. Create them in Prompts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NovaTokens.Spacing.lg, vertical = NovaTokens.Spacing.md),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = SHEET_MAX_HEIGHT)) {
                    items(prompts, key = { it.id }) { prompt ->
                        PromptRow(
                            prompt = prompt,
                            onClick = {
                                if (PromptVariables.hasVariables(prompt.content)) {
                                    variableTarget = prompt
                                } else {
                                    onSelect(prompt.content)
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    variableTarget?.let { prompt ->
        VariableValuesDialog(
            prompt = prompt,
            onConfirm = { values ->
                onSelect(PromptVariables.render(prompt.content, values))
                variableTarget = null
                onDismiss()
            },
            onDismiss = { variableTarget = null },
        )
    }
}

@Composable
private fun PromptRow(prompt: SavedPrompt, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = NovaTokens.Spacing.lg, vertical = NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prompt.favorite) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = "Favorite prompt",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(NovaTokens.Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = prompt.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText(prompt.content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = prompt.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Single-line-friendly preview: markdown code fences render as their literal
 * backticks in plain text (observed on device as a stray "``` ```"), so they
 * collapse to a compact `[code]` marker.
 */
private fun previewText(content: String): String = content
    .replace(Regex("""```[a-zA-Z0-9_+-]*(\n|\s)*"""), " [code] ")
    .replace('\n', ' ')
    .trim()

/** One text field per `{{variable}}` found in the prompt content. */
@Composable
private fun VariableValuesDialog(
    prompt: SavedPrompt,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val variables = remember(prompt.id) { PromptVariables.extract(prompt.content) }
    val values = remember(prompt.id) { mutableStateMapOf<String, String>().apply { variables.forEach { put(it, "") } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title) },
        text = {
            Column {
                Text(
                    text = "Fill in the values for this prompt:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                variables.forEach { variable ->
                    OutlinedTextField(
                        value = values[variable].orEmpty(),
                        onValueChange = { values[variable] = it },
                        label = { Text(variable) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NovaTokens.Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values.toMap()) }) { Text("Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Max height of the prompt list inside the sheet, so long libraries stay scrollable. */
private val SHEET_MAX_HEIGHT = 420.dp
