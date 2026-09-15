package com.vela.chat.data.repository

import com.vela.chat.data.AppJson
import com.vela.chat.data.local.dao.PresetDao
import com.vela.chat.data.local.dao.SavedPromptDao
import com.vela.chat.data.local.toDomain
import com.vela.chat.data.local.toEntity
import com.vela.chat.domain.model.Preset
import com.vela.chat.domain.model.SavedPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.catch

/** Stores reusable prompts (prompt library / quick prompts) and parameter presets. */
@Singleton
class LibraryRepository @Inject constructor(
    private val promptDao: SavedPromptDao,
    private val presetDao: PresetDao,
) {
    val prompts: Flow<List<SavedPrompt>> =
        promptDao.observeAll().map { list -> list.map { it.toDomain() } }.catch { emit(emptyList()) }

    val presets: Flow<List<Preset>> =
        presetDao.observeAll().map { list -> list.map { it.toDomain() } }.catch { emit(emptyList()) }

    suspend fun savePrompt(prompt: SavedPrompt) {
        runCatching { promptDao.upsert(prompt.toEntity()) }
    }

    suspend fun newPrompt(title: String, content: String, category: String): SavedPrompt {
        val prompt = SavedPrompt(UUID.randomUUID().toString(), title, content, category)
        runCatching { promptDao.upsert(prompt.toEntity()) }
        return prompt
    }

    suspend fun deletePrompt(id: String) {
        runCatching { promptDao.delete(id) }
    }

    // ---- Nova 2.0: favorites + prompt library import/export ----

    /** Marks a prompt as (un)favorited; favorites sort first in the library. */
    suspend fun setPromptFavorite(id: String, favorite: Boolean) {
        runCatching { promptDao.setFavorite(id, favorite) }
    }

    /**
     * Serializes the whole prompt library to JSON (title, content, category,
     * favorite). Content is user data only — nothing sensitive is included.
     */
    suspend fun exportPromptsJson(): String = withContext(Dispatchers.IO) {
        val items = runCatching { promptDao.observeAll().first() }.getOrDefault(emptyList())
            .map { it.toDomain() }
            .map { PromptExport(title = it.title, content = it.content, category = it.category, favorite = it.favorite) }
        AppJson.encodeToString(PromptBundle(prompts = items))
    }

    /**
     * Imports prompts from [exportPromptsJson] output. Every imported prompt gets a
     * fresh id, so re-importing never overwrites existing entries. Returns the
     * number of prompts added; a malformed payload yields a failed [Result].
     */
    suspend fun importPromptsJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = AppJson.decodeFromString<PromptBundle>(json)
            bundle.prompts.forEach { item ->
                promptDao.upsert(
                    SavedPrompt(
                        id = UUID.randomUUID().toString(),
                        title = item.title,
                        content = item.content,
                        category = item.category.ifBlank { DEFAULT_CATEGORY },
                        favorite = item.favorite,
                    ).toEntity(),
                )
            }
            bundle.prompts.size
        }
    }

    /** Every prompt, unfiltered (full-backup export). */
    suspend fun getAllPrompts(): List<SavedPrompt> = runCatching {
        promptDao.observeAll().first().map { it.toDomain() }
    }.getOrDefault(emptyList())

    /** Restores one prompt verbatim (original id), so a full-backup restore is idempotent. */
    suspend fun restorePrompt(prompt: SavedPrompt) {
        runCatching { promptDao.upsert(prompt.toEntity()) }
    }

    suspend fun savePreset(preset: Preset) {
        runCatching { presetDao.upsert(preset.toEntity()) }
    }

    suspend fun deletePreset(id: String) {
        runCatching { presetDao.delete(id) }
    }

    /** Seed a small starter prompt library the first time the app runs. */
    suspend fun seedDefaultsIfEmpty() {
        runCatching {
            if (promptDao.count() > 0) return
            DEFAULTS.forEach { (title, category, content) ->
                promptDao.upsert(SavedPrompt(UUID.randomUUID().toString(), title, content, category).toEntity())
            }
        }
    }

    private companion object {
        const val DEFAULT_CATEGORY = "General"

        @Serializable
        data class PromptBundle(val version: Int = 1, val prompts: List<PromptExport> = emptyList())

        @Serializable
        data class PromptExport(
            val title: String,
            val content: String,
            val category: String = DEFAULT_CATEGORY,
            val favorite: Boolean = false,
        )

        val DEFAULTS = listOf(
            Triple(
                "Summarize",
                "Writing",
                "Summarize the following text in 5 concise bullet points:\n\n",
            ),
            Triple(
                "Explain like I'm five",
                "Learning",
                "Explain the following concept in simple terms a five-year-old could understand:\n\n",
            ),
            Triple(
                "Code review",
                "Coding",
                "Review this code for bugs, edge cases, and readability. Suggest concrete improvements:\n\n```\n\n```",
            ),
            Triple(
                "Refactor",
                "Coding",
                "Refactor this code to be cleaner and more idiomatic without changing behavior:\n\n```\n\n```",
            ),
        )
    }
}
