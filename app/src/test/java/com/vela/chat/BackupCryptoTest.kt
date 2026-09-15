package com.vela.chat

import com.vela.chat.data.backup.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun `round trip returns the original plaintext`() {
        val plaintext = """{"conversations":[{"id":"c1","title":"hello \"world\""}]}"""
        val envelope = BackupCrypto.encrypt(plaintext, "correct horse battery staple".toCharArray())

        val result = BackupCrypto.decrypt(envelope, "correct horse battery staple".toCharArray())

        assertTrue(result.isSuccess)
        assertEquals(plaintext, result.getOrNull())
    }

    @Test
    fun `wrong passphrase fails to decrypt instead of returning garbage`() {
        val envelope = BackupCrypto.encrypt("secret payload", "right-passphrase".toCharArray())

        val result = BackupCrypto.decrypt(envelope, "wrong-passphrase".toCharArray())

        assertTrue(result.isFailure)
        assertTrue(BackupCrypto.isAuthFailure(result.exceptionOrNull()!!))
    }

    @Test
    fun `tampered ciphertext fails to decrypt`() {
        val envelope = BackupCrypto.encrypt("secret payload", "a-passphrase".toCharArray())
        // Flip one character in the ciphertext — GCM's auth tag must catch this.
        val tamperedChar = if (envelope.ciphertextB64.first() == 'A') 'B' else 'A'
        val tampered = envelope.copy(ciphertextB64 = tamperedChar + envelope.ciphertextB64.drop(1))

        val result = BackupCrypto.decrypt(tampered, "a-passphrase".toCharArray())

        assertTrue(result.isFailure)
    }

    @Test
    fun `two encryptions of the same plaintext use different salt and IV`() {
        val a = BackupCrypto.encrypt("same plaintext", "same-passphrase".toCharArray())
        val b = BackupCrypto.encrypt("same plaintext", "same-passphrase".toCharArray())

        assertNotEquals(a.saltB64, b.saltB64)
        assertNotEquals(a.ivB64, b.ivB64)
        assertNotEquals(a.ciphertextB64, b.ciphertextB64)
    }

    @Test
    fun `absurd iteration count from an untrusted file is rejected, not paid for`() {
        val envelope = BackupCrypto.encrypt("payload", "pw".toCharArray()).copy(iterations = Int.MAX_VALUE)

        val start = System.nanoTime()
        val result = BackupCrypto.decrypt(envelope, "pw".toCharArray())
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(result.isFailure)
        assertTrue("rejection should fail fast, took ${elapsedMs}ms", elapsedMs < 2000)
    }

    @Test
    fun `zero or negative iteration count is rejected`() {
        val zero = BackupCrypto.encrypt("payload", "pw".toCharArray()).copy(iterations = 0)
        val negative = zero.copy(iterations = -1)

        assertTrue(BackupCrypto.decrypt(zero, "pw".toCharArray()).isFailure)
        assertTrue(BackupCrypto.decrypt(negative, "pw".toCharArray()).isFailure)
    }

    @Test
    fun `unsupported envelope version is rejected`() {
        val envelope = BackupCrypto.encrypt("payload", "pw".toCharArray()).copy(version = 99)

        val result = BackupCrypto.decrypt(envelope, "pw".toCharArray())

        assertTrue(result.isFailure)
    }

    @Test
    fun `empty plaintext round trips too`() {
        val envelope = BackupCrypto.encrypt("", "pw".toCharArray())

        val result = BackupCrypto.decrypt(envelope, "pw".toCharArray())

        assertEquals("", result.getOrNull())
    }
}
