package com.vela.chat.data.local

import com.vela.chat.data.secure.SecureStore
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SQLCipher passphrase protecting [VelaDatabase] at rest. Generated once (32
 * random bytes, hex-encoded — hex so the same string is unambiguous whether it's
 * fed to SQLCipher as a `byte[]` via Room's `SupportOpenHelperFactory` or as a
 * `String` via [net.zetetic.database.sqlcipher.SQLiteDatabase] during migration;
 * both paths must derive the identical key from the identical bytes), then stored
 * in the same Keystore-backed [SecureStore] as every other device secret. Nothing
 * about this passphrase is ever shown to the user or exported — it protects the
 * conversation history at rest, not a secret the user manages themselves.
 */
@Singleton
class DatabasePassphrase @Inject constructor(
    private val secureStore: SecureStore,
) {
    /** The passphrase, generating and persisting a new one on first call. */
    fun get(): String {
        secureStore.getSecret(KEY)?.let { return it }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        secureStore.saveSecret(KEY, fresh)
        return fresh
    }

    private companion object {
        const val KEY = "db_passphrase"
    }
}
