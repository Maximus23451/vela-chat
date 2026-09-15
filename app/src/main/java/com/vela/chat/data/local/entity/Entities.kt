package com.vela.chat.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    /** ARGB color int for the folder chip; 0 = theme default. */
    val color: Int = 0,
    /** Material icon name for the folder; empty = default icon. */
    val icon: String = "",
    val createdAt: Long,
)

@Entity(
    tableName = "conversations",
    indices = [Index("folderId"), Index("updatedAt"), Index("pinned")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val profileId: String?,
    val model: String?,
    val systemPrompt: String?,
    /** GenerationParams serialized to JSON, or null to inherit global defaults. */
    val paramsJson: String?,
    /** Agent persona bound to this conversation; null = use the default persona. */
    val personaId: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String?,
    val model: String?,
    /** List<Attachment> serialized to JSON. */
    val attachmentsJson: String?,
    val isError: Boolean,
    val createdAt: Long,
    // ---- Usage statistics (v2; captured from the final streaming chunk) ----
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val generationTimeMs: Long = 0,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
)

@Entity(tableName = "api_profiles")
data class ApiProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val model: String?,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "saved_prompts")
data class SavedPromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val favorite: Boolean = false,
    val createdAt: Long,
)

/**
 * Enriched metadata for a model offered by a profile's server (model dashboard).
 * Keyed by (profileId, modelId) so the same model on two servers caches separately.
 */
@Entity(tableName = "model_cache", primaryKeys = ["profileId", "modelId"])
data class ModelCacheEntity(
    val profileId: String,
    val modelId: String,
    val displayName: String?,
    val contextLength: Int = 0,
    val quantization: String? = null,
    val parameterCount: String? = null,
    val family: String? = null,
    val sizeBytes: Long = 0,
    val latencyMs: Long = 0,
    val updatedAt: Long = 0,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemPrompt: String?,
    val paramsJson: String,
    val createdAt: Long,
)

/** An agent personality (system prompt + optional temperature nudge). */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val systemPrompt: String,
    /** null = inherit the global/default sampler temperature. */
    val temperature: Float?,
    val isDefault: Boolean,
    val createdAt: Long,
)

/** A conversation row joined with a small message preview for the drawer list. */
data class ConversationWithPreview(
    val id: String,
    val title: String,
    val folderId: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val updatedAt: Long,
    val preview: String?,
)

/** Room POJO: aggregated token usage per conversation. */
data class MessageStatsRow(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val generationTimeMs: Long = 0,
)

/** Room POJO: a message search hit with the parent conversation's title. */
data class MessageSearchResult(
    val messageId: String,
    val conversationId: String,
    val conversationTitle: String,
    val content: String,
    val role: String,
    val createdAt: Long,
)
