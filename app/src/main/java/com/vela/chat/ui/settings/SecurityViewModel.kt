package com.vela.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.AppLockMode
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.util.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing VM for the Security screen: app-lock mode, PIN lifecycle, auto-lock
 * timer, secure screens and the full encrypted backup via [BackupManager].
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLock: AppLockController,
    private val backupManager: BackupManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    /** Whether a screen lock (PIN/pattern/biometric) is configured on the device. */
    val deviceSecure: Boolean = appLock.isDeviceSecure()

    /** Whether an app PIN is currently stored (hash-only, never readable back). */
    val hasPin: Boolean = appLock.hasPin()

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot user feedback (import/export results). */
    val message = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun setLockMode(mode: AppLockMode) = viewModelScope.launch {
        settingsRepository.setAppLockMode(mode)
    }

    /**
     * Hashes and stores [pin] via [AppLockController.setPin], then activates the
     * PIN lock mode. [onSaved] reports success on the main thread.
     */
    fun savePin(pin: String, onSaved: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { appLock.setPin(pin) }.isSuccess
        if (ok) settingsRepository.setAppLockMode(AppLockMode.PIN)
        onSaved(ok)
    }

    /** Removes the stored PIN and deactivates the lock. */
    fun removePin() = viewModelScope.launch {
        settingsRepository.setAppLockMode(AppLockMode.NONE)
        runCatching { appLock.clearPin() }
    }

    fun setAutoLockMinutes(minutes: Int) = viewModelScope.launch {
        settingsRepository.setAutoLockMinutes(minutes)
    }

    fun setSecureScreens(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSecureScreens(enabled)
    }

    /** Immediately engages the lock overlay (MainActivity shows [com.vela.chat.ui.lock.AppLockScreen]). */
    fun lockNow() = appLock.lockNow()

    /** Builds and encrypts the full backup (see [BackupManager]); null and a snackbar on failure. */
    suspend fun exportBackup(passphrase: String): String? =
        backupManager.exportBackup(passphrase.toCharArray()).fold(
            onSuccess = { it },
            onFailure = {
                _message.value = "Export failed: ${it.message}"
                null
            },
        )

    /** Restores a backup produced by [exportBackup]; result surfaces via [message]. */
    fun importBackup(fileContent: String, passphrase: String) = viewModelScope.launch {
        backupManager.importBackup(fileContent, passphrase.toCharArray())
            .onSuccess { _message.value = it.toString() }
            .onFailure { _message.value = "Import failed: ${it.message}" }
    }
}
