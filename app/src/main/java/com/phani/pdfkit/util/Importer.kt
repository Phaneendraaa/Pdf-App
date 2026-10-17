package com.phani.pdfkit.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.phani.pdfkit.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Importer {
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Copies picked files into the library. Returns the new files. */
    suspend fun import(context: Context, uris: List<Uri>): List<File> = withContext(Dispatchers.IO) {
        val out = uris.mapNotNull { uri ->
            runCatching {
                val name = (displayName(context, uri) ?: "Document").let { if (it.contains('.')) it else "$it.${extFor(context, uri)}" }
                val ext = name.substringAfterLast('.', "pdf").lowercase()
                val target = Graph.library.newFile(name.substringBeforeLast('.'), ext)
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                target
            }.getOrNull()
        }
        Graph.library.refreshBlocking()
        out
    }

    /** File extension for a content type, used when the other app gives no file name. */
    private fun extFor(context: Context, uri: Uri): String = when (val t = context.contentResolver.getType(uri).orEmpty()) {
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        else -> if (t.startsWith("image/")) "jpg" else "pdf"
    }

    /** Copies a file opened from another app into cache/opened, keeping its real name. */
    suspend fun toOpened(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = (displayName(context, uri) ?: uri.lastPathSegment ?: "Document").replace("/", "_")
            val name = if (raw.contains('.')) raw else "$raw.${extFor(context, uri)}"
            val dir = File(context.cacheDir, "opened").apply { mkdirs() }
            val f = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            f
        }.getOrNull()
    }

    /** Copies a picked file into the cache (for tools that read but do not keep the source). */
    suspend fun toCache(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(context, uri) ?: "input"
            val f = File(context.cacheDir, "in_${System.nanoTime()}_$name")
            context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            f
        }.getOrNull()
    }
}
