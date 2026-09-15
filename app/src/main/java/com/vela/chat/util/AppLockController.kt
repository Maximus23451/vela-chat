package com.vela.chat.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App lock (Privacy settings). Uses **framework APIs only** — no androidx.biometric
 * dependency, keeping the build fully offline:
 *  - API 28+: `android.hardware.biometrics.BiometricPrompt` (the UI calls this from an
 *    Activity with [createBiometricPrompt] plumbing; API 26/27 falls back to
 *    [KeyguardManager.createConfirmDeviceCredentialIntent]).
 *  - PIN lock: PBKDF2-with-HMAC-SHA256 hash (per-PIN random salt) stored via
 *    [SecureStore] — the plaintext PIN never touches disk.
 *  - Auto-lock: background timestamp in memory; [shouldLock] is consulted on resume.
 */
@Singleton
class AppLockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStore: SecureStore,
    private val settingsRepository: SettingsRepository,
) {

    private val _locked = MutableStateFlow(false)

    /** True while the UI must hide content behind the lock screen. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long = 0

    suspend fun isLockEnabled(): Boolean = sanitizedLockMode() != com.vela.chat.data.settings.AppLockMode.NONE

    /** Called from Activity.onPause / onStop. */
    fun onBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /** Called from Activity.onResume; re-locks when the auto-lock window has elapsed. */
    suspend fun onResumed() {
        val mode = sanitizedLockMode()
        if (mode == com.vela.chat.data.settings.AppLockMode.NONE) {
            _locked.value = false
            return
        }
        val settings = settingsRepository.settings.first()
        val elapsed = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && elapsed >= settings.autoLockMinutes * 60_000L) {
            _locked.value = true
        }
        backgroundedAt = 0
    }

    /**
     * Reads the configured lock mode and self-heals it to [AppLockMode.NONE][com.vela.chat.data.settings.AppLockMode.NONE]
     * if it can no longer possibly be satisfied — PIN mode with no stored hash (e.g. a
     * legacy backup restore, or the hash getting cleared out from under it), or Biometric
     * mode after the user removes their device's screen lock in OS settings. Both would
     * otherwise present a lock screen with no way through. Every read of the lock mode
     * that can lead to showing the lock screen goes through this rather than the raw
     * setting, so the moment the app is backgrounded and reopened — always possible via
     * the OS home button, even from an already-stuck lock screen — it recovers.
     */
    private suspend fun sanitizedLockMode(): com.vela.chat.data.settings.AppLockMode {
        val mode = settingsRepository.settings.first().appLockMode
        val satisfiable = when (mode) {
            com.vela.chat.data.settings.AppLockMode.NONE -> true
            com.vela.chat.data.settings.AppLockMode.PIN -> hasPin()
            com.vela.chat.data.settings.AppLockMode.BIOMETRIC -> isDeviceSecure()
        }
        if (satisfiable) return mode
        settingsRepository.setAppLockMode(com.vela.chat.data.settings.AppLockMode.NONE)
        return com.vela.chat.data.settings.AppLockMode.NONE
    }

    /** Force the lock (e.g. manual lock button). */
    fun lockNow() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    // ---- Biometrics ----

    /** True when a screen lock (PIN/pattern/password or biometric enrollment) is configured. */
    fun isDeviceSecure(): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    /**
     * Intent-based credential confirmation for API 26/27 where the framework
     * BiometricPrompt is unavailable. Returns null when no screen lock is set.
     */
    fun createConfirmCredentialIntent(title: String, description: String) =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.createConfirmDeviceCredentialIntent(title, description)
        } else null

    // ---- PIN ----

    /** Sets or replaces the app PIN (hashes immediately; plaintext is not stored). */
    suspend fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        secureStore.saveSecret(PIN_KEY, salt.toHex() + ":" + hash.toHex())
    }

    fun hasPin(): Boolean = !secureStore.getSecret(PIN_KEY).isNullOrBlank()

    fun clearPin() {
        secureStore.saveSecret(PIN_KEY, null)
    }

    fun verifyPin(pin: String): Boolean {
        val stored = secureStore.getSecret(PIN_KEY)?.split(":") ?: return false
        if (stored.size != 2) return false
        val salt = stored[0].hexToBytesOrNull() ?: return false
        val expected = stored[1].hexToBytesOrNull() ?: return false
        return java.security.MessageDigest.isEqual(hashPin(pin, salt), expected)
    }

    // ---- Backup (full-backup export/import: hash blob only, never the plaintext PIN) ----

    /** Raw stored PIN hash blob (`salt-hex:hash-hex`), for full-backup export only. */
    fun exportPinHashBlob(): String? = secureStore.getSecret(PIN_KEY)

    /** Restores a PIN hash blob produced by [exportPinHashBlob], verbatim. */
    fun importPinHashBlob(blob: String?) {
        secureStore.saveSecret(PIN_KEY, blob)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, VelaConstants.PIN_PBKDF2_ITERATIONS, VelaConstants.PIN_KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? =
        if (length % 2 == 0) chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.takeIf { it.size == length / 2 }?.toByteArray()
        else null

    private companion object {
        const val PIN_KEY = "app_lock_pin"
        const val SALT_BYTES = 16
    }
}
