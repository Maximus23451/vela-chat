package com.vela.chat.ui.components.markdown

/** Block-level Markdown nodes produced by [parseBlocks]. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class OrderedList(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock

    /**
     * A GFM-style pipe table. [alignments] has one entry per header column; null
     * means "no explicit alignment" (the column falls back to start-aligned).
     */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MdColumnAlignment?>,
    ) : MdBlock
}

/** Explicit text alignment for a markdown table column, declared in the separator row. */
enum class MdColumnAlignment { LEFT, CENTER, RIGHT }

private val BULLET = Regex("""^\s*[-*+]\s+(.*)""")
private val ORDERED = Regex("""^\s*\d+[.)]\s+(.*)""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)""")
private val RULE = Regex("""^\s*([-*_])\1{2,}\s*$""")

/** One separator cell, e.g. `---`, `:---:`, `---:` (GFM needs at least one dash). */
private val TABLE_SEPARATOR_CELL = Regex(""":?-{1,}:?""")

/** A table row is any non-blank line containing at least one pipe. */
private const val TABLE_PIPE = '|'

/** Split raw Markdown into a flat list of block nodes. */
fun parseBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
        paragraph.clear()
    }

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        val fence = line.trimStart()
        if (fence.startsWith("```") || fence.startsWith("~~~")) {
            flushParagraph()
            val ticks = fence.take(3)
            val language = fence.removePrefix(ticks).trim().ifBlank { null }
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(ticks)) {
                code.appendLine(lines[i])
                i++
            }
            i++ // skip closing fence
            blocks.add(MdBlock.Code(language, code.toString().trimEnd('\n')))
            continue
        }

        // Pipe table: header row (contains a pipe) followed by a `---|:---:` separator row.
        val table = parseTableAt(lines, i)
        if (table != null) {
            flushParagraph()
            blocks.add(table.first)
            i = table.second
            continue
        }

        when {
            line.isBlank() -> flushParagraph()

            RULE.matches(line) -> {
                flushParagraph()
                blocks.add(MdBlock.Rule)
            }

            HEADING.matches(line) -> {
                flushParagraph()
                val m = HEADING.find(line)!!
                blocks.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim()))
            }

            BULLET.matches(line) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size && BULLET.matches(lines[i])) {
                    items.add(BULLET.find(lines[i])!!.groupValues[1].trim())
                    i++
                }
                blocks.add(MdBlock.BulletList(items))
                continue
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size && ORDERED.matches(lines[i])) {
                    items.add(ORDERED.find(lines[i])!!.groupValues[1].trim())
                    i++
                }
                blocks.add(MdBlock.OrderedList(items))
                continue
            }

            line.trimStart().startsWith(">") -> {
                flushParagraph()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote.appendLine(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                blocks.add(MdBlock.Quote(quote.toString().trim()))
                continue
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append("\n")
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

/**
 * Attempt to parse a pipe table starting at [lines][startIndex] (header row).
 * Returns the parsed [MdBlock.Table] and the index of the first line *after* the
 * table, or null when the two lines do not form a valid table start.
 */
private fun parseTableAt(lines: List<String>, startIndex: Int): Pair<MdBlock.Table, Int>? {
    if (startIndex + 1 >= lines.size) return null
    val headerLine = lines[startIndex]
    val separatorLine = lines[startIndex + 1]
    if (TABLE_PIPE !in headerLine || TABLE_PIPE !in separatorLine) return null

    val separatorCells = splitTableCells(separatorLine)
    if (separatorCells.isEmpty() || separatorCells.any { !TABLE_SEPARATOR_CELL.matches(it) }) return null

    val headers = splitTableCells(headerLine).ifEmpty { return null }
    // One alignment per separator cell; columns without a declared alignment get null.
    val alignments = separatorCells.map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> MdColumnAlignment.CENTER
            cell.endsWith(":") -> MdColumnAlignment.RIGHT
            cell.startsWith(":") -> MdColumnAlignment.LEFT
            else -> null
        }
    } + List((headers.size - separatorCells.size).coerceAtLeast(0)) { null }

    var i = startIndex + 2
    val rows = mutableListOf<List<String>>()
    while (i < lines.size) {
        val rowLine = lines[i]
        if (rowLine.isBlank() || TABLE_PIPE !in rowLine) break
        val cells = splitTableCells(rowLine)
        rows.add(padRow(cells, headers.size))
        i++
    }

    return MdBlock.Table(
        headers = headers,
        rows = rows,
        alignments = alignments.take(headers.size),
    ) to i
}

/** Normalize a table row to exactly [columnCount] cells (padding/truncating). */
private fun padRow(cells: List<String>, columnCount: Int): List<String> = when {
    cells.size > columnCount -> cells.take(columnCount)
    cells.size < columnCount -> cells + List(columnCount - cells.size) { "" }
    else -> cells
}

/**
 * Split a table row into trimmed cells. Leading/trailing pipes are optional and
 * escaped pipes (`\|`) do not split.
 */
private fun splitTableCells(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.removePrefix("|")
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.removeSuffix("|")
    if (text.isBlank()) return emptyList()
    // Placeholder-swap keeps escaped pipes intact through the split.
    return text
        .replace("\\|", "\u0000")
        .split(TABLE_PIPE)
        .map { it.trim().replace("\u0000", "|") }
}
