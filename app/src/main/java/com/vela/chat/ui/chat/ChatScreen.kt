package com.vela.chat.ui.chat

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vela.chat.domain.model.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.ui.chat.components.ChatConfigSheet
import com.vela.chat.ui.chat.components.ChatStatusLine
import com.vela.chat.ui.chat.components.ChatTopBar
import com.vela.chat.ui.chat.components.EmptyChat
import com.vela.chat.ui.chat.components.InputBar
import com.vela.chat.ui.chat.components.MessageList
import com.vela.chat.ui.components.nova.NovaSkeletonList
import com.vela.chat.ui.drawer.ConversationDrawer
import com.vela.chat.util.rememberTextToSpeech
import java.io.File
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "Explain a tricky concept simply",
    "Help me debug some code",
    "Draft an email",
    "Brainstorm ideas",
)

/** Delete snackbars stay a moment longer so the undo action is easy to hit. */
private val UNDO_SNACKBAR_DURATION = SnackbarDuration.Long

/**
 * Nova chat screen. Entry signature is navigation-owned: [onOpenSettings],
 * [onOpenConversation], [onNewChat] (plus optional quick-action callbacks the
 * navigator may wire later). Owns the drawer, transcript, composer, config
 * sheet, export/import flows and undo snackbars; all state lives in [viewModel].
 */
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenProfiles: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val tts = rememberTextToSpeech()
    var showConfig by remember { mutableStateOf(false) }

    // Auto-scroll that respects manual scrolling: true only while pinned near the
    // bottom. If you scroll up to read during a reply, following stops.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 240)
        }
    }

    // New message (you send one, or a reply finalizes): jump to bottom — but only
    // yank to a finalized reply if you were already at the bottom. Keyed on the
    // last message id (not the list size) so "load older" never scrolls you away.
    val lastMessageId = state.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        val lastIsUser = state.messages.lastOrNull()?.role == Role.USER
        if (lastMessageId != null && (lastIsUser || isAtBottom)) {
            val count = state.messages.size + if (state.streaming != null) 1 else 0
            listState.scrollToItem(count - 1, Int.MAX_VALUE)
        }
    }
    // Streaming tokens: follow only when you're already at the bottom.
    LaunchedEffect(state.streaming?.content) {
        if (state.streaming != null && isAtBottom) {
            listState.scrollToItem(state.messages.size, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    // Keep the screen awake only while a reply is actively generating.
    val view = LocalView.current
    LaunchedEffect(state.keepScreenOn, state.isGenerating) {
        view.keepScreenOn = state.keepScreenOn && state.isGenerating
    }

    // Apply the user's chosen TTS voice/speed/pitch to the read-aloud engine.
    LaunchedEffect(state.ttsLanguage, state.ttsRate, state.ttsPitch) {
        tts.configure(state.ttsLanguage, state.ttsRate, state.ttsPitch)
    }

    fun shareText(text: String, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, state.title)
        }
        context.startActivity(Intent.createChooser(intent, "Export chat"))
    }

    /** Share the exported PDF via the app's FileProvider (cache/exports is whitelisted). */
    fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export chat"))
    }

    /** Show a snackbar, optionally with an undo action the caller can react to. */
    suspend fun undoableSnackbar(message: String, onUndo: (() -> Unit)? = null) {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (onUndo != null) "Undo" else null,
            duration = if (onUndo != null) UNDO_SNACKBAR_DURATION else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
    }

    // Import: pick an exported JSON document and hand it to the repository.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val importedId = json?.let { viewModel.importConversation(it) }
            if (importedId != null) {
                snackbarHostState.showSnackbar("Conversation imported")
                onOpenConversation(importedId)
            } else {
                snackbarHostState.showSnackbar("Import failed — not a valid chat export")
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                currentConversationId = state.conversationId,
                onSelectConversation = { id ->
                    scope.launch { drawerState.close() }
                    onOpenConversation(id)
                },
                onNewChat = {
                    scope.launch { drawerState.close() }
                    onNewChat()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = state.title,
                    model = state.model,
                    profileName = state.profileName,
                    availableModels = state.availableModels,
                    customModels = state.customModels,
                    modelLoadError = state.modelLoadError,
                    isStreaming = state.isGenerating,
                    tokensPerSecond = state.tokensPerSecond,
                    connectionQuality = state.connectionQuality,
                    tailnetConnected = state.tailnetConnected,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSelectModel = viewModel::selectModel,
                    onAddModel = viewModel::addCustomModel,
                    onRemoveModel = viewModel::removeCustomModel,
                    onNewChat = onNewChat,
                    onOpenChatConfig = { showConfig = true },
                    onExportMarkdown = {
                        scope.launch { viewModel.buildExport(true)?.let { shareText(it, "text/plain") } }
                    },
                    onExportJson = {
                        scope.launch { viewModel.buildExport(false)?.let { shareText(it, "application/json") } }
                    },
                    onExportPdf = {
                        scope.launch { viewModel.exportPdf(context)?.let { sharePdf(it) } }
                    },
                    onImport = { importLauncher.launch("application/json") },
                    onOpenSettings = onOpenSettings,
                    onToggleSelectionMode = { viewModel.enterSelectionMode() },
                    selectionMode = state.selectionMode,
                    selectedCount = state.selectedMessageIds.size,
                    onExitSelectionMode = viewModel::exitSelectionMode,
                    onDeleteSelected = {
                        scope.launch {
                            val removed = viewModel.deleteSelected()
                            if (removed.isNotEmpty()) {
                                undoableSnackbar("${removed.size} message(s) deleted") {
                                    viewModel.restoreMessages(removed)
                                }
                            }
                        }
                    },
                    onShareSelected = {
                        viewModel.selectedShareText()?.let { shareText(it, "text/plain") }
                    },
                    canExport = state.conversationId != null && state.messages.isNotEmpty(),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // The top bar and input bar consume their own insets, so the
            // scaffold itself shouldn't add system-bar padding (avoids double padding).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!state.customWallpaperPath.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = state.customWallpaperPath,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.15f),
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    // Live status strip below the app bar (kept out of the TopAppBar
                    // title slot, which is height-capped and would clip/overlap it).
                    if (!state.selectionMode) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            ChatStatusLine(
                                isStreaming = state.isGenerating,
                                modelLoadError = state.modelLoadError,
                                tokensPerSecond = state.tokensPerSecond,
                                connectionQuality = state.connectionQuality,
                                tailnetConnected = state.tailnetConnected,
                                a2aPeers = state.a2aPeers,
                                activeA2aPeerId = state.activeA2aPeerId,
                                onSelectPeer = viewModel::selectA2aPeer,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        // The empty state renders immediately — the /models probe
                        // updates the header in the background and must never delay
                        // the first usable screen (cold-start responsiveness).
                        if (state.isEmpty) {
                            EmptyChat(
                                suggestions = SUGGESTIONS,
                                onSuggestion = { viewModel.onInputChange(it) },
                                onOpenProfiles = onOpenProfiles,
                                onOpenModels = onOpenModels,
                                personas = state.personas,
                                activePersonaId = state.activePersonaId,
                                onSelectPersona = viewModel::selectPersona,
                            )
                        } else {
                            MessageList(
                                state = state,
                                listState = listState,
                                onEdit = viewModel::editMessage,
                                onDelete = { message ->
                                    scope.launch {
                                        val removed = viewModel.deleteMessage(message.id)
                                        if (removed != null) {
                                            undoableSnackbar("Message deleted") {
                                                viewModel.restoreMessage(removed)
                                            }
                                        }
                                    }
                                },
                                onRegenerate = { messageId -> viewModel.regenerate(messageId) },
                                onContinue = viewModel::continueGeneration,
                                onSummarize = viewModel::summarizeConversation,
                                onTranslateTo = { message, language ->
                                    viewModel.translateMessage(message.id, language)
                                },
                                onSpeak = { tts.speak(it) },
                                onQuote = { message ->
                                    val quote = message.content.lines().joinToString("\n> ")
                                    viewModel.onInputChange("> $quote\n\n")
                                },
                                onToggleFavorite = viewModel::setMessageFavorite,
                                onTogglePin = viewModel::setMessagePinned,
                                onLoadOlder = viewModel::loadOlder,
                                onToggleSelection = viewModel::toggleSelected,
                            )
                        }

                        // Jump-to-bottom button, shown only when scrolled up.
                        if (!state.isEmpty && !isAtBottom) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        val count = state.messages.size + if (state.streaming != null) 1 else 0
                                        if (count > 0) listState.animateScrollToItem(count - 1, Int.MAX_VALUE)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to bottom")
                            }
                        }
                    }

                    InputBar(
                        state = state,
                        onInputChange = viewModel::onInputChange,
                        onSend = viewModel::send,
                        onStop = viewModel::stop,
                        onAttach = { viewModel.attach(it) },
                        onRemoveAttachment = viewModel::removeAttachment,
                        onSelectPrompt = viewModel::insertPrompt,
                        onTemperatureChange = viewModel::setTemperature,
                    )
                }
            }
        }
    }

    if (showConfig) {
        ChatConfigSheet(
            initialSystemPrompt = state.systemPrompt,
            initialParams = state.params,
            onSave = viewModel::updateConfig,
            onDismiss = { showConfig = false },
            personas = state.personas,
            activePersonaId = state.activePersonaId,
            onPersonaSelected = viewModel::selectPersona,
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            onProfileSelected = viewModel::selectProfile,
        )
    }
}
