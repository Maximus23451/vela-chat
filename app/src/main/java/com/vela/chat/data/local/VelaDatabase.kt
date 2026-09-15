package com.vela.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vela.chat.data.local.dao.ApiProfileDao
import com.vela.chat.data.local.dao.ConversationDao
import com.vela.chat.data.local.dao.FolderDao
import com.vela.chat.data.local.dao.MessageDao
import com.vela.chat.data.local.dao.ModelCacheDao
import com.vela.chat.data.local.dao.PersonaDao
import com.vela.chat.data.local.dao.PresetDao
import com.vela.chat.data.local.dao.SavedPromptDao
import com.vela.chat.data.local.entity.ApiProfileEntity
import com.vela.chat.data.local.entity.ConversationEntity
import com.vela.chat.data.local.entity.FolderEntity
import com.vela.chat.data.local.entity.MessageEntity
import com.vela.chat.data.local.entity.ModelCacheEntity
import com.vela.chat.data.local.entity.PersonaEntity
import com.vela.chat.data.local.entity.PresetEntity
import com.vela.chat.data.local.entity.SavedPromptEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        FolderEntity::class,
        ApiProfileEntity::class,
        SavedPromptEntity::class,
        PresetEntity::class,
        ModelCacheEntity::class,
        PersonaEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class VelaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun folderDao(): FolderDao
    abstract fun apiProfileDao(): ApiProfileDao
    abstract fun savedPromptDao(): SavedPromptDao
    abstract fun presetDao(): PresetDao
    abstract fun modelCacheDao(): ModelCacheDao
    abstract fun personaDao(): PersonaDao

    companion object {
        const val NAME = "vela.db"
    }
}
