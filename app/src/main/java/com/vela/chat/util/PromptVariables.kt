package com.vela.chat.util

/**
 * `{{variable}}` placeholder support for the prompt library. Placeholders are
 * detected in saved prompt content; before inserting a prompt the UI collects a
 * value for each variable (see Prompt Library 2.0), then [render] substitutes them.
 */
object PromptVariables {

    // NOTE: the closing braces MUST be escaped (\}\}) — Android's ICU-backed regex
    // engine rejects a bare `}` outside a character class, while the desktop JVM
    // (where unit tests run) accepts it. This difference caused a real crash.
    private val PATTERN = Regex("""\{\{\s*([a-zA-Z0-9_ -]+?)\s*\}\}""")

    /** Distinct variable names in the order they first appear, e.g. `{{name}}`, `{{language}}`. */
    fun extract(content: String): List<String> =
        PATTERN.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()

    /** True when the prompt has at least one placeholder. */
    fun hasVariables(content: String): Boolean = PATTERN.containsMatchIn(content)

    /**
     * Substitute each `{{var}}` with its value (case-insensitive on the variable name).
     * Variables without a provided value are left as-is so users can fill them in chat.
     */
    fun render(content: String, values: Map<String, String>): String =
        PATTERN.replace(content) { match ->
            val name = match.groupValues[1].trim()
            values.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: match.value
        }
}
