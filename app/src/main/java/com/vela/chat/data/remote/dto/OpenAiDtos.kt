package com.vela.chat.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenAI-compatible chat completion request. Only non-null fields are sent
 * (explicitNulls = false in [com.vela.chat.data.AppJson]), so omitting an
 * extension like [top_k] keeps strict servers such as OpenAI happy while still
 * supporting LM Studio's superset.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val max_tokens: Int? = null,
    val presence_penalty: Float? = null,
    val frequency_penalty: Float? = null,
    val stream: Boolean = false,
    val stream_options: StreamOptions? = null,
    /** Function/tool definitions (OpenAI tool-calling) — used for web search. */
    val tools: List<ToolDto>? = null,
    val tool_choice: String? = null,
)

@Serializable
data class StreamOptions(val include_usage: Boolean = true)

// ---- Tool calling ----

@Serializable
data class ToolDto(val function: FunctionDto, val type: String = "function")

@Serializable
data class FunctionDto(
    val name: String,
    val description: String,
    val parameters: JsonElement,
)

@Serializable
data class ToolCallDto(
    val id: String? = null,
    val type: String? = "function",
    val function: ToolCallFunctionDto? = null,
    val index: Int? = null,
)

@Serializable
data class ToolCallFunctionDto(
    val name: String? = null,
    val arguments: String? = null,
)

/**
 * A request message. [content] is a [JsonElement] so it can be either a plain
 * string or an array of multimodal parts (text + image_url), exactly as the
 * OpenAI vision schema requires.
 */
@Serializable
data class RequestMessage(
    val role: String,
    val content: JsonElement? = null,
    /** Present on assistant messages that requested tool calls. */
    val tool_calls: List<ToolCallDto>? = null,
    /** Present on `role = "tool"` result messages, linking back to the call. */
    val tool_call_id: String? = null,
)

@Serializable
data class TextContentPart(val text: String, val type: String = "text")

@Serializable
data class ImageUrl(val url: String)

@Serializable
data class ImageContentPart(val image_url: ImageUrl, val type: String = "image_url")

// ---- Responses ----

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    val delta: Delta? = null,
    val finish_reason: String? = null,
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCallDto>? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCallDto>? = null,
)

@Serializable
data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0,
)

// ---- Models endpoint ----

@Serializable
data class ModelsResponse(val data: List<ModelDto> = emptyList())

@Serializable
data class ModelDto(
    val id: String,
    val owned_by: String? = null,
)

// ---- Error envelope ----

@Serializable
data class ApiErrorResponse(val error: ApiError? = null)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
