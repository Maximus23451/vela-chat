package com.vela.chat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vela.chat.data.AppJson
import com.vela.chat.domain.model.GenerationParams
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = stringPreferencesKey("accent_color")
        val AMOLED = booleanPreferencesKey("amoled_black")
        
        val LIGHT_PRESET = stringPreferencesKey("light_theme_preset")
        val DARK_PRESET = stringPreferencesKey("dark_theme_preset")
        val CUSTOM_ACCENT = stringPreferencesKey("custom_accent_color")
        val BUBBLE_STYLE = stringPreferencesKey("bubble_style")
        val FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
        val CHAT_DENSITY = stringPreferencesKey("chat_density")
        val WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")

        val STREAM = booleanPreferencesKey("stream_responses")
        val SEND_ON_ENTER = booleanPreferencesKey("send_on_enter")
        val MARKDOWN = booleanPreferencesKey("render_markdown")
        val TOKEN_USAGE = booleanPreferencesKey("show_token_usage")
        val TIMESTAMPS = booleanPreferencesKey("show_timestamps")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TTS = booleanPreferencesKey("tts_enabled")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val WEB_SEARCH = booleanPreferencesKey("web_search_enabled")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARXNG_URL = stringPreferencesKey("searxng_url")
        val GOOGLE_CX = stringPreferencesKey("google_cx")
        val WEB_SEARCH_MAX = intPreferencesKey("web_search_max_results")
        val SYSTEM_PROMPT = stringPreferencesKey("default_system_prompt")
        val PARAMS = stringPreferencesKey("default_params")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val COLLAPSED_FOLDERS = stringSetPreferencesKey("collapsed_folders")

        // ---- Nova 2.0 ----
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val APP_LOCK_MODE = stringPreferencesKey("app_lock_mode")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val SECURE_SCREENS = booleanPreferencesKey("secure_screens")
        val TAILSCALE_DISCOVERY = booleanPreferencesKey("tailscale_discovery")
        val TAILSCALE_PEERS = stringSetPreferencesKey("tailscale_peers")
    }

    /**
     * IDs of folders the user has collapsed in the drawer. Kept out of [AppSettings]
     * (which is observed app-wide) since it's purely drawer UI state; the drawer
     * observes this dedicated flow instead.
     */
    val collapsedFolders: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.COLLAPSED_FOLDERS] ?: emptySet() }

    suspend fun setFolderCollapsed(folderId: String, collapsed: Boolean) = edit { prefs ->
        val current = prefs[Keys.COLLAPSED_FOLDERS]?.toMutableSet() ?: mutableSetOf()
        if (collapsed) current.add(folderId) else current.remove(folderId)
        prefs[Keys.COLLAPSED_FOLDERS] = current
    }

    /**
     * User-added model IDs per profile, kept alongside (not replacing) whatever the
     * server's `/models` endpoint advertises. This is how models the catalogue omits
     * — e.g. z.ai's GLM-4.x-Flash variants — stay selectable in the model bar.
     * Stored per profile under a dynamic DataStore key; no DB schema change needed.
     */
    fun customModels(profileId: String): Flow<Set<String>> {
        val key = stringSetPreferencesKey("custom_models_$profileId")
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key] ?: emptySet() }
    }

    suspend fun addCustomModel(profileId: String, modelId: String) = edit { prefs ->
        val key = stringSetPreferencesKey("custom_models_$profileId")
        val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
        current.add(modelId)
        prefs[key] = current
    }

    suspend fun removeCustomModel(profileId: String, modelId: String) = edit { prefs ->
        val key = stringSetPreferencesKey("custom_models_$profileId")
        val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
        current.remove(modelId)
        prefs[key] = current
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { p ->
        AppSettings(
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            accentColor = p[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.VIOLET,
            amoledBlack = p[Keys.AMOLED] ?: false,
            lightThemePreset = p[Keys.LIGHT_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: ThemePreset.MATERIAL_YOU,
            darkThemePreset = p[Keys.DARK_PRESET]?.let { runCatching { ThemePreset.valueOf(it) }.getOrNull() } ?: ThemePreset.MATERIAL_YOU,
            customAccentColor = p[Keys.CUSTOM_ACCENT],
            bubbleStyle = p[Keys.BUBBLE_STYLE]?.let { runCatching { BubbleStyle.valueOf(it) }.getOrNull() } ?: BubbleStyle.ROUNDED,
            fontSizeScale = p[Keys.FONT_SIZE_SCALE] ?: 1.0f,
            chatDensity = p[Keys.CHAT_DENSITY]?.let { runCatching { ChatDensity.valueOf(it) }.getOrNull() } ?: ChatDensity.COZY,
            customWallpaperPath = p[Keys.WALLPAPER_PATH],
            streamResponses = p[Keys.STREAM] ?: true,
            sendOnEnter = p[Keys.SEND_ON_ENTER] ?: false,
            renderMarkdown = p[Keys.MARKDOWN] ?: true,
            showTokenUsage = p[Keys.TOKEN_USAGE] ?: true,
            showTimestamps = p[Keys.TIMESTAMPS] ?: false,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            ttsEnabled = p[Keys.TTS] ?: false,
            ttsLanguage = p[Keys.TTS_LANGUAGE] ?: "",
            ttsRate = p[Keys.TTS_RATE] ?: 1.0f,
            ttsPitch = p[Keys.TTS_PITCH] ?: 1.0f,
            webSearchEnabled = p[Keys.WEB_SEARCH] ?: false,
            searchProvider = p[Keys.SEARCH_PROVIDER]?.let { runCatching { SearchProvider.valueOf(it) }.getOrNull() } ?: SearchProvider.SEARXNG,
            searxngUrl = p[Keys.SEARXNG_URL] ?: "",
            googleCx = p[Keys.GOOGLE_CX] ?: "",
            webSearchMaxResults = p[Keys.WEB_SEARCH_MAX] ?: 5,
            defaultSystemPrompt = p[Keys.SYSTEM_PROMPT] ?: "",
            defaultParams = p[Keys.PARAMS]?.let {
                runCatching { AppJson.decodeFromString<GenerationParams>(it) }.getOrNull()
            } ?: GenerationParams.Default,
            onboarded = p[Keys.ONBOARDED] ?: false,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            appLockMode = p[Keys.APP_LOCK_MODE]?.let { runCatching { AppLockMode.valueOf(it) }.getOrNull() } ?: AppLockMode.NONE,
            autoLockMinutes = p[Keys.AUTO_LOCK_MINUTES] ?: 5,
            secureScreens = p[Keys.SECURE_SCREENS] ?: false,
            tailscaleDiscovery = p[Keys.TAILSCALE_DISCOVERY] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setAccent(accent: AccentColor) = edit { it[Keys.ACCENT] = accent.name }
    suspend fun setAmoled(enabled: Boolean) = edit { it[Keys.AMOLED] = enabled }
    
    suspend fun setLightThemePreset(preset: ThemePreset) = edit { it[Keys.LIGHT_PRESET] = preset.name }
    suspend fun setDarkThemePreset(preset: ThemePreset) = edit { it[Keys.DARK_PRESET] = preset.name }
    suspend fun setCustomAccentColor(colorHex: String?) = edit { 
        if (colorHex == null) it.remove(Keys.CUSTOM_ACCENT) else it[Keys.CUSTOM_ACCENT] = colorHex 
    }
    suspend fun setBubbleStyle(style: BubbleStyle) = edit { it[Keys.BUBBLE_STYLE] = style.name }
    suspend fun setFontSizeScale(scale: Float) = edit { it[Keys.FONT_SIZE_SCALE] = scale }
    suspend fun setChatDensity(density: ChatDensity) = edit { it[Keys.CHAT_DENSITY] = density.name }
    suspend fun setCustomWallpaperPath(path: String?) = edit {
        if (path == null) it.remove(Keys.WALLPAPER_PATH) else it[Keys.WALLPAPER_PATH] = path
    }

    suspend fun setStream(enabled: Boolean) = edit { it[Keys.STREAM] = enabled }
    suspend fun setSendOnEnter(enabled: Boolean) = edit { it[Keys.SEND_ON_ENTER] = enabled }
    suspend fun setRenderMarkdown(enabled: Boolean) = edit { it[Keys.MARKDOWN] = enabled }
    suspend fun setShowTokenUsage(enabled: Boolean) = edit { it[Keys.TOKEN_USAGE] = enabled }
    suspend fun setShowTimestamps(enabled: Boolean) = edit { it[Keys.TIMESTAMPS] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    suspend fun setTts(enabled: Boolean) = edit { it[Keys.TTS] = enabled }
    suspend fun setTtsLanguage(tag: String) = edit { it[Keys.TTS_LANGUAGE] = tag }
    suspend fun setTtsRate(rate: Float) = edit { it[Keys.TTS_RATE] = rate }
    suspend fun setTtsPitch(pitch: Float) = edit { it[Keys.TTS_PITCH] = pitch }
    suspend fun setWebSearchEnabled(enabled: Boolean) = edit { it[Keys.WEB_SEARCH] = enabled }
    suspend fun setSearchProvider(provider: SearchProvider) = edit { it[Keys.SEARCH_PROVIDER] = provider.name }
    suspend fun setSearxngUrl(url: String) = edit { it[Keys.SEARXNG_URL] = url }
    suspend fun setGoogleCx(cx: String) = edit { it[Keys.GOOGLE_CX] = cx }
    suspend fun setWebSearchMaxResults(n: Int) = edit { it[Keys.WEB_SEARCH_MAX] = n }
    suspend fun setDefaultSystemPrompt(prompt: String) = edit { it[Keys.SYSTEM_PROMPT] = prompt }
    suspend fun setDefaultParams(params: GenerationParams) = edit { it[Keys.PARAMS] = AppJson.encodeToString(params) }
    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }

    // ---- Nova 2.0 ----
    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.HAPTICS] = enabled }
    suspend fun setAppLockMode(mode: AppLockMode) = edit { it[Keys.APP_LOCK_MODE] = mode.name }
    suspend fun setAutoLockMinutes(minutes: Int) = edit { it[Keys.AUTO_LOCK_MINUTES] = minutes }
    suspend fun setSecureScreens(enabled: Boolean) = edit { it[Keys.SECURE_SCREENS] = enabled }
    suspend fun setTailscaleDiscovery(enabled: Boolean) = edit { it[Keys.TAILSCALE_DISCOVERY] = enabled }

    /**
     * Persisted tailnet peers to scan for AI servers, encoded "host|port|providerType".
     * Kept in DataStore (not Room) so peer lists never require a DB migration.
     */
    val tailscalePeers: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.TAILSCALE_PEERS] ?: emptySet() }

    suspend fun addTailscalePeer(encoded: String) = edit { prefs ->
        val current = prefs[Keys.TAILSCALE_PEERS]?.toMutableSet() ?: mutableSetOf()
        current.add(encoded)
        prefs[Keys.TAILSCALE_PEERS] = current
    }

    suspend fun removeTailscalePeer(encoded: String) = edit { prefs ->
        val current = prefs[Keys.TAILSCALE_PEERS]?.toMutableSet() ?: mutableSetOf()
        current.remove(encoded)
        prefs[Keys.TAILSCALE_PEERS] = current
    }

    /**
     * A2A conversation context: keeps the agent-side `contextId` per conversation
     * AND per peer (gateway) so switching a conversation between agents never
     * crosses threads — each peer keeps its own isolated contextId. DataStore,
     * not Room — purely protocol state, never worth a schema change.
     */
    suspend fun a2aContextId(conversationId: String, peerKey: String): String? =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[stringPreferencesKey("a2a_context_${conversationId}_$peerKey")] }
            .firstOrNull()

    suspend fun setA2aContextId(conversationId: String, peerKey: String, contextId: String?) = edit { prefs ->
        val key = stringPreferencesKey("a2a_context_${conversationId}_$peerKey")
        if (contextId == null) prefs.remove(key) else prefs[key] = contextId
    }

    /**
     * A2A peer table for a profile (one profile reaching several agent gateways,
     * each with its own token in SecureStore). JSON list in DataStore — an
     * additive per-profile concept, no Room schema involved; profiles without
     * peers keep their single base URL and key unchanged.
     */
    fun a2aPeers(profileId: String): Flow<List<com.vela.chat.domain.model.A2aPeer>> {
        val key = stringPreferencesKey("a2a_peers_$profileId")
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                prefs[key]?.let { json ->
                    runCatching { AppJson.decodeFromString<List<com.vela.chat.domain.model.A2aPeer>>(json) }.getOrNull()
                } ?: emptyList()
            }
    }

    /** One-shot peer read for target resolution on the send path. */
    suspend fun a2aPeersOnce(profileId: String): List<com.vela.chat.domain.model.A2aPeer> =
        a2aPeers(profileId).firstOrNull().orEmpty()

    suspend fun setA2aPeers(profileId: String, peers: List<com.vela.chat.domain.model.A2aPeer>) = edit { prefs ->
        prefs[stringPreferencesKey("a2a_peers_$profileId")] = AppJson.encodeToString(peers)
    }

    /** Which peer (id) a conversation currently talks through; null = the profile's default. */
    suspend fun a2aPeerChoice(conversationId: String): String? =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[stringPreferencesKey("a2a_peer_choice_$conversationId")] }
            .firstOrNull()

    suspend fun setA2aPeerChoice(conversationId: String, peerId: String?) = edit { prefs ->
        val key = stringPreferencesKey("a2a_peer_choice_$conversationId")
        if (peerId == null) prefs.remove(key) else prefs[key] = peerId
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
