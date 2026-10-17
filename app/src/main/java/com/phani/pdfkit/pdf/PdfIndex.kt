package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/** A rectangle as fractions of the page (origin top-left), so it fits any rendered size. */
data class Frac(val x: Float, val y: Float, val w: Float, val h: Float)

/** A search hit: the page and the rectangles to highlight (one per line the hit spans). */
data class Match(val page: Int, val rects: List<Frac>)

/** A tappable link on a page: either a web/mail address or a jump to another page (0-based). */
data class LinkBox(val box: Frac, val uri: String?, val page: Int)

/** One entry of the PDF's own table of contents. [page] is 0-based. */
data class Bookmark(val title: String, val page: Int, val level: Int)

/** Links and bookmarks read from the file in one pass. */
data class PdfMeta(val links: Map<Int, List<LinkBox>>, val outline: List<Bookmark>)

/** Text of one page with a box for every character, used by search. */
class PageText(
    val index: Int,
    val text: String,
    private val left: FloatArray,
    private val right: FloatArray,
    private val top: FloatArray,
    private val bottom: FloatArray,
) {
    /** All case-insensitive hits of [query] on this page. */
    fun find(query: String): List<Match> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        var from = 0
        while (out.size < 200) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            out += Match(index, rectsFor(at, at + query.length))
            from = at + query.length
        }
        return out
    }

    private fun rectsFor(start: Int, end: Int): List<Frac> {
        val rects = ArrayList<Frac>()
        var l = Float.MAX_VALUE; var r = -1f; var t = Float.MAX_VALUE; var b = -1f
        fun flush() {
            if (r > l && b > t) rects += Frac(l, t, r - l, b - t)
            l = Float.MAX_VALUE; r = -1f; t = Float.MAX_VALUE; b = -1f
        }
        for (i in start until end) {
            if (text[i] == '\n') { flush(); continue }
            if (right[i] <= left[i] && text[i] == ' ') continue
            l = minOf(l, left[i]); r = maxOf(r, right[i]); t = minOf(t, top[i]); b = maxOf(b, bottom[i])
        }
        flush()
        return rects
    }
}

/** Reads text positions, links and bookmarks out of a PDF with PdfBox. Everything is local. */
object PdfIndex {
    private const val MAX_PAGES = 400

    /** Text of up to [MAX_PAGES] pages with character boxes. Scanned PDFs give empty pages. */
    fun build(file: File): List<PageText> = PdfTools.load(file).use { doc ->
        val pages = ArrayList<PageText>()
        val sb = StringBuilder()
        val l = ArrayList<Float>(); val r = ArrayList<Float>(); val t = ArrayList<Float>(); val b = ArrayList<Float>()
        var pw = 1f; var ph = 1f
        var lastRight = 0f; var lastTop = 0f; var lastBottom = 0f; var hasLast = false

        val stripper = object : PDFTextStripper() {
            override fun startPage(page: PDPage?) {
                sb.setLength(0); l.clear(); r.clear(); t.clear(); b.clear(); hasLast = false
                val box = page?.cropBox
                val rot = rotationOf(page)
                val swap = rot == 90 || rot == 270
                pw = ((if (swap) box?.height else box?.width) ?: 1f).coerceAtLeast(1f)
                ph = ((if (swap) box?.width else box?.height) ?: 1f).coerceAtLeast(1f)
            }

            override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                if (positions == null) return
                for (p in positions) {
                    val u = p.unicode ?: continue
                    val size = p.fontSizeInPt.coerceAtLeast(1f)
                    val base = p.yDirAdj
                    val lf = p.xDirAdj / pw
                    val rt = (p.xDirAdj + p.widthDirAdj) / pw
                    val tp = (base - size * 0.92f) / ph
                    val bt = (base + size * 0.28f) / ph
                    for (ch in u) {
                        sb.append(ch); l += lf; r += rt; t += tp; b += bt
                    }
                    lastRight = rt; lastTop = tp; lastBottom = bt; hasLast = true
                }
            }

            override fun writeWordSeparator() {
                if (!hasLast) return
                sb.append(' '); l += lastRight; r += lastRight; t += lastTop; b += lastBottom
            }

            override fun writeLineSeparator() {
                if (sb.isEmpty() || sb.last() == '\n') return
                sb.append('\n'); l += 0f; r += 0f; t += 0f; b += 0f
                hasLast = false
            }

            override fun endPage(page: PDPage?) {
                pages += PageText(currentPageNo - 1, sb.toString(), l.toFloatArray(), r.toFloatArray(), t.toFloatArray(), b.toFloatArray())
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = 1
        stripper.endPage = minOf(doc.numberOfPages, MAX_PAGES)
        stripper.getText(doc)
        pages
    }

    /** Plain text of one page ([pageIndex] is 0-based). */
    fun pageText(file: File, pageIndex: Int): String = PdfTools.load(file).use { doc ->
        textOf(doc, pageIndex + 1, pageIndex + 1)
    }

    /** Plain text of the whole document, with a blank line between pages. */
    fun allText(file: File): String = PdfTools.load(file).use { doc ->
        (1..minOf(doc.numberOfPages, MAX_PAGES)).joinToString("\n\n") { textOf(doc, it, it).trim() }.trim()
    }

    private fun textOf(doc: PDDocument, from: Int, to: Int): String =
        PDFTextStripper().apply { sortByPosition = true; startPage = from; endPage = to }.getText(doc).trim()

    /** Links per page and the bookmark tree. */
    fun meta(file: File): PdfMeta = PdfTools.load(file).use { doc ->
        val links = HashMap<Int, List<LinkBox>>()
        var total = 0
        for (i in 0 until minOf(doc.numberOfPages, MAX_PAGES)) {
            if (total > 2000) break
            val page = doc.getPage(i)
            val found = ArrayList<LinkBox>()
            val box = page.cropBox
            val rot = rotationOf(page)
            for (a in runCatching { page.annotations }.getOrDefault(emptyList())) {
                if (a !is PDAnnotationLink) continue
                val rc = a.rectangle ?: continue
                val bw = box.width.coerceAtLeast(1f); val bh = box.height.coerceAtLeast(1f)
                val x0 = (rc.lowerLeftX - box.lowerLeftX) / bw
                val x1 = (rc.upperRightX - box.lowerLeftX) / bw
                val y0 = 1f - (rc.upperRightY - box.lowerLeftY) / bh
                val y1 = 1f - (rc.lowerLeftY - box.lowerLeftY) / bh
                val rect = rotate(x0, y0, x1, y1, rot)
                var uri: String? = null
                var target = -1
                runCatching {
                    val action = a.action
                    if (action is PDActionURI) uri = action.uri
                    else if (action is PDActionGoTo) target = pageOf(doc, action.destination)
                    else target = pageOf(doc, a.destination)
                }
                if (uri == null && target < 0) continue
                found += LinkBox(rect, uri, target)
            }
            if (found.isNotEmpty()) { links[i] = found; total += found.size }
        }
        PdfMeta(links, outline(doc))
    }

    private fun rotationOf(page: PDPage?): Int = (((page?.rotation ?: 0) % 360) + 360) % 360

    /** Maps an upright rectangle (fractions, top-left origin) into the page as it is displayed. */
    private fun rotate(x0: Float, y0: Float, x1: Float, y1: Float, rot: Int): Frac {
        val ax = minOf(x0, x1).coerceIn(0f, 1f); val bx = maxOf(x0, x1).coerceIn(0f, 1f)
        val ay = minOf(y0, y1).coerceIn(0f, 1f); val by = maxOf(y0, y1).coerceIn(0f, 1f)
        return when (rot) {
            90 -> Frac(1f - by, ax, by - ay, bx - ax)
            180 -> Frac(1f - bx, 1f - by, bx - ax, by - ay)
            270 -> Frac(ay, 1f - bx, by - ay, bx - ax)
            else -> Frac(ax, ay, bx - ax, by - ay)
        }
    }

    private fun outline(doc: PDDocument): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        val root = runCatching { doc.documentCatalog.documentOutline }.getOrNull() ?: return out
        fun walk(first: PDOutlineItem?, level: Int) {
            var next = first
            while (next != null && out.size < 500 && level < 6) {
                val item: PDOutlineItem = next
                val title = item.title?.trim().orEmpty()
                val page = runCatching {
                    val action = item.action
                    pageOf(doc, item.destination ?: (action as? PDActionGoTo)?.destination)
                }.getOrDefault(-1)
                if (title.isNotEmpty() && page >= 0) out += Bookmark(title, page, level)
                walk(item.firstChild, level + 1)
                next = item.nextSibling
            }
        }
        walk(root.firstChild, 0)
        return out
    }

    private fun pageOf(doc: PDDocument, dest: PDDestination?): Int = try {
        when (dest) {
            is PDPageDestination -> dest.retrievePageNumber().let { if (it >= 0) it else dest.pageNumber }
            is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(dest)?.let { pageOf(doc, it) } ?: -1
            else -> -1
        }
    } catch (e: Exception) { -1 }
}
