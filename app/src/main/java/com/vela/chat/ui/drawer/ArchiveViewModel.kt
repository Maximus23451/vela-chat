package com.vela.chat.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM for the archive screen: archived conversations with unarchive and
 * delete-with-undo (same pending-delete pattern as the drawer).
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _pendingDelete = MutableStateFlow<PendingDeleteConversation?>(null)
    val pendingDelete = _pendingDelete.asStateFlow()

    /** Archived conversations, newest first (DAO order). */
    val archived = repository.observeArchived()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unarchive(id: String) = viewModelScope.launch { repository.setArchived(id, false) }

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
}
