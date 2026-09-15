package com.vela.chat.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.domain.model.Folder
import com.vela.chat.ui.components.ConfirmDialog
import com.vela.chat.ui.components.TextInputDialog

/** Preset folder colors (ARGB); the list is intentionally fixed for consistency. */
private val FolderPresetColors = listOf(
    0xFFEF5350.toInt(), // red
    0xFFAB47BC.toInt(), // purple
    0xFF5C6BC0.toInt(), // indigo
    0xFF29B6F6.toInt(), // light blue
    0xFF26A69A.toInt(), // teal
    0xFF9CCC65.toInt(), // light green
    0xFFFFB74D.toInt(), // orange
    0xFF8D6E63.toInt(), // brown
)

/** Named Material icons a folder can use; stored by name string in [Folder.icon]. */
private val FolderPresetIcons: List<Pair<String, ImageVector>> = listOf(
    "work" to Icons.Rounded.Work,
    "school" to Icons.Rounded.School,
    "code" to Icons.Rounded.Code,
    "science" to Icons.Rounded.Science,
    "chat" to Icons.Rounded.Forum,
    "lightbulb" to Icons.Rounded.Lightbulb,
    "favorite" to Icons.Rounded.Favorite,
    "sports_esports" to Icons.Rounded.SportsEsports,
    "shopping" to Icons.Rounded.ShoppingCart,
    "music" to Icons.Rounded.MusicNote,
)

/** Resolves a stored icon name; unknown or empty names fall back to the folder glyph. */
internal fun folderIconByName(name: String): ImageVector =
    FolderPresetIcons.firstOrNull { it.first == name }?.second ?: Icons.Rounded.Folder

/**
 * Drawer over the conversation list: folders (with color/icon editor), search,
 * multi-select, swipe-to-archive and swipe-to-delete with undo snackbars.
 *
 * Drag & drop reordering was deliberately replaced by the "Move to folder"
 * context-menu entry — touch drag&drop inside a nested-scroll drawer proved
 * fragile; noted in docs/SCREENS_V2.md.
 */
@Composable
fun ConversationDrawer(
    currentConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var renameTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }
    var moveTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }
    var showFolderEditor by remember { mutableStateOf(false) }
    var editFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var archiveUndoTarget by remember { mutableStateOf<ConversationWithPreview?>(null) }

    val folderIds = remember(state.folders) { state.folders.mapTo(HashSet()) { it.id } }
    val grouped = remember(state.conversations) { state.conversations.groupBy { it.folderId } }
    val visibleConversations = state.conversations.filterNot { it.id == pendingDelete?.id }
    val pinned = visibleConversations.filter { it.pinned }

    // Undo window for swiped-away conversations.
    LaunchedEffect(pendingDelete) {
        val pending = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted \"${pending.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.commitPendingDelete()
    }

    // Undo window for swiped-into-archive conversations.
    LaunchedEffect(archiveUndoTarget) {
        val convo = archiveUndoTarget ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Archived \"${convo.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(convo.id)
        archiveUndoTarget = null
    }

    val conversationRow: @Composable (ConversationWithPreview) -> Unit = { convo ->
        SwipeableConversationRow(
            convo = convo,
            selectionMode = state.selectionMode,
            onArchive = {
                archiveUndoTarget = convo
                viewModel.archive(convo.id)
            },
            onDelete = { viewModel.swipeDelete(convo) },
        ) {
            ConversationRow(
                convo = convo,
                isCurrent = convo.id == currentConversationId && !state.selectionMode,
                isChecked = state.isSelected(convo.id),
                selectionMode = state.selectionMode,
                onClick = {
                    if (state.selectionMode) viewModel.toggleSelection(convo.id)
                    else onSelectConversation(convo.id)
                },
                onLongClick = { viewModel.toggleSelection(convo.id) },
                onSelect = { viewModel.toggleSelection(convo.id) },
                onPinToggle = { viewModel.pin(convo.id, !convo.pinned) },
                onRename = { renameTarget = convo },
                onMove = { moveTarget = convo },
                onArchive = {
                    archiveUndoTarget = convo
                    viewModel.archive(convo.id)
                },
                onDelete = { deleteTarget = convo },
            )
        }
    }

    Box {
        ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(312.dp)) {
            Column(Modifier.fillMaxHeight()) {
                if (state.selectionMode) {
                    SelectionBar(
                        count = state.selectedIds.size,
                        onClose = viewModel::clearSelection,
                        onMove = { showBulkMove = true },
                        onRemoveFromFolder = { viewModel.moveSelectedToFolder(null) },
                        onArchive = viewModel::archiveSelected,
                        onDelete = { showBulkDelete = true },
                    )
                } else {
                    // Header
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("V.E.L.A.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButtonBox(Icons.Rounded.CreateNewFolder, "New folder") { showFolderEditor = true }
                    }

                    // New chat button
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onNewChat)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "New chat",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    // Search
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search conversations") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(Modifier.weight(1f)) {
                    if (pinned.isNotEmpty()) {
                        item(key = "label-pinned") { DrawerSectionLabel("Pinned") }
                        items(pinned, key = { "pin-${it.id}" }) { convo -> conversationRow(convo) }
                    }

                    state.folders.forEach { folder ->
                        val items = grouped[folder.id]?.filterNot { it.pinned }.orEmpty()
                        // Collapse is ignored while searching so matches inside folders stay visible.
                        val collapsed = state.isCollapsed(folder.id) && state.query.isBlank()
                        item(key = "folder-${folder.id}") {
                            FolderHeader(
                                folder = folder,
                                count = items.size,
                                collapsed = collapsed,
                                onToggle = { viewModel.setFolderCollapsed(folder.id, !state.isCollapsed(folder.id)) },
                                onEdit = { editFolderTarget = folder },
                                onRename = { renameFolderTarget = folder },
                                onDelete = { deleteFolderTarget = folder },
                                onSelectAll = { viewModel.selectAllInFolder(folder.id) },
                            )
                        }
                        if (!collapsed) {
                            items(items, key = { "f-${it.id}" }) { convo -> conversationRow(convo) }
                        }
                    }

                    val loose = visibleConversations
                        .filterNot { it.pinned }
                        .filter { it.folderId == null || it.folderId !in folderIds }
                    if (loose.isNotEmpty()) {
                        item(key = "label-loose") {
                            DrawerSectionLabel(if (state.query.isBlank()) "Chats" else "Results")
                        }
                        items(loose, key = { "c-${it.id}" }) { convo -> conversationRow(convo) }
                    }

                    if (visibleConversations.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (state.query.isBlank()) "No conversations yet" else "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }

                // Footer
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text("Settings", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // Undo snackbars overlay the sheet content.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Rename chat",
            initialValue = target.title,
            label = "Title",
            onConfirm = { viewModel.rename(target.id, it) },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete chat?",
            message = "\"${target.title}\" and its messages will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(target.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    moveTarget?.let { target ->
        MoveToFolderDialog(
            folders = state.folders,
            onMove = { viewModel.moveToFolder(target.id, it) },
            onDismiss = { moveTarget = null },
        )
    }

    if (showBulkMove) {
        val count = state.selectedIds.size
        MoveToFolderDialog(
            folders = state.folders,
            title = "Move $count chat${if (count == 1) "" else "s"} to",
            onMove = { viewModel.moveSelectedToFolder(it) },
            onDismiss = { showBulkMove = false },
        )
    }

    if (showBulkDelete) {
        val count = state.selectedIds.size
        ConfirmDialog(
            title = "Delete $count chat${if (count == 1) "" else "s"}?",
            message = "The selected conversations and their messages will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteSelected() },
            onDismiss = { showBulkDelete = false },
        )
    }

    if (showFolderEditor) {
        FolderEditorDialog(
            initial = null,
            onSave = { name, color, icon ->
                viewModel.createFolder(name, color, icon)
                showFolderEditor = false
            },
            onDismiss = { showFolderEditor = false },
        )
    }

    editFolderTarget?.let { folder ->
        FolderEditorDialog(
            initial = folder,
            onSave = { name, color, icon ->
                viewModel.saveFolder(folder.copy(name = name.ifBlank { folder.name }, color = color, icon = icon))
                editFolderTarget = null
            },
            onDismiss = { editFolderTarget = null },
        )
    }

    renameFolderTarget?.let { folder ->
        TextInputDialog(
            title = "Rename folder",
            initialValue = folder.name,
            label = "Folder name",
            onConfirm = { viewModel.renameFolder(folder, it) },
            onDismiss = { renameFolderTarget = null },
        )
    }

    deleteFolderTarget?.let { folder ->
        ConfirmDialog(
            title = "Delete folder?",
            message = "\"${folder.name}\" will be removed. Its chats are kept and moved out of the folder.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteFolder(folder.id) },
            onDismiss = { deleteFolderTarget = null },
        )
    }
}

/**
 * Swipe wrapper: right-swipe archives (undoable snackbar), left-swipe deletes
 * (undoable via pendingDelete). Disabled while multi-select is active.
 */
@Composable
private fun SwipeableConversationRow(
    convo: ConversationWithPreview,
    selectionMode: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    row: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onArchive()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            val direction = dismissState.targetValue
            if (direction == SwipeToDismissBoxValue.Settled) {
                // Idle: render nothing. The row itself is transparent, so an
                // always-on colored background here bleeds through under every
                // row and reads as an overlap bug (observed on device).
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp))
            } else {
                val tint = if (direction == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    },
                ) {
                    Icon(
                        if (direction == SwipeToDismissBoxValue.EndToStart) Icons.Rounded.Delete else Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        row()
    }
}

/** Contextual action bar shown at the top of the drawer while selecting chats. */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonBox(Icons.Rounded.Close, "Cancel selection", tint = MaterialTheme.colorScheme.onSecondaryContainer, onClick = onClose)
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButtonBox(Icons.AutoMirrored.Rounded.DriveFileMove, "Move to folder", tint = MaterialTheme.colorScheme.onSecondaryContainer, onClick = onMove)
        IconButtonBox(Icons.Rounded.FolderOff, "Remove from folder", tint = MaterialTheme.colorScheme.onSecondaryContainer, onClick = onRemoveFromFolder)
        IconButtonBox(Icons.Rounded.Inventory2, "Archive", tint = MaterialTheme.colorScheme.onSecondaryContainer, onClick = onArchive)
        IconButtonBox(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSecondaryContainer, onClick = onDelete)
    }
}

@Composable
private fun ConversationRow(
    convo: ConversationWithPreview,
    isCurrent: Boolean,
    isChecked: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onPinToggle: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val background = when {
        isChecked -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = isChecked, onCheckedChange = { onSelect() })
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (convo.pinned) {
                    Icon(
                        Icons.Rounded.PushPin, null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    convo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            convo.preview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            Box {
                IconButtonBox(Icons.Rounded.MoreVert, "More") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Select") },
                        leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                        onClick = { menuOpen = false; onSelect() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (convo.pinned) "Unpin" else "Pin") },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                        onClick = { menuOpen = false; onPinToggle() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
                        onClick = { menuOpen = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = { Icon(Icons.Rounded.Inventory2, null) },
                        onClick = { menuOpen = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * Create/edit folder dialog: name plus the preset color swatches (first swatch
 * resets to the theme default) and the named icon grid; both persist on
 * [Folder.color]/[Folder.icon] via [ConversationsViewModel.saveFolder].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderEditorDialog(
    initial: Folder?,
    onSave: (name: String, color: Int, icon: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: 0) }
    var icon by remember { mutableStateOf(initial?.icon.orEmpty()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New folder" else "Edit folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ColorSwatch(colorInt = 0, selected = color == 0, onClick = { color = 0 })
                    FolderPresetColors.forEach { preset ->
                        ColorSwatch(colorInt = preset, selected = color == preset, onClick = { color = preset })
                    }
                }

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconChoice(icon = Icons.Rounded.Folder, name = "", selected = icon.isEmpty(), onClick = { icon = "" })
                    FolderPresetIcons.forEach { (presetName, presetIcon) ->
                        IconChoice(
                            icon = presetIcon,
                            name = presetName,
                            selected = icon == presetName,
                            onClick = { icon = presetName },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), color, icon) },
                enabled = name.isNotBlank(),
            ) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One selectable color swatch; [colorInt] 0 renders the theme-default option. */
@Composable
private fun ColorSwatch(colorInt: Int, selected: Boolean, onClick: () -> Unit) {
    val fill = if (colorInt == 0) MaterialTheme.colorScheme.surfaceContainerHighest else Color(colorInt)
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {}
}

/** One selectable icon choice in the folder editor grid. */
@Composable
private fun IconChoice(icon: ImageVector, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = name.ifEmpty { "Default folder icon" },
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoveToFolderDialog(
    folders: List<Folder>,
    onMove: (String?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Move to folder",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                DrawerMenuRow("No folder", Icons.Rounded.FolderOff) { onMove(null); onDismiss() }
                folders.forEach { folder ->
                    DrawerMenuRow(
                        text = folder.name,
                        icon = folderIconByName(folder.icon),
                        iconTint = folder.takeIf { it.color != 0 }?.let { Color(it.color) },
                        onClick = { onMove(folder.id); onDismiss() },
                    )
                }
                if (folders.isEmpty()) {
                    Text(
                        "No folders yet — create one from the drawer header.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DrawerMenuRow(
    text: String,
    icon: ImageVector,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Collapsible folder header: tap to expand/collapse; overflow for edit
 * (color/icon), rename, delete and select-all. Renders the folder's chosen
 * color (icon tint) and icon.
 */
@Composable
private fun FolderHeader(
    folder: Folder,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val folderTint = folder.takeIf { it.color != 0 }?.let { Color(it.color) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = 10.dp, end = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (collapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
            if (collapsed) "Expand folder" else "Collapse folder",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            folderIconByName(folder.icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = folderTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            folder.name,
            style = MaterialTheme.typography.labelLarge,
            color = folderTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButtonBox(Icons.Rounded.MoreVert, "Folder options") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Select all in folder") },
                    leadingIcon = { Icon(Icons.Rounded.Checklist, null) },
                    enabled = count > 0,
                    onClick = { menuOpen = false; onSelectAll() },
                )
                DropdownMenuItem(
                    text = { Text("Edit folder") },
                    leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Rename folder") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete folder") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun IconButtonBox(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint)
    }
}
