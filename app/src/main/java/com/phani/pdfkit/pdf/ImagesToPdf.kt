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
    val filter: String = "Original",  // Original, Auto, Gray, B&W
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
        filters: Map<String, String> = emptyMap(),
    ): Int {
        val margin = when (opts.margin) { "Small" -> 18f; "Large" -> 40f; else -> 0f }
        var pages = 0
        PDDocument().use { doc ->
            uris.forEachIndexed { i, uri ->
                coroutineContext.ensureActive()
                val raw = Bitmaps.decode(context, uri, quality.maxSide) ?: return@forEachIndexed
                val filtered = Filters.apply(raw, filters[uri.toString()] ?: opts.filter).also { if (it !== raw) raw.recycle() }
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

    /** One picture for [idCard]: where it is, extra rotation in degrees, and its filter. */
    data class Card(val uri: Uri, val turn: Int = 0, val filter: String = "Auto")

    /**
     * ID cards two to a page (front above back), the way forms ask for Aadhaar or PAN.
     * [actualSize] prints each card at its real size (85.6 mm wide); otherwise larger. Returns pages written.
     */
    suspend fun idCard(context: Context, cards: List<Card>, out: File, actualSize: Boolean, quality: Quality): Int {
        PDDocument().use { doc ->
            val cardW = if (actualSize) 85.6f / 25.4f * 72f else A4_W * 0.72f
            val gap = 28f
            cards.chunked(2).forEach { pair ->
                val imgs = pair.mapNotNull { card ->
                    coroutineContext.ensureActive()
                    val raw = Bitmaps.decode(context, card.uri, quality.maxSide.coerceAtMost(2000)) ?: return@mapNotNull null
                    val f = Filters.apply(raw, card.filter).also { if (it !== raw) raw.recycle() }
                    val t = ((card.turn % 360) + 360) % 360
                    val b = if (t == 0) f else android.graphics.Bitmap.createBitmap(f, 0, 0, f.width, f.height, android.graphics.Matrix().apply { postRotate(t.toFloat()) }, true).also { if (it !== f) f.recycle() }
                    val x = JPEGFactory.createFromImage(doc, b, quality.jpeg)
                    val ratio = b.width.toFloat() / b.height
                    b.recycle()
                    // Very tall pictures (a portrait card) are limited by height so two still fit.
                    var w = cardW; var h = w / ratio
                    if (h > A4_H * 0.42f) { h = A4_H * 0.42f; w = h * ratio }
                    Triple(x, w, h)
                }
                if (imgs.isEmpty()) return@forEach
                val page = PDPage(PDRectangle(A4_W, A4_H))
                doc.addPage(page)
                val total = imgs.sumOf { it.third.toDouble() }.toFloat() + gap * (imgs.size - 1)
                var y = (A4_H + total) / 2f
                PDPageContentStream(doc, page).use { cs ->
                    imgs.forEach { (img, w, h) ->
                        y -= h
                        cs.drawImage(img, (A4_W - w) / 2f, y, w, h)
                        y -= gap
                    }
                }
            }
            if (doc.numberOfPages == 0) return 0
            doc.save(out)
            return doc.numberOfPages
        }
    }
}
