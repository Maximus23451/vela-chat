package com.vela.chat.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations. Every schema change MUST ship a real migration here — the
 * destructive fallback is only a last-resort safety net, not a substitute.
 */
object Migrations {

    /**
     * v1 → v2 (Nova 2.0):
     *  - folders: color/icon customization
     *  - messages: per-message token stats + favorite/pin flags
     *  - saved_prompts: favorite flag
     *  - new model_cache table (model dashboard metadata)
     * All changes are additive (ALTER TABLE ADD COLUMN / CREATE TABLE), so no
     * existing user data is touched.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN promptTokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN completionTokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN generationTimeMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE saved_prompts ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS model_cache (
                    profileId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    displayName TEXT,
                    contextLength INTEGER NOT NULL DEFAULT 0,
                    quantization TEXT,
                    parameterCount TEXT,
                    family TEXT,
                    sizeBytes INTEGER NOT NULL DEFAULT 0,
                    latencyMs INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(profileId, modelId)
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * v2 → v3 (agent personalities): new `personas` table plus a `personaId`
     * column on conversations. Purely additive — no existing data is touched.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS personas (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    emoji TEXT NOT NULL,
                    systemPrompt TEXT NOT NULL,
                    temperature REAL,
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("ALTER TABLE conversations ADD COLUMN personaId TEXT")
        }
    }

    /** Declared last so it can reference every migration above it. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
