package com.vela.chat.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vela.chat.ui.theme.MonoTextStyle
import kotlinx.coroutines.delay

/** Fenced-block language tag that triggers the diagram chrome. */
private const val MERMAID_LANGUAGE = "mermaid"

/** Human-readable header for Mermaid blocks (real renderer = documented future work). */
private const val MERMAID_TITLE = "Mermaid diagram"

/** Estimated dp width of one body-size character, used to size table columns. */
private val TABLE_CHAR_WIDTH = 8.dp

/** Per-cell horizontal padding, applied to both sides of a table cell. */
private val TABLE_CELL_PADDING = 10.dp

/** Clamp bounds for auto-sized table columns (small numbers stay readable, wide ones scroll). */
private val TABLE_MIN_COLUMN_WIDTH = 64.dp
private val TABLE_MAX_COLUMN_WIDTH = 280.dp

/**
 * Renders a Markdown string into Compose text with headings, emphasis, lists,
 * blockquotes, links, inline code, tables, and fenced code blocks (highlighted,
 * with a copy button; `mermaid` fences get a diagram chrome). Self-contained —
 * no external markdown library. Parses via [MarkdownCache] so repeat renders of
 * unchanged content skip the parse.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(markdown) { MarkdownCache.getOrParse(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = parseInline(block.text, color),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = color,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = parseInline(block.text, color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    modifier = Modifier.padding(vertical = 2.dp),
                )

                is MdBlock.Code -> CodeBlock(language = block.language, code = block.code)

                is MdBlock.Table -> MarkdownTable(table = block, color = color)

                is MdBlock.BulletList -> Column(Modifier.padding(vertical = 2.dp)) {
                    block.items.forEach { item ->
                        ListItemRow(marker = "•", content = parseInline(item, color), color = color)
                    }
                }

                is MdBlock.OrderedList -> Column(Modifier.padding(vertical = 2.dp)) {
                    block.items.forEachIndexed { idx, item ->
                        ListItemRow(marker = "${idx + 1}.", content = parseInline(item, color), color = color)
                    }
                }

                is MdBlock.Quote -> Row(Modifier.padding(vertical = 4.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(intrinsicQuoteHeight())
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = parseInline(block.text, color.copy(alpha = 0.85f)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color.copy(alpha = 0.85f),
                    )
                }

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/**
 * Column-major table layout: each column is a [Column] of its cells, which keeps
 * cells within a column perfectly aligned without measuring text. The whole
 * table scrolls horizontally when the auto-sized columns exceed the bubble width,
 * and thin vertical separators divide the columns.
 */
@Composable
private fun MarkdownTable(table: MdBlock.Table, color: Color) {
    val scheme = MaterialTheme.colorScheme
    val columns = table.headers.size

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp)
            .height(IntrinsicSize.Max),
    ) {
        repeat(columns) { column ->
            if (column > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(scheme.outlineVariant),
                )
            }
            val alignment = table.alignments.getOrNull(column)
            val cells = buildList {
                add(table.headers[column])
                table.rows.forEach { row -> add(row.getOrElse(column) { "" }) }
            }
            Column(
                Modifier
                    .width(tableColumnWidth(cells))
                    .padding(horizontal = TABLE_CELL_PADDING, vertical = 4.dp),
            ) {
                // Header
                Text(
                    text = parseInline(cells.first(), color),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    textAlign = alignment.toTextAlign(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                cells.drop(1).forEach { cell ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                    Text(
                        text = parseInline(cell, color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        textAlign = alignment.toTextAlign(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Map a parsed column alignment to Compose text alignment (null → start). */
private fun MdColumnAlignment?.toTextAlign(): TextAlign = when (this) {
    MdColumnAlignment.CENTER -> TextAlign.Center
    MdColumnAlignment.RIGHT -> TextAlign.End
    null, MdColumnAlignment.LEFT -> TextAlign.Start
}

/**
 * Auto-size a column from its longest cell: an estimate of text width clamped to
 * readable bounds. Cells wrap inside the clamp, so very long content never
 * overflows — the table just stays within [TABLE_MAX_COLUMN_WIDTH] per column.
 */
private fun tableColumnWidth(cells: List<String>): Dp {
    val longest = cells.maxOfOrNull { it.length } ?: 0
    val estimated = TABLE_CHAR_WIDTH * longest + TABLE_CELL_PADDING * 2
    return estimated.coerceIn(TABLE_MIN_COLUMN_WIDTH, TABLE_MAX_COLUMN_WIDTH)
}

@Composable
private fun intrinsicQuoteHeight() = 20.dp

@Composable
private fun ListItemRow(marker: String, content: AnnotatedString, color: Color) {
    Row(Modifier.padding(start = 4.dp, top = 1.dp, bottom = 1.dp)) {
        Text(
            text = "$marker ",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.width(if (marker == "•") 16.dp else 24.dp),
        )
        Text(text = content, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val isMermaid = language == MERMAID_LANGUAGE

    val highlighted = remember(code, language) {
        CodeHighlighter.highlight(
            code = code,
            language = language,
            colors = CodeColors(
                base = scheme.onSurface,
                keyword = scheme.primary,
                string = scheme.tertiary,
                comment = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                number = scheme.secondary,
                function = scheme.tertiary,
            ),
        )
    }

    if (copied) {
        LaunchedEffect(Unit) {
            delay(1500)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerHigh)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMermaid) {
                // Diagram chrome: a real renderer is documented future work; for now
                // the Mermaid source is shown as highlighted, copyable code.
                Icon(
                    imageVector = Icons.Rounded.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = scheme.primary,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (isMermaid) MERMAID_TITLE else language?.takeIf { it.isNotBlank() } ?: "code",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (copied) scheme.primaryContainer else Color.Transparent)
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(15.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (copied) "Copied" else "Copy",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(text = highlighted, style = MonoTextStyle, color = scheme.onSurface)
        }
    }
}

/** Build an [AnnotatedString] from a span of inline markdown. */
fun parseInline(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    appendInline(this, text)
}

private fun appendInline(builder: AnnotatedString.Builder, text: String) {
    var i = 0
    val n = text.length
    while (i < n) {
        val token = nextToken(text, i)
        if (token == null) {
            builder.append(text.substring(i))
            return
        }
        if (token.start > i) builder.append(text.substring(i, token.start))
        when (token.kind) {
            TokenKind.CODE -> builder.withStyle(
                SpanStyle(fontFamily = MonoTextStyle.fontFamily, background = Color(0x22808080)),
            ) { append(token.content) }

            TokenKind.BOLD -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(this, token.content)
            }

            TokenKind.ITALIC -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                appendInline(this, token.content)
            }

            TokenKind.STRIKE -> builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInline(this, token.content)
            }

            TokenKind.LINK -> builder.withLink(
                LinkAnnotation.Url(
                    url = token.url ?: token.content,
                    styles = TextLinkStyles(
                        SpanStyle(color = Color(0xFF1B6CF3), textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) { append(token.content) }
        }
        i = token.end
    }
}

private enum class TokenKind { CODE, BOLD, ITALIC, STRIKE, LINK }

private data class InlineToken(
    val kind: TokenKind,
    val start: Int,
    val end: Int,
    val content: String,
    val url: String? = null,
)

/** Find the earliest inline markup token at or after [from]. */
private fun nextToken(text: String, from: Int): InlineToken? {
    var i = from
    val n = text.length
    while (i < n) {
        when {
            text[i] == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close != -1) return InlineToken(TokenKind.CODE, i, close + 1, text.substring(i + 1, close))
            }
            text.startsWith("**", i) -> {
                val close = text.indexOf("**", i + 2)
                if (close != -1) return InlineToken(TokenKind.BOLD, i, close + 2, text.substring(i + 2, close))
            }
            text.startsWith("__", i) -> {
                val close = text.indexOf("__", i + 2)
                if (close != -1) return InlineToken(TokenKind.BOLD, i, close + 2, text.substring(i + 2, close))
            }
            text.startsWith("~~", i) -> {
                val close = text.indexOf("~~", i + 2)
                if (close != -1) return InlineToken(TokenKind.STRIKE, i, close + 2, text.substring(i + 2, close))
            }
            text[i] == '*' -> {
                val close = text.indexOf('*', i + 1)
                if (close != -1) return InlineToken(TokenKind.ITALIC, i, close + 1, text.substring(i + 1, close))
            }
            text[i] == '_' && (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                val close = text.indexOf('_', i + 1)
                if (close != -1) return InlineToken(TokenKind.ITALIC, i, close + 1, text.substring(i + 1, close))
            }
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        return InlineToken(
                            kind = TokenKind.LINK,
                            start = i,
                            end = closeParen + 1,
                            content = text.substring(i + 1, closeBracket),
                            url = text.substring(closeBracket + 2, closeParen),
                        )
                    }
                }
            }
        }
        i++
    }
    return null
}
