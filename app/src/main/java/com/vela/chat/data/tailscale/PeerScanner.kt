package com.vela.chat.data.tailscale

import com.vela.chat.data.AppJson
import com.vela.chat.data.a2a.A2aJson
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.util.VelaConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes tailnet hosts for OpenAI-compatible AI servers by issuing
 * `GET http://host:port/v1/models` with the short probe timeouts from
 * [VelaConstants]. Discovery is deliberately best-effort: offline peers, closed
 * ports, non-JSON responses and empty model lists all yield `null` and are
 * silently skipped — a tailnet always has machines that are asleep.
 */
@Singleton
class PeerScanner @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /**
     * The app's shared client rebuilt with the snappy probe timeouts from
     * [VelaConstants] — the shared client's long read timeout exists for token
     * streams and would make scans crawl. Deriving via [OkHttpClient.newBuilder]
     * keeps the shared connection pool and dispatcher.
     */
    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(VelaConstants.PROBE_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(VelaConstants.PROBE_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Probes one host:port for an OpenAI-compatible model catalogue. No API key is
     * sent — tailnet peers are the user's own machines (cleartext HTTP is by design
     * for LAN/tailnet LM Studio).
     *
     * @return an [AiServerCandidate] with measured round-trip latency and a provider
     * classification (default port first, then `lmstudio`/`ollama` markers in the
     * response headers and model metadata), or null when the peer is unreachable,
     * too slow, or answers without a non-empty model list.
     */
    suspend fun probe(host: String, port: Int): AiServerCandidate? = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty() || port !in VALID_PORT_RANGE) return@withContext null
        runCatching {
            val baseUrl = "http://${urlHost(trimmedHost)}:$port$BASE_PATH"
            val request = Request.Builder()
                .url("$baseUrl$MODELS_PATH")
                .header("Accept", "application/json")
                .build()
            val startedAt = System.nanoTime()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val latencyMs = (System.nanoTime() - startedAt) / NANOS_PER_MS
                val models = AppJson.decodeFromString<ModelsResponse>(body).data
                val namedModels = models.count { !it.id.isNullOrBlank() }
                if (namedModels == 0) return@runCatching null
                AiServerCandidate(
                    host = trimmedHost,
                    port = port,
                    baseUrl = baseUrl,
                    providerType = classify(port, response.headers, models),
                    modelNameCount = namedModels,
                    latencyMs = latencyMs,
                )
            }
        }.getOrNull()
    }

    /**
     * Probes every port in [VelaConstants.AI_SERVER_PORTS] on every host in [hosts],
     * plus the A2A agent port ([VelaConstants.A2A_DEFAULT_PORT], Hermes Agent's
     * gateway) via an Agent Card probe, bounded by [MAX_CONCURRENT_PROBES] parallel
     * probes so a large tailnet does not open dozens of sockets at once. Returns
     * only the servers that answered; a blank/whitespace input list yields an empty
     * result without any network I/O.
     */
    suspend fun scan(hosts: List<String>): List<AiServerCandidate> {
        val targets = hosts.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (targets.isEmpty()) return emptyList()
        val gate = Semaphore(MAX_CONCURRENT_PROBES)
        return coroutineScope {
            targets.flatMap { host ->
                VelaConstants.AI_SERVER_PORTS.map { port ->
                    async { gate.withPermit { probe(host, port) } }
                } + listOf(
                    async { gate.withPermit { probeA2a(host) } },
                )
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Probes the A2A well-known Agent Card paths on [VelaConstants.A2A_DEFAULT_PORT].
     * A parseable card means an agent gateway (e.g. Hermes Agent) is listening; the
     * candidate carries the card's declared name in `modelNameCount` slot as the
     * skill count and its endpoint as the base URL.
     */
    suspend fun probeA2a(host: String, port: Int = VelaConstants.A2A_DEFAULT_PORT): AiServerCandidate? = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty() || port !in VALID_PORT_RANGE) return@withContext null
        runCatching {
            val baseUrl = "http://${urlHost(trimmedHost)}:$port/"
            for (cardPath in VelaConstants.A2A_CARD_PATHS) {
                val request = Request.Builder()
                    .url(baseUrl + cardPath)
                    .header("Accept", "application/json")
                    .build()
                val startedAt = System.nanoTime()
                val card = probeClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    A2aJson.parseAgentCard(response.body?.string().orEmpty())
                }
                if (card != null) {
                    return@runCatching AiServerCandidate(
                        host = trimmedHost,
                        port = port,
                        baseUrl = baseUrl,
                        providerType = ProviderType.A2A_AGENT,
                        modelNameCount = card.skills.size,
                        latencyMs = (System.nanoTime() - startedAt) / NANOS_PER_MS,
                    )
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Provider classification, most confident signal first: Ollama's default port,
     * LM Studio's default port, then response-shape hints (`lmstudio` markers in
     * headers or model metadata; `ollama` in `owned_by`, which Ollama's
     * OpenAI-compatible endpoint reports); everything else is a generic
     * [ProviderType.CUSTOM] OpenAI-compatible server.
     */
    private fun classify(port: Int, headers: Headers, models: List<ModelEntry>): ProviderType = when {
        port == OLLAMA_DEFAULT_PORT -> ProviderType.OLLAMA
        port == LM_STUDIO_DEFAULT_PORT -> ProviderType.LM_STUDIO
        headers.names().any { it.contains(LM_STUDIO_MARKER, ignoreCase = true) } -> ProviderType.LM_STUDIO
        models.any { it.owned_by.orEmpty().contains(OLLAMA_MARKER, ignoreCase = true) } -> ProviderType.OLLAMA
        models.any { entry ->
            entry.owned_by.orEmpty().contains(LM_STUDIO_MARKER, ignoreCase = true) ||
                entry.id.orEmpty().contains(LM_STUDIO_MARKER, ignoreCase = true)
        } -> ProviderType.LM_STUDIO
        else -> ProviderType.CUSTOM
    }

    /** Wraps IPv6 literals in brackets for URL construction (tailnet hosts are normally IPv4/hostnames). */
    private fun urlHost(host: String): String = if (host.contains(':')) "[$host]" else host
}

/** Minimal `/v1/models` payload, parsed leniently with [AppJson] (unknown fields ignored). */
@Serializable
private data class ModelsResponse(val data: List<ModelEntry> = emptyList())

/** One entry of `/v1/models`; `owned_by` hints at the serving stack. */
@Serializable
private data class ModelEntry(val id: String? = null, val owned_by: String? = null)

private const val BASE_PATH = "/v1"
private const val MODELS_PATH = "/models"
private const val NANOS_PER_MS = 1_000_000L

/** Parallelism cap for [PeerScanner.scan] — enough for a large tailnet, gentle on sleepy peers. */
private const val MAX_CONCURRENT_PROBES = 8

private const val LM_STUDIO_MARKER = "lmstudio"
private const val OLLAMA_MARKER = "ollama"

/**
 * Default ports mirrored from [VelaConstants.AI_SERVER_PORTS]; named here (instead
 * of raw literals) until they are promoted to named entries in VelaConstants, which
 * this package does not own.
 */
private const val LM_STUDIO_DEFAULT_PORT = 1234
private const val OLLAMA_DEFAULT_PORT = 11434

private val VALID_PORT_RANGE = 1..65535
