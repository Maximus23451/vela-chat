package com.vela.chat.data.net

import com.vela.chat.data.secure.SecureStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-host pinned certificate fingerprints for [TofuTrustManager] — the one-time
 * trust decision a user makes for a self-signed local HTTPS endpoint (LM Studio,
 * Ollama, a custom server behind a reverse proxy), stored in the same
 * Keystore-backed [SecureStore] as every other device secret so it survives the
 * same way a saved API key does. Hosts that present a certificate the system CA
 * store already trusts never touch this store at all — pinning only exists for
 * the self-signed case.
 */
@Singleton
class TrustedCertStore @Inject constructor(
    private val secureStore: SecureStore,
) {
    fun fingerprintFor(host: String): String? = secureStore.getSecret(keyFor(host))

    fun trust(host: String, fingerprintSha256: String) = secureStore.saveSecret(keyFor(host), fingerprintSha256)

    fun untrust(host: String) = secureStore.saveSecret(keyFor(host), null)

    private fun keyFor(host: String) = "cert_pin_${host.lowercase()}"
}
