package com.vela.chat.ui.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.repository.PersonaRepository
import com.vela.chat.domain.model.AgentPersona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backing VM for the personalities manager screen. */
@HiltViewModel
class PersonasViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
) : ViewModel() {

    /** All personalities, default first (DAO ordering), failures mapped to an empty list. */
    val personas: StateFlow<List<AgentPersona>> = personaRepository.observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun upsert(persona: AgentPersona) = viewModelScope.launch { personaRepository.upsert(persona) }

    fun setDefault(id: String) = viewModelScope.launch { personaRepository.setDefault(id) }

    fun delete(id: String) = viewModelScope.launch { personaRepository.delete(id) }
}
