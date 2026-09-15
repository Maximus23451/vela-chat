package com.vela.chat.ui.personas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens
import java.util.UUID

/** Emoji palette offered by the persona editor (kept fixed for predictability). */
private val PERSONA_EMOJIS = listOf(
    "🤖", "🪶", "💻", "🎓", "💡", "⚡", "✍️", "📊", "🎭", "🧪", "🧭", "🎯", "🔍", "🛠️",
)

/** Lower/upper bounds for the persona temperature slider. */
private const val TEMP_MIN = 0f
private const val TEMP_MAX = 1.5f

/**
 * Agent personalities manager: list with default marker, create/edit (emoji,
 * description, system prompt, optional temperature nudge), set-default, delete.
 */
@Composable
fun PersonasScreen(
    onBack: () -> Unit,
    viewModel: PersonasViewModel = hiltViewModel(),
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AgentPersona?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AgentPersona?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NovaTopBar(
                title = "Personalities",
                subtitle = "Choose who answers",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, "New personality")
            }
        },
    ) { padding ->
        if (personas.isEmpty()) {
            Box(Modifier.padding(padding)) {
                NovaEmptyState(
                    icon = Icons.Rounded.StarBorder,
                    title = "No personalities",
                    subtitle = "Create one to give your agent a style.",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = NovaTokens.Spacing.lg,
                    vertical = NovaTokens.Spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaRow(
                        persona = persona,
                        onClick = { editing = persona },
                        onSetDefault = { viewModel.setDefault(persona.id) },
                        onDelete = { deleting = persona },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        PersonaEditorDialog(
            persona = editing,
            onSave = { persona ->
                viewModel.upsert(persona)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { persona ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${persona.name}\"?") },
            text = { Text("Conversations using it keep their saved prompt, but it won't be offered anymore.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(persona.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PersonaRow(
    persona: AgentPersona,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(NovaTokens.Shape.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(NovaTokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(persona.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(NovaTokens.Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, style = MaterialTheme.typography.titleMedium)
                    if (persona.isDefault) {
                        Spacer(Modifier.width(NovaTokens.Spacing.sm))
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Default personality",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (persona.description.isNotBlank()) {
                    Text(
                        persona.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    persona.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (!persona.isDefault) {
                IconButton(onClick = onSetDefault) {
                    Icon(Icons.Rounded.StarBorder, "Set as default")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, "Delete personality")
                }
            }
        }
    }
}

@Composable
private fun PersonaEditorDialog(
    persona: AgentPersona?,
    onSave: (AgentPersona) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(persona?.name ?: "") }
    var description by remember { mutableStateOf(persona?.description ?: "") }
    var emoji by remember { mutableStateOf(persona?.emoji ?: "🤖") }
    var systemPrompt by remember { mutableStateOf(persona?.systemPrompt ?: "") }
    var useTemp by remember { mutableStateOf(persona?.temperature != null) }
    var temperature by remember { mutableStateOf(persona?.temperature ?: 0.7f) }

    val valid = name.isNotBlank() && systemPrompt.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (persona == null) "New personality" else "Edit personality") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PERSONA_EMOJIS.forEach { candidate ->
                        Text(
                            candidate,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clickable { emoji = candidate }
                                .padding(4.dp),
                            color = if (candidate == emoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System prompt") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = useTemp,
                        onClick = { useTemp = !useTemp },
                        label = { Text("Custom temperature") },
                    )
                    if (useTemp) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "%.2f".format(temperature),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (useTemp) {
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = TEMP_MIN..TEMP_MAX,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        (persona ?: AgentPersona(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            systemPrompt = "",
                        )).copy(
                            name = name.trim(),
                            description = description.trim(),
                            emoji = emoji,
                            systemPrompt = systemPrompt.trim(),
                            temperature = if (useTemp) temperature else null,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
