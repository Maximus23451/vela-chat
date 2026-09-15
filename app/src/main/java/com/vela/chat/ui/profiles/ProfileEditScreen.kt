package com.vela.chat.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.ui.components.BackScaffold
import com.vela.chat.ui.components.ConfirmDialog
import com.vela.chat.ui.components.SectionHeader
import com.vela.chat.ui.components.ToggleRow

@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var keyVisible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    BackScaffold(
        title = if (state.isNew) "Add profile" else "Edit profile",
        onBack = onBack,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Provider")
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ProviderType.entries.toList()) { type ->
                    FilterChip(
                        selected = state.providerType == type,
                        onClick = { viewModel.selectProvider(type) },
                        label = { Text(type.label) },
                    )
                }
            }

            SectionHeader("Details")
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Display name") },
                placeholder = { Text(state.providerType.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.1.10:1234/v1") },
                singleLine = true,
                supportingText = { Text("Auto-filled per provider (usually ends in /v1). For LM Studio, use your computer's LAN IP from a phone.") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text("API key") },
                placeholder = {
                    Text(if (state.hasStoredKey) "•••••••• (saved — leave blank to keep)" else "Optional for LM Studio")
                },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "Toggle key visibility",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                "Keys are encrypted with the Android Keystore and never leave the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            // Security: warn if a key would be transmitted over unencrypted HTTP.
            val keyOverHttp = state.baseUrl.trim().startsWith("http://", ignoreCase = true) &&
                (state.apiKey.isNotBlank() || state.hasStoredKey)
            if (keyOverHttp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This API key will be sent over unencrypted HTTP. If your server can be reached over https:// — even with its own self-signed certificate — switch the URL and use Test Connection to review and trust it once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---- Peer table: A2A profiles talk THROUGH these gateways; other
            // profiles get them as tools (the model can message these agents). ----
            if (state.providerType == ProviderType.A2A_AGENT) {
                SectionHeader("Peers")
                Text(
                    "Optional: let this agent talk through several gateways. Each peer has its own URL and its own token — a token is the identity, never reuse one across peers or profiles. Without peers, the Base URL and API key above are used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                PeerRows(state, viewModel)
            } else {
                SectionHeader("Agent contacts (A2A tools)")
                Text(
                    "Optional: give this model remote-agent contacts — each peer becomes a tool (e.g. \"message_alpha\") the model can call to message that agent and relay the reply. Peers need their own token: a token is the identity, never reuse one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                PeerRows(state, viewModel)
            }

            SectionHeader("Model")

            OutlinedTextField(
                value = state.model.orEmpty(),
                onValueChange = viewModel::setModel,
                label = { Text("Model id") },
                placeholder = { Text("Test connection to load available models") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (state.availableModels.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.availableModels) { model ->
                        FilterChip(
                            selected = state.model == model,
                            onClick = { viewModel.selectModel(model) },
                            label = { Text(model, maxLines = 1) },
                        )
                    }
                }
            }

            ToggleRow("Set as default profile", state.isDefault, viewModel::setDefault)

            // Test connection
            OutlinedButton(
                onClick = viewModel::test,
                enabled = !state.testing && state.baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Bolt, null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Test connection")
            }

            state.testResult?.let { result ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.testSuccess == true) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        null,
                        tint = if (state.testSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.testSuccess == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) { Text("Save profile") }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete profile?",
            message = "This profile and its stored API key will be removed.",
            confirmLabel = "Delete",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }

    state.pendingCertTrust?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissPendingCertTrust,
            title = { Text("New certificate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${pending.host} presented a certificate that isn't from a recognized " +
                            "authority — expected for a self-signed local server. Verify the " +
                            "fingerprint below yourself (e.g. against the server's own logs) " +
                            "before trusting it; it's pinned afterward, so a MITM swapping the " +
                            "certificate later will be rejected rather than trusted silently.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        pending.fingerprintSha256,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::trustPendingCertificate) {
                    Text("Trust & retry")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::dismissPendingCertTrust) { Text("Cancel") }
            },
        )
    }
}

/** Shared peer rows + "Add peer" button for both the A2A and the agent-contacts flavor. */
@Composable
private fun PeerRows(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    state.peers.forEach { peer ->
        PeerEditorRow(
            peer = peer,
            onName = { viewModel.setPeerName(peer.id, it) },
            onBaseUrl = { viewModel.setPeerBaseUrl(peer.id, it) },
            onToken = { viewModel.setPeerToken(peer.id, it) },
            onEnabled = { viewModel.setPeerEnabled(peer.id, it) },
            onRemove = { viewModel.removePeer(peer.id) },
        )
    }
    OutlinedButton(
        onClick = viewModel::addPeer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Rounded.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("Add peer")
    }
}

/** One editable peer of an A2A profile: gateway URL + its own bearer token + enable switch. */
@Composable
private fun PeerEditorRow(
    peer: com.vela.chat.ui.profiles.PeerDraft,
    onName: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var tokenVisible by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = peer.name,
            onValueChange = onName,
            label = { Text("Peer name") },
            placeholder = { Text("Home assistant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = peer.baseUrl,
            onValueChange = onBaseUrl,
            label = { Text("Gateway URL") },
            placeholder = { Text("http://192.0.2.10:9900") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = peer.token,
            onValueChange = onToken,
            label = { Text("Peer token") },
            placeholder = {
                Text(if (peer.hasStoredToken) "•••••••• (saved — leave blank to keep)" else "Bearer token issued by this gateway")
            },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        "Toggle token visibility",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = peer.enabled, onCheckedChange = onEnabled)
            Text(
                "Enabled",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove peer") }
        }
    }
}
