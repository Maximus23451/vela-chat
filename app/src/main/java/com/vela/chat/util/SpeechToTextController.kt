package com.vela.chat.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Wrapper around the platform [SpeechRecognizer] for dictation into the composer.
 * Exposes a Compose-observable [isListening] flag and delivers partial + final
 * transcripts via callbacks. Must be created and driven from the main thread
 * (Compose composition / click handlers satisfy this).
 */
class SpeechToTextController(private val context: Context) {

    var isListening by mutableStateOf(false)
        private set

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(
        languageTag: String = "",
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isAvailable) {
            onError("Speech recognition isn't available on this device.")
            return
        }
        if (isListening) {
            stop()
            return
        }
        this.onPartial = onPartial
        this.onFinal = onFinal
        this.onError = onError

        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag.ifBlank { Locale.getDefault().toLanguageTag() })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching {
            sr.startListening(intent)
            isListening = true
        }.onFailure {
            isListening = false
            onError(it.message ?: "Couldn't start listening.")
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        isListening = false
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        isListening = false
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            isListening = false
            // No-speech / no-match are normal when the user taps without speaking —
            // surface them quietly via the same channel so the UI can choose to ignore.
            onError?.invoke(describeError(error))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { onPartial?.invoke(it) }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            firstResult(results)?.let { onFinal?.invoke(it) }
        }

        private fun firstResult(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition error."
    }

    companion object {
        /** Errors that are routine (user tapped but didn't speak) and not worth alerting on. */
        fun isBenignError(message: String): Boolean =
            message.startsWith("Didn't catch") || message.startsWith("No speech")
    }
}

@Composable
fun rememberSpeechToText(): SpeechToTextController {
    val context = LocalContext.current
    val controller = remember { SpeechToTextController(context) }
    DisposableEffect(Unit) {
        onDispose { controller.destroy() }
    }
    return controller
}
