package com.vela.chat.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.AppJson
import com.vela.chat.data.local.dao.ModelCacheDao
import com.vela.chat.data.local.entity.ModelCacheEntity
import com.vela.chat.data.remote.NetworkResult
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.tailscale.AiServerHeuristics
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Estimated model RAM footprint in GB per 1B parameters at Q4 quantization. */
private const val RAM_GB_PER_B_PARAM_Q4 = 0.6f

/** Nanoseconds per millisecond for latency math. */
private const val NANOS_PER_MS = 1_000_000L

/** One enrichable model row of the dashboard. */
data class ModelCardUi(
    val modelId: String,
    val family: String?,
    val quantization: String?,
    val parameterCount: String?,
    /** Context length from the model cache; null renders as "—". */
    val contextLength: Int?,
    /** Measured catalogue round-trip latency; null when never probed. */
    val latencyMs: Long?,
    /** Rough RAM estimate in GB derived from the parameter count at Q4. */
    val ramEstimateGb: Float?,
    val isDefault: Boolean,
)

/** Streaming status line of an Ollama pull shown in the pull dialog. */
data class PullUiState(
    val model: String,
    val status: String,
    /** 0..1 fraction when the server reports total/completed, else null. */
    val progress: Float?,
    val finished: Boolean,
    val failed: Boolean,
)

/** Immutable UI state of the model dashboard. */
data class ModelsUiState(
    val profiles: List<ApiProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val cards: List<ModelCardUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pull: PullUiState? = null,
)

/**
 * Model dashboard VM: fetches each profile's `/models` catalogue via
 * [ApiProfileRepository] (the same path the chat model bar uses), decorates rows
 * with `model_cache` metadata and [AiServerHeuristics] parsing, caches probe
 * latency, and supports "set default" plus Ollama model pulls over streamed
 * NDJSON on the profile's host root.
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val profileRepository: ApiProfileRepository,
    private val modelCacheDao: ModelCacheDao,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val selectedProfileId = MutableStateFlow<String?>(null)
    private val cards = MutableStateFlow<List<ModelCardUi>>(emptyList())
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val pull = MutableStateFlow<PullUiState?>(null)

    private val profiles = profileRepository.profiles

    val state: StateFlow<ModelsUiState> = combine(
        combine(profiles, selectedProfileId) { list, selected -> Pair(list, selected) },
        combine(cards, isLoading, error, pull) { c, l, e, p -> CardsExtras(c, l, e, p) },
    ) { (list, selected), extras ->
        ModelsUiState(
            profiles = list,
            selectedProfileId = selected,
            cards = extras.cards,
            isLoading = extras.isLoading,
            error = extras.error,
            pull = extras.pull,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelsUiState())

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                val current = selectedProfileId.value
                if ((current == null || list.none { it.id == current }) && list.isNotEmpty()) {
                    selectProfile(list.first().id)
                }
            }
        }
    }

    /** Switches the dashboard to [profileId] and (re)probes its catalogue. */
    fun selectProfile(profileId: String) {
        selectedProfileId.value = profileId
        loadModels(profileId)
    }

    /** Re-probes the selected profile's catalogue and refreshes the cache. */
    fun refresh() {
        selectedProfileId.value?.let(::loadModels)
    }

    /** Saves [modelId] as the selected profile's default model (key untouched). */
    fun setDefaultModel(modelId: String) = viewModelScope.launch {
        val profileId = selectedProfileId.value ?: return@launch
        val profile = profileRepository.getProfile(profileId) ?: return@launch
        runCatching { profileRepository.saveProfile(profile.copy(model = modelId), null) }
        cards.value = cards.value.map { it.copy(isDefault = it.modelId == modelId) }
    }

    /**
     * Pulls [modelName] into the selected Ollama server by POSTing to its host
     * root `/api/pull` (streaming NDJSON). Progress updates land in [state.pull].
     */
    fun pullModel(modelName: String) {
        val trimmed = modelName.trim()
        val profileId = selectedProfileId.value ?: return
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            isLoading.value = true
            pull.value = PullUiState(model = trimmed, status = "Starting…", progress = null, finished = false, failed = false)
            val result = runCatching {
                val profile = profileRepository.getProfile(profileId)
                    ?: throw IllegalStateException("Profile not found")
                require(profile.providerType == ProviderType.OLLAMA) { "Pull is only available for Ollama profiles" }
                val hostRoot = profile.baseUrl
                    .trimEnd('/')
                    .removeSuffix(OPENAI_VERSION_PATH)
                    .trimEnd('/')
                pullOllama(hostRoot, trimmed)
            }
            isLoading.value = false
            result
                .onSuccess { done ->
                    val previous = pull.value
                    pull.value = previous?.copy(
                        status = if (done) "Success — $trimmed is available" else "Pull cancelled",
                        finished = true,
                        progress = when {
                            !done -> null
                            previous?.progress != null -> previous.progress
                            else -> 1f
                        },
                    )
                    if (done) refresh()
                }
                .onFailure { failure ->
                    pull.value = pull.value?.copy(
                        status = failure.message ?: "Pull failed",
                        finished = true,
                        failed = true,
                    )
                }
        }
    }

    fun dismissPull() {
        pull.value = null
    }

    /**
     * Streams `POST {hostRoot}/api/pull` NDJSON lines on [Dispatchers.IO],
     * publishing the latest status line and progress fraction. Returns true when
     * the server reported "success".
     */
    private suspend fun pullOllama(hostRoot: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        val payload: JsonObject = buildJsonObject {
            put("model", modelName)
            put("name", modelName)
            put("stream", true)
        }
        val request = Request.Builder()
            .url("$hostRoot$OLLAMA_PULL_PATH")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        var succeeded = false
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Ollama returned HTTP ${response.code}")
            val source = response.body?.source() ?: throw IllegalStateException("Empty response body")
            while (true) {
                val line = source.readUtf8Line() ?: break
                val parsed = runCatching { AppJson.decodeFromString<AppPullLine>(line) }.getOrNull() ?: continue
                parsed.error?.let { throw IllegalStateException(it) }
                val progress = if (parsed.total != null && parsed.total > 0 && parsed.completed != null) {
                    (parsed.completed.toFloat() / parsed.total).coerceIn(0f, 1f)
                } else null
                pull.value = PullUiState(
                    model = modelName,
                    status = parsed.status ?: "Working…",
                    progress = progress ?: pull.value?.progress,
                    finished = false,
                    failed = false,
                )
                if (parsed.status?.equals(OLLAMA_SUCCESS_STATUS, ignoreCase = true) == true) succeeded = true
            }
        }
        succeeded
    }

    /**
     * Probes `/models` for [profileId], enriches rows with the cache and
     * heuristics, persists fresh cache entries (latency + heuristic fields) and
     * falls back to cache-only rows when the server is unreachable.
     */
    private fun loadModels(profileId: String) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            val profile = profileRepository.getProfile(profileId)
            if (profile == null) {
                cards.value = emptyList()
                isLoading.value = false
                error.value = "Profile not found"
                return@launch
            }
            val cache = modelCacheDao.getByProfile(profileId).associateBy { it.modelId }
            val startedAt = System.nanoTime()
            when (val result = profileRepository.listModels(profileId)) {
                is NetworkResult.Success -> {
                    val latencyMs = (System.nanoTime() - startedAt) / NANOS_PER_MS
                    val now = System.currentTimeMillis()
                    cards.value = result.data.map { info ->
                        cardFor(info.id, cache[info.id], profile.model, latencyMs)
                    }
                    runCatching {
                        modelCacheDao.upsertAll(
                            result.data.map { info -> cacheEntityFor(profileId, info.id, cache[info.id], latencyMs, now) },
                        )
                    }
                    isLoading.value = false
                }
                is NetworkResult.Error -> {
                    // Server unreachable: show whatever metadata is cached.
                    cards.value = cache.values
                        .sortedBy { it.modelId }
                        .map { entry -> cardFor(entry.modelId, entry, profile.model, entry.latencyMs.takeIf { it > 0 }) }
                    error.value = result.message
                    isLoading.value = false
                }
            }
        }
    }

    private fun cardFor(
        modelId: String,
        cached: ModelCacheEntity?,
        defaultModel: String?,
        latencyMs: Long?,
    ): ModelCardUi {
        val heuristics = AiServerHeuristics.parse(modelId)
        val context = cached?.contextLength.takeUnless { it == null || it == 0 }
        return ModelCardUi(
            modelId = modelId,
            family = heuristics.family ?: cached?.family,
            quantization = heuristics.quantization ?: cached?.quantization,
            parameterCount = heuristics.parameterCount ?: cached?.parameterCount,
            contextLength = context,
            latencyMs = latencyMs ?: cached?.latencyMs?.takeIf { it > 0 },
            ramEstimateGb = ramEstimateFor(heuristics.parameterCount),
            isDefault = defaultModel == modelId,
        )
    }

    private fun cacheEntityFor(
        profileId: String,
        modelId: String,
        cached: ModelCacheEntity?,
        latencyMs: Long,
        now: Long,
    ): ModelCacheEntity {
        val heuristics = AiServerHeuristics.parse(modelId)
        return ModelCacheEntity(
            profileId = profileId,
            modelId = modelId,
            displayName = cached?.displayName,
            contextLength = cached?.contextLength ?: 0,
            quantization = heuristics.quantization ?: cached?.quantization,
            parameterCount = heuristics.parameterCount ?: cached?.parameterCount,
            family = heuristics.family ?: cached?.family,
            sizeBytes = cached?.sizeBytes ?: 0,
            latencyMs = latencyMs,
            updatedAt = now,
        )
    }

    /** "8B" → ~4.8 GB at Q4 (see [RAM_GB_PER_B_PARAM_Q4]); null when unknown. */
    private fun ramEstimateFor(parameterCount: String?): Float? = parameterCount
        ?.removeSuffix(PARAM_SUFFIX_BILLION)
        ?.toFloatOrNull()
        ?.times(RAM_GB_PER_B_PARAM_Q4)

    /** Carries the four remaining flows through one [combine] slot. */
    private data class CardsExtras(
        val cards: List<ModelCardUi>,
        val isLoading: Boolean,
        val error: String?,
        val pull: PullUiState?,
    )
}

/** One NDJSON line of Ollama's `/api/pull` stream (leniently decoded). */
@Serializable
private data class AppPullLine(
    val status: String? = null,
    val total: Long? = null,
    val completed: Long? = null,
    val error: String? = null,
)

private const val OPENAI_VERSION_PATH = "/v1"
private const val OLLAMA_PULL_PATH = "/api/pull"
private const val OLLAMA_SUCCESS_STATUS = "success"
private const val PARAM_SUFFIX_BILLION = "B"
