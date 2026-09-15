package com.vela.chat.data.remote

import com.vela.chat.data.remote.dto.Usage

/** Incremental events emitted while streaming a chat completion. */
sealed interface StreamEvent {
    /** A chunk of visible answer text. */
    data class Token(val text: String) : StreamEvent

    /** A chunk of model "thinking" / reasoning content, when the model emits it. */
    data class Reasoning(val text: String) : StreamEvent

    /** Stream finished cleanly; carries token usage if the server reported it. */
    data class Completed(val usage: Usage?) : StreamEvent

    /** Stream failed. [message] is safe to surface to the user. */
    data class Failed(val message: String) : StreamEvent
}

/** Result wrapper for one-shot network calls (models list, connection test). */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>
}
