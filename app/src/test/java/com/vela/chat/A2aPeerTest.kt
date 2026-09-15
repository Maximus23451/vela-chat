package com.vela.chat

import com.vela.chat.data.AppJson
import com.vela.chat.data.a2a.A2aPeerTool
import com.vela.chat.domain.model.A2aPeer
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the A2A peer table primitives: JSON round-trip through the shared
 * lenient parser (peers persist as a DataStore JSON blob, not Room) and the pure
 * resolution rule that decides which gateway a conversation talks through.
 */
class A2aPeerTest {

    // Reserved documentation addresses (RFC 5737) — no real infrastructure in tests.
    private val alpha = A2aPeer(id = "p-alpha", name = "Alpha", baseUrl = "http://192.0.2.10:9900/")
    private val bravo = A2aPeer(id = "p-bravo", name = "Bravo", baseUrl = "http://192.0.2.20:9900")

    @Test
    fun `peer table round-trips through the shared json`() {
        val encoded = AppJson.encodeToString(listOf(alpha, bravo))
        val decoded = AppJson.decodeFromString<List<A2aPeer>>(encoded)
        assertEquals(listOf(alpha, bravo), decoded)
    }

    @Test
    fun `normalized base url always ends in one slash`() {
        assertEquals("http://host:9900/", A2aPeer(id = "x", name = "x", baseUrl = "http://host:9900").normalizedBaseUrl)
        assertEquals("http://host:9900/", A2aPeer(id = "x", name = "x", baseUrl = "http://host:9900/").normalizedBaseUrl)
    }

    @Test
    fun `resolution honors the persisted choice`() {
        val peers = listOf(alpha, bravo)
        assertEquals(bravo, A2aPeer.resolve(peers, "p-bravo"))
        assertEquals(alpha, A2aPeer.resolve(peers, "p-alpha"))
    }

    @Test
    fun `resolution falls back to the first enabled peer`() {
        val peers = listOf(
            alpha.copy(enabled = false),
            bravo,
        )
        assertEquals(bravo, A2aPeer.resolve(peers, null))
        // A stale choice pointing at a now-disabled peer must not select it.
        assertEquals(bravo, A2aPeer.resolve(peers, "p-alpha"))
    }

    @Test
    fun `resolution returns null when no peer is enabled`() {
        val peers = listOf(alpha.copy(enabled = false), bravo.copy(enabled = false))
        assertNull(A2aPeer.resolve(peers, "p-bravo"))
        assertNull(A2aPeer.resolve(peers, null))
    }

    @Test
    fun `resolution with an empty table means legacy single-gateway behavior`() {
        assertNull(A2aPeer.resolve(emptyList(), "p-bravo"))
        assertNull(A2aPeer.resolve(emptyList(), null))
    }

    @Test
    fun `peer tool names are openai-safe and derived from the peer name`() {
        assertEquals("message_bravo", A2aPeerTool.toolName("Bravo"))
        assertEquals("message_alpha", A2aPeerTool.toolName("Alpha"))
        assertEquals("message_testagenta", A2aPeerTool.toolName("Test Agent A"))
        assertEquals("message_peer", A2aPeerTool.toolName("   "))
    }

    @Test
    fun `peer tool definition is well-formed and mentions the peer`() {
        val tool = A2aPeerTool.definition("Bravo")
        assertEquals("message_bravo", tool.function.name)
        val serialized = AppJson.encodeToString(tool)
        assert(serialized.contains("message_bravo"))
        assert(serialized.contains("Send a message to Bravo"))
        assertNull(A2aPeerTool.extractArgument(serialized, "text"))
        assertEquals(
            "hello Bravo",
            A2aPeerTool.extractArgument("""{"text":"hello Bravo"}""", "text"),
        )
        assertNull(A2aPeerTool.extractArgument("not json at all", "text"))
        assertNull(A2aPeerTool.extractArgument("""{"other":1}""", "text"))
    }
}
