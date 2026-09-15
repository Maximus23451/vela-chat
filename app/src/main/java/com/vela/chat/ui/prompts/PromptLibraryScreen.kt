package com.vela.chat.ui.prompts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaFilledChip
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.components.nova.rememberHaptics
import com.vela.chat.ui.theme.nova.NovaTokens
import kotlinx.coroutines.launch

/** Max preview lines of prompt content shown in a library row. */
private const val ROW_PREVIEW_LINES = 2

/**
 * Prompt library 2.0: category filter chips, favorite stars, a full editor with
 * `{{variable}}` detection, copy-to-clipboard and JSON import/export through the
 * system share / file pickers.
 */
@Composable
fun PromptLibraryScreen(
    onBack: () -> Unit,
    viewModel: PromptsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var editing by remember { mutableStateOf<PromptUiModel?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val text = runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
            }.getOrNull()
            if (text != null) viewModel.import(text)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NovaTopBar(
                title = "Prompt library",
                subtitle = "Categories, favorites & {{variables}}",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Import prompts")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val json = viewModel.export()
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    putExtra(Intent.EXTRA_TITLE, "vela-prompts.json")
                                }
                                context.startActivity(Intent.createChooser(intent, "Export prompts"))
                            }
                        }
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Export prompts")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New prompt") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
                    contentPadding = PaddingValues(horizontal = NovaTokens.Spacing.md),
                    modifier = Modifier.padding(vertical = NovaTokens.Spacing.xs),
                ) {
                    item(key = "all") {
                        NovaFilledChip(
                            selected = state.selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = "All",
                        )
                    }
                    items(state.categories, key = { it }) { category ->
                        NovaFilledChip(
                            selected = state.selectedCategory == category,
                            onClick = {
                                haptics(HapticFeedbackType.TextHandleMove)
                                viewModel.selectCategory(category)
                            },
                            label = category,
                        )
                    }
                }
            }

            if (state.prompts.isEmpty()) {
                NovaEmptyState(
                    icon = Icons.Rounded.DataObject,
                    title = "No prompts yet",
                    subtitle = "Create a prompt with {{variables}} you fill in before sending.",
                    actionLabel = "New prompt",
                    onAction = {
                        editing = null
                        showEditor = true
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = NovaTokens.Spacing.md,
                        vertical = NovaTokens.Spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
                ) {
                    items(state.prompts, key = { it.prompt.id }) { item ->
                        PromptRow(
                            item = item,
                            onToggleFavorite = { viewModel.toggleFavorite(item.prompt) },
                            onCopy = {
                                clipboard.setText(AnnotatedString(item.prompt.content))
                                scope.launch { snackbarHostState.showSnackbar("Prompt copied") }
                            },
                            onEdit = {
                                editing = item
                                showEditor = true
                            },
                            onDelete = { viewModel.delete(item.prompt.id) },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        PromptEditorDialog(
            initial = editing,
            onSave = { id, title, content, category ->
                viewModel.save(id, title, content, category)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

/** One library row: favorite star, title/content preview, variable badges, menu. */
@Composable
private fun PromptRow(
    item: PromptUiModel,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = NovaTokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (item.prompt.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (item.prompt.favorite) "Unfavorite" else "Favorite",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.prompt.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.variables.isNotEmpty()) {
                    Text(
                        "  " + item.variables.joinToString(" ") { "{{$it}}" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                item.prompt.content.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = ROW_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.prompt.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy prompt") }
        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/**
 * Create/edit dialog. Shows the `{{variables}}` detected in the current content
 * so authors can see what the insert flow will ask for.
 */
@Composable
private fun PromptEditorDialog(
    initial: PromptUiModel?,
    onSave: (id: String?, title: String, content: String, category: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.prompt?.title.orEmpty()) }
    var category by remember { mutableStateOf(initial?.prompt?.category.orEmpty()) }
    var content by remember { mutableStateOf(initial?.prompt?.content.orEmpty()) }
    val variables = remember(content) { com.vela.chat.util.PromptVariables.extract(content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New prompt" else "Edit prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("General") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Prompt") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (variables.isNotEmpty()) {
                    Text(
                        "Variables: " + variables.joinToString(" ") { "{{$it}}" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial?.prompt?.id, title, content, category) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
