package com.phani.pdfkit.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.phani.pdfkit.data.DocFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileShare {
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun mime(doc: DocFile): String = when {
        doc.isPdf -> "application/pdf"
        doc.ext == "png" -> "image/png"
        doc.ext == "webp" -> "image/webp"
        doc.isImage -> "image/jpeg"
        doc.ext == "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }

    fun share(context: Context, docs: List<DocFile>) {
        if (docs.isEmpty()) return
        val intent = if (docs.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime(docs[0])
                putExtra(Intent.EXTRA_STREAM, uri(context, docs[0].file))
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(docs.map { uri(context, it.file) }))
            }
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openExternal(context: Context, doc: DocFile) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, doc.file), mime(doc))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Copies a file to the public Downloads/Pdf Kit folder. Returns a readable location label. */
    suspend fun saveToDownloads(context: Context, doc: DocFile): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, doc.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime(doc))
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pdf Kit")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
                context.contentResolver.openOutputStream(uri)?.use { out -> doc.file.inputStream().use { it.copyTo(out) } }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pdf Kit").apply { mkdirs() }
                doc.file.copyTo(File(dir, doc.name), overwrite = true)
            }
            "Downloads / Pdf Kit"
        }.getOrNull()
    }
}
