package com.vela.chat

import com.vela.chat.ui.components.markdown.MdBlock
import com.vela.chat.ui.components.markdown.MdColumnAlignment
import com.vela.chat.ui.components.markdown.MarkdownCache
import com.vela.chat.ui.components.markdown.parseBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses heading paragraph and code fence`() {
        val md = """
            # Title

            Some intro text.

            ```kotlin
            fun main() {}
            ```
        """.trimIndent()

        val blocks = parseBlocks(md)

        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals("Title", (blocks[0] as MdBlock.Heading).text)

        val code = blocks.first { it is MdBlock.Code } as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("fun main()"))
    }

    @Test
    fun `parses bullet and ordered lists`() {
        val md = """
            - one
            - two

            1. first
            2. second
        """.trimIndent()

        val blocks = parseBlocks(md)
        val bullets = blocks.filterIsInstance<MdBlock.BulletList>().single()
        val ordered = blocks.filterIsInstance<MdBlock.OrderedList>().single()

        assertEquals(listOf("one", "two"), bullets.items)
        assertEquals(listOf("first", "second"), ordered.items)
    }

    @Test
    fun `code fence preserves inner blank lines and ignores markers inside`() {
        val md = "```\nline1\n\nline2\n```"
        val code = parseBlocks(md).single() as MdBlock.Code
        assertEquals("line1\n\nline2", code.code)
        assertEquals(null, code.language)
    }

    @Test
    fun `parses simple table with header and rows`() {
        val md = """
            | Name | Age |
            | ---- | --- |
            | Ada  | 36  |
            | Alan | 41  |
        """.trimIndent()

        val table = parseBlocks(md).filterIsInstance<MdBlock.Table>().single()

        assertEquals(listOf("Name", "Age"), table.headers)
        assertEquals(listOf(listOf("Ada", "36"), listOf("Alan", "41")), table.rows)
    }

    @Test
    fun `parses table with alignment row`() {
        val md = """
            Left | Center | Right
            :--- | :----: | ----:
            a    | b      | c
        """.trimIndent()

        val table = parseBlocks(md).filterIsInstance<MdBlock.Table>().single()

        assertEquals(
            listOf(MdColumnAlignment.LEFT, MdColumnAlignment.CENTER, MdColumnAlignment.RIGHT),
            table.alignments,
        )
        assertEquals(listOf("Left", "Center", "Right"), table.headers)
    }

    @Test
    fun `table rows pad to header column count`() {
        val md = """
            | A | B | C |
            | - | - | - |
            | one
        """.trimIndent()

        val table = parseBlocks(md).filterIsInstance<MdBlock.Table>().single()

        assertEquals(listOf("one", "", ""), table.rows.single())
    }

    @Test
    fun `pipeless separator keeps rule and paragraph behaviour`() {
        val md = """
            a | b
            ---
            text after rule
        """.trimIndent()

        val blocks = parseBlocks(md)

        // "a | b" stays a paragraph (no separator row follows), "---" stays a rule.
        assertTrue(blocks.first() is MdBlock.Paragraph)
        assertTrue(blocks[1] is MdBlock.Rule)
    }

    @Test
    fun `escaped pipe does not split table cells`() {
        val md = """
            | Expr | Value |
            | ---- | ----- |
            | a\|b | 2     |
        """.trimIndent()

        val table = parseBlocks(md).filterIsInstance<MdBlock.Table>().single()

        assertEquals("a|b", table.rows.single().first())
    }

    @Test
    fun `cache returns identical parse for repeated content`() {
        MarkdownCache.clear()
        val md = "# Heading\n\nBody text."
        val first = MarkdownCache.getOrParse(md)
        val second = MarkdownCache.getOrParse(md)
        assertTrue(first === second)
        assertEquals(first, parseBlocks(md))
    }
}
