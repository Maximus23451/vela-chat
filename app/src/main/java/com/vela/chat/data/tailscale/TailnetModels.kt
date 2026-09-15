package com.vela.chat.data.tailscale

import com.vela.chat.domain.model.ProviderType

/**
 * Lifecycle of the device's tailnet integration as reported by [TailnetEngine].
 *
 * V.E.L.A. uses the session-integration model (docs/TAILSCALE.md): the official
 * Tailscale Android app owns the device's single VpnService and V.E.L.A. rides on
 * top of it, so these statuses describe the *external* Tailscale app, not a VPN
 * owned by V.E.L.A.
 */
enum class TailnetStatus {
    /** The Tailscale app appears to be absent (best-effort heuristic — see [SessionTailnetEngine]). */
    NOT_INSTALLED,

    /**
     * Tailscale is present but not currently carrying tailnet traffic: its VPN is
     * disconnected, or another (non-Tailscale) VPN owns the device's single
     * VpnService slot, or the VPN is up but has not been assigned a tailnet address.
     */
    VPN_INACTIVE,

    /** A VPN transport is active and carries at least one address in the tailnet CGNAT range. */
    CONNECTED,
}

/**
 * Snapshot of the tailnet integration at [lastChecked] (epoch millis). Produced by
 * [TailnetEngine.detect] and surfaced through [TailnetManager.state].
 */
data class TailnetState(
    val status: TailnetStatus,
    /** Device addresses inside the tailnet CGNAT range (e.g. 100.101.1.23); empty unless [TailnetStatus.CONNECTED]. */
    val tailnetIps: List<String> = emptyList(),
    /**
     * MagicDNS suffix (the "…ts.net" domain of the tailnet). Always null in the
     * session-integration model — the suffix is only knowable through the official
     * app's LocalAPI, which Android sandboxing hides from us (docs/TAILSCALE.md).
     */
    val magicDnsSuffix: String? = null,
    /**
     * Number of peers the engine itself can enumerate. Always 0 in the
     * session-integration model (no peer enumeration without LocalAPI); the UI peer
     * list comes from [TailnetManager.peers] instead. Kept for the embedded-engine
     * upgrade path, which can enumerate peers.
     */
    val peerCount: Int = 0,
    /** Wall-clock time (epoch millis) of the last detection run; 0 until the first [TailnetManager.refresh] completes. */
    val lastChecked: Long = 0,
)

/**
 * A user-known tailnet host that should be scanned for AI servers. Persisted as a
 * compact "host|port|providerType" encoding (see [TailnetManager]); this object is
 * the decoded, decorated view of one such entry.
 */
data class TailnetPeer(
    /** Host as the user entered it: MagicDNS short name, FQDN, or literal IP. */
    val host: String,
    /** [host] when it is a DNS name, else null. */
    val fqdn: String? = null,
    /** [host] when it is a literal IPv4 address, else null. */
    val ip: String? = null,
    /** Provider family recorded when the peer was saved (or last probed); null if unknown. */
    val providerType: ProviderType? = null,
    /** Port the AI server is expected on; 0 when unknown. */
    val port: Int = 0,
    /** Most recent successful probe latency in ms, 0 when never probed this session. */
    val latencyMs: Long = 0,
    /** When this encoding was first observed in this app session (epoch millis); the persisted format carries no timestamp. */
    val discoveredAt: Long = 0,
)

/**
 * One live AI server discovered on the tailnet, ready to be turned into an
 * [com.vela.chat.domain.model.ApiProfile] with a single tap.
 */
data class AiServerCandidate(
    /** Host the server was found on (MagicDNS name or literal IP). */
    val host: String,
    /** Port the server answered on. */
    val port: Int,
    /** OpenAI-compatible base URL including the version path, e.g. "http://my-box:1234/v1". */
    val baseUrl: String,
    /** Classified provider family (port-based first, then response-shape hints). */
    val providerType: ProviderType,
    /** How many models `/v1/models` advertised; servers with an empty list are skipped. */
    val modelNameCount: Int,
    /** Measured round-trip of the probe request in ms (connect + read). */
    val latencyMs: Long,
)
