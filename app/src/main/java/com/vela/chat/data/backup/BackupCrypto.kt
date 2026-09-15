package com.vela.chat.data.backup

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based AES-256-GCM encryption for the full-data backup (Security screen).
 * The key is derived per-backup via PBKDF2WithHmacSHA256 from a random salt plus the
 * user's passphrase — the passphrase itself is never stored, only the salt, the IV and
 * the resulting ciphertext (whose GCM authentication tag also catches a wrong
 * passphrase or a corrupted/tampered file: [decrypt] fails cleanly instead of
 * returning garbage). Pure JVM (`javax.crypto`/`java.util.Base64`, both available
 * since API 26 = this app's minSdk) — no Android framework or Keystore dependency, so
 * it also runs unmodified in JVM unit tests.
 */
object BackupCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /** PBKDF2 iterations for the backup passphrase — higher than the PIN's since this
     * key can unlock API keys and A2A tokens, and export/import is a rare, deliberate action. */
    const val ITERATIONS = 300_000

    /** Upper bound on an imported file's claimed iteration count (see [decrypt]) — well
     * above [ITERATIONS] to tolerate a future bump, far below anything that could hang the app. */
    private const val MAX_ITERATIONS = 2_000_000

    fun encrypt(plaintext: String, passphrase: CharArray): BackupEnvelope {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return BackupEnvelope(
            iterations = ITERATIONS,
            saltB64 = salt.toBase64(),
            ivB64 = iv.toBase64(),
            ciphertextB64 = ciphertext.toBase64(),
        )
    }

    /**
     * Decrypts [envelope] with [passphrase]. Fails — rather than returning garbage —
     * on a wrong passphrase, a truncated/edited file, or an unsupported version; GCM
     * can't distinguish "wrong key" from "tampered ciphertext", so both surface the
     * same [AEADBadTagException].
     */
    fun decrypt(envelope: BackupEnvelope, passphrase: CharArray): Result<String> = runCatching {
        require(envelope.version == 1) { "Unsupported backup version ${envelope.version}" }
        // A backup file is untrusted input (picked via the SAF file picker) — without this
        // bound, a crafted file with an absurd iteration count forces an arbitrarily
        // expensive PBKDF2 derivation before the GCM tag is even checked, hanging the
        // import with no cancellation point.
        require(envelope.iterations in 1..MAX_ITERATIONS) {
            "Backup file has an invalid iteration count (${envelope.iterations})"
        }
        val salt = envelope.saltB64.fromBase64()
        val iv = envelope.ivB64.fromBase64()
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, envelope.iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(envelope.ciphertextB64.fromBase64()), Charsets.UTF_8)
    }

    /** True when [decrypt]'s failure was an authentication failure (wrong passphrase / corrupted file). */
    fun isAuthFailure(t: Throwable): Boolean = t is AEADBadTagException

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}

/** Self-describing encrypted backup envelope — every field but the ciphertext is
 * needed just to re-derive the key and decrypt; none of it is sensitive on its own. */
@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val iterations: Int,
    val saltB64: String,
    val ivB64: String,
    val ciphertextB64: String,
)
