package com.vela.chat

import com.vela.chat.util.PromptVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [PromptVariables]. The original pattern used unescaped
 * `}}` which compiles on the desktop JVM but throws `PatternSyntaxException`
 * under Android's ICU-backed regex engine (crash on first prompt insert).
 * These tests run on the JVM, so they can't reproduce the ICU failure itself —
 * they pin the behavior so any pattern change stays correct on both platforms.
 * Rule of thumb for this codebase: escape every `{` and `}` in regexes.
 */
class PromptVariablesTest {

    @Test
    fun `extracts variables in order of appearance`() {
        val content = "Hello {{name}}, please write a {{ language }} poem."
        assertEquals(listOf("name", "language"), PromptVariables.extract(content))
    }

    @Test
    fun `deduplicates variables`() {
        assertEquals(listOf("name"), PromptVariables.extract("{{name}} and {{ name }} again"))
    }

    @Test
    fun `hasVariables detects placeholders`() {
        assertTrue(PromptVariables.hasVariables("Hi {{name}}"))
        assertFalse(PromptVariables.hasVariables("No placeholders here"))
    }

    @Test
    fun `renders values case-insensitively and leaves unknown untouched`() {
        val rendered = PromptVariables.render(
            "Hi {{name}}, speak {{language}}!",
            mapOf("Name" to "Ada", "topic" to "cats"),
        )
        assertEquals("Hi Ada, speak {{language}}!", rendered)
    }

    @Test
    fun `hyphens and digits are valid variable names`() {
        assertEquals(listOf("user-id2"), PromptVariables.extract("{{ user-id2 }}"))
    }
}
