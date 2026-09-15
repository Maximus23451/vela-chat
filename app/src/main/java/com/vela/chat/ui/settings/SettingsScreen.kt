package com.vela.chat.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.BuildConfig
import com.vela.chat.ui.components.ConfirmDialog
import com.vela.chat.ui.components.nova.GlassSurface
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.components.nova.NovaSettingsRow
import com.vela.chat.ui.components.nova.NovaSwitchRow
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.util.CrashLogger

/**
 * Settings hub (Nova): sectioned navigation into every settings sub-screen plus
 * the chat-behavior toggles, data maintenance and diagnostics. New destinations
 * (Tailscale, Security, Models, Search) are optional callbacks with safe
 * no-op defaults so existing call sites keep compiling until they are wired.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenParameters: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenWebSearch: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenTailscale: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenPersonas: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsOpt by viewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsOpt ?: return
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun exportCrashLog() {
        val file = CrashLogger.exportableLog(context)
        if (file == null) {
            Toast.makeText(context, "No crash logs yet — nothing to export.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "V.E.L.A. crash log (${BuildConfig.VERSION_NAME})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export crash log"))
        }.onFailure {
            Toast.makeText(context, "Couldn't export log: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NovaTopBar(
                title = "Settings",
                subtitle = "V.E.L.A. ${BuildConfig.VERSION_NAME}",
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
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
        ) {
            // Single glass hero: brand card over the Nova background gradient.
            GlassSurface(modifier = Modifier.padding(vertical = NovaTokens.Spacing.sm)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(NovaTokens.Spacing.lg),
                ) {
                    Text("V.E.L.A. ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Versatile Engine for Local AI — LM Studio, Ollama, tailnet & OpenAI-compatible servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = NovaTokens.Spacing.xs),
                    )
                }
            }

            NovaSectionHeader("Appearance")
            NovaSettingsRow(
                title = "Theme & colors",
                subtitle = "Light/dark, presets, accent, AMOLED, density",
                icon = Icons.Rounded.Palette,
                onClick = onOpenAppearance,
            )

            NovaSectionHeader("AI & models")
            NovaSettingsRow("API profiles", "LM Studio, Ollama, cloud & custom servers", Icons.Rounded.Api, onClick = onOpenProfiles)
            NovaSettingsRow(
                title = "Model dashboard",
                subtitle = "Catalogue, quantization, latency & defaults",
                icon = Icons.Rounded.Memory,
                onClick = onOpenModels,
            )
            NovaSettingsRow("Default parameters", "System prompt & sampler defaults", Icons.Rounded.Tune, onClick = onOpenParameters)
            NovaSettingsRow("Agent personalities", "Personas for your agent & default style", Icons.Rounded.Face, onClick = onOpenPersonas)
            NovaSettingsRow("Prompt library", "Categories, favorites & {{variables}}", Icons.Rounded.AutoAwesome, onClick = onOpenPrompts)

            NovaSectionHeader("Tailscale")
            NovaSettingsRow(
                title = "Tailnet & discovery",
                subtitle = "Status, AI-server scan, saved peers",
                icon = Icons.Rounded.Lan,
                onClick = onOpenTailscale,
            )

            NovaSectionHeader("Voice")
            NovaSettingsRow(
                title = "Voice (text-to-speech)",
                subtitle = "Language, speed & pitch",
                icon = Icons.Rounded.RecordVoiceOver,
                onClick = onOpenVoice,
            )

            NovaSectionHeader("Privacy & security")
            NovaSettingsRow(
                title = "App lock & privacy",
                subtitle = "PIN / biometric, auto-lock, backups",
                icon = Icons.Rounded.Lock,
                onClick = onOpenSecurity,
            )

            NovaSectionHeader("Advanced")
            NovaSettingsRow(
                title = "Search everything",
                subtitle = "Chats, messages & profiles",
                icon = Icons.Rounded.Search,
                onClick = onOpenSearch,
            )
            NovaSwitchRow(
                title = "Haptic feedback",
                subtitle = "Subtle vibrations on actions",
                icon = Icons.Rounded.Vibration,
                checked = settings.hapticsEnabled,
                onCheckedChange = viewModel::setHaptics,
            )
            NovaSwitchRow("Stream responses", "Show tokens as they are generated", settings.streamResponses, viewModel::setStream)
            NovaSwitchRow("Send on Enter", "Otherwise Enter inserts a newline", settings.sendOnEnter, viewModel::setSendOnEnter)
            NovaSwitchRow("Render Markdown", "Format replies with headings, lists & code", settings.renderMarkdown, viewModel::setRenderMarkdown)
            NovaSwitchRow("Show token usage", checked = settings.showTokenUsage, onCheckedChange = viewModel::setShowTokenUsage)
            NovaSwitchRow("Show timestamps", "Display the time under each message", settings.showTimestamps, viewModel::setShowTimestamps)
            NovaSwitchRow("Keep screen on", "Stay awake while a reply is generating", settings.keepScreenOn, viewModel::setKeepScreenOn)
            NovaSwitchRow("Text-to-speech", "Add a read-aloud action to replies", settings.ttsEnabled, viewModel::setTts)
            NovaSettingsRow(
                title = "Clear all conversations",
                subtitle = "Permanently delete every chat",
                icon = Icons.Rounded.DeleteSweep,
                onClick = { showClearConfirm = true },
            )
            NovaSettingsRow(
                title = "Export crash log",
                subtitle = "Share the detailed log from the last crash",
                icon = Icons.Rounded.BugReport,
                onClick = { exportCrashLog() },
            )

            Box(Modifier.padding(top = NovaTokens.Spacing.lg)) {
                Text(
                    "Offline-first. The only network traffic goes to servers you configure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showClearConfirm) {
            ConfirmDialog(
                title = "Clear all conversations?",
                message = "This permanently deletes every chat and its messages. This cannot be undone.",
                confirmLabel = "Delete all",
                onConfirm = { viewModel.clearAllConversations() },
                onDismiss = { showClearConfirm = false },
            )
        }
    }
}
