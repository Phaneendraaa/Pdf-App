package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import com.phani.pdfkit.Graph
import com.phani.pdfkit.ui.components.Toaster
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Turns scanned pages into searchable ones: ML Kit reads the text on each page picture, and an
 * invisible copy of it is laid over the words. The page looks the same but can be searched and copied.
 */
object SearchablePdf {
    data class Result(val pages: Int, val added: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val RENDER_WIDTH = 1800

    /** Adds a hidden text layer to pages that have no text. Writes the result to [out]. */
    suspend fun make(src: File, out: File, progress: (Float) -> Unit): Result {
        val opened = withContext(Dispatchers.IO) { PdfDoc.open(src) }
        val view = (opened as? PdfDoc.Open.Ok)?.doc
            ?: throw ToolError(if (opened is PdfDoc.Open.Locked) "This PDF is password protected. Unlock it first." else "Can't read this PDF. It may be damaged.")
        try {
            return withContext(Dispatchers.IO) {
                PdfTools.load(src).use { doc ->
                    val n = doc.numberOfPages
                    var needed = 0
                    var added = 0
                    for (i in 0 until n) {
                        coroutineContext.ensureActive()
                        progress(i.toFloat() / n)
                        val page = doc.getPage(i)
                        if (page.rotation % 360 != 0 || hasText(doc, i)) continue
                        needed++
                        val bmp = view.render(i, RENDER_WIDTH) ?: continue
                        val lines = runCatching { Ocr.lines(bmp) }.getOrDefault(emptyList())
                        if (lines.isNotEmpty() && addLayer(doc, i, bmp, lines)) added++
                    }
                    when {
                        needed == 0 -> throw ToolError("This PDF already has searchable text on every page.")
                        added == 0 -> throw ToolError("No readable text was found. Scans with clear printed text work best.")
                    }
                    doc.save(out)
                    progress(1f)
                    Result(n, added)
                }
            }
        } finally {
            view.close()
        }
    }

    /** Runs [make] quietly after a scan so the scan is searchable by the time you look for something in it. */
    fun inBackground(file: File) {
        scope.launch {
            val lib = Graph.library
            val tmp = lib.tempFile()
            val ok = runCatching { make(file, tmp) {} }.isSuccess && lib.replaceWith(file, tmp)
            if (!ok) tmp.delete() else withContext(Dispatchers.Main) { Toaster.show("Scan is now searchable") }
        }
    }

    private fun hasText(doc: PDDocument, index: Int): Boolean = runCatching {
        PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }.getText(doc).trim().length > 12
    }.getOrDefault(false)

    private fun addLayer(doc: PDDocument, index: Int, bmp: Bitmap, lines: List<OcrLine>): Boolean {
        val page = doc.getPage(index)
        val box = page.cropBox
        val sx = box.width / bmp.width
        val sy = box.height / bmp.height
        val latinFont = PDType1Font.HELVETICA
        val uni by lazy { unicodeFont(doc) }
        var wrote = false
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setRenderingMode(RenderingMode.NEITHER)
            for (l in lines) {
                val latin = l.text.all { it.code in 32..126 || it.code in 160..255 }
                val font = if (latin) latinFont else uni ?: latinFont
                val txt = (if (font === latinFont) l.text.filter { it.code in 32..126 || it.code in 160..255 } else l.text).trim()
                if (txt.isEmpty()) continue
                val hPt = (l.bottom - l.top) * sy
                val wPt = (l.right - l.left) * sx
                val size = (hPt * 0.8f).coerceIn(4f, 72f)
                val natural = runCatching { font.getStringWidth(txt) / 1000f * size }.getOrDefault(0f)
                if (natural <= 0f || wPt <= 0f) continue
                val scaleX = (wPt / natural).coerceIn(0.2f, 5f)
                val x = box.lowerLeftX + l.left * sx
                val y = box.lowerLeftY + box.height - l.bottom * sy + hPt * 0.2f
                runCatching {
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setTextMatrix(Matrix(scaleX, 0f, 0f, 1f, x, y))
                    cs.showText(txt)
                    cs.endText()
                    wrote = true
                }
            }
        }
        return wrote
    }

    /** A phone font that has Hindi letters, so Devanagari OCR text can be searched too. */
    private fun unicodeFont(doc: PDDocument): com.tom_roush.pdfbox.pdmodel.font.PDFont? {
        val dir = File("/system/fonts")
        val files = dir.listFiles { f -> f.name.startsWith("NotoSansDevanagari") && f.name.endsWith(".ttf") && !f.name.contains("VF") }.orEmpty()
            .sortedBy { if ("Regular" in it.name) 0 else 1 }
        for (f in files) runCatching { return com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(doc, f) }
        return null
    }
}
