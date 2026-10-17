package com.phani.pdfkit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A file in the Pdf Kit library (app storage). */
data class DocFile(
    val path: String,
    val name: String,
    val bytes: Long,
    val modified: Long,
) {
    val file: File get() = File(path)
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val isPdf: Boolean get() = ext == "pdf"
    val isImage: Boolean get() = ext in setOf("jpg", "jpeg", "png", "webp")
    val isDoc: Boolean get() = ext == "docx"
}

/**
 * All files the app creates or imports live in one private folder, so the app
 * works offline without broad storage permissions. "Save to folder" exports copies.
 */
class Library(context: Context) {
    val dir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "Pdf Kit").apply { mkdirs() }

    private val _files = MutableStateFlow<List<DocFile>>(emptyList())
    val files: StateFlow<List<DocFile>> = _files.asStateFlow()

    init { refreshBlocking() }

    private fun scan(): List<DocFile> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { DocFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
            .filter { it.isPdf || it.isImage || it.isDoc }
            .sortedByDescending { it.modified }

    fun refreshBlocking() { _files.value = scan() }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshBlocking() }

    fun find(path: String): DocFile? = _files.value.firstOrNull { it.path == path }
        ?: File(path).takeIf { it.exists() }?.let { DocFile(it.absolutePath, it.name, it.length(), it.lastModified()) }

    /** A new, unused file in the library, e.g. "Images_20261016.pdf", "Images_20261016_2.pdf". */
    fun newFile(base: String, ext: String = "pdf"): File {
        val clean = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "Document" }
        var f = File(dir, "$clean.$ext")
        var n = 2
        while (f.exists()) { f = File(dir, "${clean}_$n.$ext"); n++ }
        return f
    }

    /** A hidden scratch file in the library folder, used to write an edit before it replaces the original. */
    fun tempFile(): File = File(dir, ".tmp_${System.nanoTime()}.pdf")

    /** Replaces [target] with the finished [temp] file. Returns false if it couldn't. */
    suspend fun replaceWith(target: File, temp: File): Boolean = withContext(Dispatchers.IO) {
        val ok = temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = true); temp.delete(); true }.getOrDefault(false)
        refreshBlocking(); ok
    }

    fun stampedName(prefix: String): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    suspend fun delete(doc: DocFile): Boolean = withContext(Dispatchers.IO) {
        val ok = doc.file.delete(); refreshBlocking(); ok
    }

    /** Moves a file to a hidden trash name so delete can be undone. */
    suspend fun softDelete(doc: DocFile): File? = withContext(Dispatchers.IO) {
        val t = File(dir, ".trash_${System.currentTimeMillis()}_${doc.name}")
        val ok = doc.file.renameTo(t); refreshBlocking(); if (ok) t else null
    }

    suspend fun restore(trash: File, name: String) = withContext(Dispatchers.IO) {
        trash.renameTo(File(dir, name)); refreshBlocking()
    }

    suspend fun purgeTrash() = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.name.startsWith(".trash_") }?.forEach { it.delete() }
    }

    suspend fun rename(doc: DocFile, newBase: String): DocFile? = withContext(Dispatchers.IO) {
        val target = newFile(newBase, doc.ext)
        val ok = doc.file.renameTo(target)
        refreshBlocking()
        if (ok) find(target.absolutePath) else null
    }
}

object Fmt {
    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))
}
