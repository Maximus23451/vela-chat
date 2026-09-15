package com.vela.chat.data.net

import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Thrown when a server presents a certificate the system CA store doesn't trust
 * and no pin exists yet for its host — the trust-on-first-use decision point.
 * [ApiProfileRepository.testConnection][com.vela.chat.data.repository.ApiProfileRepository.testConnection]
 * surfaces [fingerprintSha256] to the user for a one-time, explicit trust
 * decision; [TrustedCertStore.trust] pins it so future connections succeed
 * silently (and a *changed* cert on an already-pinned host — a possible
 * MITM — throws this same exception again, requiring re-approval).
 */
class UntrustedCertificateException(val host: String, val fingerprintSha256: String) :
    CertificateException("Unrecognized certificate for $host")

/** Walks [t]'s cause chain for an [UntrustedCertificateException] — OkHttp/the JSSE layer
 * typically wrap it inside an `SSLHandshakeException` before it reaches calling code. */
tailrec fun findUntrustedCert(t: Throwable?): UntrustedCertificateException? = when {
    t == null -> null
    t is UntrustedCertificateException -> t
    else -> findUntrustedCert(t.cause)
}

/**
 * Trust-on-first-use for local/self-signed HTTPS — the same model SSH uses for
 * host keys, applied to LM Studio/Ollama/a custom server sitting behind a
 * reverse proxy on the user's own network. A certificate that already validates
 * against the system CA store (any cloud provider, or a local server with a
 * real certificate) is trusted immediately; pinning is never involved. A
 * certificate that fails system validation is trusted only if its SHA-256
 * fingerprint matches a pin the user already approved for that exact host; an
 * unpinned or changed certificate throws [UntrustedCertificateException]
 * instead of silently connecting, so trust is always an explicit, one-time
 * decision rather than a blanket "accept all self-signed certs" bypass.
 */
class TofuTrustManager(private val pinStore: TrustedCertStore) : X509ExtendedTrustManager() {

    private val system: X509TrustManager = run {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) =
        system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) =
        system.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        // No host available on this overload. Android/OkHttp always route through one of
        // the extended overloads below (minSdk 26 here; extended trust managers have been
        // standard since API 24), so this path exists only to satisfy the interface.
        system.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
        val host = (socket as? javax.net.ssl.SSLSocket)?.handshakeSession?.peerHost
        checkServerTrustedForHost(chain, authType, host)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) {
        checkServerTrustedForHost(chain, authType, engine.peerHost)
    }

    private fun checkServerTrustedForHost(chain: Array<out X509Certificate>, authType: String, host: String?) {
        val systemTrusted = runCatching { system.checkServerTrusted(chain, authType) }.isSuccess
        if (systemTrusted) return

        val leaf = chain.firstOrNull() ?: throw CertificateException("Empty certificate chain")
        val fingerprint = sha256Fingerprint(leaf)
        val h = host ?: throw CertificateException("No host available to validate this certificate against")
        val pinned = pinStore.fingerprintFor(h)
        if (pinned != null && pinned.equals(fingerprint, ignoreCase = true)) return
        throw UntrustedCertificateException(h, fingerprint)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    companion object {
        /** Colon-separated uppercase hex, the conventional display form (matches `openssl x509 -fingerprint`). */
        fun sha256Fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
    }
}
