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
                val name = displayName(context, uri) ?: "Document.pdf"
                val ext = name.substringAfterLast('.', "pdf").lowercase()
                val target = Graph.library.newFile(name.substringBeforeLast('.'), ext)
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                target
            }.getOrNull()
        }
        Graph.library.refreshBlocking()
        out
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
