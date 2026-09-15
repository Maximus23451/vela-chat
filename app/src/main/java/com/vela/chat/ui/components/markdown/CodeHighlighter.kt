package com.vela.chat.ui.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Palette used by the lightweight tokenizing highlighter. */
data class CodeColors(
    val base: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
)

/**
 * A small, dependency-free syntax highlighter. It tokenizes comments, strings,
 * numbers, keywords, and function-call identifiers. It is deliberately
 * approximate — good enough to read code at a glance without a full grammar.
 */
object CodeHighlighter {

    fun highlight(code: String, language: String?, colors: CodeColors): AnnotatedString {
        val keywords = keywordsFor(language)
        return buildAnnotatedString {
            var i = 0
            val n = code.length
            while (i < n) {
                val c = code[i]
                when {
                    // Line comments
                    (c == '/' && i + 1 < n && code[i + 1] == '/') ||
                        (c == '#' && language != "c" && language != "cpp") -> {
                        val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                        withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Block comments
                    c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                        val end = code.indexOf("*/", i).let { if (it == -1) n else it + 2 }
                        withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Strings
                    c == '"' || c == '\'' || c == '`' -> {
                        val end = findStringEnd(code, i, c)
                        withStyle(SpanStyle(color = colors.string)) { append(code.substring(i, end)) }
                        i = end
                    }
                    // Numbers
                    c.isDigit() -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == 'x')) j++
                        withStyle(SpanStyle(color = colors.number)) { append(code.substring(i, j)) }
                        i = j
                    }
                    // Identifiers / keywords / function calls
                    c.isLetter() || c == '_' -> {
                        var j = i
                        while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                        val word = code.substring(i, j)
                        val isCall = j < n && code[j] == '('
                        val style = when {
                            word in keywords -> SpanStyle(color = colors.keyword)
                            isCall -> SpanStyle(color = colors.function)
                            else -> SpanStyle(color = colors.base)
                        }
                        withStyle(style) { append(word) }
                        i = j
                    }
                    else -> {
                        withStyle(SpanStyle(color = colors.base)) { append(c) }
                        i++
                    }
                }
            }
        }
    }

    private fun findStringEnd(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            when (code[i]) {
                '\\' -> { i += 2; continue }
                quote -> return i + 1
                '\n' -> if (quote != '`') return i // unterminated single-line string
            }
            i++
        }
        return code.length
    }

    private fun keywordsFor(language: String?): Set<String> = when (language?.lowercase()) {
        "kotlin", "kt" -> KOTLIN
        "java" -> JAVA
        "python", "py" -> PYTHON
        "javascript", "js", "typescript", "ts", "tsx", "jsx" -> JS
        "go" -> GO
        "rust", "rs" -> RUST
        "c", "cpp", "c++" -> C_FAMILY
        "swift" -> SWIFT
        else -> COMMON
    }

    private val KOTLIN = setOf(
        "fun", "val", "var", "if", "else", "when", "for", "while", "return", "class", "object",
        "interface", "data", "sealed", "enum", "is", "in", "as", "null", "true", "false", "this",
        "super", "import", "package", "private", "public", "internal", "protected", "override",
        "suspend", "companion", "lateinit", "by", "lazy", "const", "vararg", "out", "where", "try",
        "catch", "finally", "throw", "do", "break", "continue", "init", "typealias",
    )
    private val JAVA = setOf(
        "public", "private", "protected", "class", "interface", "enum", "void", "int", "long",
        "double", "float", "boolean", "char", "byte", "short", "if", "else", "for", "while", "return",
        "new", "this", "super", "static", "final", "import", "package", "try", "catch", "finally",
        "throw", "throws", "extends", "implements", "null", "true", "false", "abstract", "switch",
        "case", "break", "continue", "instanceof",
    )
    private val PYTHON = setOf(
        "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as",
        "with", "try", "except", "finally", "raise", "lambda", "yield", "None", "True", "False",
        "and", "or", "not", "in", "is", "pass", "break", "continue", "global", "nonlocal", "async",
        "await", "self",
    )
    private val JS = setOf(
        "function", "const", "let", "var", "if", "else", "for", "while", "return", "class", "extends",
        "new", "this", "super", "import", "export", "from", "default", "try", "catch", "finally",
        "throw", "async", "await", "null", "undefined", "true", "false", "typeof", "instanceof",
        "switch", "case", "break", "continue", "yield", "interface", "type", "enum",
    )
    private val GO = setOf(
        "func", "var", "const", "if", "else", "for", "range", "return", "package", "import", "type",
        "struct", "interface", "map", "chan", "go", "defer", "select", "switch", "case", "break",
        "continue", "nil", "true", "false", "string", "int", "error",
    )
    private val RUST = setOf(
        "fn", "let", "mut", "if", "else", "match", "for", "while", "loop", "return", "struct", "enum",
        "impl", "trait", "pub", "use", "mod", "self", "Self", "true", "false", "None", "Some", "Ok",
        "Err", "as", "ref", "move", "async", "await", "where", "dyn", "const", "static",
    )
    private val C_FAMILY = setOf(
        "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed", "struct",
        "enum", "union", "class", "public", "private", "protected", "if", "else", "for", "while",
        "return", "switch", "case", "break", "continue", "const", "static", "new", "delete", "true",
        "false", "nullptr", "namespace", "template", "typename", "auto", "using", "include",
    )
    private val SWIFT = setOf(
        "func", "let", "var", "if", "else", "guard", "for", "while", "return", "class", "struct",
        "enum", "protocol", "extension", "import", "self", "init", "true", "false", "nil", "switch",
        "case", "break", "continue", "in", "where", "try", "catch", "throw", "throws", "async", "await",
    )
    private val COMMON = (KOTLIN + JS + PYTHON + GO).toSet()
}
