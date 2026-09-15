package com.vela.chat.ui.voice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.components.nova.NovaSettingsRow
import com.vela.chat.ui.components.nova.NovaSliderRow
import com.vela.chat.ui.components.nova.NovaSwitchRow
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.components.nova.rememberHaptics
import com.vela.chat.ui.settings.SettingsViewModel
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.util.rememberTextToSpeech
import java.util.Locale

/**
 * Voice (text-to-speech) settings on Nova rows: enable toggle, installed-voice
 * language picker, speed & pitch sliders and a test button. Functionality is
 * unchanged from 1.x — only the chrome was restyled.
 */
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val tts = rememberTextToSpeech()
    val haptics = rememberHaptics()

    LaunchedEffect(settings.ttsLanguage, settings.ttsRate, settings.ttsPitch) {
        tts.configure(settings.ttsLanguage, settings.ttsRate, settings.ttsPitch)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NovaTopBar(
                title = "Voice",
                subtitle = "Text-to-speech",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NovaTokens.Spacing.md)
                .padding(bottom = NovaTokens.Spacing.xl),
        ) {
            NovaSwitchRow(
                title = "Text-to-speech",
                subtitle = "Adds a read-aloud action to assistant replies",
                icon = Icons.Rounded.RecordVoiceOver,
                checked = settings.ttsEnabled,
                onCheckedChange = viewModel::setTts,
            )

            NovaSectionHeader("Language / voice")
            var menu by remember { mutableStateOf(false) }
            val currentLabel = settings.ttsLanguage.takeIf { it.isNotBlank() }
                ?.let { tag -> runCatching { Locale.forLanguageTag(tag).displayName }.getOrDefault(tag) }
                ?: "System default"
            Box(Modifier.padding(horizontal = NovaTokens.Spacing.xs)) {
                NovaSettingsRow(
                    title = currentLabel,
                    subtitle = if (tts.availableLanguages.isEmpty()) {
                        "Loading installed voices… If none appear, install a TTS engine."
                    } else {
                        "${tts.availableLanguages.size} installed voice(s)"
                    },
                    icon = Icons.Rounded.Language,
                    onClick = { menu = true },
                    trailing = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("System default") },
                        onClick = { menu = false; viewModel.setTtsLanguage("") },
                    )
                    tts.availableLanguages.forEach { locale ->
                        DropdownMenuItem(
                            text = { Text("${locale.displayName} (${locale.toLanguageTag()})") },
                            onClick = { menu = false; viewModel.setTtsLanguage(locale.toLanguageTag()) },
                        )
                    }
                }
            }

            NovaSectionHeader("Speech")
            NovaSliderRow(
                title = "Speed",
                value = settings.ttsRate,
                onValueChange = { viewModel.setTtsRate(it) },
                valueLabel = "%.1fx".format(settings.ttsRate),
                range = TTS_MIN_RATE..TTS_MAX_RATE,
            )
            NovaSliderRow(
                title = "Pitch",
                value = settings.ttsPitch,
                onValueChange = { viewModel.setTtsPitch(it) },
                valueLabel = "%.1fx".format(settings.ttsPitch),
                range = TTS_MIN_RATE..TTS_MAX_RATE,
            )

            FilledTonalButton(
                onClick = {
                    haptics(HapticFeedbackType.LongPress)
                    tts.configure(settings.ttsLanguage, settings.ttsRate, settings.ttsPitch)
                    tts.speak("This is a V.E.L.A. voice test.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NovaTokens.Spacing.lg),
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                Text("  Test voice")
            }
        }
    }
}

/** TTS rate/pitch bounds (Android SpeechRecognizer defaults sit at 1.0). */
private const val TTS_MIN_RATE = 0.5f
private const val TTS_MAX_RATE = 2.0f
