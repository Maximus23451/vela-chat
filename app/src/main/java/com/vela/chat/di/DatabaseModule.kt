package com.vela.chat.di

import android.content.Context
import androidx.room.Room
import com.vela.chat.data.local.DatabaseEncryptionMigrator
import com.vela.chat.data.local.DatabasePassphrase
import com.vela.chat.data.local.Migrations
import com.vela.chat.data.local.VelaDatabase
import com.vela.chat.data.local.dao.ApiProfileDao
import com.vela.chat.data.local.dao.ConversationDao
import com.vela.chat.data.local.dao.FolderDao
import com.vela.chat.data.local.dao.MessageDao
import com.vela.chat.data.local.dao.ModelCacheDao
import com.vela.chat.data.local.dao.PersonaDao
import com.vela.chat.data.local.dao.PresetDao
import com.vela.chat.data.local.dao.SavedPromptDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vela_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        databasePassphrase: DatabasePassphrase,
    ): VelaDatabase {
        // sqlcipher-android does not load its JNI library on its own; without this every
        // SQLCipher open throws UnsatisfiedLinkError (found on-device, not in unit tests).
        System.loadLibrary("sqlcipher")
        val passphrase = databasePassphrase.get()
        val dbFile = context.getDatabasePath(VelaDatabase.NAME)
        DatabaseEncryptionMigrator.migrateIfNeeded(dbFile, passphrase)
        val builder = Room.databaseBuilder(context, VelaDatabase::class.java, VelaDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration()
        // A fresh install (no file yet) or a successful migration leaves no plaintext file
        // behind — open with SQLCipher. A migration that failed leaves the ORIGINAL
        // plaintext file untouched by design (see DatabaseEncryptionMigrator) — opening
        // that with the encrypted factory would crash trying to decrypt a database that
        // was never encrypted, so fall back to the plain opener exactly as before this
        // feature existed; migration retries automatically on the next launch.
        if (!DatabaseEncryptionMigrator.looksLikePlaintextSqlite(dbFile)) {
            builder.openHelperFactory(
                net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)),
            )
        }
        return builder.build()
    }

    @Provides fun provideConversationDao(db: VelaDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideMessageDao(db: VelaDatabase): MessageDao = db.messageDao()
    @Provides fun provideFolderDao(db: VelaDatabase): FolderDao = db.folderDao()
    @Provides fun provideApiProfileDao(db: VelaDatabase): ApiProfileDao = db.apiProfileDao()
    @Provides fun provideSavedPromptDao(db: VelaDatabase): SavedPromptDao = db.savedPromptDao()
    @Provides fun providePresetDao(db: VelaDatabase): PresetDao = db.presetDao()
    @Provides fun provideModelCacheDao(db: VelaDatabase): ModelCacheDao = db.modelCacheDao()
    @Provides fun providePersonaDao(db: VelaDatabase): PersonaDao = db.personaDao()
}
