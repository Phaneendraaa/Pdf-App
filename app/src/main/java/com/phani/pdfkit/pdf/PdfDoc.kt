package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Thread-safe wrapper around PdfRenderer for the viewer. */
class PdfDoc private constructor(private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    val pageCount: Int = renderer.pageCount
    /** Width / height of each page. */
    val ratios: List<Float> = (0 until pageCount).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$index:$widthPx"
        cache.get(key) ?: lock.withLock {
            runCatching {
                renderer.openPage(index).use { p ->
                    val h = (widthPx * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp); bmp
                }
            }.getOrNull()
        }
    }

    fun close() { runCatching { renderer.close(); fd.close() } }

    sealed interface Open {
        data class Ok(val doc: PdfDoc) : Open
        data object Locked : Open
        data object Broken : Open
    }

    companion object {
        fun open(file: File): Open = try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try { Open.Ok(PdfDoc(fd, PdfRenderer(fd))) } catch (e: SecurityException) { fd.close(); Open.Locked } catch (e: Exception) { fd.close(); Open.Broken }
        } catch (e: Exception) { Open.Broken }
    }
}
