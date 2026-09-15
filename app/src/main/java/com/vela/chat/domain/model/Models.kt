package com.vela.chat.domain.model

import kotlinx.serialization.Serializable

/** Role of a single chat message in the OpenAI chat schema. */
enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL;

    val wire: String get() = name.lowercase()

    companion object {
        fun from(value: String): Role = entries.firstOrNull { it.wire == value.lowercase() } ?: USER
    }
}

/** A locally attached file referenced by a message. */
@Serializable
data class Attachment(
    val id: String,
    val type: AttachmentType,
    val name: String,
    /** Base64 data URL for images (sent as image_url to vision models). */
    val dataUrl: String? = null,
    /** Extracted text for documents (PDF/text), inlined into the prompt. */
    val extractedText: String? = null,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0,
)

@Serializable
enum class AttachmentType { IMAGE, PDF, TEXT, OTHER }

/** A chat message. */
data class Message(
    val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    val reasoning: String? = null,
    val model: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Usage stats captured from the server's final streaming chunk (0 when unknown). */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val generationTimeMs: Long = 0,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
) {
    /** Completion tokens per second for this message; null when stats are missing. */
    val tokensPerSecond: Float?
        get() = if (completionTokens > 0 && generationTimeMs > 0) {
            completionTokens / (generationTimeMs / 1000f)
        } else null
}

/** Aggregated token statistics for a conversation (computed by SQL SUM, not stored). */
data class TokenStats(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val generationTimeMs: Long,
    val avgTokensPerSecond: Float,
)

/** Generation parameters mirroring the OpenAI / LM Studio sampler knobs. */
@Serializable
data class GenerationParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val presencePenalty: Float = 0f,
    val frequencyPenalty: Float = 0f,
    /** When false, maxTokens is omitted so the server decides the limit. */
    val limitMaxTokens: Boolean = true,
    /** Number of most recent messages to send (0 = entire history). */
    val contextWindowMessages: Int = 0,
) {
    companion object {
        val Default = GenerationParams()
    }
}

/** A conversation / chat thread. */
data class Conversation(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val profileId: String? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val params: GenerationParams? = null,
    /** Agent personality bound to this conversation; null = use the default persona. */
    val personaId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A folder grouping conversations in the drawer. [color] is an ARGB int; 0 = theme default. */
data class Folder(
    val id: String,
    val name: String,
    val position: Int = 0,
    val color: Int = 0,
    val icon: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Supported provider families. LM Studio is the first-class target. */
enum class ProviderType(val label: String, val defaultBaseUrl: String, val requiresAuth: Boolean) {
    LM_STUDIO("LM Studio", "http://localhost:1234/v1", false),
    OLLAMA("Ollama", "http://localhost:11434/v1", false),
    A2A_AGENT("A2A Agent", "http://localhost:9900", false),
    OPENAI("OpenAI", "https://api.openai.com/v1", true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", true),
    MOONSHOT("Moonshot (Kimi)", "https://api.moonshot.ai/v1", true),
    ZHIPU("Zhipu (z.ai)", "https://api.z.ai/api/paas/v4", true),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", true),
    QWEN("Qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", true),
    CUSTOM("Custom", "http://localhost:1234/v1", false);

    /**
     * `top_k` is a llama.cpp / LM Studio sampler extension that is NOT part of the
     * OpenAI schema. Lenient cloud servers (e.g. DeepSeek) silently ignore it, but
     * stricter ones (Moonshot/Kimi, Zhipu/z.ai, OpenAI) reject the whole request
     * with a 400. So only send it to local servers that actually support it.
     */
    val supportsTopK: Boolean get() = this == LM_STUDIO || this == OLLAMA || this == CUSTOM
}

/**
 * Connection profile. The API key is never stored on this object — it lives in
 * the encrypted keystore-backed store, looked up by [id].
 */
data class ApiProfile(
    val id: String,
    val name: String,
    val providerType: ProviderType = ProviderType.LM_STUDIO,
    val baseUrl: String = ProviderType.LM_STUDIO.defaultBaseUrl,
    val model: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Normalised base URL ending in a single slash, ready for path concatenation. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

/**
 * A named gateway entry under an A2A agent profile — one profile can reach
 * several agent gateways (each with its own URL). Each peer carries its own
 * bearer token (the token lives in SecureStore, never here): a token IS
 * the identity on a gateway, so peers must never share one. Persisted as JSON in
 * DataStore per profile — no Room schema involved.
 */
@Serializable
data class A2aPeer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
) {
    /** Base URL ending in a single slash, ready for path concatenation. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    companion object {
        /**
         * Pure peer resolution for a conversation: the persisted choice while it is
         * still enabled, else the first enabled peer, else null — null meaning "no
         * peer table in play, use the profile's own URL and key" (legacy behavior).
         */
        fun resolve(peers: List<A2aPeer>, choiceId: String?): A2aPeer? =
            choiceId?.let { id -> peers.firstOrNull { it.id == id && it.enabled } }
                ?: peers.firstOrNull { it.enabled }
    }
}

/** A reusable saved prompt for the prompt library / quick prompts. */
data class SavedPrompt(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "General",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A named bundle of params + system prompt the user can apply to a chat. */
data class Preset(
    val id: String,
    val name: String,
    val systemPrompt: String? = null,
    val params: GenerationParams = GenerationParams.Default,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * An agent personality selectable before/ during chatting: a system prompt plus an
 * optional temperature nudge. Exactly one persona is the default (applied to new
 * chats and to conversations without an explicit [Conversation.personaId]).
 */
data class AgentPersona(
    val id: String,
    val name: String,
    val description: String = "",
    /** Emoji glyph shown in pickers; a fixed palette is offered by the editor. */
    val emoji: String = "🤖",
    val systemPrompt: String,
    /** null = inherit the global/default sampler temperature. */
    val temperature: Float? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A model advertised by the server's /models endpoint. */
data class ModelInfo(
    val id: String,
    val ownedBy: String? = null,
)

/**
 * Enriched model metadata cached per profile in the model dashboard. Heuristic fields
 * (quantization, parameter count) are parsed from the model id where servers don't report them.
 */
data class ModelCatalogEntry(
    val id: String,
    val displayName: String? = null,
    val contextLength: Int = 0,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val family: String? = null,
    val sizeBytes: Long = 0,
    val latencyMs: Long = 0,
)
