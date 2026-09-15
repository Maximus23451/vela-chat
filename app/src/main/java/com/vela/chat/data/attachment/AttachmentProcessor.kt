package com.vela.chat.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.vela.chat.domain.model.Attachment
import com.vela.chat.domain.model.AttachmentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns user-picked content URIs into [Attachment]s ready for the API:
 *  - Images are downscaled and re-encoded as base64 data URLs for vision models.
 *  - PDFs are text-extracted (PDFBox-Android) for RAG-style context injection.
 *  - Text/code files are read inline.
 */
@Singleton
class AttachmentProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun process(uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val meta = queryMeta(uri)
            val mime = context.contentResolver.getType(uri) ?: guessMime(meta.name)
            when {
                mime.startsWith("image/") -> buildImageAttachment(uri, meta, mime)
                mime == "application/pdf" || meta.name.endsWith(".pdf", true) ->
                    buildPdfAttachment(uri, meta)
                isTextual(mime, meta.name) -> buildTextAttachment(uri, meta, mime)
                else -> Attachment(
                    id = UUID.randomUUID().toString(),
                    type = AttachmentType.OTHER,
                    name = meta.name,
                    mimeType = mime,
                    sizeBytes = meta.size,
                )
            }
        }
    }

    private fun buildImageAttachment(uri: Uri, meta: FileMeta, mime: String): Attachment {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val (encoded, outMime) = downscaleToDataUrl(bytes, mime)
        return Attachment(
            id = UUID.randomUUID().toString(),
            type = AttachmentType.IMAGE,
            name = meta.name,
            dataUrl = "data:$outMime;base64,$encoded",
            mimeType = outMime,
            sizeBytes = meta.size,
        )
    }

    private fun buildPdfAttachment(uri: Uri, meta: FileMeta): Attachment {
        val text = context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        }.trim()
        return Attachment(
            id = UUID.randomUUID().toString(),
            type = AttachmentType.PDF,
            name = meta.name,
            extractedText = text.ifBlank { "(no extractable text — the PDF may be scanned images)" },
            mimeType = "application/pdf",
            sizeBytes = meta.size,
        )
    }

    private fun buildTextAttachment(uri: Uri, meta: FileMeta, mime: String): Attachment {
        val text = context.contentResolver.openInputStream(uri)!!
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
            .take(MAX_TEXT_CHARS)
        return Attachment(
            id = UUID.randomUUID().toString(),
            type = AttachmentType.TEXT,
            name = meta.name,
            extractedText = text,
            mimeType = mime,
            sizeBytes = meta.size,
        )
    }

    /** Decode, downscale to [MAX_IMAGE_DIM], and JPEG-compress to bound payload size. */
    private fun downscaleToDataUrl(bytes: ByteArray, mime: String): Pair<String, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIM)
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return Base64.encodeToString(bytes, Base64.NO_WRAP) to mime

        return try {
            val out = ByteArrayOutputStream()
            val keepPng = mime == "image/png"
            val format = if (keepPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            decoded.compress(format, 85, out)
            val outMime = if (keepPng) "image/png" else "image/jpeg"
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP) to outMime
        } finally {
            decoded.recycle()
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while ((w / 2) >= maxDim || (h / 2) >= maxDim) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun queryMeta(uri: Uri): FileMeta {
        var name = "attachment"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return FileMeta(name, size)
    }

    private fun isTextual(mime: String, name: String): Boolean =
        mime.startsWith("text/") ||
            mime in TEXT_MIMES ||
            TEXT_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".webp", true) -> "image/webp"
        TEXT_EXTENSIONS.any { name.endsWith(it, true) } -> "text/plain"
        else -> "application/octet-stream"
    }

    private data class FileMeta(val name: String, val size: Long)

    private companion object {
        const val MAX_IMAGE_DIM = 1280
        const val MAX_TEXT_CHARS = 100_000
        val TEXT_MIMES = setOf(
            "application/json", "application/xml", "application/javascript",
            "application/x-yaml", "application/x-sh",
        )
        val TEXT_EXTENSIONS = listOf(
            ".txt", ".md", ".markdown", ".json", ".xml", ".csv", ".yaml", ".yml",
            ".kt", ".java", ".py", ".js", ".ts", ".tsx", ".jsx", ".c", ".cpp", ".h",
            ".rs", ".go", ".rb", ".php", ".html", ".css", ".sh", ".sql", ".toml", ".ini",
        )
    }
}
