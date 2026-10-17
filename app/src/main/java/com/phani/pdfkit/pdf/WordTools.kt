package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Basic, fully offline Word conversions. Text, headings, bold, simple tables and pictures survive
 * Word to PDF; PDF to Word carries text and pictures (pictures sit after each page's text).
 * Columns and exact layout do not carry over. The UI says so.
 */
object WordTools {

    // ---------------- PDF -> DOCX ----------------
    suspend fun pdfToDocx(src: File, out: File, report: (Float) -> Unit): Int {
        val pages = ArrayList<List<String>>()
        val pics = ArrayList<List<Pic>>()
        var bytesTotal = 0L
        PdfTools.load(src).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (i in 1..doc.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = i; stripper.endPage = i
                pages += paragraphs(stripper.getText(doc))
                val found = if (bytesTotal < 40L * 1024 * 1024) pageImages(doc.getPage(i - 1)) else emptyList()
                bytesTotal += found.sumOf { it.bytes.size.toLong() }
                pics += found
                report(i.toFloat() / doc.numberOfPages * 0.9f)
            }
        }
        if (pages.all { it.isEmpty() } && pics.all { it.isEmpty() }) throw ToolError("No text found. This looks like a scanned PDF; try Make Searchable or Text Recognition first.")
        val body = StringBuilder()
        val media = ArrayList<ByteArray>()
        pages.forEachIndexed { i, paras ->
            paras.forEach { p -> body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(p)).append("</w:t></w:r></w:p>") }
            pics[i].forEach { pic ->
                media += pic.bytes
                body.append(drawingXml(media.size, pic))
            }
            if (i < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        writeDocx(out, body.toString(), media)
        report(1f)
        return pages.size
    }

    private class Pic(val bytes: ByteArray, val w: Int, val h: Int)

    /** Pictures placed directly on a page (logos, photos), as JPEG bytes. Tiny decorations are skipped. */
    private fun pageImages(page: PDPage): List<Pic> {
        val res = page.resources ?: return emptyList()
        val out = ArrayList<Pic>()
        for (name in runCatching { res.xObjectNames.toList() }.getOrDefault(emptyList())) {
            if (out.size >= 6) break
            runCatching {
                val x = res.getXObject(name)
                if (x is PDImageXObject && x.width >= 120 && x.height >= 80) {
                    var bmp = x.image
                    val big = maxOf(bmp.width, bmp.height)
                    if (big > 1600) {
                        val f = 1600f / big
                        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
                        if (scaled !== bmp) bmp.recycle()
                        bmp = scaled
                    }
                    val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
                    val bos = ByteArrayOutputStream()
                    flat.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                    out += Pic(bos.toByteArray(), flat.width, flat.height)
                    bmp.recycle(); flat.recycle()
                }
            }
        }
        return out
    }

    private fun drawingXml(n: Int, pic: Pic): String {
        val wEmu = minOf(pic.w * 9525L, 5486400L)
        val hEmu = wEmu * pic.h / pic.w
        return "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$wEmu\" cy=\"$hEmu\"/><wp:docPr id=\"$n\" name=\"Picture $n\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"$n\" name=\"image$n.jpg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdImg$n\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$wEmu\" cy=\"$hEmu\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    /** Joins wrapped lines into paragraphs; a blank line or a short line ends a paragraph. */
    private fun paragraphs(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        text.lines().map { it.trimEnd() }.forEach { line ->
            if (line.isBlank()) { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }; return@forEach }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(line.trim())
            if (line.length < 55 || line.endsWith(":")) { out += cur.toString(); cur.clear() }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it.code >= 0x20 }

    private fun writeDocx(out: File, body: String, media: List<ByteArray> = emptyList()) {
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(name: String, data: String) { z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            )
            if (media.isNotEmpty()) {
                val rels = media.indices.joinToString("") {
                    """<Relationship Id="rIdImg${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${it + 1}.jpg"/>"""
                }
                put("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
                media.forEachIndexed { i, bytes -> z.putNextEntry(ZipEntry("word/media/image${i + 1}.jpg")); z.write(bytes); z.closeEntry() }
            }
            put(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            )
        }
    }

    // ---------------- DOCX -> PDF ----------------
    private data class Run(val text: String, val bold: Boolean)
    private sealed interface Block
    private data class ParaB(val runs: List<Run>, val heading: Int, val pageBreakBefore: Boolean) : Block
    private data class TableB(val rows: List<List<String>>) : Block
    private class ImgB(val bytes: ByteArray, val wPt: Float, val hPt: Float) : Block

    private fun readDocx(src: File): List<Block> {
        val zip = try { ZipFile(src) } catch (e: Exception) { throw ToolError("This isn't a .docx file. Old .doc files aren't supported.") }
        return zip.use { z ->
            val entry = z.getEntry("word/document.xml") ?: throw ToolError("This Word file looks damaged.")
            // Picture ids -> files inside the .docx
            val images = HashMap<String, String>()
            z.getEntry("word/_rels/document.xml.rels")?.let { re ->
                val rp = Xml.newPullParser().apply { setInput(z.getInputStream(re), "UTF-8") }
                while (rp.next() != XmlPullParser.END_DOCUMENT) {
                    if (rp.eventType == XmlPullParser.START_TAG && rp.name == "Relationship" && rp.getAttributeValue(null, "Type").orEmpty().endsWith("/image")) {
                        val id = rp.getAttributeValue(null, "Id") ?: continue
                        val target = rp.getAttributeValue(null, "Target") ?: continue
                        images[id] = if (target.startsWith("/")) target.trimStart('/') else "word/$target"
                    }
                }
            }
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(z.getInputStream(entry), "UTF-8")
            }
            val blocks = ArrayList<Block>()
            var runs = ArrayList<Run>()
            var heading = 0; var bold = false; var inText = false; var breakNext = false; var pendingBreak = false
            val text = StringBuilder()
            var tblDepth = 0
            var rows = ArrayList<List<String>>(); var cells = ArrayList<String>(); val cellText = StringBuilder()
            var extentW = 0f; var extentH = 0f; var blipId: String? = null
            val pending = ArrayList<ImgB>()
            var imageBytes = 0L
            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "w:tbl" -> { tblDepth++; if (tblDepth == 1) rows = ArrayList() }
                        "w:tr" -> if (tblDepth == 1) cells = ArrayList()
                        "w:tc" -> if (tblDepth == 1) cellText.clear()
                        "w:p" -> { runs = ArrayList(); heading = 0; pendingBreak = breakNext; breakNext = false }
                        "w:pStyle" -> {
                            val v = parser.getAttributeValue(null, "w:val").orEmpty().lowercase()
                            heading = when {
                                v.startsWith("title") -> 1
                                v.startsWith("heading1") || v == "heading 1" -> 1
                                v.startsWith("heading") -> 2
                                else -> 0
                            }
                        }
                        "w:r" -> bold = false
                        "w:b" -> bold = parser.getAttributeValue(null, "w:val").let { it == null || it == "1" || it == "true" }
                        "w:t" -> { inText = true; text.clear() }
                        "w:tab" -> runs += Run("    ", bold)
                        "w:br" -> if (parser.getAttributeValue(null, "w:type") == "page") breakNext = true else runs += Run("\n", bold)
                        "w:drawing" -> { extentW = 0f; extentH = 0f; blipId = null }
                        "wp:extent" -> {
                            extentW = (parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L) / 12700f
                            extentH = (parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L) / 12700f
                        }
                        "a:blip" -> blipId = parser.getAttributeValue(null, "r:embed")
                    }
                    XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "w:t" -> { inText = false; runs += Run(text.toString(), bold) }
                        "w:drawing" -> if (tblDepth == 0 && imageBytes < 30L * 1024 * 1024) {
                            val path = blipId?.let { images[it] }
                            val bytes = path?.let { z.getEntry(it) }?.let { e -> runCatching { z.getInputStream(e).use { it.readBytes() } }.getOrNull() }
                            if (bytes != null) { imageBytes += bytes.size; pending += ImgB(bytes, extentW, extentH) }
                        }
                        "w:p" -> {
                            if (tblDepth > 0) {
                                val t = runs.joinToString("") { it.text }.trim()
                                if (t.isNotEmpty()) { if (cellText.isNotEmpty()) cellText.append('\n'); cellText.append(t) }
                            } else {
                                blocks += ParaB(runs, heading, pendingBreak)
                                blocks.addAll(pending); pending.clear()
                            }
                        }
                        "w:tc" -> if (tblDepth == 1) cells += cellText.toString().trim()
                        "w:tr" -> if (tblDepth == 1) rows += cells
                        "w:tbl" -> { if (tblDepth == 1 && rows.isNotEmpty()) blocks += TableB(rows); tblDepth-- }
                    }
                }
            }
            blocks
        }
    }

    suspend fun docxToPdf(src: File, out: File, report: (Float) -> Unit): Int {
        val blocks = readDocx(src)
        if (blocks.none { it !is ParaB || it.runs.isNotEmpty() }) throw ToolError("This Word file has no text.")
        report(0.2f)
        return PDDocument().use { doc ->
            val regular: PDFont = loadSystemFont(doc, "Roboto-Regular.ttf") ?: PDType1Font.HELVETICA
            val boldFont: PDFont = loadSystemFont(doc, "Roboto-Bold.ttf") ?: (if (regular is PDType1Font) PDType1Font.HELVETICA_BOLD else regular)
            val pageW = PDRectangle.A4.width; val pageH = PDRectangle.A4.height
            val margin = 56f; val maxW = pageW - 2 * margin
            var page = PDPage(PDRectangle.A4); doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = pageH - margin

            fun newPage() { cs.close(); page = PDPage(PDRectangle.A4); doc.addPage(page); cs = PDPageContentStream(doc, page); y = pageH - margin }
            fun safe(font: PDFont, s: String): String = s.map { ch ->
                try { font.encode(ch.toString()); ch } catch (e: Exception) { if (ch.isWhitespace()) ' ' else '?' }
            }.joinToString("")
            fun width(font: PDFont, s: String, size: Float) = try { font.getStringWidth(s) / 1000f * size } catch (e: Exception) { s.length * size * 0.5f }

            /** Greedy word wrap of a cell's text. */
            fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
                val lines = ArrayList<String>()
                text.split('\n').forEach { para ->
                    var cur = ""
                    para.split(' ').filter { it.isNotEmpty() }.forEach { w ->
                        val tryLine = if (cur.isEmpty()) w else "$cur $w"
                        if (width(font, safe(font, tryLine), size) > maxWidth && cur.isNotEmpty()) { lines += cur; cur = w } else cur = tryLine
                    }
                    lines += cur
                }
                return lines
            }

            blocks.forEachIndexed { pi, block ->
                coroutineContext.ensureActive()
                when (block) {
                    is TableB -> {
                        val cols = block.rows.maxOfOrNull { it.size } ?: 0
                        if (cols == 0) return@forEachIndexed
                        val colW = maxW / cols
                        val fs = 10f; val lead = 13f
                        y -= 4f
                        block.rows.forEach { row ->
                            val wrapped = (0 until cols).map { ci -> wrap(row.getOrElse(ci) { "" }, regular, fs, colW - 8f) }
                            val rowH = wrapped.maxOf { it.size }.coerceAtLeast(1) * lead + 8f
                            if (y - rowH < margin) newPage()
                            val top = y
                            for (ci in 0 until cols) {
                                val x0 = margin + ci * colW
                                cs.setStrokingColor(0.66f, 0.68f, 0.72f); cs.setLineWidth(0.6f)
                                cs.addRect(x0, top - rowH, colW, rowH); cs.stroke()
                                wrapped[ci].forEachIndexed { li, ln ->
                                    cs.beginText(); cs.setFont(regular, fs); cs.setNonStrokingColor(0f, 0f, 0f)
                                    cs.newLineAtOffset(x0 + 4f, top - 4f - (li + 1) * lead + 3f); cs.showText(safe(regular, ln)); cs.endText()
                                }
                            }
                            y -= rowH
                        }
                        y -= 10f
                    }
                    is ImgB -> {
                        val bmp = decodeImage(block.bytes) ?: return@forEachIndexed
                        var w = if (block.wPt > 0f) block.wPt else bmp.width * 0.75f
                        var h = if (block.hPt > 0f) block.hPt else bmp.height * 0.75f
                        val sc = minOf(1f, maxW / w, (pageH - 2 * margin) / h)
                        w *= sc; h *= sc
                        if (y - h < margin) newPage()
                        val img = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                        cs.drawImage(img, margin, y - h, w, h)
                        y -= h + 10f
                        bmp.recycle()
                    }
                    is ParaB -> {
                        val p = block
                        if (p.pageBreakBefore && y < pageH - margin) newPage()
                        val size = when (p.heading) { 1 -> 18f; 2 -> 14f; else -> 11f }
                        val lead = size * 1.45f
                        val gapAfter = if (p.heading > 0) 8f else 6f
                        // Split runs into words while keeping bold, then wrap.
                        data class Word(val t: String, val bold: Boolean)
                        val words = ArrayList<Word>()
                        p.runs.forEach { r ->
                            r.text.split(Regex("(?<=\\s)|(?=\\n)")).forEach { piece -> if (piece.isNotEmpty()) words += Word(piece, r.bold || p.heading > 0) }
                        }
                        if (words.isEmpty()) { y -= lead * 0.6f; return@forEachIndexed }
                        var line = ArrayList<Word>(); var lineW = 0f
                        fun flush() {
                            if (y - lead < margin) newPage()
                            y -= lead
                            var x = margin
                            line.forEach { w ->
                                val f = if (w.bold) boldFont else regular
                                val t = safe(f, w.t.replace("\n", ""))
                                if (t.isNotEmpty()) {
                                    cs.beginText(); cs.setFont(f, size); cs.setNonStrokingColor(0f, 0f, 0f); cs.newLineAtOffset(x, y); cs.showText(t); cs.endText()
                                    x += width(f, t, size)
                                }
                            }
                            line = ArrayList(); lineW = 0f
                        }
                        words.forEach { w ->
                            if (w.t == "\n") { flush(); return@forEach }
                            val f = if (w.bold) boldFont else regular
                            val ww = width(f, safe(f, w.t), size)
                            if (lineW + ww > maxW && line.isNotEmpty()) flush()
                            line += w; lineW += ww
                        }
                        if (line.isNotEmpty()) flush()
                        y -= gapAfter
                    }
                }
                report(0.2f + 0.75f * (pi + 1) / blocks.size)
            }
            cs.close()
            doc.save(out)
            report(1f)
            doc.numberOfPages
        }
    }

    /** Decodes a picture from a .docx on a white background, at most about 2000 px. */
    private fun decodeImage(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("unsupported picture")
        val flat = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(raw, 0f, 0f, null) }
        raw.recycle()
        flat
    }.getOrNull()

    private fun loadSystemFont(doc: PDDocument, name: String): PDFont? =
        File("/system/fonts/$name").takeIf { it.exists() }?.let { runCatching { PDType0Font.load(doc, it) }.getOrNull() }
}
