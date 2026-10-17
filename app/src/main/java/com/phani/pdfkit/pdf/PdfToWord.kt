package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * PDF to Word as editable text, like iLovePDF: fonts, sizes, bold/italic, colors, headings, line
 * spacing, centered and indented text, bullets, simple tables, two-column pages as Word columns and
 * real pictures in reading order. Page backgrounds and scan images are left out; scanned pages
 * (and PDFs with a hidden OCR layer) come through as text.
 */
object PdfToWord {

    // ---------- model ----------
    private data class Style(val font: String, val size: Float, val bold: Boolean, val italic: Boolean, val color: Int)
    private class Word(val text: String, val x0: Float, val x1: Float, val base: Float, val style: Style)
    private class Line(val words: List<Word>) {
        val x0 = words.minOf { it.x0 }
        val x1 = words.maxOf { it.x1 }
        val base = words.maxOf { it.base }
        val size = words.groupingBy { (it.style.size * 2).toInt() }.eachCount().maxByOrNull { it.value }!!.key / 2f
        val text get() = words.joinToString(" ") { it.text }
        /** Pieces separated by wide gaps (table cells, tab stops). */
        fun segments(): List<List<Word>> {
            val out = ArrayList<List<Word>>(); var cur = ArrayList<Word>()
            words.forEachIndexed { i, w ->
                if (i > 0 && w.x0 - words[i - 1].x1 > maxOf(size * 1.8f, 14f)) { out += cur; cur = ArrayList() }
                cur += w
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }
    }
    private class Pic(val bytes: ByteArray, val wPx: Int, val hPx: Int, val top: Float, val wPt: Float, val hPt: Float, val x0: Float)

    private sealed interface Block { val top: Float }
    private class Para(val lines: List<Line>, override val top: Float, val spaceBefore: Float) : Block
    private class Table(val rows: List<List<String>>, val cols: Int, override val top: Float) : Block
    private class Image(val pic: Pic) : Block { override val top get() = pic.top }

    private class PageData(val w: Float, val h: Float, val lines: List<Line>, val pics: List<Pic>)

    /** Part of a page: full-width text, or two columns side by side. */
    private sealed interface Zone { val top: Float; val bottom: Float }
    private class Full(val lines: List<Line>) : Zone {
        override val top get() = lines.minOf { it.base - it.size }
        override val bottom get() = lines.maxOf { it.base }
    }
    private class Cols(val left: List<Line>, val right: List<Line>, val all: List<Line>) : Zone {
        override val top get() = all.minOf { it.base - it.size }
        override val bottom get() = all.maxOf { it.base }
    }

    // ---------- entry ----------

    /** Converts [src] to an editable .docx at [out]. Returns the page count. */
    suspend fun convert(src: File, out: File, report: (Float) -> Unit): Int {
        val pages = ArrayList<PageData>()
        var picBytes = 0L
        var view: PdfDoc? = null
        var viewTried = false
        fun viewer(): PdfDoc? {
            if (!viewTried) { viewTried = true; view = (PdfDoc.open(src) as? PdfDoc.Open.Ok)?.doc }
            return view
        }
        try {
            PdfTools.load(src).use { doc ->
                val n = doc.numberOfPages
                val colors = IdentityHashMap<TextPosition, Int>()
                for (i in 0 until n) {
                    coroutineContext.ensureActive()
                    val page = doc.getPage(i)
                    val rot = ((page.rotation % 360) + 360) % 360
                    val box = page.cropBox
                    val pw = if (rot == 90 || rot == 270) box.height else box.width
                    val ph = if (rot == 90 || rot == 270) box.width else box.height
                    colors.clear()
                    val (lines, hidden) = readLines(doc, i, colors)
                    val area = pw * ph
                    var pics = if (picBytes < 40L * 1024 * 1024) findPictures(page, ph) else emptyList()
                    var finalLines = lines
                    if (lines.isNotEmpty()) {
                        // Leave out page backgrounds, and the scan itself when the text is a hidden OCR layer.
                        val limit = if (hidden > 0.5f) 0.25f else 0.5f
                        pics = pics.filter { it.wPt * it.hPt < area * limit }
                    } else {
                        // Scanned page: read it and keep the words, not the photo of the page.
                        val bmp = viewer()?.render(i, 2000)
                        if (bmp != null) {
                            val ocrLines = runCatching { Ocr.lines(bmp) }.getOrDefault(emptyList())
                            if (ocrLines.isNotEmpty()) {
                                val k = pw / bmp.width
                                finalLines = ocrLines.map { l ->
                                    val size = ((l.bottom - l.top) * k * 0.74f).coerceIn(6f, 40f)
                                    Line(splitOcrWords(l.text, l.left * k, l.right * k, l.bottom * k - size * 0.22f, Style("Calibri", snap(size), false, false, 0)))
                                }.sortedWith(compareBy({ it.base }, { it.x0 }))
                                pics = pics.filter { it.wPt * it.hPt < area * 0.25f }
                            }
                        }
                    }
                    picBytes += pics.sumOf { it.bytes.size.toLong() }
                    pages += PageData(pw, ph, finalLines, pics)
                    report((i + 1f) / n * 0.85f)
                }
            }
        } finally { view?.close() }
        if (pages.all { it.lines.isEmpty() && it.pics.isEmpty() }) throw ToolError("No text found in this PDF.")

        // Body text size = the most common size, weighted by characters.
        val allWords = pages.flatMap { it.lines }.flatMap { it.words }
        val bodySize = allWords.groupBy { (it.style.size * 2).toInt() }.maxByOrNull { (_, ws) -> ws.sumOf { it.text.length } }?.key?.div(2f) ?: 11f
        val bodyFont = allWords.groupBy { it.style.font }.maxByOrNull { (_, ws) -> ws.sumOf { it.text.length } }?.key ?: "Calibri"

        val first = pages.first()
        val mLeft = (first.lines.minOfOrNull { it.x0 } ?: 72f).coerceIn(18f, 108f)
        val mRight = (first.w - (first.lines.maxOfOrNull { it.x1 } ?: (first.w - 72f))).coerceIn(18f, 108f)
        val mTop = (first.lines.minOfOrNull { it.base - it.size } ?: 72f).coerceIn(18f, 108f)
        val mBottom = 54f
        val tw = { v: Float -> (v * 20).toInt() }
        fun sectPara(cols: Int, space: Float) =
            "<w:p><w:pPr><w:sectPr><w:type w:val=\"continuous\"/><w:pgSz w:w=\"${tw(first.w)}\" w:h=\"${tw(first.h)}\"/>" +
                "<w:pgMar w:top=\"${tw(mTop)}\" w:right=\"${tw(mRight)}\" w:bottom=\"${tw(mBottom)}\" w:left=\"${tw(mLeft)}\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/>" +
                "<w:cols w:num=\"$cols\" w:space=\"${tw(space)}\"/></w:sectPr></w:pPr></w:p>"

        val body = StringBuilder()
        val media = ArrayList<ByteArray>()
        var sinceSection = false
        fun emit(lines: List<Line>, pics: List<Pic>, colLeft: Float, colRight: Float) {
            val blocks = layout(lines) + pics.map { Image(it) }
            blocks.sortedBy { it.top }.forEach { b ->
                when (b) {
                    is Para -> body.append(paraXml(b, colLeft, colRight, bodySize))
                    is Table -> body.append(tableXml(b, colRight - colLeft))
                    is Image -> {
                        media += b.pic.bytes
                        val maxW = (colRight - colLeft).coerceAtLeast(60f)
                        val s = minOf(1f, maxW / b.pic.wPt)
                        val picR = b.pic.x0 + b.pic.wPt
                        val jc = when {
                            b.pic.x0 - colLeft < 12f && picR < colRight - 24f -> null
                            colRight - picR < 12f && b.pic.x0 > colLeft + 24f -> "right"
                            else -> "center"
                        }
                        body.append(drawingXml(media.size, (b.pic.wPt * s * 12700).toLong(), (b.pic.hPt * s * 12700).toLong(), jc))
                    }
                }
                sinceSection = true
            }
        }
        pages.forEachIndexed { pi, pg ->
            if (pi > 0) { body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"); sinceSection = true }
            val zones = zones(pg.lines, pg.w)
            val pageLeft = pg.lines.minOfOrNull { it.x0 } ?: mLeft
            val pageRight = pg.lines.maxOfOrNull { it.x1 } ?: (pg.w - mRight)
            // Each picture goes to the zone it sits in (by its top), and to the column it sits over.
            val zonePics = zones.map { ArrayList<Pic>() }
            pg.pics.forEach { p ->
                val idx = zones.indexOfFirst { p.top < it.bottom }.let { if (it < 0) zones.lastIndex else it }
                if (idx >= 0) zonePics[idx] += p
            }
            if (zones.isEmpty()) emit(emptyList(), pg.pics, pageLeft, pageRight)
            zones.forEachIndexed { zi, z ->
                when (z) {
                    is Full -> emit(z.lines, zonePics[zi], pageLeft, pageRight)
                    is Cols -> {
                        if (sinceSection) body.append(sectPara(1, 36f))
                        val lL = z.left.minOf { it.x0 }; val lR = z.left.maxOf { it.x1 }
                        val rL = z.right.minOf { it.x0 }; val rR = z.right.maxOf { it.x1 }
                        val g = (lR + rL) / 2f
                        val (lp, rp) = zonePics[zi].partition { it.x0 + it.wPt / 2f < g }
                        emit(z.left, lp, lL, lR)
                        body.append("<w:p><w:r><w:br w:type=\"column\"/></w:r></w:p>")
                        emit(z.right, rp, rL, rR)
                        body.append(sectPara(2, (rL - lR).coerceIn(12f, 72f)))
                        sinceSection = false
                    }
                }
            }
        }
        writeDocx(out, body.toString(), media, first.w, first.h, mTop, mBottom, mLeft, mRight, bodyFont, bodySize)
        report(1f)
        return pages.size
    }

    /** Body sizes like 10.9 or 11.2 become 11, so Word shows tidy sizes. */
    private fun snap(size: Float): Float = (Math.round(size * 2f) / 2f)

    /** OCR gives a line of text and its box; spread the words across the box so columns can be found. */
    private fun splitOcrWords(text: String, x0: Float, x1: Float, base: Float, st: Style): List<Word> {
        val parts = text.split(' ').filter { it.isNotEmpty() }
        if (parts.size <= 1) return listOf(Word(text, x0, x1, base, st))
        val total = parts.sumOf { it.length } + parts.size - 1
        val per = (x1 - x0) / total
        var x = x0
        return parts.map { w -> val wx1 = x + w.length * per; Word(w, x, wx1, base, st).also { x = wx1 + per } }
    }

    // ---------- columns ----------

    /** Splits a page into full-width parts and two-column parts. */
    private fun zones(lines: List<Line>, pw: Float): List<Zone> {
        if (lines.isEmpty()) return emptyList()
        val g = gutter(lines, pw) ?: return listOf(Full(lines))
        val raw = ArrayList<Zone>()
        var full = ArrayList<Line>(); var l = ArrayList<Line>(); var r = ArrayList<Line>(); var all = ArrayList<Line>()
        fun flushCols() { if (all.isNotEmpty()) { raw += Cols(l, r, all); l = ArrayList(); r = ArrayList(); all = ArrayList() } }
        fun flushFull() { if (full.isNotEmpty()) { raw += Full(full); full = ArrayList() } }
        lines.forEach { line ->
            if (line.words.any { it.x0 < g - 1f && it.x1 > g + 1f }) { flushCols(); full += line }
            else {
                flushFull()
                val (a, b) = line.words.partition { (it.x0 + it.x1) / 2f < g }
                if (a.isNotEmpty()) l += Line(a)
                if (b.isNotEmpty()) r += Line(b)
                all += line
            }
        }
        flushCols(); flushFull()
        // Short two-sided bits (a date on the right of a name, say) stay as normal lines.
        val out = ArrayList<Zone>()
        raw.forEach { z ->
            val zz = if (z is Cols && (z.left.size < 3 || z.right.size < 3)) Full(z.all) else z
            val last = out.lastOrNull()
            if (zz is Full && last is Full) out[out.lastIndex] = Full(last.lines + zz.lines) else out += zz
        }
        return out
    }

    /** The x of an empty vertical strip between two columns of flowing text, or null for one-column pages. */
    private fun gutter(lines: List<Line>, pw: Float): Float? {
        if (lines.size < 8) return null
        val n = pw.toInt() + 2
        val cov = IntArray(n)
        val mark = BooleanArray(n)
        lines.forEach { line ->
            java.util.Arrays.fill(mark, false)
            line.words.forEach { w -> for (x in w.x0.toInt().coerceIn(0, n - 1)..w.x1.toInt().coerceIn(0, n - 1)) mark[x] = true }
            for (x in 0 until n) if (mark[x]) cov[x]++
        }
        val allowed = maxOf(1, (lines.size * 0.08f).toInt())
        var best = -1; var bestLen = 0; var runStart = -1
        val from = (pw * 0.3f).toInt(); val to = (pw * 0.7f).toInt()
        for (x in from..to + 1) {
            val free = x <= to && cov[x] <= allowed
            if (free && runStart < 0) runStart = x
            if (!free && runStart >= 0) { val len = x - runStart; if (len > bestLen) { bestLen = len; best = runStart + len / 2 }; runStart = -1 }
        }
        if (bestLen < 8) return null
        val g = best.toFloat()
        val both = lines.filter { l -> l.words.any { it.x1 < g } && l.words.any { it.x0 > g } }
        if (both.size < maxOf(6, (lines.size * 0.3f).toInt())) return null
        // Both sides must be flowing text that fills its column, not labels and values.
        val leftMin = lines.minOf { it.x0 }; val rightMax = lines.maxOf { it.x1 }
        val lw = both.map { l -> l.words.filter { it.x1 < g }.let { ws -> ws.maxOf { it.x1 } - ws.minOf { it.x0 } } }.sorted()
        val rw = both.map { l -> l.words.filter { it.x0 > g }.let { ws -> ws.maxOf { it.x1 } - ws.minOf { it.x0 } } }.sorted()
        if (lw[lw.size / 2] < (g - leftMin) * 0.6f || rw[rw.size / 2] < (rightMax - g) * 0.6f) return null
        return g
    }

    // ---------- reading ----------

    /** Lines of words on a page, and the share of text that is invisible (an OCR layer over a scan). */
    private fun readLines(doc: com.tom_roush.pdfbox.pdmodel.PDDocument, index: Int, colors: IdentityHashMap<TextPosition, Int>): Pair<List<Line>, Float> {
        val lines = ArrayList<Line>()
        var cur = ArrayList<Word>()
        var total = 0
        var hidden = 0
        val stripper = object : PDFTextStripper() {
            init {
                // The text stripper skips color operators; add them so each word knows its color.
                addOperator(SetNonStrokingColorSpace()); addOperator(SetNonStrokingColor()); addOperator(SetNonStrokingColorN())
                addOperator(SetNonStrokingDeviceRGBColor()); addOperator(SetNonStrokingDeviceGrayColor()); addOperator(SetNonStrokingDeviceCMYKColor())
            }

            override fun processTextPosition(text: TextPosition?) {
                if (text != null) {
                    colors[text] = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrDefault(0)
                    total++
                    val mode = runCatching { graphicsState.textState.renderingMode }.getOrNull()
                    if (mode != null && !mode.isFill && !mode.isStroke) hidden++
                }
                super.processTextPosition(text)
            }

            override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                val ps = positions?.filter { !it.unicode.isNullOrEmpty() } ?: return
                if (ps.isEmpty()) return
                val t = (text ?: ps.joinToString("") { it.unicode }).trim()
                if (t.isEmpty()) return
                val p = ps.firstOrNull { !it.unicode.isNullOrBlank() } ?: ps.first()
                cur += Word(
                    t, ps.minOf { it.xDirAdj }, ps.maxOf { it.xDirAdj + it.widthDirAdj }, ps.maxOf { it.yDirAdj },
                    styleOf(p, colors[p] ?: 0),
                )
            }

            override fun writeLineSeparator() {
                if (cur.isNotEmpty()) { lines += Line(cur); cur = ArrayList() }
            }

            override fun writeWordSeparator() {}
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        runCatching { stripper.getText(doc) }
        if (cur.isNotEmpty()) lines += Line(cur)
        return lines.sortedWith(compareBy({ it.base }, { it.x0 })) to (if (total == 0) 0f else hidden.toFloat() / total)
    }

    private fun styleOf(p: TextPosition, color: Int): Style {
        val f: PDFont? = p.font
        val raw = (f?.name ?: "").substringAfter('+')
        val lower = raw.lowercase()
        val weight = runCatching { f?.fontDescriptor?.fontWeight ?: 0f }.getOrDefault(0f)
        val bold = "bold" in lower || "black" in lower || "heavy" in lower || "semibold" in lower || weight >= 600f ||
            runCatching { f?.fontDescriptor?.isForceBold == true }.getOrDefault(false)
        val italic = "italic" in lower || "oblique" in lower || runCatching { f?.fontDescriptor?.isItalic == true }.getOrDefault(false)
        var rgb = color and 0xFFFFFF
        // Light text was drawn on a dark box or picture that Word won't have; make it readable.
        val lum = (0.299f * (rgb shr 16 and 255) + 0.587f * (rgb shr 8 and 255) + 0.114f * (rgb and 255)) / 255f
        if (lum > 0.85f) rgb = 0
        return Style(familyName(raw), snap((p.fontSizeInPt.takeIf { it > 0f } ?: p.heightDir).coerceIn(4f, 96f)), bold, italic, rgb)
    }

    /** "ABCDEF+TimesNewRomanPS-BoldMT" -> "Times New Roman". */
    private fun familyName(raw: String): String {
        val base = raw.substringBefore(',').substringBefore('-').removeSuffix("MT").removeSuffix("PS").removeSuffix("PSMT")
        val l = base.lowercase()
        return when {
            l.isEmpty() -> "Calibri"
            "timesnewroman" in l || l.startsWith("times") -> "Times New Roman"
            "arial" in l || "helvetica" in l -> "Arial"
            "courier" in l -> "Courier New"
            "calibri" in l -> "Calibri"
            "cambria" in l -> "Cambria"
            "georgia" in l -> "Georgia"
            "verdana" in l -> "Verdana"
            "tahoma" in l -> "Tahoma"
            "garamond" in l -> "Garamond"
            "segoe" in l -> "Segoe UI"
            "roboto" in l -> "Roboto"
            "symbol" in l || "wingding" in l || "zapf" in l -> "Symbol"
            else -> base.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        }
    }

    /** Pictures drawn on the page, with where they sit. */
    private fun findPictures(page: PDPage, pageH: Float): List<Pic> {
        val found = ArrayList<Pic>()
        val box = page.cropBox
        val engine = object : PDFStreamEngine() {
            init {
                addOperator(Concatenate()); addOperator(SetGraphicsStateParameters())
                addOperator(Save()); addOperator(Restore()); addOperator(SetMatrix())
            }

            override fun processOperator(operator: Operator?, operands: MutableList<COSBase>?) {
                if (operator?.name == "Do" && !operands.isNullOrEmpty() && found.size < 8) {
                    val name = operands[0] as? COSName
                    val x = name?.let { runCatching { resources?.getXObject(it) }.getOrNull() }
                    if (x is PDImageXObject) {
                        val m = graphicsState.currentTransformationMatrix
                        val wPt = abs(m.scalingFactorX); val hPt = abs(m.scalingFactorY)
                        if (wPt >= 40f && hPt >= 30f) runCatching {
                            val top = pageH - (m.translateY - box.lowerLeftY + hPt)
                            val x0 = m.translateX - box.lowerLeftX
                            jpeg(x)?.let { (bytes, w, h) -> found += Pic(bytes, w, h, top, wPt, hPt, x0) }
                        }
                        return
                    }
                    if (x is PDFormXObject) { runCatching { showForm(x) }; return }
                }
                super.processOperator(operator, operands)
            }
        }
        runCatching { engine.processPage(page) }
        return found
    }

    private fun jpeg(x: PDImageXObject): Triple<ByteArray, Int, Int>? {
        var bmp = x.image ?: return null
        val big = maxOf(bmp.width, bmp.height)
        if (big > 1600) {
            val f = 1600f / big
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val flat = flatten(bmp)
        val bos = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        val res = Triple(bos.toByteArray(), flat.width, flat.height)
        if (flat !== bmp) flat.recycle()
        bmp.recycle()
        return res
    }

    private fun flatten(b: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        return out
    }

    // ---------- layout ----------

    private val BULLET = Regex("^([•●▪◦■□➢►✓\\-–*]|\\(?[0-9]{1,3}[.)]|\\(?[a-zA-Z][.)])\\s")

    private fun layout(lines: List<Line>): List<Block> {
        val blocks = ArrayList<Block>()
        var i = 0
        var prevBottom = lines.firstOrNull()?.let { it.base - it.size } ?: 0f
        while (i < lines.size) {
            // Table: 2+ lines in a row that split into the same number (2+) of aligned pieces.
            val segs = lines[i].segments()
            if (segs.size >= 2) {
                var j = i + 1
                val starts = segs.map { s -> s.first().x0 }
                while (j < lines.size) {
                    val s2 = lines[j].segments()
                    val close = lines[j].base - lines[j - 1].base <= lines[j - 1].size * 2.6f
                    if (!close || s2.size != segs.size) break
                    if (s2.map { it.first().x0 }.zip(starts).count { (a, b) -> abs(a - b) <= 16f } < segs.size - 1) break
                    j++
                }
                if (j - i >= 2) {
                    val rows = (i until j).map { k -> lines[k].segments().map { s -> s.joinToString(" ") { it.text } } }
                    blocks += Table(rows, segs.size, lines[i].base - lines[i].size)
                    prevBottom = lines[j - 1].base
                    i = j
                    continue
                }
            }
            // Paragraph: keep adding lines while they look like the same flowing text.
            val para = arrayListOf(lines[i])
            var k = i + 1
            while (k < lines.size) {
                val prev = para.last(); val next = lines[k]
                val gap = next.base - prev.base
                val sameSize = abs(next.size - prev.size) <= 0.8f
                val nearLeft = abs(next.x0 - para.first().x0) <= 8f || (para.size == 1 && next.x0 < para.first().x0 && para.first().x0 - next.x0 < 40f)
                val prevFull = prev.x1 >= para.maxOf { it.x1 } - prev.size * 3f
                val bullet = BULLET.containsMatchIn(next.text)
                if (!sameSize || gap > prev.size * 1.75f || gap <= 0f || !nearLeft || !prevFull || bullet || next.segments().size > 1) break
                para += next; k++
            }
            val top = para.first().base - para.first().size
            blocks += Para(para, top, (top - prevBottom).coerceAtLeast(0f))
            prevBottom = para.last().base
            i = k
        }
        return blocks
    }

    // ---------- XML ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it.code >= 0x20 }

    private fun paraXml(p: Para, colLeft: Float, colRight: Float, body: Float): String {
        val first = p.lines.first()
        val width = colRight - colLeft
        val size = p.lines.maxOf { it.size }
        val text = p.lines.joinToString(" ") { it.text }
        val centered = p.lines.all { l -> abs((l.x0 + l.x1) / 2f - (colLeft + colRight) / 2f) < 8f && l.x0 - colLeft > 14f }
        val right = !centered && p.lines.all { l -> abs(l.x1 - colRight) < 6f && l.x0 - colLeft > width * 0.3f }
        val justified = !centered && !right && p.lines.size > 2 && p.lines.dropLast(1).all { abs(it.x1 - colRight) < 4f }
        val heading = when {
            size >= body * 1.45f && text.length < 160 -> 1
            size >= body * 1.18f && text.length < 160 -> 2
            else -> 0
        }
        val pPr = StringBuilder("<w:pPr>")
        if (heading > 0) pPr.append("<w:pStyle w:val=\"Heading$heading\"/>")
        val before = (p.spaceBefore - size * 0.6f).coerceIn(0f, 36f)
        // Line spacing from the gap between lines; Word's "single" is about 1.17 x the font size.
        val lineAttr = if (p.lines.size >= 2) {
            val gap = (p.lines.last().base - first.base) / (p.lines.size - 1)
            val mult = (gap / (size * 1.17f)).coerceIn(1f, 2.5f)
            if (mult > 1.08f) " w:line=\"${(mult * 240).toInt()}\" w:lineRule=\"auto\"" else ""
        } else ""
        pPr.append("<w:spacing w:before=\"${(before * 20).toInt()}\" w:after=\"0\"$lineAttr/>")
        if (!centered && !right) {
            val left = ((p.lines.drop(1).minOfOrNull { it.x0 } ?: first.x0) - colLeft).coerceAtLeast(0f)
            val firstLine = first.x0 - colLeft - left
            val bullet = BULLET.containsMatchIn(first.text)
            if (bullet && p.lines.size > 1) {
                val textStart = p.lines[1].x0 - colLeft
                pPr.append("<w:ind w:left=\"${(textStart * 20).toInt()}\" w:hanging=\"${((textStart - (first.x0 - colLeft)) * 20).toInt().coerceAtLeast(0)}\"/>")
            } else if (left > 4f || abs(firstLine) > 4f) {
                pPr.append("<w:ind w:left=\"${(left * 20).toInt()}\"" + (if (firstLine > 4f) " w:firstLine=\"${(firstLine * 20).toInt()}\"" else if (firstLine < -4f) " w:hanging=\"${(-firstLine * 20).toInt()}\"" else "") + "/>")
            }
        }
        when {
            centered -> pPr.append("<w:jc w:val=\"center\"/>")
            right -> pPr.append("<w:jc w:val=\"right\"/>")
            justified -> pPr.append("<w:jc w:val=\"both\"/>")
        }
        pPr.append("</w:pPr>")
        val sb = StringBuilder("<w:p>").append(pPr)
        // Runs: neighbouring words with the same style share a run.
        var curStyle: Style? = null
        val curText = StringBuilder()
        fun flush() {
            val st = curStyle ?: return
            sb.append(runXml(curText.toString(), st))
            curText.clear()
        }
        p.lines.forEachIndexed { li, line ->
            line.words.forEachIndexed { wi, w ->
                val t = w.text
                val sep = if (li == 0 && wi == 0) "" else " "
                if (curStyle != null && curStyle != w.style) { flush() }
                // Join a word split by a hyphen at the end of a line.
                if (wi == 0 && li > 0 && curText.endsWith("-") && t.firstOrNull()?.isLowerCase() == true) {
                    curText.setLength(curText.length - 1)
                    curText.append(t)
                } else curText.append(sep).append(t)
                curStyle = w.style
            }
        }
        flush()
        return sb.append("</w:p>").toString()
    }

    private fun runXml(text: String, st: Style): String {
        val rPr = StringBuilder("<w:rPr>")
        rPr.append("<w:rFonts w:ascii=\"${esc(st.font)}\" w:hAnsi=\"${esc(st.font)}\" w:cs=\"${esc(st.font)}\"/>")
        if (st.bold) rPr.append("<w:b/>")
        if (st.italic) rPr.append("<w:i/>")
        if (st.color != 0) rPr.append("<w:color w:val=\"${String.format("%06X", st.color)}\"/>")
        val half = (st.size * 2).toInt().coerceIn(8, 192)
        rPr.append("<w:sz w:val=\"$half\"/><w:szCs w:val=\"$half\"/>")
        rPr.append("</w:rPr>")
        return "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r>"
    }

    private fun tableXml(t: Table, width: Float): String {
        val totalTw = (width * 20).toInt().coerceAtLeast(2000)
        val colTw = totalTw / t.cols
        val sb = StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"$totalTw\" w:type=\"dxa\"/>")
        sb.append("<w:tblBorders>")
        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { sb.append("<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFC3CC\"/>") }
        sb.append("</w:tblBorders></w:tblPr><w:tblGrid>")
        repeat(t.cols) { sb.append("<w:gridCol w:w=\"$colTw\"/>") }
        sb.append("</w:tblGrid>")
        t.rows.forEach { row ->
            sb.append("<w:tr>")
            for (ci in 0 until t.cols) {
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$colTw\" w:type=\"dxa\"/></w:tcPr><w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(esc(row.getOrElse(ci) { "" })).append("</w:t></w:r></w:p></w:tc>")
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl><w:p/>")
        return sb.toString()
    }

    private fun drawingXml(n: Int, wEmu: Long, hEmu: Long, jc: String?): String =
        "<w:p>" + (if (jc != null) "<w:pPr><w:jc w:val=\"$jc\"/></w:pPr>" else "") +
            "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$wEmu\" cy=\"$hEmu\"/><wp:docPr id=\"$n\" name=\"Picture $n\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"$n\" name=\"image$n.jpg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdImg$n\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$wEmu\" cy=\"$hEmu\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"

    private fun writeDocx(
        out: File, body: String, media: List<ByteArray>, pageW: Float, pageH: Float,
        top: Float, bottom: Float, left: Float, right: Float, font: String, size: Float,
    ) {
        val f = esc(font)
        val half = (size * 2).toInt().coerceIn(12, 48)
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(name: String, data: String) { z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            )
            val rels = StringBuilder("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            media.indices.forEach { rels.append("""<Relationship Id="rIdImg${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${it + 1}.jpg"/>""") }
            put("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
            media.forEachIndexed { i, bytes -> z.putNextEntry(ZipEntry("word/media/image${i + 1}.jpg")); z.write(bytes); z.closeEntry() }
            put(
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
                    """<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="$f" w:hAnsi="$f" w:cs="$f"/><w:sz w:val="$half"/><w:szCs w:val="$half"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
                    """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
                    """<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/></w:rPr></w:style>""" +
                    """<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/></w:rPr></w:style>""" +
                    """</w:styles>""",
            )
            val tw = { v: Float -> (v * 20).toInt() }
            put(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><w:body>$body<w:sectPr><w:pgSz w:w="${tw(pageW)}" w:h="${tw(pageH)}"/><w:pgMar w:top="${tw(top)}" w:right="${tw(right)}" w:bottom="${tw(bottom)}" w:left="${tw(left)}" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            )
        }
    }
}
