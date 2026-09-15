package com.vela.chat.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaFilledChip
import com.vela.chat.ui.components.nova.NovaSkeletonList
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens

/** Fraction formatter for RAM estimates ("~3.0 GB"). */
private fun ramLabel(estimateGb: Float?): String? =
    estimateGb?.let { "~%.1f GB (est.)".format(it) }

/** Latency badge label ("123 ms"); null when never probed. */
private fun latencyLabel(latencyMs: Long?): String? =
    latencyMs?.let { "$it ms" }

/**
 * Model dashboard: profile picker chips, then one card per model with family,
 * quantization, parameter count, RAM estimate, context length and latency.
 * Actions per card: set as default, refresh (top bar) and, for Ollama profiles,
 * pull a model by name with live streamed progress.
 */
@Composable
fun ModelDashboardScreen(
    onBack: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pullDraft by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NovaTopBar(
                title = "Models",
                subtitle = "Catalogue & metadata",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh models")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.profiles.isEmpty()) {
                NovaEmptyState(
                    icon = Icons.Rounded.Memory,
                    title = "No profiles yet",
                    subtitle = "Add an API profile in Settings to browse its models.",
                )
                return@Column
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
                contentPadding = PaddingValues(horizontal = NovaTokens.Spacing.md),
                modifier = Modifier.padding(vertical = NovaTokens.Spacing.xs),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    NovaFilledChip(
                        selected = profile.id == state.selectedProfileId,
                        onClick = { viewModel.selectProfile(profile.id) },
                        label = profile.name,
                    )
                }
            }

            state.error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = NovaTokens.Spacing.lg),
                )
            }

            if (state.isLoading && state.cards.isEmpty()) {
                Column(Modifier.padding(NovaTokens.Spacing.lg)) {
                    NovaSkeletonList(rows = 5)
                }
            } else if (state.cards.isEmpty()) {
                NovaEmptyState(
                    icon = Icons.Rounded.Memory,
                    title = "No models reported",
                    subtitle = "The server did not return a model catalogue. Try Refresh.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = NovaTokens.Spacing.md,
                        vertical = NovaTokens.Spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
                ) {
                    items(state.cards, key = { it.modelId }) { card ->
                        ModelCard(
                            card = card,
                            profile = state.profiles.firstOrNull { it.id == state.selectedProfileId },
                            onSetDefault = { viewModel.setDefaultModel(card.modelId) },
                            onPull = { pullDraft = card.modelId },
                        )
                    }
                }
            }
        }
    }

    if (state.pull != null || pullDraft != null) {
        PullDialog(
            pull = state.pull,
            initialName = state.pull?.model ?: pullDraft.orEmpty(),
            onStart = { name ->
                pullDraft = null
                viewModel.pullModel(name)
            },
            onDismiss = {
                pullDraft = null
                viewModel.dismissPull()
            },
        )
    }
}

/** One model row: metadata line + heuristic chips + actions. */
@Composable
private fun ModelCard(
    card: ModelCardUi,
    profile: ApiProfile?,
    onSetDefault: () -> Unit,
    onPull: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(NovaTokens.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.modelId,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (card.isDefault) {
                    Spacer(Modifier.width(NovaTokens.Spacing.sm))
                    Text(
                        "Default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs)) {
                card.family?.let { MetaChip(label = it) }
                card.quantization?.let { MetaChip(label = it) }
                card.parameterCount?.let { MetaChip(label = it) }
            }

            Text(
                listOfNotNull(
                    ramLabel(card.ramEstimateGb),
                    card.contextLength?.let { "ctx ${it.toString().toCharArray().reversed().chunked(3).joinToString(",").reversed()}" },
                    latencyLabel(card.latencyMs),
                ).joinToString("  ·  ")
                    .ifEmpty { "Metadata unknown — run Refresh while the server is online." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs)) {
                if (!card.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set as default") }
                }
                if (profile?.providerType == ProviderType.OLLAMA) {
                    TextButton(onClick = onPull) { Text("Pull…") }
                }
            }
        }
    }
}

/** Small non-interactive metadata chip. */
@Composable
private fun MetaChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

/**
 * Ollama pull dialog. Draft mode ([pull] null): editable model name + "Pull"
 * confirm. Streaming mode: live status line and progress bar fed by the
 * streamed `/api/pull` NDJSON response; "Hide" closes mid-pull (the pull keeps
 * running and the result dialog reopens when it finishes).
 */
@Composable
private fun PullDialog(
    pull: PullUiState?,
    initialName: String,
    onStart: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(pull?.model, initialName) { mutableStateOf(initialName) }
    val running = pull != null && !pull.finished

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val active = pull
            Text(if (active != null && !active.finished) "Pulling ${active.model}" else "Pull model")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm)) {
                if (!running) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Model name") },
                        placeholder = { Text("llama3.1:8b") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pull != null) {
                    Text(
                        pull.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pull.failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    pull.progress?.let { fraction ->
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${(fraction * PULL_PROGRESS_PERCENT).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = onDismiss) { Text("Hide") }
            } else {
                TextButton(onClick = { onStart(name.trim()) }, enabled = name.isNotBlank()) { Text("Pull") }
            }
        },
        dismissButton = {
            if (pull != null) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/** Pull dialog progress shown as a percentage of the streamed total. */
private const val PULL_PROGRESS_PERCENT = 100f
