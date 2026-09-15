package com.vela.chat.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed secure storage for API keys. Keys are encrypted at rest via
 * [EncryptedSharedPreferences] using an AES-256 master key in the Android
 * Keystore (hardware-backed where available). The plaintext key never touches
 * Room or backups.
 *
 * **Fail-closed:** if the Keystore is unusable (corruption / unsupported ROM),
 * we do NOT silently persist keys in plaintext. Instead they are held in memory
 * for the current process only — they keep working for the session but are never
 * written unencrypted to disk. [isUsingSecureStorage] reports which mode is active.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val memoryFallback = ConcurrentHashMap<String, String>()

    @Volatile private var encryptedPrefs: SharedPreferences? = null
    @Volatile private var initialized = false

    /** True if keys are persisted encrypted; false if running on the in-memory fallback. */
    val isUsingSecureStorage: Boolean
        get() {
            ensureInitialized()
            return encryptedPrefs != null
        }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            encryptedPrefs = runCatching { createEncryptedPrefs() }.getOrElse {
                // One recovery attempt: wipe a possibly-corrupt store and retry.
                runCatching { context.deleteSharedPreferences(PREFS_NAME) }
                runCatching { createEncryptedPrefs() }.getOrNull()
            }
            initialized = true
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveApiKey(profileId: String, apiKey: String?) {
        ensureInitialized()
        val key = keyFor(profileId)
        val prefs = encryptedPrefs
        if (apiKey.isNullOrBlank()) {
            if (prefs != null) prefs.edit().remove(key).apply() else memoryFallback.remove(key)
        } else {
            if (prefs != null) prefs.edit().putString(key, apiKey).apply() else memoryFallback[key] = apiKey
        }
    }

    fun getApiKey(profileId: String): String? {
        ensureInitialized()
        val key = keyFor(profileId)
        return encryptedPrefs?.getString(key, null) ?: memoryFallback[key]
    }

    fun hasApiKey(profileId: String): Boolean = !getApiKey(profileId).isNullOrBlank()

    fun deleteApiKey(profileId: String) {
        ensureInitialized()
        val key = keyFor(profileId)
        encryptedPrefs?.edit()?.remove(key)?.apply()
        memoryFallback.remove(key)
    }

    private fun keyFor(profileId: String) = "$KEY_PREFIX$profileId"

    // ---- Generic secrets (Nova 2.0: app-lock PIN etc.) ----

    /** Persists a small secret (already hashed by the caller) under [key]. */
    fun saveSecret(key: String, value: String?) {
        ensureInitialized()
        val prefs = encryptedPrefs
        val fullKey = "$SECRET_PREFIX$key"
        if (value.isNullOrBlank()) {
            if (prefs != null) prefs.edit().remove(fullKey).apply() else memoryFallback.remove(fullKey)
        } else {
            if (prefs != null) prefs.edit().putString(fullKey, value).apply() else memoryFallback[fullKey] = value
        }
    }

    fun getSecret(key: String): String? {
        ensureInitialized()
        val fullKey = "$SECRET_PREFIX$key"
        return encryptedPrefs?.getString(fullKey, null) ?: memoryFallback[fullKey]
    }

    /** Test hook: inject a fake [SharedPreferences] and skip Keystore init. */
    @androidx.annotation.VisibleForTesting
    internal fun setPrefsForTest(prefs: SharedPreferences) {
        encryptedPrefs = prefs
        initialized = true
    }

    private companion object {
        const val PREFS_NAME = "vela_secure_prefs"
        const val KEY_PREFIX = "api_key_"
        const val SECRET_PREFIX = "secret_" // pragma: allowlist secret
    }
}
