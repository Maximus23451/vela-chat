package com.vela.chat.data.remote

import com.vela.chat.data.remote.dto.ChatCompletionRequest
import com.vela.chat.data.remote.dto.ImageContentPart
import com.vela.chat.data.remote.dto.ImageUrl
import com.vela.chat.data.remote.dto.RequestMessage
import com.vela.chat.data.remote.dto.StreamOptions
import com.vela.chat.data.remote.dto.TextContentPart
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.AttachmentType
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.domain.model.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import com.vela.chat.data.AppJson

/** Resolved everything-needed-to-make-one-API-call bundle. */
data class ChatTarget(
    val chatUrl: String,
    val modelsUrl: String,
    val authHeader: String?,
    val model: String,
    val providerType: ProviderType,
    /**
     * Display name of the A2A peer that answers (peer-table profiles); null when
     * the profile itself is the endpoint. Stamped onto assistant messages so
     * multi-peer agents are attributed per bubble.
     */
    val peerName: String? = null,
)

/**
 * Builds OpenAI-compatible request payloads from domain messages, handling
 * vision content parts, inlined document text, context-window trimming, and
 * provider-specific sampler fields.
 */
object RequestFactory {

    fun build(
        target: ChatTarget,
        history: List<Message>,
        params: GenerationParams,
        systemPrompt: String?,
        stream: Boolean,
    ): ChatCompletionRequest {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) {
                add(RequestMessage(Role.SYSTEM.wire, JsonPrimitive(systemPrompt)))
            }
            trimToWindow(history, params.contextWindowMessages).forEach { msg ->
                add(toRequestMessage(msg))
            }
        }

        // top_k is a llama.cpp/LM Studio extension that strict OpenAI-compatible
        // servers (OpenAI, Moonshot/Kimi, Zhipu/z.ai…) reject — only local servers get it.
        val includeTopK = target.providerType.supportsTopK

        return ChatCompletionRequest(
            model = target.model,
            messages = messages,
            temperature = params.temperature,
            top_p = params.topP,
            top_k = if (includeTopK) params.topK else null,
            max_tokens = if (params.limitMaxTokens) params.maxTokens else null,
            presence_penalty = params.presencePenalty.takeIf { it != 0f },
            frequency_penalty = params.frequencyPenalty.takeIf { it != 0f },
            stream = stream,
            stream_options = if (stream) StreamOptions(include_usage = true) else null,
        )
    }

    private fun trimToWindow(history: List<Message>, window: Int): List<Message> {
        if (window <= 0 || history.size <= window) return history
        return history.takeLast(window)
    }

    private fun toRequestMessage(message: Message): RequestMessage {
        val documentContext = message.attachments
            .filter { it.type == AttachmentType.PDF || it.type == AttachmentType.TEXT }
            .mapNotNull { it.extractedText?.let { text -> renderDocument(it.name, text) } }

        val baseText = buildString {
            documentContext.forEach { append(it).append("\n\n") }
            append(message.content)
        }.trim()

        val images = message.attachments.filter { it.type == AttachmentType.IMAGE && it.dataUrl != null }

        val content: JsonElement = if (images.isEmpty()) {
            JsonPrimitive(baseText)
        } else {
            val parts = buildList {
                if (baseText.isNotEmpty()) {
                    add(AppJson.encodeToJsonElement(TextContentPart(text = baseText)))
                }
                images.forEach { img ->
                    add(AppJson.encodeToJsonElement(ImageContentPart(image_url = ImageUrl(img.dataUrl!!))))
                }
            }
            JsonArray(parts)
        }

        return RequestMessage(message.role.wire, content)
    }

    private fun renderDocument(name: String, text: String): String =
        "[Attached document: $name]\n\"\"\"\n$text\n\"\"\""

    fun bearer(apiKey: String?): String? = apiKey?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    /** OpenAI tool definition for the SearXNG-backed web search. */
    fun webSearchTool(): com.vela.chat.data.remote.dto.ToolDto {
        val parameters = AppJson.parseToJsonElement(
            """{"type":"object","properties":{"query":{"type":"string","description":"The search query"}},"required":["query"]}""",
        )
        return com.vela.chat.data.remote.dto.ToolDto(
            function = com.vela.chat.data.remote.dto.FunctionDto(
                name = "web_search",
                description = "Search the web for current, factual, or up-to-date information. " +
                    "Use this whenever the user asks about facts, events, products, or anything you are unsure about.",
                parameters = parameters,
            ),
        )
    }
}
