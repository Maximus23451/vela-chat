package com.vela.chat.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vela.chat.ui.chat.ConnectionQuality
import com.vela.chat.ui.components.nova.NovaStatusDot
import com.vela.chat.ui.theme.nova.LocalNovaColors
import com.vela.chat.ui.theme.nova.NovaTokens

/** Idle-connected status color (server reachable, nothing streaming). */
private val STATUS_GREEN = Color(0xFF2E7D32)

/** Reconnecting / probe-failure status color. */
private val STATUS_AMBER = Color(0xFFF59E0B)

/**
 * Nova chat header: interactive model selector (title), a live status line
 * (streaming/connected dot, tok/s of the last generation, connection quality,
 * tailnet chip), and an overflow menu with chat config, message multi-select,
 * Markdown/JSON/PDF export and JSON import.
 *
 * Uses M3 [TopAppBar] with a custom interactive title because the model menu
 * must open from the title itself; Nova tokens/status components are used for
 * the status line.
 */
@Composable
fun ChatTopBar(
    title: String,
    model: String?,
    profileName: String?,
    availableModels: List<String>,
    customModels: Set<String>,
    modelLoadError: String?,
    isStreaming: Boolean,
    tokensPerSecond: Float?,
    connectionQuality: ConnectionQuality,
    tailnetConnected: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectModel: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onRemoveModel: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChatConfig: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportJson: () -> Unit,
    onExportPdf: () -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    canExport: Boolean,
    selectionMode: Boolean = false,
    selectedCount: Int = 0,
    onExitSelectionMode: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onShareSelected: () -> Unit = {},
) {
    if (selectionMode) {
        SelectionTopBar(
            selectedCount = selectedCount,
            onExit = onExitSelectionMode,
            onDelete = onDeleteSelected,
            onShare = onShareSelected,
        )
        return
    }

    var modelMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var showAddModel by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Rounded.Menu, "Open conversations") }
        },
        title = {
            Column(
                Modifier
                    .clickable { modelMenu = true }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model ?: "Select model",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    Icon(Icons.Rounded.ArrowDropDown, "Choose model")
                }
                Text(
                    text = profileName ?: title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    Text(
                        "Models",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (availableModels.isEmpty()) {
                        Text(
                            modelLoadError ?: "No models loaded yet. Add one below or test the connection in the profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (modelLoadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    availableModels.forEach { m ->
                        val isCustom = m in customModels
                        DropdownMenuItem(
                            text = { Text(m, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp)) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (m == model) Icon(Icons.Rounded.Check, "Selected")
                                    if (isCustom) {
                                        IconButton(onClick = { onRemoveModel(m) }) {
                                            Icon(Icons.Rounded.Close, "Remove model", modifier = Modifier.heightIn(max = 18.dp))
                                        }
                                    }
                                }
                            },
                            onClick = { modelMenu = false; onSelectModel(m) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Add model ID…") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { modelMenu = false; showAddModel = true },
                    )
                    if (modelLoadError != null && availableModels.isNotEmpty()) {
                        Text(
                            modelLoadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onNewChat) { Icon(Icons.Rounded.Add, "New chat") }
            IconButton(onClick = { overflow = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                DropdownMenuItem(
                    text = { Text("Chat settings") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    onClick = { overflow = false; onOpenChatConfig() },
                )
                DropdownMenuItem(
                    text = { Text("Select messages") },
                    leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                    onClick = { overflow = false; onToggleSelectionMode() },
                )
                if (canExport) {
                    DropdownMenuItem(
                        text = { Text("Export as Markdown") },
                        leadingIcon = { Icon(Icons.Rounded.Description, null) },
                        onClick = { overflow = false; onExportMarkdown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Export as JSON") },
                        leadingIcon = { Icon(Icons.Rounded.DataObject, null) },
                        onClick = { overflow = false; onExportJson() },
                    )
                    DropdownMenuItem(
                        text = { Text("Export as PDF") },
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                        onClick = { overflow = false; onExportPdf() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Import chat…") },
                    leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                    onClick = { overflow = false; onImport() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                    onClick = { overflow = false; onOpenSettings() },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )

    if (showAddModel) {
        com.vela.chat.ui.components.TextInputDialog(
            title = "Add model ID",
            label = "e.g. GLM-4.6-Flash",
            confirmLabel = "Add",
            onConfirm = { onAddModel(it) },
            onDismiss = { showAddModel = false },
        )
    }
}

/**
 * Live status strip rendered directly beneath the chat app bar (NOT inside the
 * [TopAppBar] title slot — the title is height-capped at 64dp and a third row
 * there clips/overlaps on device). Shown only outside selection mode.
 */
@Composable
fun ChatStatusLine(
    isStreaming: Boolean,
    modelLoadError: String?,
    tokensPerSecond: Float?,
    connectionQuality: ConnectionQuality,
    tailnetConnected: Boolean,
    modifier: Modifier = Modifier,
    a2aPeers: List<com.vela.chat.domain.model.A2aPeer> = emptyList(),
    activeA2aPeerId: String? = null,
    onSelectPeer: (String) -> Unit = {},
) {
    var showPeerPicker by remember { mutableStateOf(false) }
    val activePeer = a2aPeers.firstOrNull { it.id == activeA2aPeerId }

    Row(
        modifier = modifier.padding(horizontal = NovaTokens.Spacing.lg, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotColor = when {
            isStreaming -> LocalNovaColors.current.streamIndicator
            modelLoadError != null -> STATUS_AMBER
            else -> STATUS_GREEN
        }
        NovaStatusDot(color = dotColor, pulsing = isStreaming)
        Text(
            text = buildString {
                append(
                    when {
                        isStreaming -> "Streaming"
                        modelLoadError != null -> "Reconnecting"
                        else -> "Connected"
                    },
                )
                tokensPerSecond?.let { append(" · %.1f tok/s".format(it)) }
                if (connectionQuality != ConnectionQuality.UNKNOWN) {
                    append(" · ${connectionQuality.label}")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = NovaTokens.Spacing.sm),
        )
        if (tailnetConnected) {
            Spacer(Modifier.width(NovaTokens.Spacing.sm))
            TailnetChip()
        }
        // Peer chip: for A2A profiles with a peer table (multi-gateway agents),
        // the agent that answers is always one glance away — and one tap from switching.
        if (a2aPeers.any { it.enabled }) {
            Spacer(Modifier.width(NovaTokens.Spacing.sm))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clickable { showPeerPicker = true },
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .padding(horizontal = NovaTokens.Spacing.sm, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.SmartToy,
                        contentDescription = "Switch peer",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = activePeer?.name ?: "Peer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }

    if (showPeerPicker) {
        PeerPickerDialog(
            peers = a2aPeers,
            activePeerId = activeA2aPeerId,
            onSelect = {
                showPeerPicker = false
                onSelectPeer(it.id)
            },
            onDismiss = { showPeerPicker = false },
        )
    }
}

/** Radio-list dialog over the active profile's A2A peers; disabled peers are listed but locked. */
@Composable
private fun PeerPickerDialog(
    peers: List<com.vela.chat.domain.model.A2aPeer>,
    activePeerId: String?,
    onSelect: (com.vela.chat.domain.model.A2aPeer) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Talk through which peer?") },
        text = {
            Column {
                peers.forEach { peer ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = peer.enabled) { onSelect(peer) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = peer.id == activePeerId,
                            onClick = if (peer.enabled) ({ onSelect(peer) }) else null,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                peer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (peer.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = peer.baseUrl + if (peer.enabled) "" else " · disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Small network chip shown while a tailnet VPN session is detected. */
@Composable
private fun TailnetChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .padding(horizontal = NovaTokens.Spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Lan,
                contentDescription = "Tailnet connected",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Compact app bar shown while selecting messages for delete/share. */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, "Exit selection") }
        },
        title = { Text("$selectedCount selected") },
        actions = {
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(Icons.Rounded.Share, "Share selected")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Rounded.Delete, "Delete selected")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
