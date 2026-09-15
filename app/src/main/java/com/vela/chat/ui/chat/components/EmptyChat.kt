package com.vela.chat.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaFilledChip
import com.vela.chat.ui.theme.nova.NovaTokens

/**
 * First-run / new-chat greeting: Nova empty state, agent-personality picker
 * (applied before the chat starts), quick actions and prompt suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChat(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenModels: () -> Unit,
    personas: List<AgentPersona> = emptyList(),
    activePersonaId: String? = null,
    onSelectPersona: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(NovaTokens.Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NovaEmptyState(
            icon = Icons.Rounded.AutoAwesome,
            title = "How can I help today?",
            subtitle = "V.E.L.A. — your Versatile Engine for Local AI. Ask anything, attach files, or pick a prompt below.",
        )

        if (personas.isNotEmpty()) {
            Text(
                text = "Personality",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = NovaTokens.Spacing.lg),
                textAlign = TextAlign.Center,
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = NovaTokens.Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
            ) {
                personas.forEach { persona ->
                    NovaFilledChip(
                        selected = persona.id == activePersonaId,
                        onClick = { onSelectPersona(persona.id) },
                        label = "${persona.emoji} ${persona.name}",
                    )
                }
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = NovaTokens.Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
        ) {
            NovaFilledChip(selected = false, onClick = onOpenProfiles, label = "Profiles")
            NovaFilledChip(selected = false, onClick = onOpenModels, label = "Browse models")
        }

        Text(
            text = "Try asking",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = NovaTokens.Spacing.lg),
            textAlign = TextAlign.Center,
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = NovaTokens.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
        ) {
            suggestions.forEach { suggestion ->
                NovaFilledChip(selected = false, onClick = { onSuggestion(suggestion) }, label = suggestion)
            }
        }
    }
}
