package com.vela.chat.data.a2a

import com.vela.chat.data.remote.StreamEvent
import com.vela.chat.data.remote.describeHttpError
import com.vela.chat.util.VelaConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal A2A (Agent2Agent) client for talking to remote agents — the same
 * protocol the Hermes Agent gateway speaks (JSON-RPC 2.0 at `POST /`, Agent Card
 * at `/.well-known/agent-card.json`, bearer auth, optional SSE streaming).
 *
 * Deliberately tolerant of dialect drift: both 1.0 (`SendMessage`/ROLE_USER/
 * TASK_STATE_*) and pre-1.0 (`message/send`/user/camelCase) shapes are parsed;
 * requests use `message/send`/`message/stream`, which v1.0 servers accept as
 * aliases and older servers require. Long agent reasoning is expected — the
 * blocking send gets a generous read timeout from [VelaConstants].
 */
@Singleton
class A2aClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /** Dedicated client: agent tasks can run for minutes (Hermes default 300 s). */
    private val agentClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(VelaConstants.A2A_READ_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    /** Card cache so repeated sends don't re-fetch discovery (5 min TTL). */
    private val cardCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, A2aAgentCard>>()

    /**
     * Fetches and caches the Agent Card for a base URL (e.g. `http://host:9900`).
     * Tries the v1.0 card path, then the legacy one. Returns null when neither
     * answers with a parseable card.
     */
    suspend fun fetchAgentCard(baseUrl: String, forceRefresh: Boolean = false): A2aAgentCard? = withContext(Dispatchers.IO) {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cached = cardCache[normalized]
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && now - cached.first < CARD_TTL_MS) {
            return@withContext cached.second
        }
        for (path in VelaConstants.A2A_CARD_PATHS) {
            val request = Request.Builder().url(normalized + path).get().build()
            runCatching {
                agentClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val card = A2aJson.parseAgentCard(response.body?.string().orEmpty())
                    if (card != null) {
                        cardCache[normalized] = now to card
                        return@withContext card
                    }
                }
            }
        }
        null
    }

    /**
     * Sends one user turn to the agent and reports the outcome as [StreamEvent]s:
     * streaming agents emit incremental [StreamEvent.Token]s from SSE artifacts;
     * blocking agents emit the full text once complete. `contextId` continues an
     * existing agent-side thread when non-null; the returned `Completed` event
     * carries no usage stats (A2A has no token accounting). [preferBlocking]
     * skips the SSE path even when the card advertises it — used by tool callers
     * that only need the final text (blocking send + poll is the more
     * deterministic wire path there).
     */
    fun send(
        baseUrl: String,
        text: String,
        authHeader: String?,
        contextId: String?,
        onContextId: (String?) -> Unit = {},
        preferBlocking: Boolean = false,
    ): Flow<StreamEvent> = flow {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val card = fetchAgentCard(normalized)
        if (card == null) {
            emit(StreamEvent.Failed("No A2A agent found at $normalized (agent card unreachable). Is the agent running?"))
            return@flow
        }
        val endpoint = resolveEndpoint(card.url, normalized)
        val messageId = UUID.randomUUID().toString()
        val jsonMediaType = "application/json".toMediaType()

        if (card.streamingSupported && !preferBlocking) {
            val body = A2aJson.buildStreamMessage(text, messageId, contextId, card.isV1Dialect)
            val request = buildRequest(endpoint, authHeader).post(body.toRequestBody(jsonMediaType)).build()
            var lastContext: String? = null
            var failure: String? = null
            var receivedText = false
            runCatching {
                executeWithRetry(request).use { response ->
                    if (!response.isSuccessful) {
                        failure = a2aHttpFailure(response.code)
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        if (A2aJson.isErrorMessage(payload)) {
                            failure = A2aJson.parseResult(payload).agentText.ifBlank { "Agent returned an error" }
                            break
                        }
                        val event = A2aJson.parseStreamEvent(payload)
                        event.contextId?.let {
                            lastContext = it
                            onContextId(it)
                        }
                        when (event.state) {
                            A2aTaskState.WORKING, A2aTaskState.SUBMITTED -> if (event.agentText.isNotEmpty()) emit(StreamEvent.Token(event.agentText))
                            A2aTaskState.FAILED, A2aTaskState.REJECTED, A2aTaskState.CANCELED ->
                                failure = event.agentText.ifBlank { "Agent task ${event.state.name.lowercase()}" }
                            else -> if (event.agentText.isNotEmpty()) {
                            receivedText = true
                            emit(StreamEvent.Token(event.agentText))
                        }
                        }
                        if (event.state.isTerminal) break
                    }
                }
            }.onFailure { failure = it.message ?: "Agent connection failed" }
            if (failure == null && !receivedText) {
                // Stream closed without any content — surface it instead of
                // persisting a blank assistant message.
                failure = "Agent returned an empty response."
            }
            if (failure != null) {
                emit(StreamEvent.Failed(failure!!))
            } else {
                emit(StreamEvent.Completed(null))
            }
        } else {
            // Blocking send (optionally poll): agents without streaming support.
            val body = A2aJson.buildSendMessage(text, messageId, contextId, card.isV1Dialect)
            val request = buildRequest(endpoint, authHeader).post(body.toRequestBody(jsonMediaType)).build()
            val result = runCatching {
                executeWithRetry(request).use { response ->
                    if (!response.isSuccessful) return@use A2aResult(null, null, A2aTaskState.FAILED, a2aHttpFailure(response.code))
                    A2aJson.parseResult(response.body?.string().orEmpty())
                }
            }.getOrElse { A2aResult(null, null, A2aTaskState.FAILED, it.message ?: "Agent connection failed") }

            result.contextId?.let { onContextId(it) }
            var finalResult = result
            if (!result.state.isTerminal && result.taskId != null && result.state != A2aTaskState.UNKNOWN) {
                finalResult = pollUntilTerminal(endpoint, authHeader, result.taskId) { finalResult = it }
                finalResult.contextId?.let { onContextId(it) }
            }

            when {
                finalResult.state == A2aTaskState.INPUT_REQUIRED ->
                    emit(StreamEvent.Failed(finalResult.agentText.ifBlank { "The agent asks for more input (not supported in this chat yet)." }))
                finalResult.state.isTerminal && finalResult.state != A2aTaskState.COMPLETED ->
                    emit(StreamEvent.Failed(finalResult.agentText.ifBlank { "Agent task ${finalResult.state.name.lowercase()}" }))
                finalResult.agentText.isNotEmpty() -> {
                    emit(StreamEvent.Token(finalResult.agentText))
                    emit(StreamEvent.Completed(null))
                }
                else -> emit(StreamEvent.Failed("Agent returned an empty response."))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Polls `tasks/get` until a terminal state or the timeout budget is spent. */
    private suspend fun pollUntilTerminal(
        endpoint: String,
        authHeader: String?,
        taskId: String,
        onIntermediate: (A2aResult) -> Unit,
    ): A2aResult {
        val deadline = System.currentTimeMillis() + VelaConstants.A2A_TASK_TIMEOUT_MS
        val jsonMediaType = "application/json".toMediaType()
        var last = A2aResult(taskId, null, A2aTaskState.WORKING, "")
        while (System.currentTimeMillis() < deadline) {
            delay(VelaConstants.A2A_TASK_POLL_MS)
            val request = buildRequest(endpoint, authHeader)
                .post(A2aJson.buildTasksGet(taskId).toRequestBody(jsonMediaType))
                .build()
            val result = runCatching {
                executeWithRetry(request).use { response ->
                    if (!response.isSuccessful) return@use null
                    A2aJson.parseResult(response.body?.string().orEmpty())
                }
            }.getOrNull()
            if (result != null) {
                last = result
                onIntermediate(result)
                if (result.state.isTerminal) return result
            }
        }
        return last.copy(state = A2aTaskState.FAILED, agentText = last.agentText.ifBlank { "Timed out waiting for the agent." })
    }

    /** 429-aware execute: a single backoff-and-retry, per the gateway rate-limit rule. */
    private suspend fun executeWithRetry(request: Request): okhttp3.Response {
        val first = agentClient.newCall(request).execute()
        if (first.code != 429) return first
        first.close()
        delay(VelaConstants.A2A_RATE_LIMIT_BACKOFF_MS)
        return agentClient.newCall(request).execute()
    }

    /**
     * Error text for gateway status codes. A 401 is an IDENTITY failure — the
     * token is wrong or revoked, not a network problem — and is worded as such
     * so a mis-provisioned peer is never mistaken for downtime.
     */
    private fun a2aHttpFailure(code: Int): String = when (code) {
        401 -> "Identity rejected — token invalid or revoked (401)."
        429 -> "Rate limited by the gateway (429) — wait a moment and retry."
        else -> describeHttpError(code, null)
    }

    private fun buildRequest(url: String, authHeader: String?): Request.Builder =
        Request.Builder().url(url).let { builder ->
            if (!authHeader.isNullOrBlank()) builder.header("Authorization", authHeader) else builder
        }

    companion object {
        private const val CARD_TTL_MS = 5 * 60 * 1000L

        /**
         * The URL to send JSON-RPC calls (and the bearer token) to. The agent card is fetched
         * without authentication, so its advertised URL is only used when it has the same
         * scheme, host and port as the configured [baseUrl]; anything else — another host, a
         * downgrade to http, or a gateway describing itself as localhost — falls back to
         * [baseUrl] rather than sending the token wherever a card points.
         */
        internal fun resolveEndpoint(cardUrl: String?, baseUrl: String): String {
            val card = cardUrl?.toHttpUrlOrNull() ?: return baseUrl
            val base = baseUrl.toHttpUrlOrNull() ?: return baseUrl
            val sameOrigin = card.scheme == base.scheme &&
                card.host.equals(base.host, ignoreCase = true) &&
                card.port == base.port
            return if (sameOrigin) cardUrl else baseUrl
        }
    }
}
