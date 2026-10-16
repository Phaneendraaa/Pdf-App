package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * A line of existing text on a page. Positions are fractions of the page (origin top-left),
 * so the UI can overlay them on any rendered size.
 */
data class TextBlock(
    val id: Int,
    val text: String,
    val x: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    val baseline: Float,   // fraction from top
    val sizePt: Float,     // font size in points
    val bold: Boolean,
    val italic: Boolean,
    val family: String,    // "sans", "serif" or "mono"
)

/** One change to an existing line. Empty [text] deletes the line. Colors are ARGB ints. */
data class TextEditOp(
    val block: TextBlock,
    val text: String,
    val sizePt: Float,
    val bold: Boolean,
    val color: Int,
    val background: Int,
)

/**
 * Editing existing PDF text the way mobile editors do it: cover the original line with
 * its own background color, then write the new text at the same baseline in a matching
 * standard font. Works on text PDFs; scanned pages have no text lines to edit.
 */
object TextEdit {

    /** Result of reading a page: its lines, or why it can't be edited. */
    data class Page(val blocks: List<TextBlock>, val widthPt: Float, val heightPt: Float, val problem: String? = null)

    fun read(file: File, pageIndex: Int): Page = PdfTools.load(file).use { doc -> read(doc, pageIndex) }

    private fun read(doc: PDDocument, pageIndex: Int): Page {
        val page = doc.getPage(pageIndex)
        val box = page.cropBox
        if (page.rotation % 360 != 0) return Page(emptyList(), box.width, box.height, "Rotated pages can't be edited yet. Rotate the PDF back to upright first.")
        val blocks = ArrayList<TextBlock>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                val ps = positions?.filter { !it.unicode.isNullOrBlank() || it.unicode == " " } ?: return
                if (ps.isEmpty() || text.isNullOrBlank()) return
                // Split a run where there is a large horizontal gap (columns, tabs).
                var start = 0
                for (i in 1..ps.size) {
                    val gap = if (i < ps.size) ps[i].xDirAdj - (ps[i - 1].xDirAdj + ps[i - 1].widthDirAdj) else Float.MAX_VALUE
                    val size = ps[i - 1].fontSizeInPt.coerceAtLeast(1f)
                    if (i == ps.size || gap > size * 2.5f) {
                        add(ps.subList(start, i))
                        start = i
                    }
                }
            }

            private fun add(run: List<TextPosition>) {
                val txt = run.joinToString("") { it.unicode ?: "" }.trim()
                if (txt.isEmpty()) return
                val size = run.groupingBy { (it.fontSizeInPt * 2).toInt() }.eachCount().maxByOrNull { it.value }!!.key / 2f
                val sizePt = if (size > 0f) size else run.first().heightDir.coerceAtLeast(6f)
                val left = run.minOf { it.xDirAdj }
                val right = run.maxOf { it.xDirAdj + it.widthDirAdj }
                val base = run.maxOf { it.yDirAdj }
                val top = base - sizePt * 0.92f
                val bottom = base + sizePt * 0.28f
                val name = (run.first().font?.name ?: "").lowercase()
                blocks += TextBlock(
                    id = blocks.size,
                    text = txt,
                    x = (left / box.width).coerceIn(0f, 1f),
                    top = (top / box.height).coerceIn(0f, 1f),
                    w = ((right - left) / box.width).coerceIn(0.005f, 1f),
                    h = ((bottom - top) / box.height).coerceIn(0.005f, 1f),
                    baseline = (base / box.height).coerceIn(0f, 1f),
                    sizePt = sizePt,
                    bold = "bold" in name || "black" in name || "heavy" in name || "semibold" in name,
                    italic = "italic" in name || "oblique" in name,
                    family = when {
                        "courier" in name || "mono" in name || "consol" in name -> "mono"
                        "times" in name || "serif" in name && "sans" !in name || "georgia" in name || "garamond" in name || "cambria" in name -> "serif"
                        else -> "sans"
                    },
                )
            }
        }.apply { sortByPosition = true; startPage = pageIndex + 1; endPage = pageIndex + 1 }
        stripper.getText(doc)
        val problem = if (blocks.isEmpty()) "No editable text on this page. If it's a scan, use Text Recognition to copy its text." else null
        return Page(blocks, box.width, box.height, problem)
    }

    private fun standard(family: String, bold: Boolean, italic: Boolean): PDFont = when (family) {
        "serif" -> when { bold && italic -> PDType1Font.TIMES_BOLD_ITALIC; bold -> PDType1Font.TIMES_BOLD; italic -> PDType1Font.TIMES_ITALIC; else -> PDType1Font.TIMES_ROMAN }
        "mono" -> when { bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE; bold -> PDType1Font.COURIER_BOLD; italic -> PDType1Font.COURIER_OBLIQUE; else -> PDType1Font.COURIER }
        else -> when { bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE; bold -> PDType1Font.HELVETICA_BOLD; italic -> PDType1Font.HELVETICA_OBLIQUE; else -> PDType1Font.HELVETICA }
    }

    private fun encodable(font: PDFont, s: String) = try { font.encode(s); true } catch (e: Exception) { false }

    /** Picks a font that can write [text]: matching standard font, else Roboto (wider Unicode), else replaces unknown characters. */
    private fun fontFor(doc: PDDocument, op: TextEditOp, unicodeFont: () -> PDFont?): Pair<PDFont, String> {
        val std = standard(op.block.family, op.bold, op.block.italic)
        if (encodable(std, op.text)) return std to op.text
        unicodeFont()?.let { if (encodable(it, op.text)) return it to op.text }
        return std to op.text.map { if (encodable(std, it.toString())) it else '?' }.joinToString("")
    }

    private fun rgb(c: Int) = Triple(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)

    /** Writes a copy of [src] with [ops] applied to page [pageIndex]. Returns the page count. */
    fun apply(src: File, pageIndex: Int, ops: List<TextEditOp>, out: File): Int = PdfTools.load(src).use { doc ->
        applyTo(doc, pageIndex, ops)
        doc.save(out)
        doc.numberOfPages
    }

    /** Applies edits to an open document (used together with the other page edits). */
    fun applyTo(doc: PDDocument, pageIndex: Int, ops: List<TextEditOp>) {
        if (ops.isEmpty()) return
        val page = doc.getPage(pageIndex)
        val box = page.cropBox
        var roboto: PDFont? = null; var robotoTried = false
        val unicode = {
            if (!robotoTried) {
                robotoTried = true
                roboto = File("/system/fonts/Roboto-Regular.ttf").takeIf { it.exists() }?.let { runCatching { PDType0Font.load(doc, it) }.getOrNull() }
            }
            roboto
        }
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            ops.forEach { op ->
                val b = op.block
                val x = box.lowerLeftX + b.x * box.width
                val yTop = box.lowerLeftY + (1f - b.top) * box.height
                val h = b.h * box.height
                val pad = 1.2f
                // Cover the old line.
                val (br, bg, bb) = rgb(op.background)
                cs.setNonStrokingColor(br, bg, bb)
                cs.addRect(x - pad, yTop - h - pad, b.w * box.width + 2 * pad, h + 2 * pad)
                cs.fill()
                if (op.text.isBlank()) return@forEach
                // Write the new line on the same baseline.
                val (font, safe) = fontFor(doc, op, unicode)
                val (r, g, bl) = rgb(op.color)
                cs.beginText()
                cs.setFont(font, op.sizePt)
                cs.setNonStrokingColor(r, g, bl)
                cs.newLineAtOffset(x, box.lowerLeftY + (1f - b.baseline) * box.height)
                cs.showText(safe)
                cs.endText()
            }
        }
    }

    /** Width of [text] in points with the font Pdf Kit will use, for the live preview. */
    fun widthPt(text: String, family: String, bold: Boolean, italic: Boolean, sizePt: Float): Float {
        val f = standard(family, bold, italic)
        return runCatching { f.getStringWidth(text) / 1000f * sizePt }.getOrDefault(text.length * sizePt * 0.5f)
    }
}
