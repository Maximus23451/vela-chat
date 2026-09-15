package com.vela.chat.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.domain.model.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A conversation swiped away for deletion but not yet committed: the row is
 * hidden while the undo snackbar shows, and hard-deleted only if the snackbar
 * is dismissed without "Undo".
 */
data class PendingDeleteConversation(
    val id: String,
    val title: String,
)

data class DrawerUiState(
    val query: String = "",
    val folders: List<Folder> = emptyList(),
    val conversations: List<ConversationWithPreview> = emptyList(),
    val collapsedFolderIds: Set<String> = emptySet(),
    val selectedIds: Set<String> = emptySet(),
) {
    /** Selection mode is active whenever at least one conversation is checked. */
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
    fun isCollapsed(folderId: String): Boolean = folderId in collapsedFolderIds
    fun isSelected(id: String): Boolean = id in selectedIds
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Swiped-for-deletion row awaiting the undo snackbar outcome. */
    private val _pendingDelete = MutableStateFlow<PendingDeleteConversation?>(null)
    val pendingDelete = _pendingDelete.asStateFlow()

    private val conversations = query.flatMapLatest { q ->
        if (q.isBlank()) repository.observeActive() else repository.search(q.trim())
    }

    val state = combine(
        query,
        repository.observeFolders(),
        conversations,
        settingsRepository.collapsedFolders,
        selectedIds,
    ) { q, folders, convos, collapsed, selected ->
        // Prune any selected ids that no longer exist (deleted/archived elsewhere).
        val live = convos.mapTo(HashSet()) { it.id }
        val prunedSelection = selected.intersect(live)
        if (prunedSelection.size != selected.size) selectedIds.value = prunedSelection
        DrawerUiState(
            query = q,
            folders = folders,
            conversations = convos,
            collapsedFolderIds = collapsed,
            selectedIds = prunedSelection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun onQueryChange(value: String) { query.value = value }

    // ---- Single-conversation actions (per-row menu) ----

    fun pin(id: String, pinned: Boolean) = viewModelScope.launch { repository.setPinned(id, pinned) }
    fun archive(id: String) = viewModelScope.launch { repository.setArchived(id, true) }
    fun unarchive(id: String) = viewModelScope.launch { repository.setArchived(id, false) }
    fun rename(id: String, title: String) = viewModelScope.launch { repository.rename(id, title) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
    fun moveToFolder(id: String, folderId: String?) = viewModelScope.launch { repository.moveToFolder(id, folderId) }

    // ---- Swipe-to-delete with undo (pendingDelete) ----

    /** Hides the row and starts the undo window; nothing is deleted yet. */
    fun swipeDelete(convo: ConversationWithPreview) {
        _pendingDelete.value = PendingDeleteConversation(id = convo.id, title = convo.title)
    }

    /** Undo: restore the hidden row without touching the database. */
    fun undoDelete() {
        _pendingDelete.value = null
    }

    /** The undo window expired — hard-delete the hidden conversation. */
    fun commitPendingDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { repository.delete(pending.id) }
    }

    // ---- Multi-select ----

    fun toggleSelection(id: String) = selectedIds.update { if (id in it) it - id else it + id }
    fun clearSelection() { selectedIds.value = emptySet() }
    /** Select every (non-pinned) conversation currently in a folder; null = top-level. */
    fun selectAllInFolder(folderId: String?) {
        val ids = state.value.conversations
            .filter { it.folderId == folderId && !it.pinned }
            .map { it.id }
        if (ids.isNotEmpty()) selectedIds.update { it + ids }
    }

    fun moveSelectedToFolder(folderId: String?) {
        val ids = selectedIds.value.toList()
        clearSelection()
        viewModelScope.launch { repository.moveToFolder(ids, folderId) }
    }

    fun archiveSelected() {
        val ids = selectedIds.value.toList()
        clearSelection()
        viewModelScope.launch { repository.setArchived(ids, true) }
    }

    fun deleteSelected() {
        val ids = selectedIds.value.toList()
        clearSelection()
        viewModelScope.launch { repository.delete(ids) }
    }

    // ---- Folders ----

    fun setFolderCollapsed(folderId: String, collapsed: Boolean) =
        viewModelScope.launch { settingsRepository.setFolderCollapsed(folderId, collapsed) }

    fun createFolder(name: String, color: Int, icon: String) = viewModelScope.launch {
        val folder = repository.createFolder(name.trim().ifEmpty { "New folder" })
        repository.updateFolder(folder.copy(color = color, icon = icon))
    }

    fun renameFolder(folder: Folder, name: String) = viewModelScope.launch { repository.renameFolder(folder, name) }

    /** Full folder editor save (name + color + icon) via [ConversationRepository.updateFolder]. */
    fun saveFolder(folder: Folder) = viewModelScope.launch { repository.updateFolder(folder) }

    fun deleteFolder(id: String) = viewModelScope.launch { repository.deleteFolder(id) }
}
