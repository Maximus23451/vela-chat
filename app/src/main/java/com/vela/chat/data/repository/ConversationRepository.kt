package com.vela.chat.data.repository

import com.vela.chat.data.AppJson
import com.vela.chat.data.export.ChatExporter
import com.vela.chat.data.local.dao.ConversationDao
import com.vela.chat.data.local.dao.FolderDao
import com.vela.chat.data.local.dao.MessageDao
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.local.entity.MessageSearchResult
import com.vela.chat.data.local.toDomain
import com.vela.chat.data.local.toEntity
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Folder
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.TokenStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.catch

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val folderDao: FolderDao,
) {
    fun observeActive(): Flow<List<ConversationWithPreview>> = conversationDao.observeActive().catch { emit(emptyList()) }
    fun observeArchived(): Flow<List<ConversationWithPreview>> = conversationDao.observeArchived().catch { emit(emptyList()) }
    fun search(query: String): Flow<List<ConversationWithPreview>> = conversationDao.search(query).catch { emit(emptyList()) }

    fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }.catch { emit(emptyList()) }

    fun observeConversation(id: String): Flow<Conversation?> =
        conversationDao.observeById(id).map { it?.toDomain() }.catch { emit(null) }

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId).map { list -> list.map { it.toDomain() } }.catch { emit(emptyList()) }

    suspend fun getConversation(id: String): Conversation? = runCatching {
        conversationDao.getById(id)?.toDomain()
    }.getOrNull()

    suspend fun getMessages(conversationId: String): List<Message> = runCatching {
        messageDao.getForConversation(conversationId).map { it.toDomain() }
    }.getOrDefault(emptyList())

    suspend fun createConversation(
        title: String = "New chat",
        profileId: String? = null,
        model: String? = null,
        systemPrompt: String? = null,
        params: GenerationParams? = null,
        folderId: String? = null,
        personaId: String? = null,
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            profileId = profileId,
            model = model,
            systemPrompt = systemPrompt,
            params = params,
            folderId = folderId,
            personaId = personaId,
            createdAt = now,
            updatedAt = now,
        )
        runCatching { conversationDao.upsert(conversation.toEntity()) }
        return conversation
    }

    suspend fun updateConversation(conversation: Conversation) {
        runCatching {
            conversationDao.upsert(conversation.copy(updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    suspend fun rename(id: String, title: String) {
        runCatching {
            conversationDao.rename(id, title, System.currentTimeMillis())
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        runCatching {
            conversationDao.setPinned(id, pinned, System.currentTimeMillis())
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        runCatching {
            conversationDao.setArchived(id, archived, System.currentTimeMillis())
        }
    }

    suspend fun moveToFolder(id: String, folderId: String?) {
        runCatching {
            conversationDao.moveToFolder(id, folderId, System.currentTimeMillis())
        }
    }

    // ---- Bulk operations (drawer multi-select) ----

    suspend fun moveToFolder(ids: List<String>, folderId: String?) {
        if (ids.isEmpty()) return
        runCatching {
            conversationDao.moveToFolderBulk(ids, folderId, System.currentTimeMillis())
        }
    }

    suspend fun setArchived(ids: List<String>, archived: Boolean) {
        if (ids.isEmpty()) return
        runCatching {
            conversationDao.setArchivedBulk(ids, archived, System.currentTimeMillis())
        }
    }

    suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        runCatching {
            conversationDao.deleteBulk(ids)
        }
    }

    suspend fun touch(id: String) {
        runCatching {
            conversationDao.touch(id, System.currentTimeMillis())
        }
    }

    suspend fun delete(id: String) {
        runCatching {
            conversationDao.delete(id)
        }
    }

    suspend fun deleteAll() {
        runCatching {
            conversationDao.deleteAll()
        }
    }

    // ---- Messages ----

    suspend fun addMessage(message: Message) {
        runCatching {
            messageDao.insert(message.toEntity())
            conversationDao.touch(message.conversationId, System.currentTimeMillis())
        }
    }

    suspend fun updateMessage(message: Message) {
        runCatching {
            messageDao.update(message.toEntity())
        }
    }

    suspend fun deleteMessage(message: Message) {
        runCatching {
            messageDao.delete(message.toEntity())
        }
    }

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) {
        runCatching {
            messageDao.deleteAfter(conversationId, timestamp)
        }
    }

    // ---- Folders ----

    suspend fun createFolder(name: String): Folder {
        val folder = Folder(id = UUID.randomUUID().toString(), name = name)
        runCatching { folderDao.upsert(folder.toEntity()) }
        return folder
    }

    suspend fun renameFolder(folder: Folder, name: String) {
        runCatching {
            folderDao.upsert(folder.copy(name = name).toEntity())
        }
    }

    suspend fun deleteFolder(id: String) {
        runCatching {
            // Detach the folder's conversations first so they fall back to the
            // top-level list instead of being orphaned (folderId pointing nowhere).
            conversationDao.clearFolder(id, System.currentTimeMillis())
            folderDao.delete(id)
        }
    }

    // ---- Nova 2.0: message flags, stats, windowed loading, global search, import ----

    fun observeRecentMessages(conversationId: String, limit: Int): Flow<List<Message>> =
        messageDao.observeRecent(conversationId, limit)
            .map { list -> list.map { it.toDomain() } }
            .catch { emit(emptyList()) }

    suspend fun setMessageFavorite(messageId: String, favorite: Boolean) {
        runCatching { messageDao.setFavorite(messageId, favorite) }
    }

    suspend fun setMessagePinned(messageId: String, pinned: Boolean) {
        runCatching { messageDao.setPinned(messageId, pinned) }
    }

    /** Aggregated token usage for a conversation; null when the row can't be read. */
    suspend fun tokenStats(conversationId: String): TokenStats? = runCatching {
        val row = messageDao.stats(conversationId)
        val total = row.promptTokens + row.completionTokens
        TokenStats(
            promptTokens = row.promptTokens,
            completionTokens = row.completionTokens,
            totalTokens = total,
            generationTimeMs = row.generationTimeMs,
            avgTokensPerSecond = if (row.completionTokens > 0 && row.generationTimeMs > 0) {
                row.completionTokens / (row.generationTimeMs / 1000f)
            } else 0f,
        )
    }.getOrNull()

    /** Global message search (global search screen). */
    suspend fun searchMessages(query: String, limit: Int = 50): List<MessageSearchResult> = runCatching {
        if (query.isBlank()) emptyList() else messageDao.searchMessages(query.trim(), limit)
    }.getOrDefault(emptyList())

    suspend fun updateFolder(folder: Folder) {
        runCatching { folderDao.upsert(folder.toEntity()) }
    }

    /**
     * Imports a conversation from a JSON string in the [ChatExporter.ConversationExport]
     * format (as produced by the app's JSON export). Returns the new conversation id.
     */
    // ---- Full backup (encrypted export/import: all chat data, ids preserved) ----

    /** Everything needed for a full-fidelity backup — every folder, conversation and message. */
    suspend fun getAllForBackup(): FullChatData = runCatching {
        FullChatData(
            folders = folderDao.getAll().map { it.toDomain() },
            conversations = conversationDao.getAll().map { it.toDomain() },
            messages = messageDao.getAll().map { it.toDomain() },
        )
    }.getOrElse { FullChatData(emptyList(), emptyList(), emptyList()) }

    /**
     * Restores one folder/conversation/message verbatim (original id + timestamps),
     * so a restore is idempotent and FK-safe when called in this order: folders,
     * then conversations, then messages (messages FK-reference their conversation).
     */
    suspend fun restoreFolder(folder: Folder) = runCatching { folderDao.upsert(folder.toEntity()) }
    suspend fun restoreConversation(conversation: Conversation) = runCatching { conversationDao.upsert(conversation.toEntity()) }
    suspend fun restoreMessage(message: Message) = runCatching { messageDao.insert(message.toEntity()) }

    suspend fun importConversation(json: String): String? = runCatching {
        val export = AppJson.decodeFromString<ChatExporter.ConversationExport>(json)
        val now = System.currentTimeMillis()
        val conversation = createConversation(
            title = export.title.ifBlank { "Imported chat" },
        )
        export.messages
            .map { Message(id = UUID.randomUUID().toString(), conversationId = conversation.id, role = Role.from(it.role), content = it.content, createdAt = it.createdAt) }
            .forEach { messageDao.insert(it.toEntity()) }
        conversationDao.touch(conversation.id, now)
        conversation.id
    }.getOrNull()
}

/** All chat data for a full backup: every folder, conversation and message. */
data class FullChatData(
    val folders: List<Folder>,
    val conversations: List<Conversation>,
    val messages: List<Message>,
)
