package com.vela.chat.data.local

import com.vela.chat.data.AppJson
import com.vela.chat.data.local.entity.ApiProfileEntity
import com.vela.chat.data.local.entity.ConversationEntity
import com.vela.chat.data.local.entity.FolderEntity
import com.vela.chat.data.local.entity.MessageEntity
import com.vela.chat.data.local.entity.PresetEntity
import com.vela.chat.data.local.entity.SavedPromptEntity
import com.vela.chat.domain.model.ApiProfile
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.AgentPersona
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Folder
import com.vela.chat.domain.model.GenerationParams
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Preset
import com.vela.chat.domain.model.ProviderType
import com.vela.chat.domain.model.Role
import com.vela.chat.domain.model.SavedPrompt
import com.vela.chat.data.local.entity.PersonaEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

// ---- Conversation ----

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    folderId = folderId,
    profileId = profileId,
    model = model,
    systemPrompt = systemPrompt,
    params = paramsJson?.let { runCatching { AppJson.decodeFromString<GenerationParams>(it) }.getOrNull() },
    personaId = personaId,
    pinned = pinned,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    folderId = folderId,
    profileId = profileId,
    model = model,
    systemPrompt = systemPrompt,
    paramsJson = params?.let { AppJson.encodeToString(it) },
    personaId = personaId,
    pinned = pinned,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- Message ----

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    role = Role.from(role),
    content = content,
    reasoning = reasoning,
    model = model,
    attachments = attachmentsJson?.let {
        runCatching { AppJson.decodeFromString(ListSerializer(Attachment.serializer()), it) }.getOrDefault(emptyList())
    } ?: emptyList(),
    isError = isError,
    createdAt = createdAt,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    generationTimeMs = generationTimeMs,
    favorite = favorite,
    pinned = pinned,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.wire,
    content = content,
    reasoning = reasoning,
    model = model,
    attachmentsJson = if (attachments.isEmpty()) null
    else AppJson.encodeToString(ListSerializer(Attachment.serializer()), attachments),
    isError = isError,
    createdAt = createdAt,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    generationTimeMs = generationTimeMs,
    favorite = favorite,
    pinned = pinned,
)

// ---- Folder ----

fun FolderEntity.toDomain() = Folder(id, name, position, color, icon, createdAt)
fun Folder.toEntity() = FolderEntity(id, name, position, color, icon, createdAt)

// ---- ApiProfile ----

fun ApiProfileEntity.toDomain(): ApiProfile = ApiProfile(
    id = id,
    name = name,
    providerType = runCatching { ProviderType.valueOf(providerType) }.getOrDefault(ProviderType.CUSTOM),
    baseUrl = baseUrl,
    model = model,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ApiProfile.toEntity(): ApiProfileEntity = ApiProfileEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    baseUrl = baseUrl,
    model = model,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- SavedPrompt ----

fun SavedPromptEntity.toDomain() = SavedPrompt(id, title, content, category, favorite, createdAt)
fun SavedPrompt.toEntity() = SavedPromptEntity(id, title, content, category, favorite, createdAt)

// ---- Preset ----

fun PresetEntity.toDomain(): Preset = Preset(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    params = runCatching { AppJson.decodeFromString<GenerationParams>(paramsJson) }.getOrDefault(GenerationParams.Default),
    createdAt = createdAt,
)

fun Preset.toEntity(): PresetEntity = PresetEntity(
    id = id,
    name = name,
    systemPrompt = systemPrompt,
    paramsJson = AppJson.encodeToString(params),
    createdAt = createdAt,
)

// ---- AgentPersona ----

fun PersonaEntity.toDomain() = AgentPersona(
    id = id,
    name = name,
    description = description,
    emoji = emoji,
    systemPrompt = systemPrompt,
    temperature = temperature,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun AgentPersona.toEntity() = PersonaEntity(
    id = id,
    name = name,
    description = description,
    emoji = emoji,
    systemPrompt = systemPrompt,
    temperature = temperature,
    isDefault = isDefault,
    createdAt = createdAt,
)
