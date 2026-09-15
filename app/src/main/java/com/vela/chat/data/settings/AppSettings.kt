package com.vela.chat.data.settings

import com.vela.chat.domain.model.GenerationParams

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemePreset {
    MATERIAL_YOU,
    MIDNIGHT_BLUE,
    OPENWEBUI,
    CHATGPT,
    CLAUDE,
    NORD,
    DRACULA,
    CATPPUCCIN,
    SOLARIZED_DARK,
    HIGH_CONTRAST,
    GRUVBOX,
    TOKYO_NIGHT,
    ROSE_PINE,
    ONE_DARK,
    MONOKAI
}

enum class BubbleStyle {
    ROUNDED,
    SEMI_ROUNDED,
    SHARP
}

/** How the app locks itself when backgrounded (Privacy section). */
enum class AppLockMode { NONE, PIN, BIOMETRIC }

enum class ChatDensity {
    COMPACT,
    COZY,
    ROOMY
}

/** Named accent seeds offered when dynamic color is unavailable or disabled. */
enum class AccentColor(val seed: Long) {
    VIOLET(0xFF6750A4),
    BLUE(0xFF1B6CF3),
    TEAL(0xFF00897B),
    GREEN(0xFF2E7D32),
    AMBER(0xFFF59E0B),
    ROSE(0xFFE11D48),
}

/** Immutable snapshot of all user preferences. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: AccentColor = AccentColor.VIOLET,
    val amoledBlack: Boolean = false,
    val lightThemePreset: ThemePreset = ThemePreset.MATERIAL_YOU,
    val darkThemePreset: ThemePreset = ThemePreset.MATERIAL_YOU,
    val customAccentColor: String? = null,
    val bubbleStyle: BubbleStyle = BubbleStyle.ROUNDED,
    val fontSizeScale: Float = 1.0f,
    val chatDensity: ChatDensity = ChatDensity.COZY,
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
    val searchProvider: SearchProvider = SearchProvider.SEARXNG,
    val searxngUrl: String = "",
    val googleCx: String = "",
    val webSearchMaxResults: Int = 5,
    val defaultSystemPrompt: String = "",
    val defaultParams: GenerationParams = GenerationParams.Default,
    val onboarded: Boolean = false,
    // ---- Nova 2.0 ----
    val hapticsEnabled: Boolean = true,
    val appLockMode: AppLockMode = AppLockMode.NONE,
    val autoLockMinutes: Int = 5,
    /** FLAG_SECURE: blur the app in recents and block screenshots. */
    val secureScreens: Boolean = false,
    /** Automatically probe persisted tailnet peers for AI servers. */
    val tailscaleDiscovery: Boolean = true,
)
