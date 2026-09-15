package com.vela.chat.ui.chat

import com.vela.chat.data.remote.dto.Usage
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.domain.model.A2aPeer
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.domain.model.TokenStats
import com.vela.chat.util.VelaConstants

/** Transient overlay shown while a reply is being generated/streamed. */
data class StreamingState(
    val messageId: String,
    val content: String = "",
    val reasoning: String = "",
    /** Wall-clock ms timestamp of the first streamed token — feeds the live tok/s estimate. */
    val startedAtMs: Long = 0L,
)

/** Derived connection-quality bucket for the chat header. */
enum class ConnectionQuality(val label: String) {
    GOOD("Good"),
    OK("OK"),
    SLOW("Slow"),
    UNKNOWN("—"),
}

/**
 * Immutable UI state for the chat screen. All mutations happen through
 * [ChatViewModel] via `copy`, so instances are safe to hoist and diff.
 */
data class ChatUiState(
    val conversationId: String? = null,
    val title: String = "New chat",
    /** Windowed view of the conversation (most recent [messagesVisibleLimit] messages). */
    val messages: List<Message> = emptyList(),
    /** Window size the current [messages] view was loaded with; grows via "load older". */
    val messagesVisibleLimit: Int = VelaConstants.CHAT_WINDOW_SIZE,
    val streaming: StreamingState? = null,
    val input: String = "",
    val attachments: List<Attachment> = emptyList(),
    val isProcessingAttachment: Boolean = false,
    val model: String? = null,
    val availableModels: List<String> = emptyList(),
    /** Subset of [availableModels] the user added by hand (removable in the model bar). */
    val customModels: Set<String> = emptySet(),
    val profileName: String? = null,
    val systemPrompt: String = "",
    val params: GenerationParams = GenerationParams.Default,
    val lastUsage: Usage? = null,
    val error: String? = null,
    val renderMarkdown: Boolean = true,
    val showTokenUsage: Boolean = true,
    val sendOnEnter: Boolean = false,
    val ttsEnabled: Boolean = false,
    val ttsLanguage: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val showTimestamps: Boolean = false,
    val keepScreenOn: Boolean = false,
    val modelLoadError: String? = null,
    val customWallpaperPath: String? = null,
    // ---- Nova 2.0 ----
    /** Aggregated conversation token statistics, refreshed after each completed generation. */
    val tokenStats: TokenStats? = null,
    /** Live completion rate of the in-flight stream (approximate), tok/s. */
    val liveTokensPerSecond: Float? = null,
    /** Latency of the last /models probe, used to derive [connectionQuality]. */
    val probeLatencyMs: Long? = null,
    /** True while the /models probe is in flight (drives the skeleton loading state). */
    val isProbingModels: Boolean = false,
    /** True when the tailnet VPN session is detected (header chip). */
    val tailnetConnected: Boolean = false,
    /** Prompt library entries for the composer sheet. */
    val savedPrompts: List<SavedPrompt> = emptyList(),
    // ---- Agent personalities ----
    /** All available personalities (settings → Agent personalities manages them). */
    val personas: List<AgentPersona> = emptyList(),
    /** Persona currently applied to this chat (or pre-selected for a new chat). */
    val activePersonaId: String? = null,
    /** All API profiles (server/agent picker in chat settings). */
    val profiles: List<ApiProfile> = emptyList(),
    /** Profile this chat talks to (or pre-selected for a new chat). */
    val activeProfileId: String? = null,
    /** A2A peer table of the active profile (empty for non-A2A / peer-less profiles). */
    val a2aPeers: List<A2aPeer> = emptyList(),
    /** Peer id this chat currently talks through (resolved: pending → saved → first enabled). */
    val activeA2aPeerId: String? = null,
    /** Multi-select mode state (topbar action → checkbox selection of messages). */
    val selectionMode: Boolean = false,
    val selectedMessageIds: Set<String> = emptySet(),
    /** True while a PDF export is being written (button spinner). */
    val isExportingPdf: Boolean = false,
) {
    val isGenerating: Boolean get() = streaming != null
    val canSend: Boolean get() = !isGenerating && (input.isNotBlank() || attachments.isNotEmpty())
    val isEmpty: Boolean get() = messages.isEmpty() && streaming == null

    /** "Load older" is offered when the window is full — there may be more history above. */
    val hasMoreMessages: Boolean get() = messages.size >= messagesVisibleLimit

    /** tok/s of the most recent assistant message that carries usage stats. */
    val lastTokensPerSecond: Float?
        get() = messages.lastOrNull { it.role == Role.ASSISTANT && it.completionTokens > 0 }
            ?.tokensPerSecond

    /** Header rate: live stream estimate while generating, last finished rate otherwise. */
    val tokensPerSecond: Float?
        get() = if (isGenerating) liveTokensPerSecond ?: lastTokensPerSecond else lastTokensPerSecond

    /** Connection quality bucket derived from the last probe latency. */
    val connectionQuality: ConnectionQuality
        get() = when (val latency = probeLatencyMs) {
            null -> ConnectionQuality.UNKNOWN
            in 0L until PROBE_GOOD_MS -> ConnectionQuality.GOOD
            in PROBE_GOOD_MS until PROBE_OK_MS -> ConnectionQuality.OK
            else -> ConnectionQuality.SLOW
        }

    val isSelectionMode: Boolean get() = selectionMode && selectedMessageIds.isNotEmpty()

    companion object {
        /** Probe latency below this is "Good" (LAN/tailnet territory). */
        const val PROBE_GOOD_MS = 150L

        /** Probe latency below this is still "OK"; above it, "Slow". */
        const val PROBE_OK_MS = 400L
    }
}
