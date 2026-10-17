package com.phani.pdfkit.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

data class PageOptions(
    val size: String = "A4",          // A4, Letter, Fit to image
    val margin: String = "None",      // None, Small, Large
    val orientation: String = "Portrait", // Portrait, Landscape
    val filter: String = "Original",  // Original, Enhanced, B&W
)

object ImagesToPdf {
    private const val A4_W = 595.28f; private const val A4_H = 841.89f
    private const val LT_W = 612f; private const val LT_H = 792f

    /**
     * Builds a PDF from images. [progress] receives 0..1 as pages are added.
     * Returns the number of pages written.
     */
    suspend fun create(
        context: Context,
        uris: List<Uri>,
        out: File,
        opts: PageOptions,
        quality: Quality,
        progress: (Float) -> Unit,
        rotations: Map<String, Int> = emptyMap(),
    ): Int {
        val margin = when (opts.margin) { "Small" -> 18f; "Large" -> 40f; else -> 0f }
        var pages = 0
        PDDocument().use { doc ->
            uris.forEachIndexed { i, uri ->
                coroutineContext.ensureActive()
                val raw = Bitmaps.decode(context, uri, quality.maxSide) ?: return@forEachIndexed
                val filtered = Filters.apply(raw, opts.filter).also { if (it !== raw) raw.recycle() }
                val turn = ((rotations[uri.toString()] ?: 0) % 360 + 360) % 360
                val bmp = if (turn == 0) filtered else android.graphics.Bitmap.createBitmap(
                    filtered, 0, 0, filtered.width, filtered.height, android.graphics.Matrix().apply { postRotate(turn.toFloat()) }, true,
                ).also { if (it !== filtered) filtered.recycle() }
                val imgW = bmp.width.toFloat(); val imgH = bmp.height.toFloat()
                val (pw, ph) = when (opts.size) {
                    "Letter" -> LT_W to LT_H
                    "Fit to image" -> { val w = A4_W; w to (w * imgH / imgW) }
                    else -> A4_W to A4_H
                }.let { (w, h) -> if (opts.size != "Fit to image" && opts.orientation == "Landscape") h to w else w to h }
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, quality.jpeg)
                bmp.recycle()
                val boxW = pw - 2 * margin; val boxH = ph - 2 * margin
                val s = minOf(boxW / imgW, boxH / imgH)
                val dw = imgW * s; val dh = imgH * s
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
                }
                pages++
                progress((i + 1f) / uris.size)
            }
            if (pages > 0) doc.save(out)
        }
        return pages
    }
}
