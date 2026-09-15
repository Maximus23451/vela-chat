package com.vela.chat.ui.websearch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.settings.SearchProvider
import com.vela.chat.ui.components.BackScaffold
import com.vela.chat.ui.components.SectionHeader
import com.vela.chat.ui.components.ToggleRow
import com.vela.chat.ui.components.ValueSlider
import kotlin.math.roundToInt

@Composable
fun WebSearchScreen(
    onBack: () -> Unit,
    viewModel: WebSearchViewModel = hiltViewModel(),
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val provider = settings.searchProvider

    BackScaffold(title = "Web search", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            ToggleRow(
                "Enable web search",
                settings.webSearchEnabled,
                viewModel::setEnabled,
                "The model can call a search tool and answer from real results",
            )

            SectionHeader("Provider")
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = { menu = true }) {
                    Text(provider.label, Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    SearchProvider.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = { menu = false; viewModel.setProvider(p) },
                        )
                    }
                }
            }

            // ---- Provider-specific config (local-state fields → stable cursor) ----
            if (provider.needsUrl) {
                var url by remember { mutableStateOf(settings.searxngUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; viewModel.setSearxngUrl(it) },
                    label = { Text("SearXNG URL") },
                    placeholder = { Text("http://192.168.1.x:8080") },
                    singleLine = true,
                    supportingText = { Text("Must have its JSON API enabled (search.formats: [html, json]).") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (provider.needsApiKey) {
                var apiKey by remember(provider) { mutableStateOf(viewModel.getApiKey(provider)) }
                var visible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; viewModel.setApiKey(provider, it) },
                    label = { Text("${provider.label} API key") },
                    placeholder = { Text(provider.keyHint) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle")
                        }
                    },
                    supportingText = { Text("Stored encrypted in the Android Keystore.") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (provider.needsCx) {
                var cx by remember { mutableStateOf(settings.googleCx) }
                OutlinedTextField(
                    value = cx,
                    onValueChange = { cx = it; viewModel.setGoogleCx(it) },
                    label = { Text("Search engine ID (cx)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (provider == SearchProvider.DUCKDUCKGO) {
                Text(
                    "DuckDuckGo's free API returns limited instant-answer results — fine for definitions, weaker for general queries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            SectionHeader("Options")
            ValueSlider(
                label = "Max results",
                value = settings.webSearchMaxResults.toFloat(),
                onValueChange = { viewModel.setMaxResults(it.roundToInt()) },
                valueRange = 1f..10f,
                valueFormatter = { it.roundToInt().toString() },
            )

            Text(
                "Tip: web search needs a tool-capable model (Qwen2.5-7B-Instruct, Llama-3.1-8B-Instruct, etc.). " +
                    "Tiny models can't reliably call tools.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
