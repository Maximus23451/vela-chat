package com.vela.chat.data.repository

import com.vela.chat.data.AppJson
import com.vela.chat.data.a2a.A2aClient
import com.vela.chat.data.a2a.A2aPeerTool
import com.vela.chat.data.a2a.A2aTaskState
import com.vela.chat.data.net.findUntrustedCert
import com.vela.chat.data.remote.ChatStreamClient
import com.vela.chat.data.remote.ChatTarget
import com.vela.chat.data.remote.NetworkResult
import com.vela.chat.data.remote.OpenAiApi
import com.vela.chat.data.remote.RequestFactory
import com.vela.chat.data.remote.SearchResult
import com.vela.chat.data.remote.StreamEvent
import com.vela.chat.data.remote.WebSearchClient
import com.vela.chat.data.remote.describeHttpError
import com.vela.chat.data.remote.dto.RequestMessage
import com.vela.chat.data.remote.dto.ToolDto
import com.vela.chat.data.remote.dto.Usage
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.SearchConfig
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.util.VelaConstants
import com.vela.chat.domain.model.A2aPeer
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a non-streaming completion. */
data class CompletionResult(
    val content: String,
    val reasoning: String?,
    val usage: Usage?,
)

/**
 * One callable tool for the agentic (OpenAI tool-calling) loop, with its in-app
 * executor. The model only sees [definition]; [status] renders the live line in
 * the chat overlay while the tool runs; [execute] returns the text fed back to
 * the model as the tool result.
 */
data class AgentTool(
    val definition: ToolDto,
    val status: (argumentsJson: String) -> String,
    val execute: suspend (argumentsJson: String) -> String,
)

@Singleton
class ChatRepository @Inject constructor(
    private val api: OpenAiApi,
    private val streamClient: ChatStreamClient,
    private val profileRepository: ApiProfileRepository,
    private val webSearchClient: WebSearchClient,
    private val a2aClient: A2aClient,
    private val settingsRepository: SettingsRepository,
    private val secureStore: SecureStore,
) {
    /**
     * Resolve the API target for a conversation, falling back to the default
     * profile and its configured model.
     */
    suspend fun resolveTarget(conversation: Conversation): Result<ChatTarget> {
        val profile = conversation.profileId?.let { profileRepository.getProfile(it) }
            ?: profileRepository.getDefaultOrFirst()
            ?: return Result.failure(IllegalStateException("No API profile configured. Add one in Settings → API Profiles."))

        val apiKey = profileRepository.getApiKey(profile.id)
        val model = conversation.model ?: profile.model
        if (profile.providerType == ProviderType.A2A_AGENT) {
            // Peer-table profiles (one profile reaching several agent gateways)
            // override URL and credential per conversation. Profiles
            // without peers keep their own base URL and key — behavior unchanged.
            val peers = settingsRepository.a2aPeersOnce(profile.id)
            val choice = conversation.id.takeIf { peers.isNotEmpty() }
                ?.let { settingsRepository.a2aPeerChoice(it) }
            val peer = A2aPeer.resolve(peers, choice)
            val baseUrl = peer?.normalizedBaseUrl ?: profile.normalizedBaseUrl
            val peerKey = peer?.let {
                secureStore.getSecret(VelaConstants.A2A_PEER_SECRET_PREFIX + "${profile.id}_${it.id}")
            } ?: apiKey
            // A2A agents speak JSON-RPC at the root; the agent card is the
            // closest analogue of a models list.
            return Result.success(
                ChatTarget(
                    chatUrl = baseUrl,
                    modelsUrl = baseUrl + VelaConstants.A2A_CARD_PATHS.first(),
                    authHeader = RequestFactory.bearer(peerKey),
                    model = model ?: peer?.name ?: "agent",
                    providerType = profile.providerType,
                    peerName = peer?.name,
                ),
            )
        }
        if (model.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No model selected. Pick one for this chat or in the profile."))
        }
        return Result.success(
            ChatTarget(
                chatUrl = profile.normalizedBaseUrl + "chat/completions",
                modelsUrl = profile.normalizedBaseUrl + "models",
                authHeader = RequestFactory.bearer(apiKey),
                model = model,
                providerType = profile.providerType,
            ),
        )
    }

    /**
     * Streams a generation. For OpenAI-compatible targets this is the SSE chat
     * pipeline; for [ProviderType.A2A_AGENT] targets the last user turn is sent
     * over the A2A protocol instead (history/context lives agent-side, keyed by
     * a per-conversation `contextId` persisted in DataStore).
     */
    fun stream(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        conversationId: String? = null,
    ): Flow<StreamEvent> =
        if (target.providerType == ProviderType.A2A_AGENT) {
            a2aStream(target, history, conversationId)
        } else {
            val request = RequestFactory.build(target, history, params, systemPrompt, stream = true)
            streamClient.stream(target.chatUrl, target.authHeader, request)
        }

    suspend fun complete(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        conversationId: String? = null,
    ): NetworkResult<CompletionResult> = withContext(Dispatchers.IO) {
        if (target.providerType == ProviderType.A2A_AGENT) {
            var text = ""
            var error: String? = null
            a2aStream(target, history, conversationId).collect { event ->
                when (event) {
                    is StreamEvent.Token -> text += event.text
                    is StreamEvent.Failed -> error = event.message
                    else -> Unit
                }
            }
            if (error != null) NetworkResult.Error(error!!) else NetworkResult.Success(CompletionResult(text, null, null))
        } else {
            val request = RequestFactory.build(target, history, params, systemPrompt, stream = false)
            runCatching {
                api.chatCompletion(target.chatUrl, target.authHeader, request)
            }.fold(
                onSuccess = { response ->
                    val choice = response.choices.firstOrNull()?.message
                    NetworkResult.Success(
                        CompletionResult(
                            content = choice?.content.orEmpty(),
                            reasoning = choice?.reasoning_content,
                            usage = response.usage,
                        ),
                    )
                },
                onFailure = { NetworkResult.Error(describeError(it), it) },
            )
        }
    }

    /** A2A turn: only the newest user text is sent; context continues via contextId. */
    private fun a2aStream(target: ChatTarget, history: List<Message>, conversationId: String?): Flow<StreamEvent> {
        val userText = history.lastOrNull { it.role == com.vela.chat.domain.model.Role.USER }?.content
            ?: return kotlinx.coroutines.flow.flow {
                emit(StreamEvent.Failed("Nothing to send to the agent."))
            }
        return kotlinx.coroutines.flow.flow {
            // contextId is isolated per (conversation, gateway) — switching a
            // conversation between agents never crosses their threads.
            val contextId = conversationId?.let { settingsRepository.a2aContextId(it, target.chatUrl) }
            a2aClient.send(
                baseUrl = target.chatUrl,
                text = userText,
                authHeader = target.authHeader,
                contextId = contextId,
                onContextId = { id ->
                    if (id != null && conversationId != null && id != contextId) {
                        kotlinx.coroutines.runBlocking {
                            settingsRepository.setA2aContextId(conversationId, target.chatUrl, id)
                        }
                    }
                },
            ).collect { event -> emit(event) }
        }
    }

    /**
     * Agentic completion with web search: runs the OpenAI tool-calling loop,
     * querying SearXNG whenever the model requests it, until the model produces a
     * final answer (or the iteration cap is hit). Non-streaming by design — tool
     * loops over streamed deltas are brittle. [onSearch] reports each query so the
     * UI can show a "searching…" status.
     */
    suspend fun searchComplete(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        searchConfig: SearchConfig,
        onSearch: (String) -> Unit,
    ): NetworkResult<CompletionResult> = withContext(Dispatchers.IO) {
        runCatching {
            runToolLoop(target, history, params, systemPrompt, listOf(webSearchAgentTool(searchConfig, onSearch)), onStatus = {})
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { NetworkResult.Error(describeError(it), it) },
        )
    }

    /**
     * Agentic completion over arbitrary [tools] — this is how a brain profile
     * (a cloud/local model) reaches remote agents: one tool per enabled A2A
     * peer, executed in-app with that peer's own identity. [onStatus] receives a
     * human-readable line per running tool for the chat overlay.
     */
    suspend fun agentComplete(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        tools: List<AgentTool>,
        onStatus: (String) -> Unit,
    ): NetworkResult<CompletionResult> = withContext(Dispatchers.IO) {
        runCatching {
            runToolLoop(target, history, params, systemPrompt, tools, onStatus)
        }.fold(
            onSuccess = { NetworkResult.Success(it) },
            onFailure = { NetworkResult.Error(describeError(it), it) },
        )
    }

    /** The SearXNG-backed web_search tool, also composable alongside peer tools. */
    fun webSearchAgentTool(searchConfig: SearchConfig, onSearch: (String) -> Unit): AgentTool = AgentTool(
        definition = RequestFactory.webSearchTool(),
        status = { args -> "🔍 Searching the web: \"${extractQuery(args)}\"…" },
        execute = { args ->
            val query = extractQuery(args)
            onSearch(query)
            val results = webSearchClient.search(searchConfig, query)
                .getOrElse { listOf(SearchResult("Search failed", "", it.message ?: "error")) }
            formatResults(query, results)
        },
    )

    /**
     * One message tool per enabled A2A peer of [profileId] — the profile's
     * "arms". The executor sends through [A2aClient] with the peer's own bearer
     * token (the gateway sees the peer's identity, never the brain model's
     * name), and keeps the agent-side thread alive via the
     * per-(conversation, gateway) contextId. Failures return "ERROR: …" text so
     * the model can relay them.
     */
    suspend fun a2aPeerTools(profileId: String, conversationId: String): List<AgentTool> =
        settingsRepository.a2aPeersOnce(profileId).filter { it.enabled }.map { peer ->
            AgentTool(
                definition = A2aPeerTool.definition(peer.name),
                status = { "📨 Messaging ${peer.name}…" },
                execute = { args ->
                    val text = A2aPeerTool.extractArgument(args, "text")
                    if (text.isNullOrBlank()) {
                        "ERROR: no message text was provided."
                    } else {
                        val token = secureStore.getSecret(
                            VelaConstants.A2A_PEER_SECRET_PREFIX + "${profileId}_${peer.id}",
                        )
                        if (token.isNullOrBlank()) {
                            "ERROR: no token is stored for ${peer.name} — provision it in the profile's Peers section first."
                        } else {
                            var reply = ""
                            var failure: String? = null
                            val contextId = settingsRepository.a2aContextId(conversationId, peer.normalizedBaseUrl)
                            a2aClient.send(
                                baseUrl = peer.normalizedBaseUrl,
                                text = text,
                                authHeader = RequestFactory.bearer(token),
                                contextId = contextId,
                                onContextId = { id ->
                                    if (id != null && conversationId.isNotEmpty() && id != contextId) {
                                        kotlinx.coroutines.runBlocking {
                                            settingsRepository.setA2aContextId(conversationId, peer.normalizedBaseUrl, id)
                                        }
                                    }
                                },
                                // Blocking send + poll: a tool only needs the final
                                // text, and it is the deterministic wire path.
                                preferBlocking = true,
                            ).collect { event ->
                                when (event) {
                                    is StreamEvent.Token -> reply += event.text
                                    is StreamEvent.Failed -> failure = event.message
                                    else -> Unit
                                }
                            }
                            failure?.let { "ERROR: $it" }
                                ?: reply.ifBlank { "ERROR: ${peer.name} returned an empty response." }
                        }
                    }
                },
            )
        }

    private suspend fun runToolLoop(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        tools: List<AgentTool>,
        onStatus: (String) -> Unit,
    ): CompletionResult {
        var request = RequestFactory.build(target, history, params, systemPrompt, stream = false)
            .copy(tools = tools.map { it.definition }, tool_choice = "auto")
        var lastUsage: Usage? = null

        repeat(MAX_TOOL_ITERATIONS) {
            val response = api.chatCompletion(target.chatUrl, target.authHeader, request)
            lastUsage = response.usage ?: lastUsage
            val message = response.choices.firstOrNull()?.message
            val toolCalls = message?.tool_calls

            if (toolCalls.isNullOrEmpty()) {
                return CompletionResult(message?.content.orEmpty(), message?.reasoning_content, lastUsage)
            }

            val assistantMessage = RequestMessage(
                role = "assistant",
                content = message?.content?.let { JsonPrimitive(it) },
                tool_calls = toolCalls,
            )
            val toolResults = toolCalls.map { call ->
                val tool = tools.firstOrNull { it.definition.function.name == call.function?.name }
                onStatus(
                    tool?.status(call.function?.arguments.orEmpty())
                        ?: "Running ${call.function?.name ?: "tool"}…",
                )
                val result = if (tool == null) {
                    "ERROR: unknown tool ${call.function?.name}"
                } else {
                    runCatching { tool.execute(call.function?.arguments.orEmpty()) }
                        .getOrElse { "ERROR: ${it.message ?: "tool failed"}" }
                }
                RequestMessage(
                    role = "tool",
                    content = JsonPrimitive(result),
                    tool_call_id = call.id,
                )
            }
            request = request.copy(messages = request.messages + assistantMessage + toolResults)
        }

        // Iteration cap reached: force a final answer without tools.
        val finalResponse = api.chatCompletion(
            target.chatUrl,
            target.authHeader,
            request.copy(tools = null, tool_choice = null),
        )
        val message = finalResponse.choices.firstOrNull()?.message
        return CompletionResult(message?.content.orEmpty(), message?.reasoning_content, finalResponse.usage ?: lastUsage)
    }

    private fun extractQuery(arguments: String?): String {
        if (arguments.isNullOrBlank()) return ""
        return runCatching {
            AppJson.parseToJsonElement(arguments).jsonObject["query"]?.jsonPrimitive?.content
        }.getOrNull() ?: arguments
    }

    private fun formatResults(query: String, results: List<SearchResult>): String {
        if (results.isEmpty()) return "No web results found for \"$query\"."
        return buildString {
            appendLine("Web search results for \"$query\":")
            appendLine()
            results.forEachIndexed { index, r ->
                appendLine("[${index + 1}] ${r.title}")
                appendLine("URL: ${r.url}")
                if (r.snippet.isNotBlank()) appendLine(r.snippet)
                appendLine()
            }
            appendLine("Answer the user using these results and cite the source URLs.")
        }.trim()
    }

    private fun describeError(t: Throwable): String = when (val cert = findUntrustedCert(t)) {
        null -> when (t) {
            is UnknownHostException -> "Host unreachable — check the base URL and that the server is running."
            is SocketTimeoutException -> "Request timed out."
            is retrofit2.HttpException -> describeHttpError(
                t.code(),
                runCatching { t.response()?.errorBody()?.string() }.getOrNull(),
            )
            else -> t.message ?: "Request failed"
        }
        else -> "New certificate for ${cert.host} — open this profile and tap Test Connection to review and trust it."
    }

    private companion object {
        const val MAX_TOOL_ITERATIONS = 4
    }
}
