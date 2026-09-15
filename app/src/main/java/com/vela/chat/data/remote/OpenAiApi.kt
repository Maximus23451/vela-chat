package com.vela.chat.data.remote

import com.vela.chat.data.remote.dto.ChatCompletionRequest
import com.vela.chat.data.remote.dto.ChatCompletionResponse
import com.vela.chat.data.remote.dto.ModelsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface for the non-streaming OpenAI-compatible endpoints. Full
 * URLs are passed per call via [Url] so a single client can target any
 * configured profile (LM Studio, Ollama, OpenAI, custom).
 */
interface OpenAiApi {

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("Authorization") authorization: String?,
    ): ModelsResponse

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body body: ChatCompletionRequest,
    ): ChatCompletionResponse
}
