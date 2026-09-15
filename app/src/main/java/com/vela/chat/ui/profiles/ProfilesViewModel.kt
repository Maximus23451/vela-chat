package com.vela.chat.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.domain.model.ApiProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ApiProfileRepository,
) : ViewModel() {

    val profiles = repository.profiles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun hasKey(id: String): Boolean = repository.hasApiKey(id)

    fun setDefault(profile: ApiProfile) = viewModelScope.launch { repository.setDefault(profile.id) }

    fun delete(profile: ApiProfile) = viewModelScope.launch { repository.deleteProfile(profile.id) }

    suspend fun export(includeKeys: Boolean, passphrase: CharArray? = null): String =
        repository.exportProfiles(includeKeys, passphrase)

    fun isEncryptedExport(json: String): Boolean = repository.isEncryptedExport(json)

    fun import(json: String, passphrase: CharArray? = null) = viewModelScope.launch {
        repository.importProfiles(json, passphrase)
            .onSuccess { count -> _message.value = "Imported $count profile(s)" }
            .onFailure { _message.value = "Import failed: ${it.message}" }
    }

    fun consumeMessage() { _message.value = null }
}
