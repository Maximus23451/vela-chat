package com.vela.chat.ui.settings

import com.vela.chat.data.AppJson
import com.vela.chat.data.backup.BackupCrypto
import com.vela.chat.data.backup.BackupEnvelope
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.data.repository.LibraryRepository
import com.vela.chat.data.repository.PersonaRepository
import com.vela.chat.data.settings.AccentColor
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.AppLockMode
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.data.settings.SearchProvider
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.data.settings.ThemeMode
import com.vela.chat.data.settings.ThemePreset
import com.vela.chat.domain.model.A2aPeer
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Folder
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.util.AppLockController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a successful backup restore, one count per data kind. */
data class BackupSummary(
    val promptsRestored: Int = 0,
    val profilesRestored: Int = 0,
    val personasRestored: Int = 0,
    val foldersRestored: Int = 0,
    val conversationsRestored: Int = 0,
    val messagesRestored: Int = 0,
) {
    override fun toString(): String =
        "Restored $conversationsRestored conversation(s), $messagesRestored message(s), " +
            "$profilesRestored profile(s), $personasRestored persona(s), $promptsRestored prompt(s)"
}

/**
 * Full-data encrypted backup (Privacy & Security screen): every conversation and
 * message, agent personas, folders, prompt library, API profiles (**with** their API
 * keys and A2A peer tokens), the app-lock PIN hash, and settings — everything needed
 * to restore this device's state elsewhere.
 *
 * The whole bundle is serialized to JSON, then encrypted with [BackupCrypto]
 * (AES-256-GCM, key derived from a user passphrase via PBKDF2). **The passphrase is
 * the only thing standing between this file and every secret on the device** — it is
 * never stored anywhere; losing it makes the backup unrecoverable. All I/O runs on
 * [Dispatchers.IO]; every decode is lenient via [AppJson].
 *
 * [importBackup] also accepts a backup produced by the older keyless, unencrypted
 * format (settings/prompts/profile-metadata only) for backward compatibility — it is
 * detected automatically and restored with fresh ids, exactly as it always was.
 */
@Singleton
class BackupManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val profileRepository: ApiProfileRepository,
    private val conversationRepository: ConversationRepository,
    private val personaRepository: PersonaRepository,
    private val appLockController: AppLockController,
) {

    /** Builds the full bundle and encrypts it; fails if [passphrase] is too short. */
    suspend fun exportBackup(passphrase: CharArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
                "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters"
            }
            val bundleJson = AppJson.encodeToString(buildBundle())
            AppJson.encodeToString(BackupCrypto.encrypt(bundleJson, passphrase))
        }
    }

    /**
     * Restores a backup. Tries the current encrypted envelope format first; if
     * [fileContent] doesn't parse as one, falls back to the legacy plaintext format
     * (pre-2.3, no passphrase needed for that path).
     */
    suspend fun importBackup(fileContent: String, passphrase: CharArray): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            val envelope = runCatching { AppJson.decodeFromString<BackupEnvelope>(fileContent) }.getOrNull()
            if (envelope != null) importEncrypted(envelope, passphrase) else importLegacyPlaintext(fileContent)
        }

    private suspend fun importEncrypted(envelope: BackupEnvelope, passphrase: CharArray): Result<BackupSummary> =
        runCatching {
            val bundleJson = BackupCrypto.decrypt(envelope, passphrase).getOrElse { err ->
                throw if (BackupCrypto.isAuthFailure(err)) {
                    IllegalStateException("Wrong passphrase, or the backup file is corrupted")
                } else {
                    err
                }
            }
            restoreBundle(AppJson.decodeFromString(bundleJson))
        }

    // ---- Building the bundle ----

    private suspend fun buildBundle(): FullBackupBundle {
        val settings = settingsRepository.settings.firstOrNull() ?: AppSettings()
        val prompts = libraryRepository.getAllPrompts().map {
            PromptExport(it.id, it.title, it.content, it.category, it.favorite, it.createdAt)
        }
        val profiles = profileRepository.getAllForBackup().map { p ->
            val peers = settingsRepository.a2aPeersOnce(p.id).map { peer ->
                BackupPeer(
                    id = peer.id,
                    name = peer.name,
                    baseUrl = peer.baseUrl,
                    enabled = peer.enabled,
                    token = profileRepository.getA2aPeerToken(p.id, peer.id),
                )
            }
            ProfileExport(
                id = p.id,
                name = p.name,
                providerType = p.providerType.name,
                baseUrl = p.baseUrl,
                model = p.model,
                isDefault = p.isDefault,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                apiKey = profileRepository.getApiKey(p.id),
                peers = peers,
            )
        }
        val personas = personaRepository.getAll().map {
            BackupPersona(it.id, it.name, it.description, it.emoji, it.systemPrompt, it.temperature, it.isDefault, it.createdAt)
        }
        val chatData = conversationRepository.getAllForBackup()
        val folders = chatData.folders.map { BackupFolder(it.id, it.name, it.position, it.color, it.icon, it.createdAt) }
        val conversations = chatData.conversations.map {
            BackupConversation(
                it.id, it.title, it.folderId, it.profileId, it.model, it.systemPrompt,
                it.params, it.personaId, it.pinned, it.archived, it.createdAt, it.updatedAt,
            )
        }
        val messages = chatData.messages.map {
            BackupMessage(
                it.id, it.conversationId, it.role.wire, it.content, it.reasoning, it.model,
                it.attachments, it.isError, it.createdAt, it.promptTokens, it.completionTokens,
                it.generationTimeMs, it.favorite, it.pinned,
            )
        }
        return FullBackupBundle(
            settings = SettingsBackup.from(settings),
            prompts = prompts,
            profiles = profiles,
            personas = personas,
            folders = folders,
            conversations = conversations,
            messages = messages,
            appLockPinHash = appLockController.exportPinHashBlob(),
        )
    }

    // ---- Restoring the bundle ----

    /**
     * Restore order matters: folders before conversations before messages — messages
     * FK-reference their conversation (Room enforces it). Every id is preserved from
     * the export, so restoring the same backup twice is idempotent (upsert by id).
     */
    private suspend fun restoreBundle(bundle: FullBackupBundle): BackupSummary {
        // PIN hash first: restoreSettingsSafely's lockout guard needs to see the final
        // hasPin() state, not the pre-restore one.
        bundle.appLockPinHash?.let { appLockController.importPinHashBlob(it) }
        bundle.settings?.let { restoreSettingsSafely(it) }

        bundle.profiles.forEach { item ->
            val provider = runCatching { ProviderType.valueOf(item.providerType) }.getOrDefault(ProviderType.CUSTOM)
            profileRepository.restoreProfile(
                ApiProfile(
                    id = item.id, name = item.name, providerType = provider, baseUrl = item.baseUrl,
                    model = item.model, isDefault = item.isDefault, createdAt = item.createdAt, updatedAt = item.updatedAt,
                ),
                apiKey = item.apiKey,
            )
            if (item.peers.isNotEmpty()) {
                settingsRepository.setA2aPeers(item.id, item.peers.map { A2aPeer(it.id, it.name, it.baseUrl, it.enabled) })
                item.peers.forEach { peer -> profileRepository.saveA2aPeerToken(item.id, peer.id, peer.token) }
            }
        }
        bundle.personas.forEach { item ->
            personaRepository.upsert(
                AgentPersona(item.id, item.name, item.description, item.emoji, item.systemPrompt, item.temperature, item.isDefault, item.createdAt),
            )
        }
        bundle.prompts.forEach { item ->
            libraryRepository.restorePrompt(SavedPrompt(item.id, item.title, item.content, item.category, item.favorite, item.createdAt))
        }
        bundle.folders.forEach { item ->
            conversationRepository.restoreFolder(Folder(item.id, item.name, item.position, item.color, item.icon, item.createdAt))
        }
        bundle.conversations.forEach { item ->
            conversationRepository.restoreConversation(
                Conversation(
                    item.id, item.title, item.folderId, item.profileId, item.model, item.systemPrompt,
                    item.params, item.personaId, item.pinned, item.archived, item.createdAt, item.updatedAt,
                ),
            )
        }
        bundle.messages.forEach { item ->
            conversationRepository.restoreMessage(
                Message(
                    item.id, item.conversationId, Role.from(item.role), item.content, item.reasoning, item.model,
                    item.attachments, item.isError, item.createdAt, item.promptTokens, item.completionTokens,
                    item.generationTimeMs, item.favorite, item.pinned,
                ),
            )
        }
        return BackupSummary(
            promptsRestored = bundle.prompts.size,
            profilesRestored = bundle.profiles.size,
            personasRestored = bundle.personas.size,
            foldersRestored = bundle.folders.size,
            conversationsRestored = bundle.conversations.size,
            messagesRestored = bundle.messages.size,
        )
    }

    /**
     * Applies [s], then closes a lockout gap: [AppLockMode.PIN] with no stored PIN hash
     * (a pre-2.3 backup's settings snapshot carried `appLockMode` but the app never
     * exported a PIN hash until this version, so a legacy import can otherwise restore
     * "PIN required" with nothing that will ever satisfy it) leaves the app permanently
     * unenterable. Falls back to [AppLockMode.NONE] rather than lock the user out of
     * their own just-restored data.
     */
    private suspend fun restoreSettingsSafely(s: SettingsBackup) {
        restoreSettings(s)
        if (s.appLockMode == AppLockMode.PIN.name && !appLockController.hasPin()) {
            settingsRepository.setAppLockMode(AppLockMode.NONE)
        }
    }

    private suspend fun restoreSettings(s: SettingsBackup) {
        settingsRepository.setThemeMode(s.themeMode.enumOr(ThemeMode.SYSTEM) { ThemeMode.valueOf(it) })
        settingsRepository.setDynamicColor(s.dynamicColor)
        settingsRepository.setAccent(s.accentColor.enumOr(AccentColor.VIOLET) { AccentColor.valueOf(it) })
        settingsRepository.setAmoled(s.amoledBlack)
        settingsRepository.setLightThemePreset(s.lightThemePreset.enumOr(ThemePreset.MATERIAL_YOU) { ThemePreset.valueOf(it) })
        settingsRepository.setDarkThemePreset(s.darkThemePreset.enumOr(ThemePreset.MATERIAL_YOU) { ThemePreset.valueOf(it) })
        settingsRepository.setCustomAccentColor(s.customAccentColor?.takeIf(String::isNotBlank))
        settingsRepository.setBubbleStyle(s.bubbleStyle.enumOr(BubbleStyle.ROUNDED) { BubbleStyle.valueOf(it) })
        settingsRepository.setFontSizeScale(s.fontSizeScale)
        settingsRepository.setChatDensity(s.chatDensity.enumOr(ChatDensity.COZY) { ChatDensity.valueOf(it) })
        settingsRepository.setCustomWallpaperPath(s.customWallpaperPath?.takeIf(String::isNotBlank))
        settingsRepository.setStream(s.streamResponses)
        settingsRepository.setSendOnEnter(s.sendOnEnter)
        settingsRepository.setRenderMarkdown(s.renderMarkdown)
        settingsRepository.setShowTokenUsage(s.showTokenUsage)
        settingsRepository.setShowTimestamps(s.showTimestamps)
        settingsRepository.setKeepScreenOn(s.keepScreenOn)
        settingsRepository.setTts(s.ttsEnabled)
        settingsRepository.setTtsLanguage(s.ttsLanguage)
        settingsRepository.setTtsRate(s.ttsRate)
        settingsRepository.setTtsPitch(s.ttsPitch)
        settingsRepository.setWebSearchEnabled(s.webSearchEnabled)
        settingsRepository.setSearchProvider(s.searchProvider.enumOr(SearchProvider.SEARXNG) { SearchProvider.valueOf(it) })
        settingsRepository.setSearxngUrl(s.searxngUrl)
        settingsRepository.setGoogleCx(s.googleCx)
        settingsRepository.setWebSearchMaxResults(s.webSearchMaxResults)
        settingsRepository.setDefaultSystemPrompt(s.defaultSystemPrompt)
        settingsRepository.setDefaultParams(s.defaultParams ?: GenerationParams.Default)
        settingsRepository.setHaptics(s.hapticsEnabled)
        settingsRepository.setAppLockMode(s.appLockMode.enumOr(AppLockMode.NONE) { AppLockMode.valueOf(it) })
        settingsRepository.setAutoLockMinutes(s.autoLockMinutes)
        settingsRepository.setSecureScreens(s.secureScreens)
        settingsRepository.setTailscaleDiscovery(s.tailscaleDiscovery)
    }

    // ---- Legacy (pre-2.3) plaintext backup: settings + prompts + keyless profile metadata ----

    private suspend fun importLegacyPlaintext(json: String): Result<BackupSummary> = runCatching {
        val bundle = AppJson.decodeFromString<LegacyBackupBundle>(json)
        // Legacy backups never carried a PIN hash at all — restoreSettingsSafely falls
        // an inherited appLockMode=PIN back to NONE rather than lock the user out.
        bundle.settings?.let { restoreSettingsSafely(it) }
        bundle.prompts.forEach { item ->
            libraryRepository.restorePrompt(
                SavedPrompt(id = UUID.randomUUID().toString(), title = item.title, content = item.content, category = item.category, favorite = item.favorite),
            )
        }
        bundle.profiles.forEach { item ->
            val provider = runCatching { ProviderType.valueOf(item.providerType) }.getOrDefault(ProviderType.CUSTOM)
            profileRepository.saveProfile(
                ApiProfile(id = UUID.randomUUID().toString(), name = item.name, providerType = provider, baseUrl = item.baseUrl, model = item.model, isDefault = false),
                apiKey = null,
            )
        }
        BackupSummary(promptsRestored = bundle.prompts.size, profilesRestored = bundle.profiles.size)
    }

    private companion object {
        const val BACKUP_VERSION = 1
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}

/** Decodes an enum name leniently; unknown names fall back to [fallback]. */
private inline fun <reified T : Enum<T>> String.enumOr(fallback: T, decode: (String) -> T): T =
    runCatching { decode(this) }.getOrDefault(fallback)

/** Root document, pre-encryption. Every id is the original device's — a restore is upsert-by-id. */
@Serializable
private data class FullBackupBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: SettingsBackup? = null,
    val prompts: List<PromptExport> = emptyList(),
    val profiles: List<ProfileExport> = emptyList(),
    val personas: List<BackupPersona> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val conversations: List<BackupConversation> = emptyList(),
    val messages: List<BackupMessage> = emptyList(),
    /** Raw PIN hash blob (see [AppLockController.exportPinHashBlob]) — never the plaintext PIN. */
    val appLockPinHash: String? = null,
)

/** Mirror of [AppSettings] with enum values stored as names for forward compatibility. */
@Serializable
private data class SettingsBackup(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = true,
    val accentColor: String = AccentColor.VIOLET.name,
    val amoledBlack: Boolean = false,
    val lightThemePreset: String = ThemePreset.MATERIAL_YOU.name,
    val darkThemePreset: String = ThemePreset.MATERIAL_YOU.name,
    val customAccentColor: String? = null,
    val bubbleStyle: String = BubbleStyle.ROUNDED.name,
    val fontSizeScale: Float = 1.0f,
    val chatDensity: String = ChatDensity.COZY.name,
    val customWallpaperPath: String? = null,
    val streamResponses: Boolean = true,
    val sendOnEnter: Boolean = false,
    val renderMarkdown: Boolean = true,
    val showTokenUsage: Boolean = true,
    val showTimestamps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val ttsEnabled: Boolean = false,
    val ttsLanguage: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val webSearchEnabled: Boolean = false,
    val searchProvider: String = SearchProvider.SEARXNG.name,
    val searxngUrl: String = "",
    val googleCx: String = "",
    val webSearchMaxResults: Int = 5,
    val defaultSystemPrompt: String = "",
    val defaultParams: GenerationParams? = null,
    val hapticsEnabled: Boolean = true,
    val appLockMode: String = AppLockMode.NONE.name,
    val autoLockMinutes: Int = 5,
    val secureScreens: Boolean = false,
    val tailscaleDiscovery: Boolean = true,
) {
    companion object {
        fun from(s: AppSettings) = SettingsBackup(
            themeMode = s.themeMode.name,
            dynamicColor = s.dynamicColor,
            accentColor = s.accentColor.name,
            amoledBlack = s.amoledBlack,
            lightThemePreset = s.lightThemePreset.name,
            darkThemePreset = s.darkThemePreset.name,
            customAccentColor = s.customAccentColor,
            bubbleStyle = s.bubbleStyle.name,
            fontSizeScale = s.fontSizeScale,
            chatDensity = s.chatDensity.name,
            customWallpaperPath = s.customWallpaperPath,
            streamResponses = s.streamResponses,
            sendOnEnter = s.sendOnEnter,
            renderMarkdown = s.renderMarkdown,
            showTokenUsage = s.showTokenUsage,
            showTimestamps = s.showTimestamps,
            keepScreenOn = s.keepScreenOn,
            ttsEnabled = s.ttsEnabled,
            ttsLanguage = s.ttsLanguage,
            ttsRate = s.ttsRate,
            ttsPitch = s.ttsPitch,
            webSearchEnabled = s.webSearchEnabled,
            searchProvider = s.searchProvider.name,
            searxngUrl = s.searxngUrl,
            googleCx = s.googleCx,
            webSearchMaxResults = s.webSearchMaxResults,
            defaultSystemPrompt = s.defaultSystemPrompt,
            defaultParams = s.defaultParams,
            hapticsEnabled = s.hapticsEnabled,
            appLockMode = s.appLockMode.name,
            autoLockMinutes = s.autoLockMinutes,
            secureScreens = s.secureScreens,
            tailscaleDiscovery = s.tailscaleDiscovery,
        )
    }
}

@Serializable
private data class PromptExport(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "General",
    val favorite: Boolean = false,
    val createdAt: Long = 0,
)

/** One A2A peer of a profile, its bearer token included — a token IS the identity on a gateway. */
@Serializable
private data class BackupPeer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val token: String? = null,
)

@Serializable
private data class ProfileExport(
    val id: String,
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val model: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val apiKey: String? = null,
    val peers: List<BackupPeer> = emptyList(),
)

@Serializable
private data class BackupPersona(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "🤖",
    val systemPrompt: String,
    val temperature: Float? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
private data class BackupFolder(
    val id: String,
    val name: String,
    val position: Int = 0,
    val color: Int = 0,
    val icon: String = "",
    val createdAt: Long = 0,
)

@Serializable
private data class BackupConversation(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val profileId: String? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val params: GenerationParams? = null,
    val personaId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
private data class BackupMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val model: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isError: Boolean = false,
    val createdAt: Long = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val generationTimeMs: Long = 0,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
)

// ---- Legacy (pre-2.3) plaintext shapes — decode-only, for backward-compatible import ----

@Serializable
private data class LegacyBackupBundle(
    val version: Int = 1,
    val settings: SettingsBackup? = null,
    val prompts: List<LegacyPromptExport> = emptyList(),
    val profiles: List<LegacyProfileExport> = emptyList(),
)

@Serializable
private data class LegacyPromptExport(
    val title: String,
    val content: String,
    val category: String = "General",
    val favorite: Boolean = false,
)

@Serializable
private data class LegacyProfileExport(
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val model: String? = null,
    val isDefault: Boolean = false,
)
