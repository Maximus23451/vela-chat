package com.vela.chat.data.repository

import com.vela.chat.data.AppJson
import com.vela.chat.data.backup.BackupCrypto
import com.vela.chat.data.backup.BackupEnvelope
import com.vela.chat.data.local.dao.ApiProfileDao
import com.vela.chat.data.net.findUntrustedCert
import com.vela.chat.data.local.toDomain
import com.vela.chat.data.local.toEntity
import com.vela.chat.data.remote.NetworkResult
import com.vela.chat.data.remote.OpenAiApi
import com.vela.chat.data.remote.RequestFactory
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.ModelInfo
import com.vela.chat.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiProfileRepository @Inject constructor(
    private val dao: ApiProfileDao,
    private val secureStore: SecureStore,
    private val api: OpenAiApi,
) {

    val profiles: Flow<List<ApiProfile>> = dao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .catch { emit(emptyList()) }

    val defaultProfile: Flow<ApiProfile?> = dao.observeDefault()
        .map { it?.toDomain() }
        .catch { emit(null) }

    suspend fun getProfile(id: String): ApiProfile? = runCatching {
        dao.getById(id)?.toDomain()
    }.getOrNull()

    suspend fun getDefaultOrFirst(): ApiProfile? = runCatching {
        dao.getDefault()?.toDomain() ?: dao.getAll().firstOrNull()?.toDomain()
    }.getOrNull()

    fun getApiKey(profileId: String): String? = runCatching {
        secureStore.getApiKey(profileId)
    }.getOrNull()

    fun hasApiKey(profileId: String): Boolean = runCatching {
        secureStore.hasApiKey(profileId)
    }.getOrDefault(false)

    // ---- A2A per-peer tokens (SecureStore, one identity per peer) ----

    private fun peerTokenKey(profileId: String, peerId: String) =
        com.vela.chat.util.VelaConstants.A2A_PEER_SECRET_PREFIX + "${profileId}_$peerId"

    /** Stored bearer token for one peer of a profile; null when not set. */
    fun getA2aPeerToken(profileId: String, peerId: String): String? = runCatching {
        secureStore.getSecret(peerTokenKey(profileId, peerId))
    }.getOrNull()

    fun hasA2aPeerToken(profileId: String, peerId: String): Boolean =
        !getA2aPeerToken(profileId, peerId).isNullOrBlank()

    /** Persists (or clears with null) one peer's bearer token, encrypted at rest. */
    fun saveA2aPeerToken(profileId: String, peerId: String, token: String?) = runCatching {
        secureStore.saveSecret(peerTokenKey(profileId, peerId), token)
    }

    suspend fun saveProfile(profile: ApiProfile, apiKey: String?): ApiProfile = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val existing = dao.getById(profile.id)
            val toSave = profile.copy(
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            dao.upsert(toSave.toEntity())
            if (toSave.isDefault) dao.setDefault(toSave.id)
            if (apiKey != null) secureStore.saveApiKey(toSave.id, apiKey)
            toSave
        }.getOrElse { profile }
    }

    suspend fun createDefaultIfEmpty() {
        runCatching {
            if (dao.getAll().isNotEmpty()) return
            val profile = ApiProfile(
                id = UUID.randomUUID().toString(),
                name = "LM Studio (local)",
                providerType = ProviderType.LM_STUDIO,
                baseUrl = ProviderType.LM_STUDIO.defaultBaseUrl,
                isDefault = true,
            )
            dao.upsert(profile.toEntity())
            dao.setDefault(profile.id)
        }
    }

    /** Every profile, unfiltered (full-backup export). */
    suspend fun getAllForBackup(): List<ApiProfile> = runCatching {
        dao.getAll().map { it.toDomain() }
    }.getOrDefault(emptyList())

    /**
     * Restores one profile verbatim (original id + timestamps) plus its key, so
     * conversations referencing [profile]'s id by FK stay valid after a full-backup
     * restore. Unlike [saveProfile], this never recomputes createdAt.
     */
    suspend fun restoreProfile(profile: ApiProfile, apiKey: String?) = withContext(Dispatchers.IO) {
        runCatching {
            dao.upsert(profile.toEntity())
            if (profile.isDefault) dao.setDefault(profile.id)
            if (apiKey != null) secureStore.saveApiKey(profile.id, apiKey)
        }
    }

    suspend fun deleteProfile(id: String) {
        runCatching {
            dao.delete(id)
            secureStore.deleteApiKey(id)
        }
    }

    suspend fun setDefault(id: String) {
        runCatching {
            dao.setDefault(id)
        }
    }

    /** Probe `/models` to validate connectivity and surface the catalogue. */
    suspend fun testConnection(profile: ApiProfile, apiKey: String?): NetworkResult<List<ModelInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = profile.normalizedBaseUrl + "models"
                val response = api.listModels(url, RequestFactory.bearer(apiKey))
                response.data.map { ModelInfo(it.id, it.owned_by) }
            }.fold(
                onSuccess = { NetworkResult.Success(it) },
                onFailure = { NetworkResult.Error(describeError(it), it) },
            )
        }

    suspend fun listModels(profileId: String): NetworkResult<List<ModelInfo>> {
        val profile = getProfile(profileId) ?: return NetworkResult.Error("Profile not found")
        return testConnection(profile, getApiKey(profileId))
    }

    // ---- Import / Export ----

    /**
     * [includeKeys] plaintext export carries no secret, so it's plain JSON. With keys,
     * the bundle (API keys included) is run through the same AES-256-GCM envelope as
     * [com.vela.chat.ui.settings.BackupManager]'s full backup — [passphrase] is
     * required in that case and is never itself stored.
     */
    suspend fun exportProfiles(includeKeys: Boolean, passphrase: CharArray? = null): String =
        withContext(Dispatchers.IO) {
            val items = dao.getAll().map { entity ->
                val domain = entity.toDomain()
                ProfileExport(
                    name = domain.name,
                    providerType = domain.providerType.name,
                    baseUrl = domain.baseUrl,
                    model = domain.model,
                    apiKey = if (includeKeys) secureStore.getApiKey(domain.id) else null,
                    isDefault = domain.isDefault,
                )
            }
            val bundleJson = AppJson.encodeToString(ProfileBundle(profiles = items))
            if (includeKeys) {
                require(passphrase != null && passphrase.isNotEmpty()) { "Passphrase required to export API keys" }
                AppJson.encodeToString(BackupCrypto.encrypt(bundleJson, passphrase))
            } else {
                bundleJson
            }
        }

    /** True when [json] is an encrypted export ([exportProfiles] with keys) rather than a plain one. */
    fun isEncryptedExport(json: String): Boolean =
        runCatching { AppJson.decodeFromString<BackupEnvelope>(json) }.isSuccess

    suspend fun importProfiles(json: String, passphrase: CharArray? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = runCatching { AppJson.decodeFromString<BackupEnvelope>(json) }.getOrNull()
            val bundleJson = if (envelope != null) {
                requireNotNull(passphrase) { "Passphrase required to decrypt this export" }
                BackupCrypto.decrypt(envelope, passphrase).getOrElse { err ->
                    throw if (BackupCrypto.isAuthFailure(err)) {
                        IllegalStateException("Wrong passphrase, or the file is corrupted")
                    } else {
                        err
                    }
                }
            } else {
                json
            }
            val bundle = AppJson.decodeFromString<ProfileBundle>(bundleJson)
            bundle.profiles.forEach { item ->
                val profile = ApiProfile(
                    id = UUID.randomUUID().toString(),
                    name = item.name,
                    providerType = runCatching { ProviderType.valueOf(item.providerType) }
                        .getOrDefault(ProviderType.CUSTOM),
                    baseUrl = item.baseUrl,
                    model = item.model,
                    isDefault = false,
                )
                saveProfile(profile, item.apiKey)
            }
            bundle.profiles.size
        }
    }

    private fun describeError(t: Throwable): String = when (val cert = findUntrustedCert(t)) {
        null -> when (t) {
            is UnknownHostException -> "Host unreachable — check the base URL and that the server is running."
            is SocketTimeoutException -> "Connection timed out — is LM Studio's server started and reachable?"
            is retrofit2.HttpException -> com.vela.chat.data.remote.describeHttpError(
                t.code(),
                runCatching { t.response()?.errorBody()?.string() }.getOrNull(),
            )
            else -> t.message ?: "Unknown connection error"
        }
        else -> "New certificate for ${cert.host} — review its fingerprint and trust it to continue."
    }

    @Serializable
    private data class ProfileBundle(val version: Int = 1, val profiles: List<ProfileExport>)

    @Serializable
    private data class ProfileExport(
        val name: String,
        val providerType: String,
        val baseUrl: String,
        val model: String? = null,
        val apiKey: String? = null,
        val isDefault: Boolean = false,
    )
}
