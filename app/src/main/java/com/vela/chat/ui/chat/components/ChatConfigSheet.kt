package com.vela.chat.ui.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.ui.components.ParameterEditor
import com.vela.chat.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigSheet(
    initialSystemPrompt: String,
    initialParams: GenerationParams,
    onSave: (String, GenerationParams) -> Unit,
    onDismiss: () -> Unit,
    personas: List<AgentPersona> = emptyList(),
    activePersonaId: String? = null,
    onPersonaSelected: (String) -> Unit = {},
    profiles: List<ApiProfile> = emptyList(),
    activeProfileId: String? = null,
    onProfileSelected: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var systemPrompt by remember { mutableStateOf(initialSystemPrompt) }
    var params by remember { mutableStateOf(initialParams) }
    var selectedPersonaId by remember { mutableStateOf(activePersonaId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                "Chat settings",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
            )

            if (profiles.isNotEmpty()) {
                SectionHeader("Talk to")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == activeProfileId,
                            onClick = { onProfileSelected(profile.id) },
                            label = { Text(profile.name, maxLines = 1) },
                        )
                    }
                }
            }

            if (personas.isNotEmpty()) {
                SectionHeader("Personality")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    personas.forEach { persona ->
                        FilterChip(
                            selected = persona.id == selectedPersonaId,
                            onClick = {
                                selectedPersonaId = persona.id
                                onPersonaSelected(persona.id)
                                // Reflect the persona's prompt in the field so the user
                                // sees (and can tweak) what the persona actually does.
                                systemPrompt = persona.systemPrompt
                            },
                            label = { Text("${persona.emoji} ${persona.name}") },
                        )
                    }
                }
            }

            SectionHeader("System prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                placeholder = { Text("You are a helpful assistant…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                minLines = 3,
                maxLines = 8,
            )

            SectionHeader("Parameters")
            ParameterEditor(params = params, onChange = { params = it })

            Button(
                onClick = { onSave(systemPrompt, params); onDismiss() },
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) { Text("Apply to this chat") }
        }
    }
}
