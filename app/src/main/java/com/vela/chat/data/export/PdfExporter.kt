package com.vela.chat.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.vela.chat.domain.model.Conversation
import com.vela.chat.domain.model.Message
import com.vela.chat.domain.model.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversation → PDF export using the framework's [PdfDocument] (no external
 * dependency). Produces A4 pages with a title header, speaker labels and basic
 * word-wrapped text; markdown emphasis characters are stripped for readability.
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Renders the conversation to a PDF in the app cache dir and returns the file. */
    fun export(conversation: Conversation, messages: List<Message>): File {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val speakerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(0x33, 0x33, 0x33)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val labelWidth = 64f

        var pageNo = 1
        var page = doc.startPage(pageInfo(pageNo))
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            doc.finishPage(page)
            pageNo += 1
            page = doc.startPage(pageInfo(pageNo))
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        fun drawLines(text: String, paint: Paint, indent: Float) {
            val maxWidth = PAGE_WIDTH - MARGIN - indent
            text.lines().forEach { rawLine ->
                val words = rawLine.split(' ').filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    ensureSpace(paint.textSize * 1.3f)
                    y += paint.textSize * 1.3f
                    return@forEach
                }
                var line = StringBuilder()
                words.forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                        ensureSpace(paint.textSize * 1.4f)
                        canvas.drawText(line.toString(), indent, y + paint.textSize, paint)
                        y += paint.textSize * 1.4f
                        line = StringBuilder(word)
                    } else {
                        line = StringBuilder(candidate)
                    }
                }
                if (line.isNotEmpty()) {
                    ensureSpace(paint.textSize * 1.4f)
                    canvas.drawText(line.toString(), indent, y + paint.textSize, paint)
                    y += paint.textSize * 1.4f
                }
            }
        }

        // Header block.
        canvas.drawText(conversation.title, MARGIN, y + titlePaint.textSize, titlePaint)
        y += titlePaint.textSize * 1.6f
        val exported = "Exported from Vela ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}" +
            (conversation.model?.let { " · $it" } ?: "")
        canvas.drawText(exported, MARGIN, y + metaPaint.textSize, metaPaint)
        y += metaPaint.textSize * 2.5f

        messages.filter { it.role != Role.SYSTEM }.forEach { msg ->
            val speaker = when (msg.role) {
                Role.USER -> "You"
                Role.ASSISTANT -> "Assistant"
                else -> "System"
            }
            ensureSpace(speakerPaint.textSize * 2f)
            canvas.drawText(speaker, MARGIN, y + speakerPaint.textSize, speakerPaint)
            drawLines(stripMarkdown(msg.content), bodyPaint, MARGIN + labelWidth)
            y += speakerPaint.textSize // spacing between messages
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "vela-${conversation.id.take(8)}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun pageInfo(no: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, no).create()

    private fun stripMarkdown(text: String): String = text
        .replace(Regex("```[a-zA-Z0-9_+-]*\\n?"), "[code] ")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")

    private companion object {
        // A4 at 72 dpi.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
    }
}
