package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.io.File
import java.util.Calendar

/**
 * Marks that stay editable: real PDF annotations (ink, highlight, underline, strike-through,
 * shapes, sticky notes, stamps) with drawn appearances, so they show in every viewer and can be
 * removed later here or in Acrobat. Coordinates are page fractions (origin top-left).
 */
sealed interface Annot {
    data class Ink(val points: List<Pair<Float, Float>>, val color: Int, val highlight: Boolean) : Annot
    data class LineMark(val rect: Frac, val kind: String, val color: Int) : Annot // kind: Underline, StrikeOut, Highlight
    data class Shape(val rect: Frac, val circle: Boolean, val color: Int) : Annot
    data class Note(val x: Float, val y: Float, val text: String) : Annot
    data class Stamp(val label: String, val x: Float, val y: Float, val w: Float, val color: Int) : Annot
}

object Annotations {
    /** One mark already in a PDF, for the "Comments & marks" list. */
    data class Info(val page: Int, val index: Int, val kind: String, val text: String)

    private val KINDS = mapOf(
        "Ink" to "Drawing", "Highlight" to "Highlight", "Underline" to "Underline", "StrikeOut" to "Strike-through",
        "Squiggly" to "Squiggly underline", "Square" to "Rectangle", "Circle" to "Circle", "Text" to "Note",
        "Stamp" to "Stamp", "FreeText" to "Text box", "Line" to "Line", "Polygon" to "Shape", "PolyLine" to "Shape",
    )

    private fun color(c: Int) = PDColor(floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f), PDDeviceRGB.INSTANCE)

    private fun stamp(a: com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup) {
        a.titlePopup = "Pdf Kit"
        a.creationDate = Calendar.getInstance()
        a.isPrinted = true
    }

    /** Adds [list] to page [pageIndex] of an open document. */
    fun add(doc: PDDocument, pageIndex: Int, list: List<Annot>) {
        if (list.isEmpty()) return
        val page = doc.getPage(pageIndex)
        val box = page.mediaBox
        fun px(x: Float) = box.lowerLeftX + x * box.width
        fun py(y: Float) = box.lowerLeftY + (1f - y) * box.height
        val annots = page.annotations
        list.forEach { m ->
            val a: PDAnnotation = when (m) {
                is Annot.Ink -> PDAnnotationMarkup().apply {
                    cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
                    val flat = FloatArray(m.points.size * 2)
                    m.points.forEachIndexed { i, (x, y) -> flat[i * 2] = px(x); flat[i * 2 + 1] = py(y) }
                    inkList = arrayOf(flat)
                    color = color(m.color)
                    borderStyle = PDBorderStyleDictionary().apply { width = if (m.highlight) box.width * 0.025f else box.width * 0.0045f }
                    if (m.highlight) constantOpacity = 0.35f
                    val xs = m.points.map { px(it.first) }; val ys = m.points.map { py(it.second) }
                    rectangle = PDRectangle(xs.min(), ys.min(), (xs.max() - xs.min()).coerceAtLeast(1f), (ys.max() - ys.min()).coerceAtLeast(1f))
                    stamp(this)
                }
                is Annot.LineMark -> PDAnnotationTextMarkup(m.kind).apply {
                    val x0 = px(m.rect.x); val x1 = px(m.rect.x + m.rect.w)
                    val yTop = py(m.rect.y); val yBot = py(m.rect.y + m.rect.h)
                    rectangle = PDRectangle(x0, yBot, x1 - x0, yTop - yBot)
                    quadPoints = floatArrayOf(x0, yTop, x1, yTop, x0, yBot, x1, yBot)
                    color = color(m.color)
                    if (m.kind == PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT) constantOpacity = 0.4f
                    stamp(this)
                }
                is Annot.Shape -> PDAnnotationSquareCircle(if (m.circle) PDAnnotationSquareCircle.SUB_TYPE_CIRCLE else PDAnnotationSquareCircle.SUB_TYPE_SQUARE).apply {
                    val x0 = px(m.rect.x); val y1 = py(m.rect.y); val y0 = py(m.rect.y + m.rect.h)
                    rectangle = PDRectangle(x0, y0, m.rect.w * box.width, y1 - y0)
                    color = color(m.color)
                    borderStyle = PDBorderStyleDictionary().apply { width = 2f }
                    stamp(this)
                }
                is Annot.Note -> PDAnnotationText().apply {
                    name = PDAnnotationText.NAME_COMMENT
                    contents = m.text
                    rectangle = PDRectangle(px(m.x), py(m.y) - 20f, 20f, 20f)
                    color = color(0xFFF5B301.toInt())
                    stamp(this)
                }
                is Annot.Stamp -> PDAnnotationRubberStamp().apply {
                    name = m.label.replace(" ", "")
                    contents = m.label
                    val w = m.w * box.width; val h = w * 0.3f
                    rectangle = PDRectangle(px(m.x), py(m.y) - h, w, h)
                    color = color(m.color)
                    stamp(this)
                    appearance = stampAppearance(doc, m.label, w, h, m.color)
                }
            }
            annots += a
            if (a !is PDAnnotationRubberStamp) runCatching { a.constructAppearances(doc) }
        }
        page.annotations = annots
    }

    /** A bordered box with the label in bold, like a rubber stamp. */
    private fun stampAppearance(doc: PDDocument, label: String, w: Float, h: Float, c: Int): PDAppearanceDictionary {
        val ap = PDAppearanceStream(doc)
        ap.bBox = PDRectangle(w, h)
        ap.resources = PDResources()
        val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
        PDPageContentStream(doc, ap).use { cs ->
            val lw = (h * 0.07f).coerceAtLeast(1.5f)
            cs.setStrokingColor(r, g, b); cs.setNonStrokingColor(r, g, b)
            cs.setLineWidth(lw)
            cs.addRect(lw, lw, w - 2 * lw, h - 2 * lw); cs.stroke()
            val font = PDType1Font.HELVETICA_BOLD
            val text = label.uppercase().filter { it.code in 32..126 }
            var size = h * 0.5f
            val tw = font.getStringWidth(text) / 1000f * size
            if (tw > w * 0.86f) size *= w * 0.86f / tw
            val textW = font.getStringWidth(text) / 1000f * size
            cs.beginText(); cs.setFont(font, size)
            cs.newLineAtOffset((w - textW) / 2f, (h - size * 0.72f) / 2f)
            cs.showText(text); cs.endText()
        }
        return PDAppearanceDictionary().apply { setNormalAppearance(ap) }
    }

    /** Every editable mark in the file, page by page. */
    fun list(file: File): List<Info> = PdfTools.load(file).use { doc ->
        val out = ArrayList<Info>()
        doc.pages.forEachIndexed { p, page ->
            runCatching { page.annotations }.getOrDefault(emptyList()).forEachIndexed { i, a ->
                val kind = KINDS[a.subtype] ?: return@forEachIndexed
                out += Info(p, i, kind, a.contents.orEmpty().trim())
            }
        }
        out
    }

    /** Writes [out] without the marks in [remove] (page to annotation index). Returns the page count. */
    fun remove(src: File, remove: Set<Pair<Int, Int>>, out: File): Int = PdfTools.load(src).use { doc ->
        doc.pages.forEachIndexed { p, page: PDPage ->
            val list = page.annotations
            if (remove.none { it.first == p }) return@forEachIndexed
            page.annotations = list.filterIndexed { i, _ -> (p to i) !in remove }
        }
        doc.save(out)
        doc.numberOfPages
    }
}
