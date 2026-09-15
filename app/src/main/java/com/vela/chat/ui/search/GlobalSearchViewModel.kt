package com.vela.chat.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.local.entity.MessageSearchResult
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.domain.model.ApiProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Debounce before a keystroke triggers the three-way search. */
private const val SEARCH_DEBOUNCE_MS = 200L

/** Cap on returned message hits (oldest omission; sorted newest-first). */
private const val MESSAGE_SEARCH_LIMIT = 50

/** Message hits grouped with their loading flag. */
data class SearchResults(
    val conversations: List<ConversationWithPreview> = emptyList(),
    val messages: List<MessageSearchResult> = emptyList(),
    val profiles: List<ApiProfile> = emptyList(),
    /** True while the first batch for a non-empty query is being computed. */
    val isSearching: Boolean = false,
) {
    val isEmpty: Boolean get() = conversations.isEmpty() && messages.isEmpty() && profiles.isEmpty()
}

/** Immutable UI state of the global search screen. */
data class GlobalSearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
) {
    /** True when the user typed something and nothing matched anywhere. */
    val showEmptyResults: Boolean get() = query.isNotBlank() && !results.isSearching && results.isEmpty
    val showSkeleton: Boolean get() = query.isNotBlank() && results.isSearching
}

/**
 * Global search VM: debounced (200 ms) three-way search across conversation
 * titles/previews, message bodies (newest first, [MESSAGE_SEARCH_LIMIT] hits)
 * and profile names/models, via flatMapLatest keyed on the trimmed query.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val profileRepository: ApiProfileRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state = combine(query, resultsFor(query)) { q, results ->
        GlobalSearchUiState(query = q, results = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun resultsFor(source: MutableStateFlow<String>): Flow<SearchResults> =
        source.debounce(SEARCH_DEBOUNCE_MS).flatMapLatest { raw ->
            val q = raw.trim()
            if (q.isEmpty()) {
                flowOf(SearchResults())
            } else {
                flow {
                    emit(SearchResults(isSearching = true))
                    emitAll(
                        combine(
                            conversationRepository.search(q),
                            flow { emit(conversationRepository.searchMessages(q, MESSAGE_SEARCH_LIMIT)) },
                            profileRepository.profiles,
                        ) { conversations, messages, profiles ->
                            SearchResults(
                                conversations = conversations,
                                messages = messages,
                                profiles = profiles.filter { profile ->
                                    profile.name.contains(q, ignoreCase = true) ||
                                        profile.model.orEmpty().contains(q, ignoreCase = true)
                                },
                            )
                        },
                    )
                }
            }
        }
}
