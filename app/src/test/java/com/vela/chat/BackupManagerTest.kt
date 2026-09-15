package com.vela.chat

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vela.chat.data.local.dao.ApiProfileDao
import com.vela.chat.data.local.dao.ConversationDao
import com.vela.chat.data.local.dao.FolderDao
import com.vela.chat.data.local.dao.MessageDao
import com.vela.chat.data.local.dao.PersonaDao
import com.vela.chat.data.local.dao.PresetDao
import com.vela.chat.data.local.dao.SavedPromptDao
import com.vela.chat.data.local.entity.ApiProfileEntity
import com.vela.chat.data.local.entity.ConversationEntity
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.local.entity.FolderEntity
import com.vela.chat.data.local.entity.MessageEntity
import com.vela.chat.data.local.entity.MessageSearchResult
import com.vela.chat.data.local.entity.MessageStatsRow
import com.vela.chat.data.local.entity.PersonaEntity
import com.vela.chat.data.local.entity.PresetEntity
import com.vela.chat.data.local.entity.SavedPromptEntity
import com.vela.chat.data.remote.OpenAiApi
import com.vela.chat.data.remote.dto.ChatCompletionRequest
import com.vela.chat.data.remote.dto.ChatCompletionResponse
import com.vela.chat.data.remote.dto.ModelsResponse
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.repository.ConversationRepository
import com.vela.chat.data.repository.LibraryRepository
import com.vela.chat.data.repository.PersonaRepository
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.domain.model.A2aPeer
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.ui.settings.BackupManager
import com.vela.chat.util.AppLockController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips the full encrypted backup (conversations, messages, profiles + API
 * keys + A2A peer tokens, personas, prompts, PIN hash) through export and import,
 * using the same hand-rolled-fake style as [SettingsAndProfilePersistenceTest] —
 * plain JVM, no Room/Robolectric.
 */
class BackupManagerTest {

    // ---- Minimal fake DAOs (only what BackupManager's paths touch) ----

    private class FakeConversationDao : ConversationDao {
        val list = mutableListOf<ConversationEntity>()
        override suspend fun upsert(conversation: ConversationEntity) {
            list.removeAll { it.id == conversation.id }
            list.add(conversation)
        }
        override suspend fun getById(id: String) = list.find { it.id == id }
        override fun observeById(id: String): Flow<ConversationEntity?> = throw NotImplementedError()
        override fun observeActive(): Flow<List<ConversationWithPreview>> = throw NotImplementedError()
        override fun observeArchived(): Flow<List<ConversationWithPreview>> = throw NotImplementedError()
        override fun search(query: String): Flow<List<ConversationWithPreview>> = throw NotImplementedError()
        override suspend fun setPinned(id: String, pinned: Boolean, now: Long) {}
        override suspend fun setArchived(id: String, archived: Boolean, now: Long) {}
        override suspend fun rename(id: String, title: String, now: Long) {}
        override suspend fun moveToFolder(id: String, folderId: String?, now: Long) {}
        override suspend fun moveToFolderBulk(ids: List<String>, folderId: String?, now: Long) {}
        override suspend fun setArchivedBulk(ids: List<String>, archived: Boolean, now: Long) {}
        override suspend fun deleteBulk(ids: List<String>) {}
        override suspend fun clearFolder(folderId: String, now: Long) {}
        override suspend fun touch(id: String, now: Long) {}
        override suspend fun delete(id: String) { list.removeAll { it.id == id } }
        override suspend fun deleteAll() { list.clear() }
        override suspend fun getAll(): List<ConversationEntity> = list
    }

    private class FakeMessageDao : MessageDao {
        val list = mutableListOf<MessageEntity>()
        override suspend fun insert(message: MessageEntity) {
            list.removeAll { it.id == message.id }
            list.add(message)
        }
        override suspend fun update(message: MessageEntity) { insert(message) }
        override fun observeForConversation(conversationId: String): Flow<List<MessageEntity>> = throw NotImplementedError()
        override suspend fun getForConversation(conversationId: String) = list.filter { it.conversationId == conversationId }
        override suspend fun getById(id: String) = list.find { it.id == id }
        override suspend fun delete(message: MessageEntity) { list.removeAll { it.id == message.id } }
        override suspend fun deleteById(id: String) { list.removeAll { it.id == id } }
        override suspend fun deleteAfter(conversationId: String, timestamp: Long) {}
        override fun observeRecent(conversationId: String, limit: Int): Flow<List<MessageEntity>> = throw NotImplementedError()
        override suspend fun setFavorite(id: String, favorite: Boolean) {}
        override suspend fun setPinned(id: String, pinned: Boolean) {}
        override suspend fun stats(conversationId: String) = MessageStatsRow()
        override suspend fun searchMessages(query: String, limit: Int) = emptyList<MessageSearchResult>()
        override suspend fun getAll(): List<MessageEntity> = list
    }

    private class FakeFolderDao : FolderDao {
        val list = mutableListOf<FolderEntity>()
        override suspend fun upsert(folder: FolderEntity) {
            list.removeAll { it.id == folder.id }
            list.add(folder)
        }
        override fun observeAll(): Flow<List<FolderEntity>> = throw NotImplementedError()
        override suspend fun getAll(): List<FolderEntity> = list
        override suspend fun delete(id: String) { list.removeAll { it.id == id } }
    }

    private class FakePersonaDao : PersonaDao {
        val list = mutableListOf<PersonaEntity>()
        override suspend fun upsert(persona: PersonaEntity) {
            list.removeAll { it.id == persona.id }
            list.add(persona)
        }
        override fun observeAll(): Flow<List<PersonaEntity>> = throw NotImplementedError()
        override suspend fun getById(id: String) = list.find { it.id == id }
        override suspend fun getAll(): List<PersonaEntity> = list
        override suspend fun getDefault() = list.find { it.isDefault }
        override suspend fun count() = list.size
        override suspend fun clearDefaults() {
            for (i in list.indices) if (list[i].isDefault) list[i] = list[i].copy(isDefault = false)
        }
        override suspend fun delete(id: String) { list.removeAll { it.id == id } }
    }

    private class FakeSavedPromptDao : SavedPromptDao {
        val list = mutableListOf<SavedPromptEntity>()
        override suspend fun upsert(prompt: SavedPromptEntity) {
            list.removeAll { it.id == prompt.id }
            list.add(prompt)
        }
        override fun observeAll(): Flow<List<SavedPromptEntity>> = MutableStateFlow(list.toList())
        override suspend fun count() = list.size
        override suspend fun setFavorite(id: String, favorite: Boolean) {}
        override suspend fun delete(id: String) { list.removeAll { it.id == id } }
    }

    private class FakePresetDao : PresetDao {
        override suspend fun upsert(preset: PresetEntity) {}
        override fun observeAll(): Flow<List<PresetEntity>> = MutableStateFlow(emptyList())
        override suspend fun delete(id: String) {}
    }

    private class FakeApiProfileDao : ApiProfileDao {
        val list = mutableListOf<ApiProfileEntity>()
        private val flow = MutableStateFlow<List<ApiProfileEntity>>(emptyList())
        private fun emit() { flow.value = list.toList() }
        override suspend fun upsert(profile: ApiProfileEntity) {
            list.removeAll { it.id == profile.id }
            list.add(profile)
            emit()
        }
        override fun observeAll(): Flow<List<ApiProfileEntity>> = flow
        override suspend fun getAll(): List<ApiProfileEntity> = list
        override suspend fun getById(id: String) = list.find { it.id == id }
        override suspend fun getDefault() = list.find { it.isDefault }
        override fun observeDefault(): Flow<ApiProfileEntity?> = flow.map { it.find { p -> p.isDefault } }
        override suspend fun clearDefaults() {
            for (i in list.indices) if (list[i].isDefault) list[i] = list[i].copy(isDefault = false)
            emit()
        }
        override suspend fun delete(id: String) { list.removeAll { it.id == id }; emit() }
        override suspend fun setDefault(id: String) {
            clearDefaults()
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) list[idx] = list[idx].copy(isDefault = true)
            emit()
        }
    }

    private class FakeOpenAiApi : OpenAiApi {
        override suspend fun listModels(url: String, authorization: String?): ModelsResponse = throw NotImplementedError()
        override suspend fun chatCompletion(url: String, authorization: String?, body: ChatCompletionRequest): ChatCompletionResponse = throw NotImplementedError()
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val flow = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = flow
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(flow.value)
            flow.value = next
            return next
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        inner class Editor : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            override fun putString(key: String, value: String?) = apply { tempMap[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { tempMap[key] = values }
            override fun putInt(key: String, value: Int) = apply { tempMap[key] = value }
            override fun putLong(key: String, value: Long) = apply { tempMap[key] = value }
            override fun putFloat(key: String, value: Float) = apply { tempMap[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { tempMap[key] = value }
            override fun remove(key: String) = apply { toRemove.add(key) }
            override fun clear() = apply { tempMap.clear(); toRemove.addAll(map.keys) }
            override fun commit(): Boolean {
                map.putAll(tempMap)
                toRemove.forEach { map.remove(it) }
                return true
            }
            override fun apply() { commit() }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    // ---- Wiring ----

    private class Fixture {
        val conversationDao = FakeConversationDao()
        val messageDao = FakeMessageDao()
        val folderDao = FakeFolderDao()
        val personaDao = FakePersonaDao()
        val promptDao = FakeSavedPromptDao()
        val apiProfileDao = FakeApiProfileDao()
        val secureStore = SecureStore(android.content.ContextWrapper(null)).apply { setPrefsForTest(FakeSharedPreferences()) }
        val settingsRepository = SettingsRepository(FakeDataStore())
        val libraryRepository = LibraryRepository(promptDao, FakePresetDao())
        val profileRepository = ApiProfileRepository(apiProfileDao, secureStore, FakeOpenAiApi())
        val conversationRepository = ConversationRepository(conversationDao, messageDao, folderDao)
        val personaRepository = PersonaRepository(personaDao)
        val appLockController = AppLockController(android.content.ContextWrapper(null), secureStore, settingsRepository)
        val backupManager = BackupManager(
            settingsRepository, libraryRepository, profileRepository,
            conversationRepository, personaRepository, appLockController,
        )
    }

    @Test
    fun `full backup round trips conversations, keys, peers, personas and prompts`() = runBlocking {
        val src = Fixture()

        // Seed one profile (with an API key and an enabled A2A peer + token).
        // 192.0.2.0/24 is the RFC 5737 documentation range — never a real address.
        src.profileRepository.saveProfile(
            ApiProfile(id = "profile-1", name = "Test Agent", providerType = ProviderType.A2A_AGENT, baseUrl = "http://192.0.2.10:9900/", isDefault = true),
            apiKey = "sk-secret-key", // pragma: allowlist secret
        )
        src.settingsRepository.setA2aPeers("profile-1", listOf(A2aPeer(id = "peer-1", name = "Gateway", baseUrl = "http://192.0.2.10:9900", enabled = true)))
        src.profileRepository.saveA2aPeerToken("profile-1", "peer-1", "peer-bearer-token")

        // Seed a persona, a prompt, a conversation with two messages.
        src.personaRepository.upsert(AgentPersona(id = "persona-1", name = "Test Persona", systemPrompt = "Speak in few words.", isDefault = true))
        src.libraryRepository.savePrompt(SavedPrompt(id = "prompt-1", title = "Summarize", content = "Summarize:", category = "Writing"))
        val convoId = src.conversationRepository.createConversation(title = "Test chat", profileId = "profile-1", personaId = "persona-1").id
        src.conversationRepository.addMessage(Message(id = "msg-1", conversationId = convoId, role = Role.USER, content = "hi"))
        src.conversationRepository.addMessage(Message(id = "msg-2", conversationId = convoId, role = Role.ASSISTANT, content = "hello"))

        // Set a PIN, so its hash blob travels with the backup.
        src.appLockController.setPin("1234")
        val originalPinBlob = src.appLockController.exportPinHashBlob()

        val exportResult = src.backupManager.exportBackup("correct horse battery staple".toCharArray())
        assertTrue("export should succeed: ${exportResult.exceptionOrNull()}", exportResult.isSuccess)
        val envelope = exportResult.getOrThrow()

        // Restore into a completely fresh fixture (a different device).
        val dst = Fixture()
        val importResult = dst.backupManager.importBackup(envelope, "correct horse battery staple".toCharArray())
        assertTrue("import should succeed: ${importResult.exceptionOrNull()}", importResult.isSuccess)
        val summary = importResult.getOrThrow()

        assertEquals(1, summary.conversationsRestored)
        assertEquals(2, summary.messagesRestored)
        assertEquals(1, summary.profilesRestored)
        assertEquals(1, summary.personasRestored)
        assertEquals(1, summary.promptsRestored)

        // Conversation + messages restored with original ids intact.
        val restoredConvo = dst.conversationRepository.getConversation(convoId)
        assertEquals("Test chat", restoredConvo?.title)
        assertEquals("profile-1", restoredConvo?.profileId)
        val restoredMessages = dst.conversationRepository.getMessages(convoId)
        assertEquals(setOf("hi", "hello"), restoredMessages.map { it.content }.toSet())

        // API key and peer token restored (the whole point of the "full" backup).
        assertEquals("sk-secret-key", dst.profileRepository.getApiKey("profile-1"))
        assertEquals("peer-bearer-token", dst.profileRepository.getA2aPeerToken("profile-1", "peer-1"))
        val restoredPeers = dst.settingsRepository.a2aPeersOnce("profile-1")
        assertEquals(1, restoredPeers.size)
        assertEquals("Gateway", restoredPeers.first().name)

        // PIN hash blob restored verbatim; the original PIN still verifies against it.
        assertEquals(originalPinBlob, dst.appLockController.exportPinHashBlob())
        assertTrue(dst.appLockController.verifyPin("1234"))
        assertFalse(dst.appLockController.verifyPin("0000"))
    }

    @Test
    fun `full backup with a real PIN hash keeps PIN lock mode after restore`() = runBlocking {
        val src = Fixture()
        src.appLockController.setPin("1234")
        src.settingsRepository.setAppLockMode(com.vela.chat.data.settings.AppLockMode.PIN)
        val envelope = src.backupManager.exportBackup("a-passphrase".toCharArray()).getOrThrow()

        val dst = Fixture()
        val result = dst.backupManager.importBackup(envelope, "a-passphrase".toCharArray())

        assertTrue("import should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(com.vela.chat.data.settings.AppLockMode.PIN, dst.settingsRepository.settings.first().appLockMode)
        assertTrue(dst.appLockController.verifyPin("1234"))
    }

    @Test
    fun `legacy import never leaves PIN lock mode with no PIN to satisfy it`() = runBlocking {
        // Pre-2.3 backups could carry appLockMode=PIN in their settings snapshot but never
        // exported a PIN hash at all (that mechanism didn't exist yet) — importing one must
        // not brick the app behind an unsatisfiable PIN screen.
        val legacyJson = """
            {"version":1,"settings":{"appLockMode":"PIN"},"prompts":[],"profiles":[]}
        """.trimIndent()
        val dst = Fixture()

        val result = dst.backupManager.importBackup(legacyJson, "unused".toCharArray())

        assertTrue(result.isSuccess)
        assertFalse(dst.appLockController.hasPin())
        assertEquals(com.vela.chat.data.settings.AppLockMode.NONE, dst.settingsRepository.settings.first().appLockMode)
    }

    @Test
    fun `wrong passphrase fails import and touches nothing`() = runBlocking {
        val src = Fixture()
        src.profileRepository.saveProfile(ApiProfile(id = "p1", name = "X", providerType = ProviderType.LM_STUDIO, isDefault = true), apiKey = "sk-key") // pragma: allowlist secret
        val envelope = src.backupManager.exportBackup("right-passphrase".toCharArray()).getOrThrow()

        val dst = Fixture()
        val result = dst.backupManager.importBackup(envelope, "wrong-passphrase".toCharArray())

        assertTrue(result.isFailure)
        assertNull(dst.profileRepository.getProfile("p1"))
    }

    @Test
    fun `export rejects a passphrase shorter than the minimum`() = runBlocking {
        val fixture = Fixture()

        val result = fixture.backupManager.exportBackup("short".toCharArray())

        assertTrue(result.isFailure)
    }

    @Test
    fun `legacy plaintext backup still imports without a passphrase`() = runBlocking {
        val legacyJson = """
            {"version":1,"settings":null,
             "prompts":[{"title":"Old prompt","content":"do a thing","category":"General","favorite":false}],
             "profiles":[{"name":"Legacy LM Studio","providerType":"LM_STUDIO","baseUrl":"http://localhost:1234/v1","isDefault":false}]}
        """.trimIndent()
        val dst = Fixture()

        val result = dst.backupManager.importBackup(legacyJson, "unused".toCharArray())

        assertTrue("legacy import should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(1, result.getOrThrow().promptsRestored)
        assertEquals(1, result.getOrThrow().profilesRestored)
        // Legacy backups never carried conversations at all.
        assertTrue(dst.conversationRepository.getAllForBackup().conversations.isEmpty())
        // Legacy profiles are keyless by design — nothing to restore.
        val restoredProfile = dst.apiProfileDao.getAll().first()
        assertNull(dst.profileRepository.getApiKey(restoredProfile.id))
    }
}
