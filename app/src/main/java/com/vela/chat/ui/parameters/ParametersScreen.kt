package com.vela.chat.ui.parameters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.ui.components.BackScaffold
import com.vela.chat.ui.components.ParameterEditor
import com.vela.chat.ui.components.SectionHeader
import com.vela.chat.ui.settings.SettingsViewModel

@Composable
fun ParametersScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsOpt by viewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsOpt ?: return

    var systemPrompt by remember { mutableStateOf("") }
    var params by remember { mutableStateOf(com.vela.chat.domain.model.GenerationParams.Default) }
    var initialized by remember { mutableStateOf(false) }

    if (!initialized) {
        systemPrompt = settings.defaultSystemPrompt
        params = settings.defaultParams
        initialized = true
    }

    BackScaffold(
        title = "Default parameters",
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = {
                viewModel.setDefaultSystemPrompt(systemPrompt)
                viewModel.setDefaultParams(params)
                onBack()
            }) { Icon(Icons.Rounded.Check, "Save") }
        },
    ) { padding ->

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Default system prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = {
                    systemPrompt = it
                    viewModel.setDefaultSystemPrompt(it)
                },
                placeholder = { Text("You are a helpful assistant…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                minLines = 3,
                maxLines = 8,
            )
            Text(
                "Applied to new chats. Each chat keeps its own copy you can tweak from its menu.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionHeader("Sampler defaults")
            ParameterEditor(params = params, onChange = { params = it }, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
