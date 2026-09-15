package com.vela.chat.ui.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.repository.LibraryRepository
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.util.PromptVariables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the prompt library list. */
data class PromptUiModel(
    val prompt: SavedPrompt,
    /** Distinct `{{variables}}` detected in the content (order of appearance). */
    val variables: List<String>,
)

/** Immutable UI state of the prompt library. */
data class PromptsUiState(
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val prompts: List<PromptUiModel> = emptyList(),
)

/**
 * Prompt library 2.0 VM: category filter, favorites, CRUD and JSON
 * import/export (share sheet on the UI side).
 */
@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot user feedback (import/export results). */
    val message = _message.asStateFlow()

    private val allPrompts = repository.prompts

    private val filtered = allPrompts.combine(selectedCategory) { prompts, category ->
        val visible = if (category == null) prompts else prompts.filter { it.category == category }
        PromptsUiState(
            categories = prompts.mapTo(mutableListOf()) { it.category }.distinct().sorted(),
            selectedCategory = category,
            prompts = visible.map { PromptUiModel(it, PromptVariables.extract(it.content)) },
        )
    }

    /** Filtered + decorated prompts; favorites already sort first (DAO order). */
    val state = filtered.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), PromptsUiState(),
    )

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    /** Creates or updates a prompt (blank categories fall back to "General"). */
    fun save(id: String?, title: String, content: String, category: String) = viewModelScope.launch {
        val safeTitle = title.trim()
        val safeContent = content.trim()
        if (safeTitle.isEmpty() || safeContent.isEmpty()) return@launch
        val safeCategory = category.ifBlank { "General" }
        if (id == null) {
            repository.newPrompt(safeTitle, safeContent, safeCategory)
        } else {
            // Keep the existing favorite flag when editing.
            val current = repository.prompts.first().firstOrNull { it.id == id }
            repository.savePrompt(
                (current ?: SavedPrompt(id = id, title = safeTitle, content = safeContent, category = safeCategory))
                    .copy(title = safeTitle, content = safeContent, category = safeCategory),
            )
        }
    }

    fun toggleFavorite(prompt: SavedPrompt) = viewModelScope.launch {
        repository.setPromptFavorite(prompt.id, !prompt.favorite)
    }

    fun delete(id: String) = viewModelScope.launch { repository.deletePrompt(id) }

    /** Serializes the library for the export share sheet. */
    suspend fun export(): String = repository.exportPromptsJson()

    /** Restores prompts from an export JSON; result surfaces via [message]. */
    fun import(json: String) = viewModelScope.launch {
        repository.importPromptsJson(json)
            .onSuccess { count -> _message.value = "Imported $count prompt(s)" }
            .onFailure { _message.value = "Import failed: ${it.message}" }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
