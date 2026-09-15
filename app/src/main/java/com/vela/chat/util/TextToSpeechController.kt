package com.vela.chat.util

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Wrapper around the platform [TextToSpeech] engine. Holds Compose-observable
 * [ready] and [availableLanguages], and applies a configurable language, speech
 * rate, and pitch.
 */
class TextToSpeechController(context: Context) {

    var ready by mutableStateOf(false)
        private set
    var availableLanguages by mutableStateOf<List<Locale>>(emptyList())
        private set

    private var languageTag: String = ""
    private var rate: Float = 1.0f
    private var pitch: Float = 1.0f

    // Explicit type breaks the inference cycle from the listener referencing `tts`.
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            availableLanguages = runCatching {
                tts.availableLanguages?.toList().orEmpty()
                    .filter { it.displayName.isNotBlank() }
                    .sortedBy { it.displayName }
            }.getOrDefault(emptyList())
            applyConfig()
        }
    }

    fun configure(languageTag: String, rate: Float, pitch: Float) {
        this.languageTag = languageTag
        this.rate = rate
        this.pitch = pitch
        if (ready) applyConfig()
    }

    private fun applyConfig() {
        runCatching {
            val locale = languageTag.takeIf { it.isNotBlank() }
                ?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
            tts.language = locale
            tts.setSpeechRate(rate.coerceIn(0.25f, 3.0f))
            tts.setPitch(pitch.coerceIn(0.25f, 3.0f))
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) {
            tts.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "vela-tts")
        }
    }

    fun stop() { runCatching { tts.stop() } }
    fun shutdown() { runCatching { tts.shutdown() } }
}

@Composable
fun rememberTextToSpeech(): TextToSpeechController {
    val context = LocalContext.current
    val controller = remember { TextToSpeechController(context) }
    DisposableEffect(Unit) {
        onDispose { controller.shutdown() }
    }
    return controller
}
