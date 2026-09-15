package com.vela.chat.ui.navigation

object Routes {
    const val CHAT = "chat"
    const val CONVERSATION_ID = "conversationId"
    const val CHAT_WITH_ARG = "chat?conversationId={conversationId}"

    fun chat(conversationId: String? = null): String =
        if (conversationId.isNullOrBlank()) "chat" else "chat?conversationId=$conversationId"

    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val PARAMETERS = "parameters"
    const val PROFILES = "profiles"

    const val PROFILE_ID = "profileId"
    const val PROFILE_EDIT_WITH_ARG = "profile_edit?profileId={profileId}"
    fun profileEdit(profileId: String? = null): String =
        if (profileId.isNullOrBlank()) "profile_edit" else "profile_edit?profileId=$profileId"

    const val PROMPTS = "prompts"
    const val WEB_SEARCH = "web_search"
    const val VOICE = "voice"

    // ---- Nova 2.0 routes ----
    const val TAILSCALE = "tailscale"
    const val SEARCH = "search"
    const val SECURITY = "security"
    const val ARCHIVE = "archive"
    const val MODELS = "models"
    const val PERSONAS = "personas"
}
