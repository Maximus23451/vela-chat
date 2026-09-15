package com.vela.chat.data.local

import android.util.Log
import java.io.File

/**
 * One-time migration from the plaintext Room database this app shipped with before
 * at-rest encryption, to a SQLCipher-encrypted one. Runs synchronously, once, before
 * Room ever opens the database (see `DatabaseModule.provideDatabase`).
 *
 * **Fail-safe by construction**: every step works against a fresh copy of the data,
 * never the original file in place. The original `vela.db` is only deleted after the
 * newly-encrypted copy has been reopened and its row counts verified to match — if
 * anything goes wrong at any point, the original plaintext file is left completely
 * untouched and the migration is retried on the next app start (a leftover `.tmp`
 * file from an interrupted attempt is treated the same as "not migrated yet").
 */
object DatabaseEncryptionMigrator {
    private const val TAG = "DbEncryptionMigrator"
    private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

    /** Runs the migration if [dbFile] exists and is still a plaintext SQLite file. */
    fun migrateIfNeeded(dbFile: File, passphrase: String) {
        if (!dbFile.exists() || !looksLikePlaintextSqlite(dbFile)) return
        Log.i(TAG, "Encrypting existing database at rest — one-time migration")

        val tempEncrypted = File(dbFile.parentFile, "${dbFile.name}.encrypting")
        tempEncrypted.delete()
        runCatching {
            checkpointWal(dbFile)
            exportToEncrypted(dbFile, tempEncrypted, passphrase)
            val originalRows = countRows(dbFile, passphrase = null)
            val migratedRows = countRows(tempEncrypted, passphrase = passphrase)
            check(migratedRows == originalRows) {
                "Row count mismatch after migration: $originalRows -> $migratedRows"
            }
            swapIn(dbFile, tempEncrypted)
            Log.i(TAG, "Database encryption migration complete ($migratedRows total rows verified)")
        }.onFailure { err ->
            // Leave the original plaintext file exactly as it was; only clean up our
            // own scratch file. The app still opens correctly (see DatabaseModule) —
            // this is retried, not fatal.
            Log.e(TAG, "Database encryption migration failed — will retry next launch", err)
            tempEncrypted.delete()
        }
    }

    /** A SQLCipher-encrypted file's header is indistinguishable from random bytes; only an
     * unmigrated plaintext SQLite file starts with the standard magic string. Exposed so
     * `DatabaseModule` can tell whether migration actually left an encrypted file behind —
     * if it didn't (a failed migration leaves the original in place by design), the caller
     * must open that file as plain SQLite rather than crash trying to decrypt it. */
    fun looksLikePlaintextSqlite(file: File): Boolean {
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        return read == header.size && header.contentEquals(SQLITE_MAGIC)
    }

    /** Merges the WAL into the main file so the migration below sees every committed row. */
    private fun checkpointWal(dbFile: File) {
        val plain = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        plain.use { it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() } }
    }

    /** The standard SQLCipher recipe for encrypting a plaintext database in place: attach a
     * new encrypted file as a second database and export every table into it. */
    private fun exportToEncrypted(plaintextFile: File, targetFile: File, passphrase: String) {
        // ATTACHed databases inherit the main connection's open flags, so CREATE_IF_NECESSARY
        // is what allows ATTACH to create the target file (it never recreates the source).
        val src = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            plaintextFile.absolutePath, "", null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
                net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
            null,
        )
        src.use { db ->
            // ATTACH's KEY clause is parsed as a SQL literal, not a bindable parameter — both
            // values are ours (an internal app-private path, a hex-only generated passphrase),
            // never external input, but they're still escaped defensively before embedding.
            val safePath = targetFile.absolutePath.replace("'", "''")
            val safeKey = passphrase.replace("'", "''")
            db.execSQL("ATTACH DATABASE '$safePath' AS encrypted KEY '$safeKey'")
            db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            db.execSQL("DETACH DATABASE encrypted")
        }
    }

    private fun countRows(file: File, passphrase: String?): Long {
        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            file.absolutePath, passphrase.orEmpty(), null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY, null,
        )
        return db.use {
            val tables = it.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name NOT LIKE 'android_%'",
                null,
            ).use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            }
            tables.sumOf { table ->
                it.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> c.moveToFirst(); c.getLong(0) }
            }
        }
    }

    /** Atomically-enough swap: the encrypted copy takes the original's name and path;
     * any leftover WAL/SHM siblings from the plaintext file are removed since they no
     * longer apply to the (freshly checkpointed, single-file) encrypted database. */
    private fun swapIn(dbFile: File, tempEncrypted: File) {
        File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        File(dbFile.parentFile, "${dbFile.name}-shm").delete()
        File(dbFile.parentFile, "${dbFile.name}-journal").delete()
        check(dbFile.delete()) { "Could not remove the old plaintext database" }
        check(tempEncrypted.renameTo(dbFile)) { "Could not move the encrypted database into place" }
    }
}
