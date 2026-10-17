package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File

/** Edits on one page. All coordinates are fractions of the page (0..1, origin top-left). */
data class InkStroke(val points: List<Pair<Float, Float>>, val highlight: Boolean, val color: Int)
data class TextMark(val text: String, val x: Float, val y: Float, val size: Float, val color: Int)
data class ImageMark(val bitmap: Bitmap, val x: Float, val y: Float, val w: Float)

object PageEdits {
    private fun r(c: Int) = ((c shr 16) and 0xFF) / 255f
    private fun g(c: Int) = ((c shr 8) and 0xFF) / 255f
    private fun b(c: Int) = (c and 0xFF) / 255f

    /** All edits for one page. */
    data class PageChange(
        val page: Int,
        val strokes: List<InkStroke> = emptyList(),
        val texts: List<TextMark> = emptyList(),
        val images: List<ImageMark> = emptyList(),
        val textEdits: List<TextEditOp> = emptyList(),
    )

    /** Writes a copy of [src] with the edits flattened onto page [pageIndex]. Returns the page count. */
    fun apply(
        src: File, pageIndex: Int, strokes: List<InkStroke>, texts: List<TextMark>, images: List<ImageMark>, out: File,
        textEdits: List<TextEditOp> = emptyList(),
    ): Int = applyAll(src, listOf(PageChange(pageIndex, strokes, texts, images, textEdits)), out)

    /** Writes a copy of [src] with every page's edits applied in one pass. Returns the page count. */
    fun applyAll(src: File, changes: List<PageChange>, out: File): Int =
        PdfTools.load(src).use { doc ->
            changes.forEach { ch -> if (ch.page in 0 until doc.numberOfPages) applyTo(doc, ch) }
            doc.save(out)
            doc.numberOfPages
        }

    private fun applyTo(doc: com.tom_roush.pdfbox.pdmodel.PDDocument, ch: PageChange) {
        val pageIndex = ch.page; val strokes = ch.strokes; val texts = ch.texts; val images = ch.images
        run {
            // Existing-text edits go first so new ink and marks sit on top of them.
            TextEdit.applyTo(doc, pageIndex, ch.textEdits)
            val page = doc.getPage(pageIndex)
            val box = page.mediaBox
            fun px(x: Float) = box.lowerLeftX + x * box.width
            fun py(y: Float) = box.lowerLeftY + (1f - y) * box.height
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                strokes.filter { it.points.size > 1 }.forEach { s ->
                    cs.saveGraphicsState()
                    if (s.highlight) cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.35f })
                    cs.setStrokingColor(r(s.color), g(s.color), b(s.color))
                    cs.setLineWidth(if (s.highlight) box.width * 0.025f else box.width * 0.0045f)
                    cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                    val (x0, y0) = s.points.first()
                    cs.moveTo(px(x0), py(y0))
                    s.points.drop(1).forEach { (x, y) -> cs.lineTo(px(x), py(y)) }
                    cs.stroke()
                    cs.restoreGraphicsState()
                }
                images.forEach { m ->
                    val img = LosslessFactory.createFromImage(doc, m.bitmap)
                    val w = m.w * box.width
                    val h = w * m.bitmap.height / m.bitmap.width
                    cs.drawImage(img, px(m.x), py(m.y) - h, w, h)
                }
                val font = PDType1Font.HELVETICA
                texts.forEach { t ->
                    val clean = t.text.filter { it.code in 32..126 || it.code in 160..255 }
                    if (clean.isBlank()) return@forEach
                    val size = t.size * box.width
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setNonStrokingColor(r(t.color), g(t.color), b(t.color))
                    cs.newLineAtOffset(px(t.x), py(t.y) - size * 0.9f)
                    cs.showText(clean)
                    cs.endText()
                }
            }
        }
    }
}
