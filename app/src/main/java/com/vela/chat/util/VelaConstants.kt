package com.vela.chat.util

/**
 * Centralized app-wide constants. UI tokens (shape/spacing/motion) live in
 * `ui/theme/nova/NovaTokens`; this object holds behavioral constants only.
 */
object VelaConstants {

    /** Streaming text overlay repaint interval (~12 fps keeps Compose recomposition cheap). */
    const val STREAM_THROTTLE_MS = 80L

    /** Max characters of a persisted streaming preview before truncating the in-memory overlay. */
    const val STREAM_PREVIEW_MAX_CHARS = 200_000

    /** Windowed chat loading: how many messages are loaded initially / per "load older". */
    const val CHAT_WINDOW_SIZE = 60

    /** Ports probed when scanning tailnet hosts for OpenAI-compatible AI servers. */
    val AI_SERVER_PORTS = intArrayOf(
        1234,   // LM Studio default
        11434,  // Ollama default
        8080,   // llama.cpp server default
        8000,   // vLLM default
        1338,   // common llama-cpp-python / custom OpenAI shim
    )

    /** Default A2A (Agent2Agent) server port — Hermes Agent's gateway default. */
    const val A2A_DEFAULT_PORT = 9900

    /** Agent Card discovery paths, tried in order (v1.0 name first, then legacy). */
    val A2A_CARD_PATHS = listOf(
        ".well-known/agent-card.json",
        ".well-known/agent.json",
    )

    /**
     * A2A agents can legitimately think for minutes (Hermes reply timeout default
     * is 300 s) — give the blocking send a generous read budget.
     */
    const val A2A_READ_TIMEOUT_MS = 600_000

    /** Poll interval while waiting for a non-blocking A2A task to finish. */
    const val A2A_TASK_POLL_MS = 2_000L

    /** Hard ceiling for waiting on one A2A task across polls. */
    const val A2A_TASK_TIMEOUT_MS = 600_000

    /** Wait before the single 429 retry (gateway rate limit: ~60 req/min per identity). */
    const val A2A_RATE_LIMIT_BACKOFF_MS = 5_000L

    /**
     * SecureStore key prefix (inside SecureStore's own `secret_` namespace) for
     * per-peer A2A bearer tokens: `a2a_peer_<profileId>_<peerId>`. One token per
     * (profile, peer) — a token is the identity on a gateway and must never be
     * shared across peers or profiles.
     */
    const val A2A_PEER_SECRET_PREFIX = "a2a_peer_" // pragma: allowlist secret

    /** Probe timeouts for tailnet AI-server discovery (LAN links, keep them snappy). */
    const val PROBE_CONNECT_TIMEOUT_MS = 2_000
    const val PROBE_READ_TIMEOUT_MS = 3_000

    /** DNS resolution timeout budget for MagicDNS hostnames. */
    const val DNS_TIMEOUT_MS = 5_000

    /** Tailnet CGNAT range — any local interface address inside it means the VPN is up. */
    const val TAILSCALE_IP_PREFIX = "100."

    /** PIN hashing parameters (PBKDF2-with-HMAC-SHA256) for the app lock. */
    const val PIN_PBKDF2_ITERATIONS = 120_000
    const val PIN_KEY_LENGTH_BITS = 256
}
