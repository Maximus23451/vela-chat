package com.vela.chat.ui.profiles

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.ui.components.ConfirmDialog
import com.vela.chat.ui.components.PassphraseDialog
import kotlinx.coroutines.launch

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    onEditProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var overflow by remember { mutableStateOf(false) }
    var exportChoice by remember { mutableStateOf(false) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ApiProfile?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            if (text != null) {
                if (viewModel.isEncryptedExport(text)) {
                    pendingImport = text
                } else {
                    viewModel.import(text)
                }
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("Import profiles") },
                            leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                            onClick = { overflow = false; importLauncher.launch(arrayOf("application/json", "text/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export profiles") },
                            leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { overflow = false; exportChoice = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProfile,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add profile") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxWidth()) {
            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    hasKey = viewModel.hasKey(profile.id),
                    onClick = { onEditProfile(profile.id) },
                    onSetDefault = { viewModel.setDefault(profile) },
                    onDelete = { deleteTarget = profile },
                )
            }
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "No profiles yet. Add one to connect to LM Studio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    if (exportChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportChoice = false },
            title = { Text("Export profiles") },
            text = { Text("Including API keys encrypts the file with a passphrase you set — you'll need it to import the file again.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportChoice = false
                    showExportPassphraseDialog = true
                }) { Text("With keys") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportChoice = false
                    scope.launch { shareProfiles(context, viewModel.export(includeKeys = false)) }
                }) { Text("Without keys") }
            },
        )
    }

    if (showExportPassphraseDialog) {
        PassphraseDialog(
            title = "Encrypt export",
            confirmLabel = "Export",
            requireConfirm = true,
            onConfirm = { passphrase ->
                scope.launch {
                    shareProfiles(context, viewModel.export(includeKeys = true, passphrase = passphrase.toCharArray()))
                    showExportPassphraseDialog = false
                }
            },
            onDismiss = { showExportPassphraseDialog = false },
        )
    }

    pendingImport?.let { text ->
        PassphraseDialog(
            title = "Decrypt import",
            confirmLabel = "Import",
            requireConfirm = false,
            onConfirm = { passphrase ->
                viewModel.import(text, passphrase.toCharArray())
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete profile?",
            message = "\"${target.name}\" and its stored API key will be removed.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(target) },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun shareProfiles(context: android.content.Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TEXT, json)
        putExtra(Intent.EXTRA_TITLE, "vela-profiles.json")
    }
    context.startActivity(Intent.createChooser(intent, "Export profiles"))
}

@Composable
private fun ProfileRow(
    profile: ApiProfile,
    hasKey: Boolean,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    DefaultBadge()
                }
            }
            Text(
                profile.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    profile.providerType.label + (profile.model?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasKey) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Key, "Has API key", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!profile.isDefault) {
                    DropdownMenuItem(
                        text = { Text("Set as default") },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) },
                        onClick = { menu = false; onSetDefault() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menu = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun DefaultBadge() {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}
