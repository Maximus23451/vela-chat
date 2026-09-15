package com.vela.chat

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vela.chat.data.local.dao.ApiProfileDao
import com.vela.chat.data.local.entity.ApiProfileEntity
import com.vela.chat.data.local.toDomain
import com.vela.chat.data.remote.OpenAiApi
import com.vela.chat.data.remote.dto.ChatCompletionRequest
import com.vela.chat.data.remote.dto.ChatCompletionResponse
import com.vela.chat.data.remote.dto.ModelsResponse
import com.vela.chat.data.secure.SecureStore
import com.vela.chat.data.settings.AccentColor
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.data.settings.SettingsRepository
import com.vela.chat.data.settings.ThemeMode
import com.vela.chat.data.settings.ThemePreset
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.ProviderType
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

class SettingsAndProfilePersistenceTest {

    // ---- Fakes ----

    class FakeDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initialPreferences)
        override val data: Flow<Preferences> = flow
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(flow.value)
            flow.value = next
            return next
        }
    }

    class FakeSharedPreferences : SharedPreferences {
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

            override fun putString(key: String, value: String?): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { tempMap[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { tempMap[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { toRemove.add(key); return this }
            override fun clear(): SharedPreferences.Editor { tempMap.clear(); toRemove.addAll(map.keys); return this }
            override fun commit(): Boolean {
                map.putAll(tempMap)
                toRemove.forEach { map.remove(it) }
                return true
            }
            override fun apply() {
                commit()
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class FakeApiProfileDao : ApiProfileDao {
        val list = mutableListOf<ApiProfileEntity>()
        private val flow = MutableStateFlow<List<ApiProfileEntity>>(emptyList())

        private fun emit() {
            flow.value = list.toList()
        }

        override suspend fun upsert(profile: ApiProfileEntity) {
            list.removeAll { it.id == profile.id }
            list.add(profile)
            emit()
        }

        override fun observeAll(): Flow<List<ApiProfileEntity>> = flow

        override suspend fun getAll(): List<ApiProfileEntity> = list

        override suspend fun getById(id: String): ApiProfileEntity? = list.find { it.id == id }

        override suspend fun getDefault(): ApiProfileEntity? = list.find { it.isDefault }

        override fun observeDefault(): Flow<ApiProfileEntity?> = flow.map { it.find { p -> p.isDefault } }

        override suspend fun clearDefaults() {
            for (i in list.indices) {
                if (list[i].isDefault) {
                    list[i] = list[i].copy(isDefault = false)
                }
            }
            emit()
        }

        override suspend fun delete(id: String) {
            list.removeAll { it.id == id }
            emit()
        }

        override suspend fun setDefault(id: String) {
            clearDefaults()
            val index = list.indexOfFirst { it.id == id }
            if (index != -1) {
                list[index] = list[index].copy(isDefault = true)
            }
            emit()
        }
    }

    class FakeOpenAiApi : OpenAiApi {
        override suspend fun listModels(url: String, authorization: String?): ModelsResponse {
            throw NotImplementedError()
        }
        override suspend fun chatCompletion(url: String, authorization: String?, body: ChatCompletionRequest): ChatCompletionResponse {
            throw NotImplementedError()
        }
    }

    // ---- Tests ----

    @Test
    fun `verify Settings Persistence`() = runBlocking {
        val dataStore = FakeDataStore()
        val repository = SettingsRepository(dataStore)

        // Set initial values
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAmoled(true)
        repository.setFontSizeScale(1.15f)
        repository.setLightThemePreset(ThemePreset.DRACULA)
        repository.setChatDensity(ChatDensity.COMPACT)
        repository.setCustomWallpaperPath("file:///dummy/wp.jpg")

        // Retrieve and assert
        val result = repository.settings.first()
        assertEquals(ThemeMode.DARK, result.themeMode)
        assertTrue(result.amoledBlack)
        assertEquals(1.15f, result.fontSizeScale)
        assertEquals(ThemePreset.DRACULA, result.lightThemePreset)
        assertEquals(ChatDensity.COMPACT, result.chatDensity)
        assertEquals("file:///dummy/wp.jpg", result.customWallpaperPath)
    }

    @Test
    fun `verify API Key Persistence`() {
        val fakePrefs = FakeSharedPreferences()
        // Pass a dummy mockContext (subclassed MockContext is perfect for JVM test since we don't call methods on it)
        val mockContext = android.content.ContextWrapper(null)
        val secureStore = SecureStore(mockContext).apply { setPrefsForTest(fakePrefs) }

        // Save key
        secureStore.saveApiKey("profile_123", "secret-test-key")
        assertTrue(secureStore.hasApiKey("profile_123"))
        assertEquals("secret-test-key", secureStore.getApiKey("profile_123"))

        // Clear key
        secureStore.saveApiKey("profile_123", null)
        assertFalse(secureStore.hasApiKey("profile_123"))
        assertNull(secureStore.getApiKey("profile_123"))
    }

    @Test
    fun `verify Profile Persistence and Isolation`() = runBlocking {
        val fakeDao = FakeApiProfileDao()
        val fakePrefs = FakeSharedPreferences()
        val mockContext = android.content.ContextWrapper(null)
        val secureStore = SecureStore(mockContext).apply { setPrefsForTest(fakePrefs) }
        val mockApi = FakeOpenAiApi()

        val repository = ApiProfileRepository(fakeDao, secureStore, mockApi)

        val profileA = ApiProfile("id_a", "Profile A", ProviderType.OPENAI, "https://api.openai.com/v1", null, true)
        val profileB = ApiProfile("id_b", "Profile B", ProviderType.LM_STUDIO, "http://localhost:1234/v1", null, false)

        // Save profiles
        repository.saveProfile(profileA, "key-a")
        repository.saveProfile(profileB, "key-b")

        // Assert persistence
        val savedA = repository.getProfile("id_a")
        val savedB = repository.getProfile("id_b")
        assertEquals("Profile A", savedA?.name)
        assertEquals("Profile B", savedB?.name)

        // Assert default
        val defaultProf = repository.defaultProfile.first()
        assertEquals("id_a", defaultProf?.id)

        // Assert API key isolation
        assertEquals("key-a", repository.getApiKey("id_a"))
        assertEquals("key-b", repository.getApiKey("id_b"))

        // Change default
        repository.setDefault("id_b")
        val newDefault = repository.defaultProfile.first()
        assertEquals("id_b", newDefault?.id)
    }

    @Test
    fun `verify Model Selection Persistence`() = runBlocking {
        val fakeDao = FakeApiProfileDao()
        val fakePrefs = FakeSharedPreferences()
        val mockContext = android.content.ContextWrapper(null)
        val secureStore = SecureStore(mockContext).apply { setPrefsForTest(fakePrefs) }
        val mockApi = FakeOpenAiApi()

        val repository = ApiProfileRepository(fakeDao, secureStore, mockApi)
        val profile = ApiProfile("id_x", "Profile X", ProviderType.LM_STUDIO, "http://localhost:1234/v1", null, true)

        repository.saveProfile(profile, null)

        // Select a model
        val updatedProfile = profile.copy(model = "llama-3-8b")
        repository.saveProfile(updatedProfile, null)

        // Verify model is loaded
        val reloaded = repository.getProfile("id_x")
        assertEquals("llama-3-8b", reloaded?.model)
    }

    @Test
    fun `verify Context Limit Toggle Persistence`() = runBlocking {
        val dataStore = FakeDataStore()
        val repository = SettingsRepository(dataStore)

        val params = GenerationParams(limitMaxTokens = false, maxTokens = 4096)
        repository.setDefaultParams(params)

        val loaded = repository.settings.first().defaultParams
        assertFalse(loaded.limitMaxTokens)
        assertEquals(4096, loaded.maxTokens)
    }
}
