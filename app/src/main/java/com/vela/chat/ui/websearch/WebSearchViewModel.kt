package com.vela.chat.ui.websearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.SearchProvider
import com.vela.chat.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebSearchViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val secureStore: SecureStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { repository.setWebSearchEnabled(enabled) }
    fun setProvider(provider: SearchProvider) = viewModelScope.launch { repository.setSearchProvider(provider) }
    fun setSearxngUrl(url: String) = viewModelScope.launch { repository.setSearxngUrl(url) }
    fun setGoogleCx(cx: String) = viewModelScope.launch { repository.setGoogleCx(cx) }
    fun setMaxResults(n: Int) = viewModelScope.launch { repository.setWebSearchMaxResults(n) }

    /** Per-provider API keys live in the encrypted secure store. */
    fun getApiKey(provider: SearchProvider): String = secureStore.getApiKey(provider.secureKeyId).orEmpty()
    fun setApiKey(provider: SearchProvider, key: String) = secureStore.saveApiKey(provider.secureKeyId, key)
}
