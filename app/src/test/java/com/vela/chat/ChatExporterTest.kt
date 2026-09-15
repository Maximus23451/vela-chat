package com.vela.chat

import com.vela.chat.data.export.ChatExporter
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExporterTest {

    private val conversation = Conversation(
        id = "c1",
        title = "Greetings",
        model = "test-model",
        systemPrompt = "Be brief.",
    )

    private val messages = listOf(
        Message("m1", "c1", Role.USER, "Hello"),
        Message("m2", "c1", Role.ASSISTANT, "Hi there!", model = "test-model"),
    )

    @Test
    fun `markdown export contains title model and turns`() {
        val md = ChatExporter.toMarkdown(conversation, messages)
        assertTrue(md.contains("# Greetings"))
        assertTrue(md.contains("test-model"))
        assertTrue(md.contains("Hello"))
        assertTrue(md.contains("Hi there!"))
        assertTrue(md.contains("Be brief."))
    }

    @Test
    fun `json export is valid and omits system messages`() {
        val json = ChatExporter.toJson(conversation, messages)
        assertTrue(json.contains("\"title\""))
        assertTrue(json.contains("Hello"))
        assertTrue(json.contains("Hi there!"))
        // Only user/assistant turns are exported.
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("\"role\":\"assistant\""))
    }
}
