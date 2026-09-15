package com.vela.chat.data.a2a

import com.vela.chat.data.AppJson
import com.vela.chat.data.remote.dto.FunctionDto
import com.vela.chat.data.remote.dto.ToolDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns an A2A peer into an OpenAI tool-definition so a regular (non-A2A) profile's
 * model — a "brain" — can message remote agents as tools. Execution happens in-app
 * over [A2aClient] with the peer's own bearer token, so the remote gateway always
 * sees the peer's identity, and the tool result text flows back into the model's
 * context for relaying.
 */
object A2aPeerTool {

    /** OpenAI tool names must be `[a-zA-Z0-9_-]`; `message_alpha`, `message_bravo`, … */
    fun toolName(peerName: String): String =
        "message_" + peerName.lowercase().filter { it.isLetterOrDigit() }.take(32).ifBlank { "peer" }

    fun definition(peerName: String): ToolDto {
        val parameters = AppJson.parseToJsonElement(
            """
            {"type":"object","properties":{"text":{"type":"string",
            "description":"The message text to send to $peerName"}},"required":["text"]}
            """.trimIndent(),
        )
        return ToolDto(
            function = FunctionDto(
                name = toolName(peerName),
                description = "Send a message to $peerName — a separate AI agent reachable over A2A. " +
                    "Use this whenever the user asks you to contact, ask, tell, or notify $peerName, " +
                    "or when a task needs $peerName's help. Returns $peerName's reply text, " +
                    "or an error description you should relay to the user.",
                parameters = parameters,
            ),
        )
    }

    /** First string value under [key] in a tool-arguments JSON object; null on any mismatch. */
    fun extractArgument(argumentsJson: String, key: String): String? = runCatching {
        AppJson.parseToJsonElement(argumentsJson).jsonObject[key]?.jsonPrimitive?.content
    }.getOrNull()
}
