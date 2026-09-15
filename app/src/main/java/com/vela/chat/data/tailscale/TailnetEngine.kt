package com.vela.chat.data.tailscale

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.vela.chat.util.VelaConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over "how does this device reach a tailnet".
 *
 * V.E.L.A. ships the *session-integration* model (docs/TAILSCALE.md): the official
 * Tailscale Android app owns the device's single VpnService and V.E.L.A. rides on
 * top of it. [SessionTailnetEngine] implements this by inspecting the OS
 * connectivity stack; a future engine that embeds the Go libtailscale core can be
 * swapped in behind this same interface (upgrade path documented in
 * docs/TAILSCALE.md).
 */
interface TailnetEngine {

    /**
     * Snapshot of the current tailnet state. Fast (binder calls into the system
     * connectivity service; no network I/O) but blocking — callers must keep it off
     * the main thread; [TailnetManager.refresh] runs it on [Dispatchers.IO].
     */
    fun detect(): TailnetState

    /**
     * Resolves [host] — a MagicDNS short name ("my-box"), a full FQDN
     * ("my-box.tail-scale.ts.net") or a literal IP — into its addresses. When the
     * Tailscale VPN is up, MagicDNS answers transparently through the system
     * resolver; when it is down, only literal IPs resolve.
     *
     * Bounded by [VelaConstants.DNS_TIMEOUT_MS]; returns an empty list on any
     * failure or timeout instead of throwing.
     */
    suspend fun resolveHost(host: String): List<InetAddress>
}

/**
 * Session-based [TailnetEngine] that detects the official Tailscale app's VPN
 * through [ConnectivityManager]:
 *
 * 1. Any active network with [NetworkCapabilities.TRANSPORT_VPN] is a VPN.
 * 2. If that VPN's [LinkProperties] carry an IPv4 address in the tailnet CGNAT
 *    range (checked with the pragmatic [VelaConstants.TAILSCALE_IP_PREFIX]
 *    approximation of 100.64.0.0/10) the device is [TailnetStatus.CONNECTED].
 * 3. A VPN without a tailnet address (another VPN app, or Tailscale mid-handshake)
 *    is [TailnetStatus.VPN_INACTIVE].
 * 4. No VPN transport at all falls back to a best-effort "is Tailscale even
 *    installed" heuristic to pick between [TailnetStatus.VPN_INACTIVE] and
 *    [TailnetStatus.NOT_INSTALLED] (see [tailscaleAppPresent] for its limits).
 *
 * Everything is read-only system observation — V.E.L.A. never opens a VPN itself.
 */
@Singleton
class SessionTailnetEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TailnetEngine {

    override fun detect(): TailnetState {
        val now = System.currentTimeMillis()
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return stateForNoVpnTransport(lastChecked = now)
        val vpnNetworks = networksWithVpnTransport(connectivity)
        val tailnetIps = vpnNetworks.flatMap { tailnetAddressesOf(connectivity, it) }
        return when {
            tailnetIps.isNotEmpty() -> TailnetState(
                status = TailnetStatus.CONNECTED,
                tailnetIps = tailnetIps,
                // Unknowable without the official app's LocalAPI — see docs/TAILSCALE.md.
                magicDnsSuffix = null,
                peerCount = 0,
                lastChecked = now,
            )
            vpnNetworks.isNotEmpty() -> TailnetState(
                status = TailnetStatus.VPN_INACTIVE,
                magicDnsSuffix = null,
                peerCount = 0,
                lastChecked = now,
            )
            else -> stateForNoVpnTransport(lastChecked = now)
        }
    }

    override suspend fun resolveHost(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) {
            emptyList()
        } else {
            withTimeoutOrNull(VelaConstants.DNS_TIMEOUT_MS.toLong()) {
                runCatching { InetAddress.getAllByName(trimmed).toList() }.getOrDefault(emptyList())
            }.orEmpty()
        }
    }

    /** All currently-known networks whose capabilities include the VPN transport. */
    private fun networksWithVpnTransport(connectivity: ConnectivityManager): List<Network> = runCatching {
        connectivity.allNetworks.filter { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }.getOrDefault(emptyList())

    /**
     * IPv4 addresses of [network] inside the tailnet CGNAT range, identified with
     * the [VelaConstants.TAILSCALE_IP_PREFIX] check mandated by the Nova contract
     * (a pragmatic "100." approximation of 100.64.0.0/10). Only VPN transports are
     * examined, so carrier-grade NAT on cellular interfaces cannot false-positive.
     */
    private fun tailnetAddressesOf(connectivity: ConnectivityManager, network: Network): List<String> = runCatching {
        connectivity.getLinkProperties(network)?.linkAddresses
            .orEmpty()
            .mapNotNull { linkAddress -> linkAddress.address as? Inet4Address }
            .mapNotNull { address -> address.hostAddress?.takeIf { it.startsWith(VelaConstants.TAILSCALE_IP_PREFIX) } }
    }.getOrDefault(emptyList())

    /** No VPN transport is active: whether Tailscale is even installed decides the status. */
    private fun stateForNoVpnTransport(lastChecked: Long): TailnetState = TailnetState(
        status = if (tailscaleAppPresent()) TailnetStatus.VPN_INACTIVE else TailnetStatus.NOT_INSTALLED,
        magicDnsSuffix = null,
        peerCount = 0,
        lastChecked = lastChecked,
    )

    /**
     * "Is the official Tailscale app installed" check: a PackageManager lookup for
     * `com.tailscale.ipn`, permitted by the manifest `<queries>` entry (required on
     * API 30+ where package visibility is filtered). Treated as "not installed" on
     * any lookup failure.
     */
    private fun tailscaleAppPresent(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        /** Package name of the official Tailscale Android client. */
        const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    }
}
