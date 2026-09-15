package com.vela.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.settings.AccentColor
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.data.settings.ThemeMode
import com.vela.chat.data.settings.ThemePreset
import com.vela.chat.domain.model.GenerationParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backing VM for the Settings, Appearance, and Parameters screens. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setAccent(accent: AccentColor) = viewModelScope.launch { repository.setAccent(accent) }
    fun setAmoled(enabled: Boolean) = viewModelScope.launch { repository.setAmoled(enabled) }
    
    fun setLightThemePreset(preset: ThemePreset) = viewModelScope.launch { repository.setLightThemePreset(preset) }
    fun setDarkThemePreset(preset: ThemePreset) = viewModelScope.launch { repository.setDarkThemePreset(preset) }
    fun setCustomAccentColor(colorHex: String?) = viewModelScope.launch { repository.setCustomAccentColor(colorHex) }
    fun setBubbleStyle(style: BubbleStyle) = viewModelScope.launch { repository.setBubbleStyle(style) }
    fun setFontSizeScale(scale: Float) = viewModelScope.launch { repository.setFontSizeScale(scale) }
    fun setChatDensity(density: ChatDensity) = viewModelScope.launch { repository.setChatDensity(density) }
    fun setCustomWallpaperPath(path: String?) = viewModelScope.launch { repository.setCustomWallpaperPath(path) }

    fun setStream(enabled: Boolean) = viewModelScope.launch { repository.setStream(enabled) }
    fun setSendOnEnter(enabled: Boolean) = viewModelScope.launch { repository.setSendOnEnter(enabled) }
    fun setRenderMarkdown(enabled: Boolean) = viewModelScope.launch { repository.setRenderMarkdown(enabled) }
    fun setShowTokenUsage(enabled: Boolean) = viewModelScope.launch { repository.setShowTokenUsage(enabled) }
    fun setShowTimestamps(enabled: Boolean) = viewModelScope.launch { repository.setShowTimestamps(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(enabled) }
    fun setTts(enabled: Boolean) = viewModelScope.launch { repository.setTts(enabled) }
    fun setTtsLanguage(tag: String) = viewModelScope.launch { repository.setTtsLanguage(tag) }
    fun setTtsRate(rate: Float) = viewModelScope.launch { repository.setTtsRate(rate) }
    fun setTtsPitch(pitch: Float) = viewModelScope.launch { repository.setTtsPitch(pitch) }
    fun setWebSearchEnabled(enabled: Boolean) = viewModelScope.launch { repository.setWebSearchEnabled(enabled) }
    fun setSearxngUrl(url: String) = viewModelScope.launch { repository.setSearxngUrl(url) }
    fun setWebSearchMaxResults(n: Int) = viewModelScope.launch { repository.setWebSearchMaxResults(n) }

    // ---- Nova 2.0 ----
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }

    fun clearAllConversations() = viewModelScope.launch { conversationRepository.deleteAll() }
    fun setDefaultSystemPrompt(prompt: String) = viewModelScope.launch { repository.setDefaultSystemPrompt(prompt) }
    fun setDefaultParams(params: GenerationParams) = viewModelScope.launch { repository.setDefaultParams(params) }
}
