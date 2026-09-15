package com.vela.chat

import com.vela.chat.data.a2a.A2aJson
import com.vela.chat.data.a2a.A2aTaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the lenient A2A JSON parsing. The protocol exists in several dialects
 * (v1.0 Hermes-style SCREAMING_SNAKE enums + wrapper objects, and pre-1.0
 * JSON-RPC camelCase) — these fixtures pin both, plus the shapes real gateways
 * return (see docs/TAILSCALE.md).
 */
class A2aJsonTest {

    @Test
    fun `parses v1 agent card`() {
        val card = A2aJson.parseAgentCard(
            """
            {
              "name": "Hermes",
              "description": "self-improving agent",
              "url": "http://box:9900/",
              "protocolVersion": "1.0",
              "capabilities": {"streaming": true, "pushNotifications": false},
              "skills": [{"id": "s1", "name": "research"}, {"id": "s2", "name": "code"}]
            }
            """.trimIndent(),
        )!!
        assertEquals("Hermes", card.name)
        assertEquals("http://box:9900/", card.url)
        assertTrue(card.isV1Dialect)
        assertTrue(card.streamingSupported)
        assertEquals(listOf("research", "code"), card.skills)
    }

    @Test
    fun `parses legacy agent card as pre-v1`() {
        val card = A2aJson.parseAgentCard(
            """
            {"name": "OldAgent", "url": "http://box:8000", "protocolVersion": "0.2.9",
             "capabilities": {"streaming": false}}
            """.trimIndent(),
        )!!
        assertFalse(card.isV1Dialect)
        assertFalse(card.streamingSupported)
        assertNull(A2aJson.parseAgentCard("not json at all"))
        assertNull(A2aJson.parseAgentCard("{\"description\": \"no name\"}"))
    }

    @Test
    fun `parses jsonrpc task with artifacts`() {
        val result = A2aJson.parseResult(
            """
            {"jsonrpc":"2.0","id":"m1","result":{
              "id":"task-1","contextId":"ctx-9",
              "status":{"state":"TASK_STATE_COMPLETED"},
              "artifacts":[{"artifactId":"a1","name":"answer",
                "parts":[{"kind":"text","text":"Hello from the agent."}]}]}}
            """.trimIndent(),
        )
        assertEquals("task-1", result.taskId)
        assertEquals("ctx-9", result.contextId)
        assertEquals(A2aTaskState.COMPLETED, result.state)
        assertEquals("Hello from the agent.", result.agentText)
    }

    @Test
    fun `parses pre-v1 camelcase states and direct message reply`() {
        val result = A2aJson.parseResult(
            """
            {"jsonrpc":"2.0","id":"m1","result":{
              "kind":"message","role":"agent","messageId":"a1",
              "parts":[{"kind":"text","text":"Direct reply"}]}}
            """.trimIndent(),
        )
        assertEquals(A2aTaskState.COMPLETED, result.state)
        assertEquals("Direct reply", result.agentText)
    }

    @Test
    fun `parses jsonrpc error envelope as failed`() {
        val result = A2aJson.parseResult(
            """
            {"jsonrpc":"2.0","id":"m1","error":{"code":-32000,"message":"token required"}}
            """.trimIndent(),
        )
        assertEquals(A2aTaskState.FAILED, result.state)
        assertEquals("token required", result.agentText)
    }

    @Test
    fun `collects agent history text when no artifacts`() {
        val result = A2aJson.parseResult(
            """
            {"result":{
              "id":"t1","contextId":"c1",
              "status":{"state":"completed"},
              "history":[
                {"role":"user","parts":[{"text":"hi"}]},
                {"role":"agent","parts":[{"text":"part one"},{"text":"part two"}]}
              ]}}
            """.trimIndent(),
        )
        assertEquals(A2aTaskState.COMPLETED, result.state)
        assertEquals("part one\n\npart two", result.agentText)
    }

    @Test
    fun `builds send body with dialect-correct role and context`() {
        val v1 = A2aJson.buildSendMessage("hi", "m1", "ctx", v1Dialect = true)
        assertTrue(v1.contains("\"method\":\"message/send\""))
        assertTrue(v1.contains("\"role\":\"ROLE_USER\""))
        assertTrue(v1.contains("\"contextId\":\"ctx\""))

        val legacy = A2aJson.buildSendMessage("hi", "m1", null, v1Dialect = false)
        assertTrue(legacy.contains("\"role\":\"user\""))
        assertFalse(legacy.contains("contextId"))

        val stream = A2aJson.buildStreamMessage("hi", "m1", null, v1Dialect = true)
        assertTrue(stream.contains("\"method\":\"message/stream\""))
    }
}
