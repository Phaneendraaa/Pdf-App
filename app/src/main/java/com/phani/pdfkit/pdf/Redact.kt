package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * True redaction. A page with black boxes is redrawn as a picture with the boxes burned in, so
 * the hidden text, images and links under them are gone from the file, not just covered.
 * Pages without boxes are left as they are. The file's title/author info is cleared too.
 */
object Redact {

    suspend fun apply(src: File, boxes: Map<Int, List<Frac>>, out: File, report: (Float) -> Unit): Int = withContext(Dispatchers.IO) {
        val view = (PdfDoc.open(src) as? PdfDoc.Open.Ok)?.doc ?: throw ToolError("Can't read this PDF. If it has a password, unlock it first.")
        try {
            PdfTools.load(src).use { doc ->
                val targets = boxes.filterValues { it.isNotEmpty() }.keys.filter { it in 0 until doc.numberOfPages }.sorted()
                targets.forEachIndexed { k, i ->
                    coroutineContext.ensureActive()
                    val old = doc.getPage(i)
                    val rot = ((old.rotation % 360) + 360) % 360
                    val box = old.cropBox
                    val w = if (rot == 90 || rot == 270) box.height else box.width
                    val h = if (rot == 90 || rot == 270) box.width else box.height
                    val px = (w * 2.5f).toInt().coerceIn(800, 2400) // about 180 dpi
                    val rendered = view.render(i, px) ?: throw ToolError("Couldn't draw page ${i + 1}")
                    val bmp = rendered.copy(Bitmap.Config.ARGB_8888, true)
                    val cv = Canvas(bmp)
                    val black = Paint().apply { color = android.graphics.Color.BLACK; isAntiAlias = false }
                    boxes[i].orEmpty().forEach { f ->
                        val pad = 1.5f
                        cv.drawRect(RectF(f.x * bmp.width - pad, f.y * bmp.height - pad, (f.x + f.w) * bmp.width + pad, (f.y + f.h) * bmp.height + pad), black)
                    }
                    val page = PDPage(PDRectangle(w, h))
                    val img = JPEGFactory.createFromImage(doc, bmp, 0.9f)
                    bmp.recycle()
                    PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, w, h) }
                    doc.pages.insertAfter(page, old)
                    doc.removePage(old)
                    report((k + 1f) / targets.size * 0.9f)
                }
                doc.documentInformation = PDDocumentInformation()
                doc.documentCatalog.metadata = null
                doc.save(out)
                report(1f)
                doc.numberOfPages
            }
        } finally { view.close() }
    }

    /** Finds Aadhaar numbers on every page and returns boxes over their first 8 digits. Reads scans with OCR. */
    suspend fun findAadhaar(src: File, report: (Float) -> Unit = {}): Map<Int, List<Frac>> {
        val found = HashMap<Int, MutableList<Frac>>()
        val texts = withContext(Dispatchers.IO) { runCatching { PdfIndex.build(src) }.getOrDefault(emptyList()) }
        val view = (PdfDoc.open(src) as? PdfDoc.Open.Ok)?.doc
        try {
            val n = view?.pageCount ?: texts.size
            for (i in 0 until n) {
                coroutineContext.ensureActive()
                val pt = texts.getOrNull(i)
                if (pt != null && pt.text.isNotBlank()) {
                    pt.aadhaar().forEach { m -> found.getOrPut(i) { ArrayList() } += m.rects }
                } else if (view != null) {
                    val bmp = view.render(i, 1800) ?: continue
                    runCatching { Ocr.words(bmp) }.getOrDefault(emptyList()).forEach { line ->
                        ocrAadhaar(line).forEach { r ->
                            found.getOrPut(i) { ArrayList() } += Frac(r.left / bmp.width.toFloat(), r.top / bmp.height.toFloat(), r.width() / bmp.width, r.height() / bmp.height)
                        }
                    }
                }
                report((i + 1f) / n)
            }
        } finally { view?.close() }
        return found
    }

    /** In one OCR line, the first 8 digits of each 12-digit number, as pixel boxes. */
    private fun ocrAadhaar(words: List<OcrLine>): List<RectF> {
        val out = ArrayList<RectF>()
        val digits = words.map { w -> w to w.text.filter { it.isDigit() } }
        var i = 0
        while (i < digits.size) {
            val (w, d) = digits[i]
            if (d.length == 12 && w.text.count { it.isDigit() } >= w.text.length - 2) {
                val cut = w.left + (w.right - w.left) * 8f / 12f
                out += RectF(w.left.toFloat(), w.top.toFloat(), cut, w.bottom.toFloat()); i++; continue
            }
            if (i + 2 < digits.size && d.length == 4 && digits[i + 1].second.length == 4 && digits[i + 2].second.length == 4) {
                val a = w; val b = digits[i + 1].first
                out += RectF(a.left.toFloat(), minOf(a.top, b.top).toFloat(), b.right.toFloat(), maxOf(a.bottom, b.bottom).toFloat())
                i += 3; continue
            }
            i++
        }
        return out
    }
}
