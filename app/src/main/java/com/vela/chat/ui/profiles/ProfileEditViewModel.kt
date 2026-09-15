package com.vela.chat.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vela.chat.data.net.TrustedCertStore
import com.vela.chat.data.net.findUntrustedCert
import com.vela.chat.data.remote.NetworkResult
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.A2aPeer
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One editable row of an A2A profile's peer table; the token mirrors [ApiProfile]'s key handling. */
/** A certificate seen during [ProfileEditViewModel.test] that isn't system-trusted and has
 * no pin yet — surfaced so the user can review its fingerprint before trusting it. */
data class PendingCertTrust(val host: String, val fingerprintSha256: String)

data class PeerDraft(
    val id: String,
    val name: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
    val token: String = "",
    val tokenEdited: Boolean = false,
    val hasStoredToken: Boolean = false,
)

data class ProfileEditState(
    val id: String? = null,
    val name: String = "",
    val providerType: ProviderType = ProviderType.LM_STUDIO,
    val baseUrl: String = ProviderType.LM_STUDIO.defaultBaseUrl,
    val model: String? = null,
    val apiKey: String = "",
    val apiKeyEdited: Boolean = false,
    val hasStoredKey: Boolean = false,
    val isDefault: Boolean = false,
    val peers: List<PeerDraft> = emptyList(),
    val availableModels: List<String> = emptyList(),
    val testing: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean? = null,
    val pendingCertTrust: PendingCertTrust? = null,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = baseUrl.isNotBlank()
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ApiProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val trustedCertStore: TrustedCertStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditState())
    val state = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>(Routes.PROFILE_ID)?.ifBlank { null }
        if (id != null) {
            viewModelScope.launch {
                repository.getProfile(id)?.let { profile ->
                    val peers = settingsRepository.a2aPeersOnce(profile.id).map { peer ->
                        PeerDraft(
                            id = peer.id,
                            name = peer.name,
                            baseUrl = peer.baseUrl,
                            enabled = peer.enabled,
                            hasStoredToken = repository.hasA2aPeerToken(profile.id, peer.id),
                        )
                    }
                    _state.update {
                        it.copy(
                            id = profile.id,
                            name = profile.name,
                            providerType = profile.providerType,
                            baseUrl = profile.baseUrl,
                            model = profile.model,
                            isDefault = profile.isDefault,
                            hasStoredKey = repository.hasApiKey(profile.id),
                            peers = peers,
                        )
                    }
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, testResult = null) }
    fun setModel(value: String) = _state.update { it.copy(model = value.ifBlank { null }) }
    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, apiKeyEdited = true) }
    fun setDefault(value: Boolean) = _state.update { it.copy(isDefault = value) }

    fun selectProvider(type: ProviderType) = _state.update {
        // Adopt the provider's canonical base URL only while the field is untouched
        // (blank, or still another preset's default). Overwriting a URL the user
        // already typed — or worse, appending to it — has bitten real setups twice.
        val untouched = it.baseUrl.isBlank() || it.baseUrl == it.providerType.defaultBaseUrl
        it.copy(
            providerType = type,
            baseUrl = if (untouched) type.defaultBaseUrl else it.baseUrl,
            testResult = null,
        )
    }

    fun selectModel(model: String) = _state.update { it.copy(model = model) }

    // ---- A2A peer table ----

    fun addPeer() = _state.update { st ->
        st.copy(peers = st.peers + PeerDraft(id = UUID.randomUUID().toString()))
    }

    fun removePeer(peerId: String) = _state.update { st ->
        st.copy(peers = st.peers.filterNot { it.id == peerId })
    }

    fun setPeerName(peerId: String, value: String) = updatePeer(peerId) { it.copy(name = value) }

    fun setPeerBaseUrl(peerId: String, value: String) = updatePeer(peerId) { it.copy(baseUrl = value) }

    fun setPeerEnabled(peerId: String, value: Boolean) = updatePeer(peerId) { it.copy(enabled = value) }

    fun setPeerToken(peerId: String, value: String) =
        updatePeer(peerId) { it.copy(token = value, tokenEdited = true) }

    private fun updatePeer(peerId: String, transform: (PeerDraft) -> PeerDraft) = _state.update { st ->
        st.copy(peers = st.peers.map { if (it.id == peerId) transform(it) else it })
    }

    fun test() {
        val current = _state.value
        _state.update { it.copy(testing = true, testResult = null, testSuccess = null, pendingCertTrust = null) }
        viewModelScope.launch {
            val probe = ApiProfile(
                id = current.id ?: "probe",
                name = current.name,
                providerType = current.providerType,
                baseUrl = current.baseUrl,
                model = current.model,
            )
            val key = effectiveKey(current)
            when (val result = repository.testConnection(probe, key)) {
                is NetworkResult.Success -> _state.update {
                    val models = result.data.map { m -> m.id }
                    it.copy(
                        testing = false,
                        testSuccess = true,
                        testResult = "Connected — ${models.size} model(s) available",
                        availableModels = models,
                        model = it.model ?: models.firstOrNull(),
                    )
                }
                is NetworkResult.Error -> {
                    val untrusted = findUntrustedCert(result.cause)
                    _state.update {
                        it.copy(
                            testing = false,
                            testSuccess = false,
                            testResult = result.message,
                            pendingCertTrust = untrusted?.let { u -> PendingCertTrust(u.host, u.fingerprintSha256) },
                        )
                    }
                }
            }
        }
    }

    /** User reviewed [PendingCertTrust]'s fingerprint and approved it — pin it, then retry. */
    fun trustPendingCertificate() {
        val pending = _state.value.pendingCertTrust ?: return
        trustedCertStore.trust(pending.host, pending.fingerprintSha256)
        _state.update { it.copy(pendingCertTrust = null) }
        test()
    }

    fun dismissPendingCertTrust() = _state.update { it.copy(pendingCertTrust = null) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val profile = ApiProfile(
                id = current.id ?: UUID.randomUUID().toString(),
                name = current.name.ifBlank { current.providerType.label },
                providerType = current.providerType,
                baseUrl = current.baseUrl.trim(),
                model = current.model,
                isDefault = current.isDefault,
            )
            // Pass null to leave an existing key untouched; pass text (even blank) to overwrite.
            val keyToSave = if (current.apiKeyEdited) current.apiKey.trim() else null
            repository.saveProfile(profile, keyToSave)
            // Peer table + per-peer tokens, for A2A profiles (gateways to talk
            // through) AND brain profiles (remote agents the model can contact as
            // tools). Token semantics mirror the profile key: null (untouched
            // field) keeps the stored one; blank text clears it.
            settingsRepository.setA2aPeers(
                profile.id,
                current.peers.map {
                    A2aPeer(id = it.id, name = it.name.trim(), baseUrl = it.baseUrl.trim(), enabled = it.enabled)
                },
            )
            current.peers.filter { it.tokenEdited }.forEach {
                repository.saveA2aPeerToken(profile.id, it.id, it.token.trim().ifBlank { null })
            }
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repository.deleteProfile(id)
            _state.update { it.copy(saved = true) }
        }
    }

    /** The key to use for a test: edited text, else the stored key. */
    private fun effectiveKey(state: ProfileEditState): String? = when {
        state.apiKeyEdited -> state.apiKey.trim().ifBlank { null }
        state.id != null -> repository.getApiKey(state.id)
        else -> null
    }
}
