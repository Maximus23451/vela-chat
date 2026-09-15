package com.vela.chat.data.a2a

import com.vela.chat.data.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Lenient A2A (Agent2Agent) protocol types. The protocol exists in several
 * dialects (0.2.x/0.3.x JSON-RPC with camelCase enums, 1.0 with SCREAMING_SNAKE
 * enums and wrapper objects) and agents in the wild mix them, so everything here
 * parses defensively off [JsonObject] instead of strict DTOs. Hermes Agent
 * (Nous Research) speaks v1.0 JSON-RPC at `POST /` with the card at
 * `/.well-known/agent-card.json` (legacy `agent.json` also served).
 */

/** Discovery document published by an A2A server. */
data class A2aAgentCard(
    val name: String,
    val description: String,
    val url: String?,
    val protocolVersion: String?,
    val streamingSupported: Boolean,
    val skills: List<String>,
) {
    /** Agents that predate 1.0 use lowercase roles/states; 1.0 uses the ROLE- and TASK_STATE- prefixed enums. */
    val isV1Dialect: Boolean
        get() = (protocolVersion?.toDoubleOrNull() ?: 0.3) >= 1.0
}

/** Terminal-ish states we map to UI outcomes. */
enum class A2aTaskState { SUBMITTED, WORKING, INPUT_REQUIRED, COMPLETED, FAILED, CANCELED, REJECTED, AUTH_REQUIRED, UNKNOWN }

val A2aTaskState.isTerminal: Boolean
    get() = this == A2aTaskState.COMPLETED || this == A2aTaskState.FAILED ||
        this == A2aTaskState.CANCELED || this == A2aTaskState.REJECTED

/** Everything useful extracted from one A2A response (task or direct message). */
data class A2aResult(
    val taskId: String?,
    val contextId: String?,
    val state: A2aTaskState,
    /** Concatenated text of all agent-authored parts found (artifacts + history + status). */
    val agentText: String,
)

object A2aJson {

    /** Parses an Agent Card from either well-known path, tolerating missing fields. */
    fun parseAgentCard(body: String): A2aAgentCard? = runCatching {
        val obj = AppJson.parseToJsonElement(body).jsonObject
        val name = obj.string("name") ?: return@runCatching null
        val skills = (obj["skills"] as? JsonArray)
            ?.mapNotNull { skill -> (skill as? JsonObject)?.string("name") }
            .orEmpty()
        A2aAgentCard(
            name = name,
            description = obj.string("description").orEmpty(),
            url = obj.string("url"),
            protocolVersion = obj.string("protocolVersion"),
            streamingSupported = (obj["capabilities"] as? JsonObject)?.get("streaming") == JsonPrimitive(true),
            skills = skills,
        )
    }.getOrNull()

    /**
     * Parses a `message/send` outcome. Accepts a bare result object (task or
     * message), a `{"task": …}` wrapper, or a full JSON-RPC envelope with `result`
     * (or an `error` object, surfaced as a failed state).
     */
    fun parseResult(body: String): A2aResult {
        val root = runCatching { AppJson.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return A2aResult(null, null, A2aTaskState.FAILED, "")
        if (root["error"] is JsonObject) {
            val message = (root["error"] as JsonObject).string("message")
            return A2aResult(null, null, A2aTaskState.FAILED, message ?: "Agent returned an error")
        }
        val result = (root["result"] as? JsonObject) ?: root

        // Stream events wrap task/message in update envelopes — unwrap them first
        // (verbatim Hermes gateway shapes):
        //   {"result": {"artifactUpdate": {taskId, contextId, artifact:{parts:[{text}]}}}}
        //   {"result": {"statusUpdate":  {taskId, contextId, status:{state[, message]}}}}
        (result["artifactUpdate"] as? JsonObject)?.let { update ->
            val artifact = update["artifact"] as? JsonObject
            return A2aResult(
                taskId = update.string("taskId"),
                contextId = update.string("contextId"),
                state = A2aTaskState.WORKING,
                agentText = textPartsFlat(artifact?.get("parts") as? JsonArray).joinToString("\n\n").trim(),
            )
        }
        (result["statusUpdate"] as? JsonObject)?.let { update ->
            val status = update["status"] as? JsonObject
            val messageText = textPartsFlat((status?.get("message") as? JsonObject)?.let { JsonArray(listOf(it)) })
            return A2aResult(
                taskId = update.string("taskId"),
                contextId = update.string("contextId"),
                state = parseState(status?.string("state")),
                agentText = messageText.joinToString("\n\n").trim(),
            )
        }

        val task = (result["task"] as? JsonObject)
            ?: if (result.string("kind") == "task" || result["status"] is JsonObject) result else null
        if (task != null) {
            val status = task["status"] as? JsonObject
            val state = parseState(status?.string("state"))
            val texts = buildList {
                addAll(textPartsOf(task["artifacts"] as? JsonArray))
                addAll(textPartsOf((status?.get("message") as? JsonObject)?.let { JsonArray(listOf(it)) }))
                addAll(
                    textPartsOf(
                        (task["history"] as? JsonArray)
                            ?.filter { msg -> (msg as? JsonObject)?.string("role")?.equals("agent", true) == true }
                            ?.let { JsonArray(it) },
                    ),
                )
            }
            return A2aResult(
                taskId = task.string("id"),
                contextId = task.string("contextId"),
                state = state,
                agentText = texts.joinToString("\n\n").trim(),
            )
        }
        // Direct message reply (kind = message): concatenate its parts.
        val text = textPartsFlat(result["parts"] as? JsonArray).joinToString("\n\n").trim()
        return A2aResult(
            taskId = result.string("taskId"),
            contextId = result.string("contextId"),
            state = if (text.isNotEmpty()) A2aTaskState.COMPLETED else A2aTaskState.UNKNOWN,
            agentText = text,
        )
    }

    /** Parses one SSE `data:` payload from `message/stream` (statusUpdate/artifactUpdate/task). */
    fun parseStreamEvent(body: String): A2aResult {
        val direct = parseResult(body)
        if (direct.agentText.isNotEmpty() || direct.state != A2aTaskState.UNKNOWN) return direct
        // Nothing recognizable — treat as working tick.
        return A2aResult(null, null, A2aTaskState.WORKING, "")
    }

    /** True when the payload is a JSON-RPC error envelope (auth, bad request, …). */
    fun isErrorMessage(body: String): Boolean = runCatching {
        AppJson.parseToJsonElement(body).jsonObject["error"] is JsonObject
    }.getOrDefault(false)

    /** Builds the JSON-RPC `message/send` request body for the given dialect. */
    fun buildSendMessage(text: String, messageId: String, contextId: String?, v1Dialect: Boolean): String {
        val role = if (v1Dialect) "ROLE_USER" else "user"
        val part = buildMap {
            put("kind", JsonPrimitive("text"))
            put("text", JsonPrimitive(text))
        }
        val message = buildMap {
            put("role", JsonPrimitive(role))
            put("parts", JsonArray(listOf(JsonObject(part))))
            put("kind", JsonPrimitive("message"))
            put("messageId", JsonPrimitive(messageId))
            if (contextId != null) put("contextId", JsonPrimitive(contextId))
        }
        val params = buildMap {
            put("message", JsonObject(message))
        }
        return AppJson.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                buildMap {
                    put("jsonrpc", JsonPrimitive("2.0"))
                    put("id", JsonPrimitive(messageId))
                    put("method", JsonPrimitive("message/send"))
                    put("params", JsonObject(params))
                },
            ),
        )
    }

    /** Builds the JSON-RPC `message/stream` request body (same message shape). */
    fun buildStreamMessage(text: String, messageId: String, contextId: String?, v1Dialect: Boolean): String =
        buildSendMessage(text, messageId, contextId, v1Dialect)
            .replace("\"message/send\"", "\"message/stream\"")

    /** Builds the JSON-RPC `tasks/get` poll body. */
    fun buildTasksGet(taskId: String): String = AppJson.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "id" to JsonPrimitive("poll-$taskId"),
                "method" to JsonPrimitive("tasks/get"),
                "params" to JsonObject(mapOf("id" to JsonPrimitive(taskId))),
            ),
        ),
    )

    private fun parseState(raw: String?): A2aTaskState = when (raw?.uppercase()?.removePrefix("TASK_STATE_")) {
        "SUBMITTED" -> A2aTaskState.SUBMITTED
        "WORKING" -> A2aTaskState.WORKING
        "INPUT_REQUIRED" -> A2aTaskState.INPUT_REQUIRED
        "COMPLETED" -> A2aTaskState.COMPLETED
        "FAILED" -> A2aTaskState.FAILED
        "CANCELED" -> A2aTaskState.CANCELED
        "REJECTED" -> A2aTaskState.REJECTED
        "AUTH_REQUIRED" -> A2aTaskState.AUTH_REQUIRED
        else -> A2aTaskState.UNKNOWN
    }

    /** Text of every `{"text": …}` part in an artifacts array. */
    private fun textPartsOf(artifacts: JsonArray?): List<String> =
        artifacts.orEmpty().mapNotNull { artifact ->
            (artifact as? JsonObject)?.get("parts") as? JsonArray
        }.flatMap { parts -> textPartsFlat(parts) }

    /** Text of every text part in a flat parts array (both part shapes). */
    private fun textPartsFlat(parts: JsonArray?): List<String> = parts?.mapNotNull { part ->
        when (part) {
            is JsonObject -> when {
                part["text"] is JsonPrimitive -> part.string("text")
                // {"textPart": {"text": …}} legacy shape
                part["textPart"] is JsonObject -> (part["textPart"] as JsonObject).string("text")
                else -> null
            }
            is JsonPrimitive -> part.contentOrNull
            else -> null
        }
    } ?: emptyList()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
