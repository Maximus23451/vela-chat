package com.vela.chat.ui.screens.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.tailscale.TailnetPeer
import com.vela.chat.data.tailscale.TailnetStatus
import com.vela.chat.ui.components.nova.GlassSurface
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.components.nova.NovaSettingsRow
import com.vela.chat.ui.components.nova.NovaStatusDot
import com.vela.chat.ui.components.nova.NovaSwitchRow
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.components.nova.rememberHaptics
import com.vela.chat.ui.theme.nova.LocalNovaColors
import com.vela.chat.ui.theme.nova.NovaColorScheme
import com.vela.chat.ui.theme.nova.NovaTokens

/**
 * Tailscale screen: tailnet status card, AI-server scan (one tap → profile),
 * persisted peer list with forget, manual host add, and the discovery toggle.
 */
@Composable
fun TailscaleScreen(
    onBack: () -> Unit,
    viewModel: TailscaleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val nova = LocalNovaColors.current
    var manualHost by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NovaTopBar(
                title = "Tailscale",
                subtitle = "Find AI servers on your tailnet",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh status")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = NovaTokens.Spacing.md,
                vertical = NovaTokens.Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
        ) {
            item(key = "status") {
                GlassSurface {
                    Column(Modifier.padding(NovaTokens.Spacing.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NovaStatusDot(color = statusColor(state.tailnet.status, nova))
                            Spacer(Modifier.width(NovaTokens.Spacing.sm))
                            Text(
                                statusLabel(state.tailnet.status),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Rounded.Lan,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val ips = state.tailnet.tailnetIps
                        if (ips.isNotEmpty()) {
                        Text(
                            "This device: ${ips.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = NovaTokens.Spacing.sm),
                        )
                        }
                        state.tailnet.magicDnsSuffix?.let { suffix ->
                            Text(
                                "MagicDNS: $suffix",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = NovaTokens.Spacing.xs),
                            )
                        }
                        Text(
                            if (state.peers.isEmpty()) {
                                "No saved peers yet — add a host below or run a scan."
                            } else {
                                "${state.peers.size} saved peer${if (state.peers.size == 1) "" else "s"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = NovaTokens.Spacing.xs),
                        )
                    }
                }
            }

            item(key = "scan") {
                Column {
                    NovaSectionHeader("AI servers")
                    FilledTonalButton(
                        onClick = {
                            haptics(HapticFeedbackType.LongPress)
                            viewModel.scan()
                        },
                        enabled = !state.isScanning,
                        modifier = Modifier.padding(horizontal = NovaTokens.Spacing.sm),
                    ) {
                        Icon(Icons.Rounded.Radar, contentDescription = null)
                        Spacer(Modifier.width(NovaTokens.Spacing.sm))
                        Text(if (state.isScanning) "Scanning…" else "Scan for AI servers")
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                items(candidates, key = { "cand-${it.host}:${it.port}" }) { candidate ->
                    CandidateCard(candidate = candidate, onAdd = {
                        haptics(HapticFeedbackType.LongPress)
                        viewModel.addProfile(candidate)
                    })
                }
            }

            item(key = "manual") {
                Column(Modifier.padding(top = NovaTokens.Spacing.sm)) {
                    NovaSectionHeader("Add host")
                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = { manualHost = it },
                        placeholder = { Text("my-server or 100.101.1.23") },
                        singleLine = true,
                        enabled = !state.isScanning,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.addHost(manualHost)
                                manualHost = ""
                            },
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.addHost(manualHost)
                                    manualHost = ""
                                },
                                enabled = manualHost.isNotBlank() && !state.isScanning,
                            ) { Icon(Icons.Rounded.Add, contentDescription = "Probe and remember host") }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NovaTokens.Spacing.sm),
                    )
                    Text(
                        "Probed on all known AI ports and remembered for future scans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = NovaTokens.Spacing.md)
                            .padding(top = NovaTokens.Spacing.xs),
                    )
                }
            }

            item(key = "peers-header") { NovaSectionHeader("Saved peers") }
            if (state.peers.isEmpty()) {
                item(key = "peers-empty") {
                    NovaEmptyState(
                        icon = Icons.Rounded.Dns,
                        title = "No saved peers",
                        subtitle = "Add a MagicDNS name or 100.x address to remember a machine.",
                    )
                }
            } else {
                items(state.peers, key = { "peer-${it.host}:${it.port}" }) { peer ->
                    PeerRow(peer = peer, onForget = { viewModel.forgetPeer(peer.host) })
                }
            }

            item(key = "discovery") {
                NovaSwitchRow(
                    title = "Automatic discovery",
                    subtitle = "Probe saved peers for AI servers in the background",
                    checked = state.discoveryEnabled,
                    onCheckedChange = viewModel::setDiscovery,
                    modifier = Modifier.padding(top = NovaTokens.Spacing.sm),
                )
            }
        }
    }
}

/** One discovered AI server with a one-tap "Add profile" action. */
@Composable
private fun CandidateCard(candidate: AiServerUiModel, onAdd: () -> Unit) {
    GlassSurface(cornerRadius = NovaTokens.Shape.medium) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(NovaTokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(NovaTokens.Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "${candidate.host}:${candidate.port}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${candidate.providerLabel} · ${candidate.modelNameCount} model(s) · ${candidate.latencyMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(NovaTokens.Spacing.sm))
            FilledTonalButton(onClick = onAdd, enabled = !candidate.alreadyAdded) {
                Text(if (candidate.alreadyAdded) "Added" else "Add profile")
            }
        }
    }
}

/** A persisted peer with its provider hint, cached latency and a forget action. */
@Composable
private fun PeerRow(peer: TailnetPeer, onForget: () -> Unit) {
    NovaSettingsRow(
        title = "${peer.host}${if (peer.port > 0) ":${peer.port}" else ""}",
        subtitle = buildString {
            peer.providerType?.let { append(it.label) }
            if (peer.latencyMs > 0) {
                if (isNotEmpty()) append(" · ")
                append("${peer.latencyMs} ms")
            }
            if (isEmpty()) append("Tap the trash to forget this machine")
        },
        icon = Icons.Rounded.Dns,
        trailing = {
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Rounded.PersonRemove,
                    contentDescription = "Forget ${peer.host}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun statusColor(status: TailnetStatus, nova: NovaColorScheme): Color = when (status) {
    TailnetStatus.CONNECTED -> nova.success
    TailnetStatus.VPN_INACTIVE -> nova.warning
    TailnetStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: TailnetStatus): String = when (status) {
    TailnetStatus.CONNECTED -> "Connected to tailnet"
    TailnetStatus.VPN_INACTIVE -> "Tailscale VPN inactive"
    TailnetStatus.NOT_INSTALLED -> "Tailscale not detected"
}
