package com.vela.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.vela.chat.data.local.entity.ApiProfileEntity
import com.vela.chat.data.local.entity.ConversationEntity
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.local.entity.FolderEntity
import com.vela.chat.data.local.entity.MessageEntity
import com.vela.chat.data.local.entity.MessageSearchResult
import com.vela.chat.data.local.entity.MessageStatsRow
import com.vela.chat.data.local.entity.ModelCacheEntity
import com.vela.chat.data.local.entity.PersonaEntity
import com.vela.chat.data.local.entity.PresetEntity
import com.vela.chat.data.local.entity.SavedPromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    /** Drawer list: every non-archived conversation with the latest message preview. */
    @Query(
        """
        SELECT c.id, c.title, c.folderId, c.pinned, c.archived, c.updatedAt,
               (SELECT m.content FROM messages m
                WHERE m.conversationId = c.id AND m.role != 'system'
                ORDER BY m.createdAt DESC LIMIT 1) AS preview
        FROM conversations c
        WHERE c.archived = 0
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun observeActive(): Flow<List<ConversationWithPreview>>

    @Query(
        """
        SELECT c.id, c.title, c.folderId, c.pinned, c.archived, c.updatedAt,
               (SELECT m.content FROM messages m
                WHERE m.conversationId = c.id AND m.role != 'system'
                ORDER BY m.createdAt DESC LIMIT 1) AS preview
        FROM conversations c
        WHERE c.archived = 1
        ORDER BY c.updatedAt DESC
        """,
    )
    fun observeArchived(): Flow<List<ConversationWithPreview>>

    /** Full-text-ish search across titles and message bodies. */
    @Query(
        """
        SELECT DISTINCT c.id, c.title, c.folderId, c.pinned, c.archived, c.updatedAt,
               (SELECT m.content FROM messages m
                WHERE m.conversationId = c.id AND m.role != 'system'
                ORDER BY m.createdAt DESC LIMIT 1) AS preview
        FROM conversations c
        LEFT JOIN messages m2 ON m2.conversationId = c.id
        WHERE c.archived = 0 AND (c.title LIKE '%' || :query || '%'
              OR m2.content LIKE '%' || :query || '%')
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<ConversationWithPreview>>

    @Query("UPDATE conversations SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET folderId = :folderId, updatedAt = :now WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?, now: Long)

    // ---- Bulk operations (multi-select in the drawer) ----

    @Query("UPDATE conversations SET folderId = :folderId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToFolderBulk(ids: List<String>, folderId: String?, now: Long)

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setArchivedBulk(ids: List<String>, archived: Boolean, now: Long)

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    suspend fun deleteBulk(ids: List<String>)

    /** Detach every conversation from a folder (used before deleting the folder). */
    @Query("UPDATE conversations SET folderId = NULL, updatedAt = :now WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    /** Every conversation, unfiltered (full-backup export). */
    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>
}

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Used by edit/regenerate: drop everything after a given message. */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :timestamp")
    suspend fun deleteAfter(conversationId: String, timestamp: Long)

    // ---- Nova 2.0: windowed loading, flags, stats, search ----

    /** Most recent [limit] messages, oldest-first (windowed chat loading). */
    @Query(
        "SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY createdAt DESC LIMIT :limit) ORDER BY createdAt ASC",
    )
    fun observeRecent(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    /** Aggregated token usage for a conversation (single row; zeros when empty). */
    @Query(
        "SELECT COALESCE(SUM(promptTokens), 0) AS promptTokens, " +
            "COALESCE(SUM(completionTokens), 0) AS completionTokens, " +
            "COALESCE(SUM(generationTimeMs), 0) AS generationTimeMs " +
            "FROM messages WHERE conversationId = :conversationId",
    )
    suspend fun stats(conversationId: String): MessageStatsRow

    /** Global message search with the parent conversation title for context. */
    @Query(
        """
        SELECT m.id AS messageId, m.conversationId AS conversationId, c.title AS conversationTitle,
               m.content AS content, m.role AS role, m.createdAt AS createdAt
        FROM messages m JOIN conversations c ON c.id = m.conversationId
        WHERE m.content LIKE '%' || :query || '%'
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageSearchResult>

    /** Every message, unfiltered (full-backup export). */
    @Query("SELECT * FROM messages")
    suspend fun getAll(): List<MessageEntity>
}

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ApiProfileDao {
    @Upsert
    suspend fun upsert(profile: ApiProfileEntity)

    @Query("SELECT * FROM api_profiles ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<ApiProfileEntity>>

    @Query("SELECT * FROM api_profiles ORDER BY isDefault DESC, name ASC")
    suspend fun getAll(): List<ApiProfileEntity>

    @Query("SELECT * FROM api_profiles WHERE id = :id")
    suspend fun getById(id: String): ApiProfileEntity?

    @Query("SELECT * FROM api_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ApiProfileEntity?

    @Query("SELECT * FROM api_profiles WHERE isDefault = 1 LIMIT 1")
    fun observeDefault(): Flow<ApiProfileEntity?>

    @Query("UPDATE api_profiles SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("DELETE FROM api_profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun setDefault(id: String) {
        clearDefaults()
        getById(id)?.let { upsert(it.copy(isDefault = true)) }
    }
}

@Dao
interface SavedPromptDao {
    @Upsert
    suspend fun upsert(prompt: SavedPromptEntity)

    @Query("SELECT * FROM saved_prompts ORDER BY favorite DESC, category ASC, title ASC")
    fun observeAll(): Flow<List<SavedPromptEntity>>

    @Query("SELECT COUNT(*) FROM saved_prompts")
    suspend fun count(): Int

    @Query("UPDATE saved_prompts SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM saved_prompts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ModelCacheDao {    @Upsert
    suspend fun upsertAll(entries: List<ModelCacheEntity>)

    @Query("SELECT * FROM model_cache WHERE profileId = :profileId")
    fun observeByProfile(profileId: String): Flow<List<ModelCacheEntity>>

    @Query("SELECT * FROM model_cache WHERE profileId = :profileId")
    suspend fun getByProfile(profileId: String): List<ModelCacheEntity>

    @Query("DELETE FROM model_cache WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)

    @Query("DELETE FROM model_cache")
    suspend fun deleteAll()
}

@Dao
interface PresetDao {
    @Upsert
    suspend fun upsert(preset: PresetEntity)

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PersonaDao {
    @Upsert
    suspend fun upsert(persona: PersonaEntity)

    @Query("SELECT * FROM personas ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): PersonaEntity?

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int

    @Query("UPDATE personas SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun setDefault(id: String) {
        clearDefaults()
        getById(id)?.let { upsert(it.copy(isDefault = true)) }
    }
}
