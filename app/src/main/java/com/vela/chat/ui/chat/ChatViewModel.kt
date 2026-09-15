package com.vela.chat.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.attachment.AttachmentProcessor
import com.vela.chat.data.export.ChatExporter
import com.vela.chat.data.export.PdfExporter
import com.vela.chat.data.remote.ChatTarget
import com.vela.chat.data.remote.NetworkResult
import com.vela.chat.data.remote.StreamEvent
import com.vela.chat.data.remote.dto.Usage
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.repository.ChatRepository
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.data.repository.LibraryRepository
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.SearchConfig
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.data.tailscale.TailnetManager
import com.vela.chat.data.tailscale.TailnetStatus
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.TokenStats
import com.vela.chat.ui.navigation.Routes
import com.vela.chat.util.VelaConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Min interval between streaming UI updates (≈12 fps) to bound recomposition cost. */
private const val STREAM_THROTTLE_MS = 80L

/**
 * Rough characters-per-token ratio used for the *live* tok/s estimate while a
 * stream is in flight (server usage stats only arrive with the final chunk).
 */
private const val APPROX_CHARS_PER_TOKEN = 4f

/** Instruction appended as a (visible) user turn when summarizing the conversation. */
private const val SUMMARIZE_INSTRUCTION =
    "Summarize this conversation so far in a few concise bullet points, covering the " +
        "key topics, decisions and any open questions. Do not add information that is not in the conversation."

/** Hidden nudge turn used by [ChatViewModel.continueGeneration]. */
private const val CONTINUE_INSTRUCTION = "Continue exactly where you left off."

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val apiProfileRepository: ApiProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val attachmentProcessor: AttachmentProcessor,
    private val secureStore: SecureStore,
    private val pdfExporter: PdfExporter,
    private val libraryRepository: LibraryRepository,
    private val personaRepository: com.vela.chat.data.repository.PersonaRepository,
    private val tailnetManager: TailnetManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    /** Aggregated conversation token usage, refreshed after every completed generation. */
    private val _tokenStats = MutableStateFlow<TokenStats?>(null)
    val tokenStats = _tokenStats.asStateFlow()

    private val conversationId = MutableStateFlow(
        savedStateHandle.get<String>(Routes.CONVERSATION_ID)?.ifBlank { null },
    )

    /** Window size for the messages view; grows by one window per `loadOlder()` call. */
    private val messagesVisibleLimit = MutableStateFlow(VelaConstants.CHAT_WINDOW_SIZE)

    private var settings: AppSettings = AppSettings()
    private var conversation: Conversation? = null
    private var streamJob: Job? = null
    private var activeModel: String? = null

    // Model-bar inputs, merged into ChatUiState.availableModels by recomputeAvailableModels():
    // the server-advertised catalogue + the user's saved/typed models, so manual ids
    // (e.g. GLM-4.x-Flash that z.ai's /models omits) stay selectable.
    private val activeProfileId = MutableStateFlow<String?>(null)
    private var probedModels: List<String> = emptyList()
    private var customModels: Set<String> = emptySet()

    // Agent personalities, keyed by id and kept fresh by observePersonas(). Persona
    // chosen on the empty state (before any message exists) rides in pendingPersonaId
    // until ensureConversation() bakes it into the new conversation.
    private var personasById: Map<String, com.vela.chat.domain.model.AgentPersona> = emptyMap()
    private var pendingPersonaId: String? = null

    // Profile pre-selected on a new chat (before the first message exists). StateFlow
    // because activeProfile below flatMapLatest-es it.
    private val pendingProfileId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    // A2A peer pre-selected on a new chat, and the peer saved for an existing one.
    // Same declare-before-init rule as pendingProfileId: observeA2aPeers() collects
    // both flows and Main.immediate starts collection synchronously in the init block.
    private val pendingA2aPeerId = MutableStateFlow<String?>(null)
    private val savedA2aPeerId = MutableStateFlow<String?>(null)

    // MUST be declared before `init`: observeProfileAndModels() collects this, and
    // because viewModelScope uses Dispatchers.Main.immediate the collection starts
    // synchronously during construction. If this property were declared after the
    // init block it would still be null at that point → NullPointerException on launch.
    private val activeProfile: kotlinx.coroutines.flow.Flow<com.vela.chat.domain.model.ApiProfile?> = conversationId
        .flatMapLatest { id ->
            if (id == null) {
                pendingProfileId.flatMapLatest { pending ->
                    if (pending != null) {
                        kotlinx.coroutines.flow.flow { emit(apiProfileRepository.getProfile(pending)) }
                    } else {
                        apiProfileRepository.defaultProfile
                    }
                }
            } else {
                conversationRepository.observeConversation(id).flatMapLatest { conv ->
                    if (conv?.profileId != null) {
                        kotlinx.coroutines.flow.flow { emit(apiProfileRepository.getProfile(conv.profileId)) }
                    } else {
                        apiProfileRepository.defaultProfile
                    }
                }
            }
        }

    init {
        observeSettings()
        observeConversation()
        observeMessages()
        observeProfileAndModels()
        observeCustomModels()
        observeSavedPrompts()
        observePersonas()
        observeProfiles()
        observeTailnet()
        observeA2aPeers()
    }

    /**
     * Switch who this chat talks to. On an existing conversation the profile is
     * persisted and the model reset (it belonged to the previous server); before
     * the first message it only pre-selects, applied when the chat is created.
     */
    fun selectProfile(profileId: String) {
        val conv = conversation
        viewModelScope.launch {
            if (conv != null) {
                conversationRepository.updateConversation(
                    conv.copy(profileId = profileId, model = null),
                )
            } else {
                pendingProfileId.value = profileId
            }
        }
    }

    private fun observeProfiles() = viewModelScope.launch {
        apiProfileRepository.profiles.collect { list ->
            _state.update { it.copy(profiles = list) }
        }
    }

    /**
     * A2A peer table of the active profile + which peer is currently in effect
     * (pending pre-selection → saved choice → first enabled peer). Only emitted
     * when there is something to pick; peer-less profiles leave the state empty
     * and the status line unchanged.
     */
    private fun observeA2aPeers() = viewModelScope.launch {
        activeProfileId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else settingsRepository.a2aPeers(id)
            }
            .combine(pendingA2aPeerId) { peers, pending -> peers to pending }
            .combine(savedA2aPeerId) { (peers, pending), saved -> Triple(peers, pending, saved) }
            .collect { (peers, pending, saved) ->
                val enabled = peers.filter { it.enabled }
                val active = pending?.takeIf { id -> enabled.any { it.id == id } }
                    ?: saved?.takeIf { id -> enabled.any { it.id == id } }
                    ?: enabled.firstOrNull()?.id
                _state.update { it.copy(a2aPeers = peers, activeA2aPeerId = active) }
            }
    }

    /** Switch which gateway peer of an A2A profile this chat talks through. */
    fun selectA2aPeer(peerId: String) {
        val conv = conversation
        viewModelScope.launch {
            if (conv != null) {
                settingsRepository.setA2aPeerChoice(conv.id, peerId)
                savedA2aPeerId.value = peerId
            } else {
                pendingA2aPeerId.value = peerId
            }
        }
    }

    /** Persona applied to [conv], or the pre-selected / default persona for a new chat. */
    private fun personaFor(conv: Conversation?): com.vela.chat.domain.model.AgentPersona? =
        conv?.personaId?.let { personasById[it] }
            ?: pendingPersonaId?.let { personasById[it] }
            ?: personasById.values.firstOrNull { it.isDefault }

    /** System prompt precedence: explicit conversation prompt → persona → global default. */
    private fun effectiveSystemPrompt(
        conv: Conversation?,
        persona: com.vela.chat.domain.model.AgentPersona?,
        s: com.vela.chat.data.settings.AppSettings = settings,
    ): String = conv?.systemPrompt ?: persona?.systemPrompt ?: s.defaultSystemPrompt

    /** Params precedence: explicit conversation params → persona temperature nudge → global defaults. */
    private fun effectiveParams(
        conv: Conversation?,
        persona: com.vela.chat.domain.model.AgentPersona?,
        s: com.vela.chat.data.settings.AppSettings = settings,
    ): com.vela.chat.domain.model.GenerationParams =
        conv?.params
            ?: persona?.temperature?.let { s.defaultParams.copy(temperature = it) }
            ?: s.defaultParams

    private fun observePersonas() = viewModelScope.launch {
        personaRepository.observePersonas().collect { list ->
            personasById = list.associateBy { it.id }
            _state.update { it.copy(personas = list) }
            // Re-resolve the effective prompt/params: the default persona may have
            // just seeded in (first launch) or a persona may have been edited.
            val conv = conversation
            val persona = personaFor(conv)
            _state.update {
                it.copy(
                    systemPrompt = effectiveSystemPrompt(conv, persona, settings),
                    params = effectiveParams(conv, persona, settings),
                    activePersonaId = persona?.id,
                )
            }
        }
    }

    /**
     * Switch this chat's personality. On an existing conversation the persona is
     * persisted (and its system prompt replaces any previously set one — picking a
     * persona must actually change the agent). Before the first message it only
     * pre-selects, so the first send still creates the conversation with it applied.
     */
    fun selectPersona(personaId: String) {
        pendingPersonaId = personaId
        val persona = personasById[personaId]
        val conv = conversation
        viewModelScope.launch {
            if (conv != null) {
                conversationRepository.updateConversation(
                    conv.copy(
                        personaId = personaId,
                        systemPrompt = persona?.systemPrompt ?: conv.systemPrompt,
                    ),
                )
            } else {
                _state.update {
                    it.copy(
                        activePersonaId = personaId,
                        systemPrompt = persona?.systemPrompt ?: it.systemPrompt,
                        params = persona?.temperature?.let { t -> it.params.copy(temperature = t) } ?: it.params,
                    )
                }
            }
        }
    }

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.settings.collect { s ->
            settings = s
            val persona = personaFor(conversation)
            _state.update { st ->
                st.copy(
                    systemPrompt = effectiveSystemPrompt(conversation, persona, s),
                    params = effectiveParams(conversation, persona, s),
                    renderMarkdown = s.renderMarkdown,
                    showTokenUsage = s.showTokenUsage,
                    sendOnEnter = s.sendOnEnter,
                    ttsEnabled = s.ttsEnabled,
                    ttsLanguage = s.ttsLanguage,
                    ttsRate = s.ttsRate,
                    ttsPitch = s.ttsPitch,
                    showTimestamps = s.showTimestamps,
                    keepScreenOn = s.keepScreenOn,
                    customWallpaperPath = s.customWallpaperPath,
                )
            }
        }
    }

    private fun observeConversation() = viewModelScope.launch {
        conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else conversationRepository.observeConversation(id)
            }
            .collect { conv ->
                conversation = conv
                if (conv != null) {
                    val profile = conv.profileId?.let { apiProfileRepository.getProfile(it) }
                        ?: apiProfileRepository.getDefaultOrFirst()
                    val persona = personaFor(conv)
                    savedA2aPeerId.value = settingsRepository.a2aPeerChoice(conv.id)
                    _state.update {
                        it.copy(
                            conversationId = conv.id,
                            title = conv.title,
                            model = conv.model ?: profile?.model ?: it.model,
                            systemPrompt = effectiveSystemPrompt(conv, persona),
                            params = effectiveParams(conv, persona),
                            activePersonaId = persona?.id,
                        )
                    }
                    refreshTokenStats(conv.id)
                } else {
                    val profile = apiProfileRepository.getDefaultOrFirst()
                    val persona = personaFor(null)
                    savedA2aPeerId.value = null
                    _state.update {
                        it.copy(
                            conversationId = null,
                            title = "New chat",
                            messages = emptyList(),
                            streaming = null,
                            input = "",
                            attachments = emptyList(),
                            model = profile?.model,
                            systemPrompt = effectiveSystemPrompt(null, persona),
                            params = effectiveParams(null, persona),
                            activePersonaId = persona?.id,
                        )
                    }
                    _tokenStats.value = null
                }
            }
    }

    /**
     * Windowed message collection: only the most recent [messagesVisibleLimit] messages
     * are observed and rendered. The limit resets whenever the conversation changes so a
     * newly opened chat always starts with the most recent window.
     */
    private fun observeMessages() = viewModelScope.launch {
        conversationId
            .flatMapLatest { id ->
                messagesVisibleLimit.value = VelaConstants.CHAT_WINDOW_SIZE
                if (id == null) {
                    flowOf(emptyList())
                } else {
                    messagesVisibleLimit
                        .flatMapLatest { limit -> conversationRepository.observeRecentMessages(id, limit) }
                }
            }
            .collect { msgs ->
                _state.update {
                    it.copy(messages = msgs, messagesVisibleLimit = messagesVisibleLimit.value)
                }
            }
    }

    private fun observeProfileAndModels() = viewModelScope.launch {
        // distinctUntilChanged: don't re-probe /models unless the profile actually changed.
        activeProfile.distinctUntilChanged().collect { profile ->
            activeProfileId.value = profile?.id
            val effectiveProfileId = conversation?.profileId ?: pendingProfileId.value ?: profile?.id
            if (profile?.providerType == ProviderType.A2A_AGENT) {
                // A2A agents have no model catalogue; the profile's model slot holds
                // the agent's display name. Skip the /models probe entirely.
                probedModels = emptyList()
                _state.update {
                    it.copy(
                        profileName = profile.name,
                        model = conversation?.model ?: profile.model,
                        modelLoadError = null,
                        probeLatencyMs = null,
                        isProbingModels = false,
                        activeProfileId = effectiveProfileId,
                    )
                }
                return@collect
            }
            _state.update { it.copy(
                profileName = profile?.name,
                model = conversation?.model ?: profile?.model ?: it.model,
                isProbingModels = profile != null,
                activeProfileId = effectiveProfileId,
            ) }
            if (profile != null) {
                val key = apiProfileRepository.getApiKey(profile.id)
                val probeStarted = System.currentTimeMillis()
                when (val result = apiProfileRepository.testConnection(profile, key)) {
                    is NetworkResult.Success -> {
                        probedModels = result.data.map { it.id }
                        // A model saved on the profile/chat but missing from the catalogue
                        // (e.g. GLM-4.x-Flash) is registered as custom so it persists in the bar.
                        val saved = conversation?.model ?: profile.model
                        if (!saved.isNullOrBlank() && saved !in probedModels) {
                            settingsRepository.addCustomModel(profile.id, saved)
                        }
                        _state.update { st -> st.copy(
                            model = st.model ?: probedModels.firstOrNull(),
                            modelLoadError = null,
                            probeLatencyMs = System.currentTimeMillis() - probeStarted,
                            isProbingModels = false,
                        ) }
                    }
                    is NetworkResult.Error -> {
                        // Keep any saved/custom models usable even if the catalogue probe fails.
                        probedModels = emptyList()
                        _state.update { it.copy(
                            modelLoadError = result.message,
                            probeLatencyMs = null,
                            isProbingModels = false,
                        ) }
                    }
                }
            } else {
                probedModels = emptyList()
                _state.update { it.copy(model = null, modelLoadError = null, isProbingModels = false) }
            }
            recomputeAvailableModels()
        }
    }

    private fun observeCustomModels() = viewModelScope.launch {
        activeProfileId
            .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else settingsRepository.customModels(id) }
            .collect { set ->
                customModels = set
                _state.update { it.copy(customModels = set) }
                recomputeAvailableModels()
            }
    }

    private fun observeSavedPrompts() = viewModelScope.launch {
        libraryRepository.prompts.collect { prompts ->
            _state.update { it.copy(savedPrompts = prompts.sortedByDescending { p -> p.favorite }) }
        }
    }

    /** Mirrors the tailnet session state into the UI state for the header chip. */
    private fun observeTailnet() = viewModelScope.launch {
        tailnetManager.state.collect { tailnet ->
            _state.update { it.copy(tailnetConnected = tailnet.status == TailnetStatus.CONNECTED) }
        }
    }

    /**
     * Build the model-bar list: server catalogue first, then user-added custom models,
     * then the currently selected / saved model — de-duplicated, order preserved. This
     * guarantees a manually typed id is always selectable even when /models omits it.
     */
    private fun recomputeAvailableModels() {
        _state.update { st ->
            val ordered = LinkedHashSet<String>()
            ordered.addAll(probedModels)
            ordered.addAll(customModels)
            st.model?.takeIf { it.isNotBlank() }?.let { ordered.add(it) }
            conversation?.model?.takeIf { it.isNotBlank() }?.let { ordered.add(it) }
            st.copy(availableModels = ordered.toList())
        }
    }

    /** Add a model id by hand (from the model bar), persist it for this profile, and select it. */
    fun addCustomModel(modelId: String) {
        val id = modelId.trim()
        if (id.isBlank()) return
        activeProfileId.value?.let { profileId ->
            viewModelScope.launch { settingsRepository.addCustomModel(profileId, id) }
        }
        selectModel(id)
    }

    /** Remove a hand-added model id from the profile's custom list. */
    fun removeCustomModel(modelId: String) {
        val profileId = activeProfileId.value ?: return
        viewModelScope.launch { settingsRepository.removeCustomModel(profileId, modelId) }
    }

    // ---- Windowed loading ----

    /**
     * Grow the visible message window by one [VelaConstants.CHAT_WINDOW_SIZE]. The list
     * is anchored by stable message keys, so inserting older messages above the viewport
     * does not jump the scroll position.
     */
    fun loadOlder() {
        messagesVisibleLimit.update { it + VelaConstants.CHAT_WINDOW_SIZE }
    }

    // ---- Composer ----

    /** Update the composer draft text. */
    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /** Insert a rendered prompt-library text into the composer (appended to any draft). */
    fun insertPrompt(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(input = if (it.input.isBlank()) text else "${it.input}\n\n$text") }
    }

    /** Quick temperature chip: update just the temperature and persist it on the chat. */
    fun setTemperature(value: Float) {
        val clamped = value.coerceIn(TEMPERATURE_MIN, TEMPERATURE_MAX)
        val current = _state.value
        updateConfig(current.systemPrompt, current.params.copy(temperature = clamped))
    }

    /** Process a picked document/image and add it to the pending attachments. */
    fun attach(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(isProcessingAttachment = true) }
        attachmentProcessor.process(uri)
            .onSuccess { att -> _state.update { it.copy(attachments = it.attachments + att) } }
            .onFailure { e -> _state.update { it.copy(error = "Couldn't read attachment: ${e.message}") } }
        _state.update { it.copy(isProcessingAttachment = false) }
    }

    /** Remove a pending attachment before sending. */
    fun removeAttachment(id: String) =
        _state.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }

    /** Select a model for this chat and persist it on the conversation/profile. */
    fun selectModel(model: String) {
        _state.update { it.copy(model = model) }
        recomputeAvailableModels()
        val conv = conversation
        viewModelScope.launch {
            if (conv != null) {
                conversationRepository.updateConversation(conv.copy(model = model))
                conv.profileId?.let { apiProfileRepository.getProfile(it) }?.let { profile ->
                    apiProfileRepository.saveProfile(profile.copy(model = model), null)
                }
            } else {
                apiProfileRepository.getDefaultOrFirst()?.let { profile ->
                    apiProfileRepository.saveProfile(profile.copy(model = model), null)
                }
            }
        }
    }

    /** Update (and persist) this chat's system prompt and sampler parameters. */
    fun updateConfig(systemPrompt: String, params: GenerationParams) {
        _state.update { it.copy(systemPrompt = systemPrompt, params = params) }
        val conv = conversation ?: return
        viewModelScope.launch {
            conversationRepository.updateConversation(
                conv.copy(
                    systemPrompt = systemPrompt.ifBlank { null },
                    params = params,
                    model = _state.value.model,
                ),
            )
        }
    }

    // ---- Sending / generation ----

    /** Send the composer text (with pending attachments) and start generation. */
    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val text = current.input.trim()
        val attachments = current.attachments
        _state.update { it.copy(input = "", attachments = emptyList(), error = null) }
        dispatchUserText(text, attachments)
    }

    /**
     * Shared tail of every "send a user turn" flow: persists the user message and
     * kicks off generation. Used by [send], [summarizeConversation] and
     * [translateMessage]. Launches its own coroutine on the ViewModel scope.
     */
    private fun dispatchUserText(text: String, attachments: List<Attachment> = emptyList()) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val convId = ensureConversation(text)
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = Role.USER,
                content = text,
                attachments = attachments,
            )
            conversationRepository.addMessage(userMessage)
            generate(convId)
        }
    }

    /** Stop the in-flight generation and persist what arrived so far. */
    fun stop() {
        val streaming = _state.value.streaming ?: return
        streamJob?.cancel()
        viewModelScope.launch {
            persistAssistant(
                id = streaming.messageId,
                convId = activeConvId(),
                content = streaming.content,
                reasoning = streaming.reasoning,
                errorMessage = null,
                usage = null,
                generationTimeMs = streaming.elapsedMs(),
            )
            _state.update { it.copy(streaming = null, liveTokensPerSecond = null) }
            activeConvId().takeIf { it.isNotBlank() }?.let { refreshTokenStats(it) }
        }
    }

    /** Regenerate the most recent assistant reply. */
    fun regenerate() {
        val convId = _state.value.conversationId ?: return
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            val lastAssistantId = conversationRepository.getMessages(convId)
                .lastOrNull { it.role == Role.ASSISTANT }?.id
            lastAssistantId?.let { deleteUpToAndIncluding(it) }
            generate(convId)
        }
    }

    /**
     * Regenerate a specific assistant message: the message and everything after it are
     * removed, then a fresh reply is generated from the remaining history.
     */
    fun regenerate(messageId: String) {
        val convId = _state.value.conversationId ?: return
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            deleteUpToAndIncluding(messageId)
            generate(convId)
        }
    }

    /**
     * Ask the model to continue its previous answer. A hidden user turn with a canned
     * "continue" instruction is appended to the request history only (not persisted),
     * so the transcript stays clean while the model sees the nudge.
     */
    fun continueGeneration() {
        val convId = _state.value.conversationId ?: return
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            val hasAssistant = conversationRepository.getMessages(convId)
                .any { it.role == Role.ASSISTANT && it.content.isNotBlank() }
            if (!hasAssistant) return@launch
            generate(convId, pendingUserTurn = CONTINUE_INSTRUCTION)
        }
    }

    /** Summarize the whole conversation via a canned instruction user turn. */
    fun summarizeConversation() {
        if (_state.value.isGenerating) return
        if (_state.value.messages.none { it.role == Role.ASSISTANT }) return
        dispatchUserText(SUMMARIZE_INSTRUCTION)
    }

    /** Translate an assistant message via a canned instruction user turn. */
    fun translateMessage(messageId: String, targetLanguage: String) {
        if (_state.value.isGenerating) return
        val source = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (targetLanguage.isBlank()) return
        dispatchUserText(
            "Translate the following message into ${targetLanguage.trim()}. " +
                "Reply with the translation only, without commentary:\n\n${source.content}",
        )
    }

    /** Pull a user message back into the composer and truncate the thread after it. */
    fun editMessage(message: Message) {
        viewModelScope.launch {
            conversationRepository.deleteMessagesAfter(message.conversationId, message.createdAt)
            conversationRepository.deleteMessage(message)
            _state.update { it.copy(input = message.content, attachments = message.attachments) }
        }
    }

    /**
     * Delete a message and return it so the caller can offer an undo snackbar
     * (restore via [restoreMessage]).
     */
    suspend fun deleteMessage(messageId: String): Message? {
        val message = _state.value.messages.firstOrNull { it.id == messageId }
            ?: return null
        conversationRepository.deleteMessage(message)
        _state.update { st ->
            st.copy(
                messages = st.messages.filterNot { it.id == messageId },
                selectedMessageIds = st.selectedMessageIds - messageId,
            )
        }
        return message
    }

    /** Undo a single-message delete: re-insert the removed message unchanged. */
    fun restoreMessage(message: Message) = viewModelScope.launch {
        conversationRepository.addMessage(message)
    }

    /** Undo a multi-select delete: re-insert every removed message. */
    fun restoreMessages(messages: List<Message>) = viewModelScope.launch {
        messages.forEach { conversationRepository.addMessage(it) }
    }

    // ---- Message flags (favorite / pin) ----

    /** Optimistically flip the favorite flag locally, then persist it. */
    fun setMessageFavorite(messageId: String, favorite: Boolean) {
        applyMessageFlag(messageId) { it.copy(favorite = favorite) }
        viewModelScope.launch { conversationRepository.setMessageFavorite(messageId, favorite) }
    }

    /** Optimistically flip the pinned flag locally, then persist it. */
    fun setMessagePinned(messageId: String, pinned: Boolean) {
        applyMessageFlag(messageId) { it.copy(pinned = pinned) }
        viewModelScope.launch { conversationRepository.setMessagePinned(messageId, pinned) }
    }

    private fun applyMessageFlag(messageId: String, transform: (Message) -> Message) {
        _state.update { st ->
            st.copy(messages = st.messages.map { if (it.id == messageId) transform(it) else it })
        }
    }

    // ---- Multi-select ----

    /** Enter checkbox selection mode, optionally pre-selecting the triggering message. */
    fun enterSelectionMode(initialMessageId: String? = null) {
        _state.update {
            it.copy(
                selectionMode = true,
                selectedMessageIds = if (initialMessageId != null) setOf(initialMessageId) else emptySet(),
            )
        }
    }

    /** Toggle a message's checkbox in multi-select mode. */
    fun toggleSelected(messageId: String) {
        _state.update {
            it.copy(selectedMessageIds = if (messageId in it.selectedMessageIds) {
                it.selectedMessageIds - messageId
            } else {
                it.selectedMessageIds + messageId
            })
        }
    }

    /** Leave multi-select mode, clearing the selection. */
    fun exitSelectionMode() {
        _state.update { it.copy(selectionMode = false, selectedMessageIds = emptySet()) }
    }

    /** Delete all selected messages, returning them for an undo snackbar. */
    suspend fun deleteSelected(): List<Message> {
        val selectedIds = _state.value.selectedMessageIds
        val selected = _state.value.messages.filter { it.id in selectedIds }
        selected.forEach { conversationRepository.deleteMessage(it) }
        _state.update { it.copy(messages = it.messages.filterNot { m -> m.id in selectedIds }) }
        exitSelectionMode()
        return selected
    }

    /** Plain-text digest of the currently selected messages, for sharing. */
    fun selectedShareText(): String? {
        val selected = _state.value.messages.filter { it.id in _state.value.selectedMessageIds }
        if (selected.isEmpty()) return null
        return selected.joinToString("\n\n") { message ->
            val speaker = when (message.role) {
                Role.USER -> "You"
                Role.ASSISTANT -> "Assistant"
                else -> "System"
            }
            "$speaker:\n${message.content}"
        }
    }

    /** Clear the transient error banner. */
    fun dismissError() = _state.update { it.copy(error = null) }

    // ---- Export / import ----

    /** Build the conversation export payload (Markdown or JSON) for sharing. */
    suspend fun buildExport(asMarkdown: Boolean): String? {
        val convId = _state.value.conversationId ?: return null
        val conv = conversationRepository.getConversation(convId) ?: return null
        val messages = conversationRepository.getMessages(convId)
        return if (asMarkdown) ChatExporter.toMarkdown(conv, messages) else ChatExporter.toJson(conv, messages)
    }

    /**
     * Render the current conversation to a PDF file (app cache dir) suitable for a
     * share intent. Returns null when there is nothing to export; I/O runs off the
     * main thread. The cache/exports path is exposed via the app's FileProvider.
     */
    suspend fun exportPdf(context: Context): File? {
        val convId = _state.value.conversationId ?: return null
        val conv = conversationRepository.getConversation(convId) ?: return null
        val messages = conversationRepository.getMessages(convId)
        if (messages.isEmpty()) return null
        _state.update { it.copy(isExportingPdf = true) }
        return try {
            withContext(Dispatchers.IO) { pdfExporter.export(conv, messages) }
        } catch (_: Exception) {
            _state.update { it.copy(error = "PDF export failed") }
            null
        } finally {
            _state.update { it.copy(isExportingPdf = false) }
        }
    }

    /**
     * Import a conversation from an exported JSON document. Returns the new
     * conversation id, or null when the payload could not be parsed.
     */
    suspend fun importConversation(json: String): String? =
        conversationRepository.importConversation(json)

    /** Open an imported conversation in the chat. */
    fun openConversation(id: String) {
        conversationId.value = id
    }

    // ---- internals ----

    private fun activeConvId(): String = _state.value.conversationId ?: conversationId.value.orEmpty()

    private suspend fun ensureConversation(firstText: String): String {
        conversation?.let { return it.id }
        conversationId.value?.let { return it }

        val profile = apiProfileRepository.getDefaultOrFirst()
        val title = firstText.take(48).ifBlank { "New chat" }
        val conv = conversationRepository.createConversation(
            title = title,
            profileId = pendingProfileId.value ?: profile?.id,
            model = if (pendingProfileId.value != null) null else _state.value.model ?: profile?.model,
            systemPrompt = _state.value.systemPrompt.ifBlank { null },
            params = _state.value.params,
            personaId = _state.value.activePersonaId ?: pendingPersonaId,
        )
        conversation = conv
        pendingPersonaId = null
        pendingProfileId.value = null
        conversationId.value = conv.id
        // Bake a pre-selected A2A peer into the fresh conversation so its very
        // first send (and every later one) resolves to the same gateway thread.
        pendingA2aPeerId.value?.let { peerId ->
            settingsRepository.setA2aPeerChoice(conv.id, peerId)
            savedA2aPeerId.value = peerId
            pendingA2aPeerId.value = null
        }
        _state.update { it.copy(conversationId = conv.id, title = conv.title) }
        return conv.id
    }

    private suspend fun deleteUpToAndIncluding(messageId: String) {
        val message = conversationRepository.getMessages(activeConvId())
            .firstOrNull { it.id == messageId } ?: return
        conversationRepository.deleteMessagesAfter(message.conversationId, message.createdAt)
        conversationRepository.deleteMessage(message)
    }

    private suspend fun refreshTokenStats(convId: String) {
        _tokenStats.value = conversationRepository.tokenStats(convId)
        _state.update { it.copy(tokenStats = _tokenStats.value) }
    }

    private fun generate(convId: String, pendingUserTurn: String? = null) {
        streamJob = viewModelScope.launch {
            val conv = conversationRepository.getConversation(convId) ?: return@launch
            val target = chatRepository.resolveTarget(conv).getOrElse { error ->
                _state.update { it.copy(error = error.message) }
                return@launch
            }
            // Assistant messages carry the A2A peer's name (when a peer table is in
            // play) so multi-peer agents are attributed per bubble, not just per chat.
            activeModel = target.peerName ?: target.model
            if (conv.model.isNullOrBlank()) {
                conversationRepository.updateConversation(conv.copy(model = target.model))
            }

            val baseHistory = conversationRepository.getMessages(convId)
            val history = if (pendingUserTurn != null) {
                baseHistory + Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = Role.USER,
                    content = pendingUserTurn,
                )
            } else {
                baseHistory
            }
            val persona = personaFor(conv)
            val params = effectiveParams(conv, persona)
            val systemPrompt = effectiveSystemPrompt(conv, persona).ifBlank { null }
            val startedAtMs = System.currentTimeMillis()

            // Brain profiles with A2A peers ("arms") route through the tool loop;
            // A2A targets keep their server-side loop; everything else as before.
            val peerTools = if (target.providerType != ProviderType.A2A_AGENT) {
                conv.profileId?.let { chatRepository.a2aPeerTools(it, convId) }.orEmpty()
            } else {
                emptyList()
            }
            when {
                // A2A agents run their own tool loop server-side — never hijack with
                // the local tool loop.
                target.providerType == ProviderType.A2A_AGENT ->
                    if (settings.streamResponses) {
                        streamGenerate(convId, target, history, params, systemPrompt, startedAtMs)
                    } else {
                        blockingGenerate(convId, target, history, params, systemPrompt, startedAtMs)
                    }
                // Brain profile with arms (a cloud/local model that can message
                // remote agents): agentic tool loop, web search composed
                // alongside when enabled. Non-streaming by design.
                peerTools.isNotEmpty() -> {
                    val tools = if (settings.webSearchEnabled && settings.searxngUrl.isNotBlank()) {
                        peerTools + chatRepository.webSearchAgentTool(buildSearchConfig(), onSearch = {})
                    } else {
                        peerTools
                    }
                    agenticGenerate(convId, target, history, params, systemPrompt, startedAtMs, tools)
                }
                settings.webSearchEnabled &&
                    settings.searxngUrl.isNotBlank() ->
                    searchGenerate(convId, target, history, params, systemPrompt, startedAtMs)
                settings.streamResponses ->
                    streamGenerate(convId, target, history, params, systemPrompt, startedAtMs)
                else ->
                    blockingGenerate(convId, target, history, params, systemPrompt, startedAtMs)
            }
            refreshTokenStats(convId)
        }
    }

    /** SearchConfig from current settings (provider, SearXNG URL, keys, limits). */
    private fun buildSearchConfig(): SearchConfig {
        val provider = settings.searchProvider
        return SearchConfig(
            provider = provider,
            searxngUrl = settings.searxngUrl,
            apiKey = if (provider.needsApiKey) secureStore.getApiKey(provider.secureKeyId) else null,
            googleCx = settings.googleCx,
            maxResults = settings.webSearchMaxResults,
        )
    }

    /**
     * Tool-loop generation (web search and/or A2A peer "arms"): non-streaming by
     * design, with each running tool's status line shown in the streaming overlay
     * until the final answer replaces it.
     */
    private suspend fun agenticGenerate(
        convId: String,
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        startedAtMs: Long,
        tools: List<com.vela.chat.data.repository.AgentTool>,
    ) {
        val assistantId = UUID.randomUUID().toString()
        _state.update { it.copy(streaming = StreamingState(assistantId, startedAtMs = startedAtMs), lastUsage = null, liveTokensPerSecond = null) }

        val result = chatRepository.agentComplete(
            target = target,
            history = history,
            params = params,
            systemPrompt = systemPrompt,
            tools = tools,
            onStatus = { label ->
                _state.update { it.copy(streaming = it.streaming?.copy(content = label)) }
            },
        )
        when (result) {
            is NetworkResult.Success -> {
                persistAssistant(
                    assistantId, convId, result.data.content, result.data.reasoning.orEmpty(),
                    null, result.data.usage, System.currentTimeMillis() - startedAtMs,
                )
                _state.update { it.copy(streaming = null, liveTokensPerSecond = null) }
            }
            is NetworkResult.Error -> {
                persistAssistant(assistantId, convId, "", "", result.message, null, System.currentTimeMillis() - startedAtMs)
                _state.update { it.copy(streaming = null, error = result.message, liveTokensPerSecond = null) }
            }
        }
    }

    /** Generation with web search: runs the tool loop and shows a search status. */
    private suspend fun searchGenerate(
        convId: String,
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        startedAtMs: Long,
    ) {
        agenticGenerate(
            convId, target, history, params, systemPrompt, startedAtMs,
            listOf(chatRepository.webSearchAgentTool(buildSearchConfig(), onSearch = {})),
        )
    }

    private suspend fun streamGenerate(
        convId: String,
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        startedAtMs: Long,
    ) {
        val assistantId = UUID.randomUUID().toString()
        _state.update { it.copy(streaming = StreamingState(assistantId, startedAtMs = startedAtMs), lastUsage = null, liveTokensPerSecond = null) }

        val content = StringBuilder()
        val reasoning = StringBuilder()
        var failure: String? = null
        var lastEmit = 0L
        var firstTokenAtMs = 0L
        var completedUsage: Usage? = null

        fun flush() {
            _state.update {
                it.copy(streaming = it.streaming?.copy(content = content.toString(), reasoning = reasoning.toString()))
            }
        }

        chatRepository.stream(target, history, params, systemPrompt, conversationId = convId).collect { event ->
            when (event) {
                is StreamEvent.Token -> {
                    if (firstTokenAtMs == 0L) firstTokenAtMs = System.currentTimeMillis()
                    content.append(event.text)
                }
                is StreamEvent.Reasoning -> {
                    if (firstTokenAtMs == 0L) firstTokenAtMs = System.currentTimeMillis()
                    reasoning.append(event.text)
                }
                is StreamEvent.Completed -> {
                    event.usage?.let { u ->
                        completedUsage = u
                        _state.update { it.copy(lastUsage = u) }
                    }
                }
                is StreamEvent.Failed -> failure = event.message
            }
            // Throttle UI updates to ~12/sec instead of one per token. This cuts
            // recomposition and (for finalized messages) markdown re-parsing cost,
            // which is the main CPU/battery sink during fast local generation.
            val now = System.currentTimeMillis()
            if (now - lastEmit >= STREAM_THROTTLE_MS) {
                flush()
                _state.update { it.copy(liveTokensPerSecond = liveRate(content.length, firstTokenAtMs, now)) }
                lastEmit = now
            }
        }

        // Reached only on natural completion/failure (cancellation unwinds earlier).
        flush() // flush any buffered tail
        val finishedAt = System.currentTimeMillis()
        _state.update { it.copy(liveTokensPerSecond = liveRate(content.length, firstTokenAtMs, finishedAt)) }
        persistAssistant(
            id = assistantId,
            convId = convId,
            content = content.toString(),
            reasoning = reasoning.toString(),
            errorMessage = failure,
            usage = completedUsage,
            generationTimeMs = finishedAt - (firstTokenAtMs.takeIf { it != 0L } ?: startedAtMs),
        )
        _state.update { it.copy(streaming = null, error = if (content.isNotBlank()) failure else null, liveTokensPerSecond = null) }
    }

    private suspend fun blockingGenerate(
        convId: String,
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        startedAtMs: Long,
    ) {
        val assistantId = UUID.randomUUID().toString()
        _state.update { it.copy(streaming = StreamingState(assistantId, startedAtMs = startedAtMs), lastUsage = null, liveTokensPerSecond = null) }

        when (val result = chatRepository.complete(target, history, params, systemPrompt, conversationId = convId)) {
            is NetworkResult.Success -> {
                persistAssistant(
                    assistantId, convId, result.data.content, result.data.reasoning.orEmpty(),
                    null, result.data.usage, System.currentTimeMillis() - startedAtMs,
                )
                _state.update { it.copy(streaming = null, liveTokensPerSecond = null) }
            }
            is NetworkResult.Error -> {
                persistAssistant(assistantId, convId, "", "", result.message, null, System.currentTimeMillis() - startedAtMs)
                _state.update { it.copy(streaming = null, error = result.message, liveTokensPerSecond = null) }
            }
        }
    }

    private suspend fun persistAssistant(
        id: String,
        convId: String,
        content: String,
        reasoning: String,
        errorMessage: String?,
        usage: Usage?,
        generationTimeMs: Long,
    ) {
        val isError = content.isBlank() && errorMessage != null
        val text = if (isError) errorMessage!! else content
        if (text.isBlank()) return
        conversationRepository.addMessage(
            Message(
                id = id,
                conversationId = convId,
                role = Role.ASSISTANT,
                content = text,
                reasoning = reasoning.ifBlank { null },
                model = activeModel,
                isError = isError,
                promptTokens = usage?.prompt_tokens ?: 0,
                completionTokens = usage?.completion_tokens ?: 0,
                generationTimeMs = generationTimeMs,
            ),
        )
    }

    /** Approximate tok/s of the in-flight stream; null before the first token arrives. */
    private fun liveRate(contentLength: Int, firstTokenAtMs: Long, nowMs: Long): Float? {
        if (firstTokenAtMs == 0L || contentLength == 0) return null
        val elapsedSeconds = (nowMs - firstTokenAtMs) / 1000f
        if (elapsedSeconds <= 0f) return null
        return (contentLength / APPROX_CHARS_PER_TOKEN) / elapsedSeconds
    }

    private fun StreamingState.elapsedMs(): Long = System.currentTimeMillis() - startedAtMs

    private companion object {
        /** Sanity clamp for the quick temperature chip (matches the parameters screen). */
        const val TEMPERATURE_MIN = 0f
        const val TEMPERATURE_MAX = 2f
    }
}
