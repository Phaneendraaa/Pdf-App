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
    /** Folder inside the library, "" for the top level. */
    val folder: String = "",
) {
    val file: File get() = File(path)
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val isPdf: Boolean get() = ext == "pdf"
    val isImage: Boolean get() = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
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
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    /** Folder names inside the library, A to Z. */
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    init { refreshBlocking(); runCatching { purgeOldTrash() } }

    private fun folderDirs(): List<File> = (dir.listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith(".") }

    private fun scan(): List<DocFile> =
        (listOf(dir) + folderDirs()).flatMap { d ->
            val folder = if (d == dir) "" else d.name
            (d.listFiles() ?: emptyArray())
                .filter { it.isFile && !it.name.startsWith(".") }
                .map { DocFile(it.absolutePath, it.name, it.length(), it.lastModified(), folder) }
        }
            .filter { it.isPdf || it.isImage || it.isDoc }
            .sortedByDescending { it.modified }

    fun refreshBlocking() {
        _files.value = scan()
        _folders.value = folderDirs().map { it.name }.sortedBy { it.lowercase() }
    }

    // ---------- Folders ----------

    private fun cleanName(n: String) = n.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().trim('.').ifEmpty { "Folder" }

    suspend fun createFolder(name: String): String? = withContext(Dispatchers.IO) {
        val f = File(dir, cleanName(name))
        val ok = f.exists() && f.isDirectory || f.mkdirs()
        refreshBlocking(); if (ok) f.name else null
    }

    /** Removes a folder; its files move to the top level so nothing is lost. */
    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        val d = File(dir, name)
        d.listFiles()?.forEach { f -> f.renameTo(uniqueIn(dir, f.name)) }
        d.delete(); refreshBlocking()
    }

    /** Moves [doc] into [folder] ("" for the top level). Returns the moved file. */
    suspend fun move(doc: DocFile, folder: String): File? = withContext(Dispatchers.IO) {
        val target = if (folder.isEmpty()) dir else File(dir, folder).apply { mkdirs() }
        if (doc.file.parentFile == target) return@withContext doc.file
        val to = uniqueIn(target, doc.name)
        val ok = doc.file.renameTo(to)
        refreshBlocking(); if (ok) to else null
    }

    private fun uniqueIn(d: File, name: String): File {
        var f = File(d, name); var n = 2
        val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
        while (f.exists()) { f = File(d, if (ext.isEmpty()) "${base}_$n" else "${base}_$n.$ext"); n++ }
        return f
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshBlocking() }

    fun find(path: String): DocFile? = _files.value.firstOrNull { it.path == path }
        ?: File(path).takeIf { it.exists() }?.let { DocFile(it.absolutePath, it.name, it.length(), it.lastModified()) }

    /** A new, unused file in the library, e.g. "Images_20261016.pdf", "Images_20261016_2.pdf". */
    fun newFile(base: String, ext: String = "pdf", inDir: File = dir): File {
        val clean = base.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "Document" }
        var f = File(inDir, "$clean.$ext")
        var n = 2
        while (f.exists()) { f = File(inDir, "${clean}_$n.$ext"); n++ }
        return f
    }

    /** A hidden scratch file in the library folder, used to write an edit before it replaces the original. */
    fun tempFile(): File = File(dir, ".tmp_${System.nanoTime()}.pdf")

    /** Replaces [target] with the finished [temp] file. Returns false if it couldn't. */
    suspend fun replaceWith(target: File, temp: File): Boolean = withContext(Dispatchers.IO) {
        val ok = temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = true); temp.delete(); true }.getOrDefault(false)
        refreshBlocking(); ok
    }

    /** For work that must finish even after the screen that started it is gone (like Undo). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

    /** Result of saving an edit: the file that now holds it, and a backup of the original when it was overwritten. */
    data class Saved(val file: File, val backup: File?)

    /**
     * Writes an edit with [write] and either swaps it over [target] (replace, keeping a backup for Undo)
     * or keeps it as a new copy named after [target] plus [suffix]. Returns null if writing failed.
     */
    suspend fun saveEdit(target: File, replace: Boolean, suffix: String, write: (File) -> Unit): Saved? {
        val base = target.name.replace(Regex("^in_\\d+_"), "").substringBeforeLast('.').removeSuffix(suffix)
        val out = if (replace) tempFile() else newFile(base + suffix, inDir = target.parentFile?.takeIf { it.absolutePath.startsWith(dir.absolutePath) } ?: dir)
        val ok = withContext(Dispatchers.IO) { runCatching { write(out) }.isSuccess }
        if (!ok) { out.delete(); return null }
        if (!replace) { refresh(); return Saved(out, null) }
        val backup = withContext(Dispatchers.IO) { runCatching { target.copyTo(File(dir, ".undo_backup.pdf"), overwrite = true) }.getOrNull() }
        return if (replaceWith(target, out)) Saved(target, backup) else { out.delete(); null }
    }

    /** Puts the backup made by [saveEdit] back over [target]. */
    suspend fun undoSave(target: File, backup: File): Boolean {
        val tmp = tempFile()
        val copied = withContext(Dispatchers.IO) { runCatching { backup.copyTo(tmp, overwrite = true); true }.getOrDefault(false) }
        return copied && replaceWith(target, tmp)
    }

    fun stampedName(prefix: String): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    suspend fun delete(doc: DocFile): Boolean = withContext(Dispatchers.IO) {
        val ok = doc.file.delete(); refreshBlocking(); ok
    }

    // ---------- Trash (kept 30 days) ----------

    /** A deleted file waiting in the trash. */
    data class Trashed(val file: File, val name: String, val folder: String, val deletedAt: Long) {
        val daysLeft: Int get() = (30 - ((System.currentTimeMillis() - deletedAt) / 86_400_000L)).toInt().coerceAtLeast(0)
    }

    /** Moves a file to a hidden trash name so delete can be undone, and so it can be restored for 30 days. */
    suspend fun softDelete(doc: DocFile): File? = withContext(Dispatchers.IO) {
        val rel = if (doc.folder.isEmpty()) doc.name else doc.folder + "/" + doc.name
        val t = File(dir, ".trash_${System.currentTimeMillis()}_" + java.net.URLEncoder.encode(rel, "UTF-8"))
        val ok = doc.file.renameTo(t); refreshBlocking(); if (ok) t else null
    }

    private fun parseTrash(f: File): Trashed? {
        val m = Regex("^\\.trash_(\\d+)_(.+)$").find(f.name) ?: return null
        val rel = runCatching { java.net.URLDecoder.decode(m.groupValues[2], "UTF-8") }.getOrDefault(m.groupValues[2])
        val folder = rel.substringBeforeLast('/', ""); val name = rel.substringAfterLast('/')
        return Trashed(f, name, folder, m.groupValues[1].toLongOrNull() ?: f.lastModified())
    }

    fun trash(): List<Trashed> = (dir.listFiles() ?: emptyArray()).mapNotNull { parseTrash(it) }.sortedByDescending { it.deletedAt }

    /** Puts a trashed file back where it was (its folder is made again if needed). */
    suspend fun restore(trash: File) = withContext(Dispatchers.IO) {
        val t = parseTrash(trash) ?: return@withContext
        val target = if (t.folder.isEmpty()) dir else File(dir, t.folder).apply { mkdirs() }
        trash.renameTo(uniqueIn(target, t.name)); refreshBlocking()
    }

    suspend fun restore(trash: File, @Suppress("UNUSED_PARAMETER") name: String) = restore(trash)

    suspend fun deleteForever(trash: File) = withContext(Dispatchers.IO) { trash.delete(); Unit }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) { trash().forEach { it.file.delete() } }

    private fun purgeOldTrash() { trash().filter { it.daysLeft <= 0 }.forEach { it.file.delete() } }

    suspend fun rename(doc: DocFile, newBase: String): DocFile? = withContext(Dispatchers.IO) {
        val target = newFile(newBase, doc.ext, doc.file.parentFile ?: dir)
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
