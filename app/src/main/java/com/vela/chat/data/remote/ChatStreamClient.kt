package com.vela.chat.data.remote

import com.vela.chat.data.AppJson
import com.vela.chat.data.remote.dto.ChatCompletionRequest
import com.vela.chat.data.remote.dto.ChatCompletionResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams `chat/completions` responses over Server-Sent Events using OkHttp
 * directly (Retrofit doesn't expose the raw byte stream cleanly). Each `data:`
 * line is parsed into a [StreamEvent]; the flow completes on `[DONE]` and is
 * cancellable — cancelling the collector cancels the underlying HTTP call.
 */
@Singleton
class ChatStreamClient @Inject constructor(
    private val client: OkHttpClient,
) {
    fun stream(
        url: String,
        authorization: String?,
        request: ChatCompletionRequest,
    ): Flow<StreamEvent> = callbackFlow {
        val streamingRequest = request.copy(stream = true)
        val bodyJson = AppJson.encodeToString(streamingRequest)
        val httpRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                header("Accept", "text/event-stream")
                if (!authorization.isNullOrBlank()) header("Authorization", authorization)
            }
            .build()

        val call = client.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamEvent.Failed(e.message ?: "Network error"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val raw = runCatching { response.body?.string() }.getOrNull()
                        trySend(StreamEvent.Failed(describeHttpError(response.code, raw)))
                        close()
                        return
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        trySend(StreamEvent.Failed("Empty response body"))
                        close()
                        return
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val payload = when {
                                line.startsWith("data:") -> line.substring(5).trim()
                                else -> continue // ignore comments / event: lines
                            }
                            if (payload == "[DONE]") {
                                trySend(StreamEvent.Completed(null))
                                close()
                                return
                            }
                            emitChunk(payload)
                        }
                        trySend(StreamEvent.Completed(null))
                    } catch (e: IOException) {
                        trySend(StreamEvent.Failed(e.message ?: "Stream interrupted"))
                    } finally {
                        close()
                    }
                }
            }

            private fun emitChunk(payload: String) {
                val chunk = runCatching {
                    AppJson.decodeFromString<ChatCompletionResponse>(payload)
                }.getOrNull() ?: return
                val usage = chunk.usage
                if (usage != null) {
                    trySend(StreamEvent.Completed(usage))
                }
                val delta = chunk.choices.firstOrNull()?.delta ?: return
                delta.reasoning_content?.takeIf { it.isNotEmpty() }?.let { trySend(StreamEvent.Reasoning(it)) }
                delta.content?.takeIf { it.isNotEmpty() }?.let { trySend(StreamEvent.Token(it)) }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Map an HTTP error code + body into a concise, user-facing message. */
internal fun describeHttpError(code: Int, rawBody: String?): String {
    val parsed = rawBody?.let {
        runCatching {
            AppJson.decodeFromString<com.vela.chat.data.remote.dto.ApiErrorResponse>(it).error?.message
        }.getOrNull()
    }
    val base = when (code) {
        401, 403 -> "Authentication failed — check your API key."
        404 -> "Endpoint not found — verify the base URL and that a model is loaded."
        429 -> "Rate limited by the server."
        in 500..599 -> "Server error ($code)."
        else -> "Request failed ($code)."
    }
    return if (parsed.isNullOrBlank()) base else "$base $parsed"
}
