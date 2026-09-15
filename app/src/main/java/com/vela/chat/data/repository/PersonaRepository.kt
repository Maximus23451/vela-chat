package com.vela.chat.data.repository

import com.vela.chat.data.local.dao.PersonaDao
import com.vela.chat.data.local.toDomain
import com.vela.chat.data.local.toEntity
import com.vela.chat.domain.model.AgentPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent personalities: CRUD + default selection + first-run seeding. Follows the
 * same defensive style as [ConversationRepository] — every call is failure-tolerant
 * so a storage hiccup never takes down a screen.
 */
@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
) {
    fun observePersonas(): Flow<List<AgentPersona>> =
        personaDao.observeAll().map { list -> list.map { it.toDomain() } }.catch { emit(emptyList()) }

    suspend fun get(id: String): AgentPersona? = runCatching {
        personaDao.getById(id)?.toDomain()
    }.getOrNull()

    /** Every persona, unfiltered (full-backup export). */
    suspend fun getAll(): List<AgentPersona> = runCatching {
        personaDao.getAll().map { it.toDomain() }
    }.getOrDefault(emptyList())

    /** The persona applied when a conversation has none of its own. */
    suspend fun getDefault(): AgentPersona? = runCatching {
        personaDao.getDefault()?.toDomain()
    }.getOrNull()

    suspend fun upsert(persona: AgentPersona) {
        runCatching { personaDao.upsert(persona.toEntity()) }
    }

    suspend fun setDefault(id: String) {
        runCatching { personaDao.setDefault(id) }
    }

    suspend fun delete(id: String) {
        runCatching { personaDao.delete(id) }
    }

    /**
     * Ensures every built-in personality exists, inserting any whose stable id is
     * missing (first launch seeds all; later app updates add new built-ins).
     * User edits are never touched — but note a deleted built-in reappears on the
     * next launch, by design.
     */
    suspend fun seedDefaultsIfEmpty() {
        runCatching {
            val existing = personaDao.getAll().mapTo(mutableSetOf()) { it.id }
            defaultPersonas()
                .filter { it.id !in existing }
                .forEach { personaDao.upsert(it.toEntity()) }
        }
    }

    companion object {
        /** Stable ids so upgrades can reference seeded personas safely. */
        const val ID_HERMES = "persona-hermes"

        private fun defaultPersonas(): List<AgentPersona> {
            val now = System.currentTimeMillis()
            return listOf(
                AgentPersona(
                    id = ID_HERMES,
                    name = "Hermes",
                    description = "Balanced, capable general assistant",
                    emoji = "🪶",
                    systemPrompt = "You are Hermes, a versatile and helpful AI assistant. " +
                        "Answer clearly and accurately, adapt to the user's tone, use markdown " +
                        "formatting when it aids readability, and ask a clarifying question when a " +
                        "request is genuinely ambiguous.",
                    isDefault = true,
                    createdAt = now,
                ),
                AgentPersona(
                    id = "persona-coder",
                    name = "Coder",
                    description = "Precise engineering and code review",
                    emoji = "💻",
                    systemPrompt = "You are a senior software engineer. Give precise, working code " +
                        "with minimal prose. State assumptions explicitly, mention edge cases and " +
                        "complexity, and prefer standard libraries over cleverness. When reviewing " +
                        "code, point out bugs first, then style.",
                    temperature = 0.4f,
                    createdAt = now + 1,
                ),
                AgentPersona(
                    id = "persona-tutor",
                    name = "Tutor",
                    description = "Patient step-by-step explanations",
                    emoji = "🎓",
                    systemPrompt = "You are a patient tutor. Explain concepts step by step from " +
                        "first principles, use analogies and small examples, check understanding " +
                        "with a short question at the end, and never make the learner feel silly " +
                        "for asking.",
                    temperature = 0.6f,
                    createdAt = now + 2,
                ),
                AgentPersona(
                    id = "persona-brainstormer",
                    name = "Brainstormer",
                    description = "Fast, wild idea generation",
                    emoji = "💡",
                    systemPrompt = "You are a creative brainstorming partner. Generate many " +
                        "diverse ideas quickly, favor quantity over polish, combine unlikely " +
                        "concepts, and always build on the user's ideas instead of dismissing them. " +
                        "Group ideas into bold, safe, and unexpected.",
                    temperature = 0.95f,
                    createdAt = now + 3,
                ),
                AgentPersona(
                    id = "persona-concise",
                    name = "Concise",
                    description = "Terse, no-fluff answers",
                    emoji = "⚡",
                    systemPrompt = "Answer in the fewest words that fully address the question. " +
                        "No preamble, no filler, no restating the question. Use short bullets. " +
                        "If the answer is a single word, give a single word.",
                    temperature = 0.3f,
                    createdAt = now + 4,
                ),
                AgentPersona(
                    id = "persona-writer",
                    name = "Writer",
                    description = "Creative prose and storytelling",
                    emoji = "✍️",
                    systemPrompt = "You are a skilled creative writer. Favor vivid, concrete " +
                        "imagery over abstraction, vary sentence rhythm, avoid clichés and " +
                        "purple prose, and match the tone and format the user asks for. When " +
                        "critiquing, be constructive and specific.",
                    temperature = 0.9f,
                    createdAt = now + 5,
                ),
                AgentPersona(
                    id = "persona-analyst",
                    name = "Analyst",
                    description = "Structured, critical reasoning",
                    emoji = "📊",
                    systemPrompt = "You are a rigorous analyst. Structure answers with clear " +
                        "headings, weigh evidence for and against, quantify where possible, " +
                        "state confidence levels and key uncertainties, and separate facts from " +
                        "your interpretation.",
                    temperature = 0.5f,
                    createdAt = now + 6,
                ),
                AgentPersona(
                    id = "persona-roleplay",
                    name = "Roleplay",
                    description = "Immersive character play",
                    emoji = "🎭",
                    systemPrompt = "You are an immersive roleplay partner. Stay fully in " +
                        "character unless the user steps out with (OOC:) text. Write actions in " +
                        "asterisks, keep responses to a few paragraphs, end on an open beat the " +
                        "user can react to, and never act or speak for the user's character.",
                    temperature = 1.0f,
                    createdAt = now + 7,
                ),
            )
        }
    }
}
