package com.phani.pdfkit.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.phani.pdfkit.data.DocFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfThumbs {
    private val cache = LruCache<String, Bitmap>(40)

    /** Renders page [page] of a PDF at [widthPx]. Returns null for locked or broken files. */
    fun render(file: File, page: Int = 0, widthPx: Int = 300): Bitmap? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (page >= r.pageCount) return null
                r.openPage(page).use { p ->
                    val h = (widthPx * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    fun pageCount(file: File): Int = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }.getOrDefault(0)

    suspend fun thumb(d: DocFile): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${d.path}:${d.modified}"
        cache.get(key) ?: render(d.file)?.also { cache.put(key, it) }
    }
}

@Composable
fun rememberPdfThumb(d: DocFile): ImageBitmap? {
    var bmp by remember(d.path, d.modified) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(d.path, d.modified) { bmp = PdfThumbs.thumb(d)?.asImageBitmap() }
    return bmp
}
