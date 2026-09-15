package com.vela.chat.ui.screens.tailscale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.data.tailscale.AiServerCandidate
import com.vela.chat.data.tailscale.TailnetManager
import com.vela.chat.data.tailscale.TailnetPeer
import com.vela.chat.data.tailscale.TailnetState
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One discovered AI server, ready to become an [ApiProfile] with a single tap.
 * Wraps [AiServerCandidate] plus the "already added as a profile" flag.
 */
data class AiServerUiModel(
    val host: String,
    val port: Int,
    val baseUrl: String,
    val providerType: ProviderType,
    val providerLabel: String,
    val modelNameCount: Int,
    val latencyMs: Long,
    val alreadyAdded: Boolean,
)

/** Immutable UI state of the Tailscale screen. */
data class TailscaleUiState(
    val tailnet: TailnetState = TailnetState(status = TailnetStatusInitial),
    val peers: List<TailnetPeer> = emptyList(),
    val isScanning: Boolean = false,
    val discoveryEnabled: Boolean = true,
    val message: String? = null,
)

/**
 * VM behind the Tailscale screen: observes [TailnetManager.state] and
 * [TailnetManager.peers] plus the discovery setting, runs AI-server scans and
 * manual-host probes, persists peers, and turns discovered servers into real
 * API profiles (keyless — tailnet servers need no auth).
 */
@HiltViewModel
class TailscaleViewModel @Inject constructor(
    private val tailnetManager: TailnetManager,
    private val profileRepository: ApiProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val foundCandidates = MutableStateFlow<List<AiServerCandidate>>(emptyList())
    private val addedBaseUrls = MutableStateFlow<Set<String>>(emptySet())
    private val isScanning = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<TailscaleUiState> = combine(
        tailnetManager.state,
        tailnetManager.peers,
        foundCandidates,
        combine(isScanning, message) { scanning, msg -> ScanExtras(scanning, msg) },
        settingsRepository.settings,
    ) { tailnet, peers, _, extras, settings ->
        TailscaleUiState(
            tailnet = tailnet,
            peers = peers,
            isScanning = extras.isScanning,
            discoveryEnabled = settings.tailscaleDiscovery,
            message = extras.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TailscaleUiState())

    /** Discovered AI servers for the "Scan for AI servers" result list. */
    val candidates: StateFlow<List<AiServerUiModel>> = combine(
        foundCandidates,
        addedBaseUrls,
    ) { found, added ->
        found.map { candidate ->
            AiServerUiModel(
                host = candidate.host,
                port = candidate.port,
                baseUrl = candidate.baseUrl,
                providerType = candidate.providerType,
                providerLabel = candidate.providerType.label,
                modelNameCount = candidate.modelNameCount,
                latencyMs = candidate.latencyMs,
                alreadyAdded = candidate.baseUrl in added,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-runs VPN/tailnet detection (fire-and-forget inside the manager). */
    fun refresh() = tailnetManager.refresh()

    /** Scans the persisted peers for AI servers across the known ports. */
    fun scan() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            val found = runCatching { tailnetManager.scanForAiServers() }.getOrDefault(emptyList())
            foundCandidates.value = found
            message.value = if (found.isEmpty()) {
                "No AI servers answered — check that the VPN is up and servers are running."
            } else {
                "Found ${found.size} AI server${if (found.size == 1) "" else "s"}"
            }
            isScanning.value = false
        }
    }

    /**
     * Probes a manually entered host (MagicDNS name or 100.x.x.x) on all known
     * ports; every server that answers is remembered for future scans.
     */
    fun addHost(host: String) {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            val found = runCatching { tailnetManager.scanForAiServers(listOf(trimmed)) }.getOrDefault(emptyList())
            found.forEach { tailnetManager.rememberPeer(it.host, it.port, it.providerType) }
            message.value = if (found.isEmpty()) {
                "No AI server answered on $trimmed"
            } else {
                "Remembered $trimmed — ${found.size} server${if (found.size == 1) "" else "s"} found"
            }
            isScanning.value = false
        }
    }

    /** Removes every saved encoding for [host] from the persisted peer set. */
    fun forgetPeer(host: String) = viewModelScope.launch {
        tailnetManager.forgetPeer(host)
        message.value = "Removed $host from saved peers"
    }

    /** One-tap candidate → keyless profile named e.g. "LM Studio (tailnet)". */
    fun addProfile(candidate: AiServerUiModel) = viewModelScope.launch {
        val name = "${candidate.providerLabel} (tailnet)"
        runCatching {
            profileRepository.saveProfile(
                profile = ApiProfile(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    providerType = candidate.providerType,
                    baseUrl = candidate.baseUrl,
                    isDefault = false,
                ),
                apiKey = null,
            )
        }
        addedBaseUrls.update { it + candidate.baseUrl }
        message.value = "Added $name — ${candidate.host}:${candidate.port}"
    }

    /** Toggles automatic probing of persisted peers (settings). */
    fun setDiscovery(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTailscaleDiscovery(enabled)
    }

    fun consumeMessage() {
        message.value = null
    }

    /** Internal carrier so scanning state + message ride one [combine] slot. */
    private data class ScanExtras(val isScanning: Boolean, val message: String?)
}

/** Placeholder status used before the first detection run reaches the UI. */
private val TailnetStatusInitial = com.vela.chat.data.tailscale.TailnetStatus.VPN_INACTIVE
