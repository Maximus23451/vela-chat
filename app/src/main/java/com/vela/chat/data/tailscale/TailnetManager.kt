package com.vela.chat.data.tailscale

import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.util.VelaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the tailnet integration and the single source of truth ViewModels
 * observe: connection state (chat header indicator), MagicDNS resolution, AI-server
 * probing/scanning and peer persistence (Tailscale settings screen).
 *
 * Rides on the official Tailscale app's VPN (session-integration model —
 * docs/TAILSCALE.md). All blocking work (VPN detection, DNS, probes, DataStore)
 * runs on [Dispatchers.IO]; nothing here may be called on the main thread with
 * blocking expectations.
 */
@Singleton
class TailnetManager @Inject constructor(
    private val engine: TailnetEngine,
    private val scanner: PeerScanner,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Process-wide scope for fire-and-forget refreshes; this singleton outlives
     * every screen, so the scope is never cancelled.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serialises [refresh] so a slow detection cannot be overwritten by an older, still-running one. */
    private val refreshMutex = Mutex()

    /** Latency observed for "host:port" during this session's probes/scans (in-memory cache). */
    private val lastProbeLatency = ConcurrentHashMap<String, Long>()

    /** When each persisted peer encoding was first seen this session. */
    private val firstSeenAt = ConcurrentHashMap<String, Long>()

    private val _state = MutableStateFlow(
        TailnetState(status = TailnetStatus.VPN_INACTIVE, lastChecked = NOT_YET_CHECKED),
    )

    /** Bumped on every [refresh] (and after successful scans) to re-decorate [peers] with fresh latency. */
    private val peerRefreshTick = MutableStateFlow(0L)

    /**
     * Current tailnet state. Starts as [TailnetStatus.VPN_INACTIVE] with
     * [TailnetState.lastChecked] == [NOT_YET_CHECKED] ("not checked yet") and is
     * replaced as soon as the constructor-initiated detection completes.
     */
    val state: StateFlow<TailnetState> = _state.asStateFlow()

    /**
     * Persisted peers (DataStore "host|port|providerType" entries via
     * [SettingsRepository.tailscalePeers]), decoded into [TailnetPeer]s and
     * decorated with session data (fqdn/ip split, cached probe latency,
     * first-seen timestamp). Re-emitted on every [refresh] and after every
     * successful [scanForAiServers].
     */
    val peers: Flow<List<TailnetPeer>> = combine(settingsRepository.tailscalePeers, peerRefreshTick) { encoded, _ ->
        decodePeers(encoded)
    }.flowOn(Dispatchers.IO)

    init {
        refresh()
    }

    /**
     * Re-runs detection ([TailnetEngine.detect] on [Dispatchers.IO]) and publishes
     * the new [state]; also re-emits [peers] so freshly probed latencies show up.
     * Safe to call from the main thread — it launches on an IO scope and returns
     * immediately.
     */
    fun refresh() {
        scope.launch {
            refreshMutex.withLock {
                val detected = withContext(Dispatchers.IO) {
                    runCatching { engine.detect() }.getOrNull()
                }
                // On a detection failure keep the previous status but refresh the timestamp.
                _state.value = detected ?: _state.value.copy(lastChecked = System.currentTimeMillis())
            }
            peerRefreshTick.value += 1
        }
    }

    /**
     * Resolves [host] (MagicDNS short name, FQDN or literal IP) to address strings
     * via the system resolver. Tailnet (100.x) addresses are ordered first because
     * when the VPN is up they are the ones guaranteed reachable; empty on failure
     * or timeout ([VelaConstants.DNS_TIMEOUT_MS]).
     */
    suspend fun resolveHost(host: String): List<String> {
        val addresses = runCatching { engine.resolveHost(host) }.getOrDefault(emptyList())
        return addresses.mapNotNull { it.hostAddress?.takeIf(String::isNotEmpty) }
            .sortedWith(compareByDescending<String> { it.startsWith(VelaConstants.TAILSCALE_IP_PREFIX) })
    }

    /**
     * Probes a single host:port for an AI server (delegates to [PeerScanner.probe]).
     * On success the latency is cached for the [peers] decoration. Null when the
     * peer is offline or not an OpenAI-compatible server.
     */
    suspend fun probeServer(host: String, port: Int): AiServerCandidate? {
        val candidate = scanner.probe(host, port) ?: return null
        lastProbeLatency[probeKey(candidate.host, candidate.port)] = candidate.latencyMs
        return candidate
    }

    /**
     * Scans hosts for AI servers across [VelaConstants.AI_SERVER_PORTS].
     *
     * With [hosts] null, the persisted peers ([SettingsRepository.tailscalePeers])
     * are used — i.e. the machines the user actually saved, not a guess. Successful
     * candidates feed the latency cache and re-emit [peers]. Runs on
     * [Dispatchers.IO]; failures are skipped, so an empty result means "nothing
     * answered", never a crash.
     */
    suspend fun scanForAiServers(hosts: List<String>? = null): List<AiServerCandidate> = withContext(Dispatchers.IO) {
        val targets = hosts?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }?.distinct()
            ?: persistedHosts()
        val candidates = scanner.scan(targets)
        candidates.forEach { lastProbeLatency[probeKey(it.host, it.port)] = it.latencyMs }
        if (candidates.isNotEmpty()) peerRefreshTick.value += 1
        candidates
    }

    /**
     * Persists a peer for future scans as the encoded "host|port|providerType"
     * entry (duplicates of the exact encoding are collapsed by the DataStore set).
     * Invalid hosts/ports are ignored.
     */
    suspend fun rememberPeer(host: String, port: Int, providerType: ProviderType) {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || port !in MIN_VALID_PORT..MAX_VALID_PORT) return
        runCatching { settingsRepository.addTailscalePeer(encodePeer(trimmed, port, providerType)) }
    }

    /**
     * Removes every persisted encoding belonging to [host] (any port/provider —
     * hosts are compared case-insensitively). No-op when the host is unknown.
     */
    suspend fun forgetPeer(host: String) {
        val target = host.trim()
        if (target.isEmpty()) return
        runCatching {
            settingsRepository.tailscalePeers.first()
                .filter { encoded -> decodePeer(encoded)?.host?.equals(target, ignoreCase = true) == true }
                .forEach { settingsRepository.removeTailscalePeer(it) }
        }
    }

    /** Distinct hosts of all decodable persisted peers; empty on any DataStore hiccup. */
    private suspend fun persistedHosts(): List<String> = runCatching {
        settingsRepository.tailscalePeers.first()
            .mapNotNull { decodePeer(it)?.host }
            .distinct()
    }.getOrDefault(emptyList())

    private fun decodePeers(encoded: Set<String>): List<TailnetPeer> = encoded
        .mapNotNull { decodePeer(it) }
        .sortedBy { it.host.lowercase() }

    /**
     * Decodes one "host|port|providerType" entry; malformed entries are dropped
     * (never crash the flow). The providerType name parses leniently — an unknown
     * enum name decodes to null instead of failing the whole peer.
     */
    private fun decodePeer(encoded: String): TailnetPeer? {
        val parts = encoded.split(PEER_ENCODING_SEPARATOR)
        if (parts.size < ENCODED_PEER_PARTS) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in MIN_VALID_PORT..MAX_VALID_PORT) return null
        val providerType = parts.getOrNull(2)?.trim()?.let { name ->
            runCatching { ProviderType.valueOf(name) }.getOrNull()
        }
        val isLiteralIp = IPV4_REGEX.matches(host)
        return TailnetPeer(
            host = host,
            fqdn = host.takeUnless { isLiteralIp },
            ip = host.takeIf { isLiteralIp },
            providerType = providerType,
            port = port,
            latencyMs = lastProbeLatency[probeKey(host, port)] ?: 0L,
            discoveredAt = firstSeenAt.computeIfAbsent(encoded) { System.currentTimeMillis() },
        )
    }

    /** Encodes a peer into the persisted "host|port|providerType" format. */
    private fun encodePeer(host: String, port: Int, providerType: ProviderType): String =
        "$host$PEER_ENCODING_SEPARATOR$port$PEER_ENCODING_SEPARATOR${providerType.name}"

    private fun probeKey(host: String, port: Int): String = "$host:$port"
}

/** Rough "dotted-quad IPv4 literal" check (octet ranges not validated — classification only). */
private val IPV4_REGEX = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")

private const val PEER_ENCODING_SEPARATOR = "|"
private const val ENCODED_PEER_PARTS = 3
private const val MIN_VALID_PORT = 1
private const val MAX_VALID_PORT = 65535

/** Sentinel [TailnetState.lastChecked] meaning "detection has not completed yet". */
private const val NOT_YET_CHECKED = 0L
