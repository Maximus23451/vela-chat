package com.vela.chat.data.export

import com.vela.chat.data.AppJson
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Serializes a conversation to Markdown or JSON for sharing/export. */
object ChatExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun toMarkdown(conversation: Conversation, messages: List<Message>): String = buildString {
        appendLine("# ${conversation.title}")
        appendLine()
        appendLine("> Exported from Vela on ${dateFormat.format(Date())}")
        conversation.model?.let { appendLine("> Model: $it") }
        appendLine()
        if (!conversation.systemPrompt.isNullOrBlank()) {
            appendLine("## System")
            appendLine()
            appendLine(conversation.systemPrompt)
            appendLine()
        }
        messages.filter { it.role != Role.SYSTEM }.forEach { msg ->
            val speaker = if (msg.role == Role.USER) "🧑 You" else "🤖 Assistant"
            appendLine("### $speaker")
            appendLine()
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.forEach { appendLine("- 📎 ${it.name}") }
                appendLine()
            }
            appendLine(msg.content)
            appendLine()
        }
    }

    fun toJson(conversation: Conversation, messages: List<Message>): String {
        val export = ConversationExport(
            title = conversation.title,
            model = conversation.model,
            systemPrompt = conversation.systemPrompt,
            exportedAt = System.currentTimeMillis(),
            messages = messages
                .filter { it.role != Role.SYSTEM }
                .map { MessageExport(it.role.wire, it.content, it.createdAt) },
        )
        return AppJson.encodeToString(export)
    }

    /** Public for the conversation importer (`ConversationRepository.importConversation`). */
    @Serializable
    data class ConversationExport(
        val title: String,
        val model: String? = null,
        val systemPrompt: String? = null,
        val exportedAt: Long = 0,
        val messages: List<MessageExport> = emptyList(),
    )

    @Serializable
    data class MessageExport(val role: String, val content: String, val createdAt: Long = 0)
}
