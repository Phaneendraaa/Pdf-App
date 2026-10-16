package com.phani.pdfkit.pdf

import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Basic, fully offline Word conversions. Text and paragraphs survive; complex layout
 * (tables, columns, images) does not. The UI says so.
 */
object WordTools {

    // ---------------- PDF -> DOCX ----------------
    suspend fun pdfToDocx(src: File, out: File, report: (Float) -> Unit): Int {
        val pages = ArrayList<List<String>>()
        PdfTools.load(src).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (i in 1..doc.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = i; stripper.endPage = i
                pages += paragraphs(stripper.getText(doc))
                report(i.toFloat() / doc.numberOfPages * 0.9f)
            }
        }
        if (pages.all { it.isEmpty() }) throw ToolError("No text found. This looks like a scanned PDF; try Text Recognition instead.")
        val body = StringBuilder()
        pages.forEachIndexed { i, paras ->
            paras.forEach { p -> body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(p)).append("</w:t></w:r></w:p>") }
            if (i < pages.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        writeDocx(out, body.toString())
        report(1f)
        return pages.size
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

    private fun writeDocx(out: File, body: String) {
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(name: String, data: String) { z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            )
            put(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            )
        }
    }

    // ---------------- DOCX -> PDF ----------------
    private data class Run(val text: String, val bold: Boolean)
    private data class Para(val runs: List<Run>, val heading: Int, val pageBreakBefore: Boolean)

    private fun readDocx(src: File): List<Para> {
        val zip = try { ZipFile(src) } catch (e: Exception) { throw ToolError("This isn't a .docx file. Old .doc files aren't supported.") }
        return zip.use { z ->
            val entry = z.getEntry("word/document.xml") ?: throw ToolError("This Word file looks damaged.")
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(z.getInputStream(entry), "UTF-8")
            }
            val paras = ArrayList<Para>()
            var runs = ArrayList<Run>()
            var heading = 0; var bold = false; var inText = false; var breakNext = false; var pendingBreak = false
            val text = StringBuilder()
            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> when (parser.name) {
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
                    }
                    XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "w:t" -> { inText = false; runs += Run(text.toString(), bold) }
                        "w:p" -> paras += Para(runs, heading, pendingBreak)
                    }
                }
            }
            paras
        }
    }

    suspend fun docxToPdf(src: File, out: File, report: (Float) -> Unit): Int {
        val paras = readDocx(src)
        if (paras.isEmpty()) throw ToolError("This Word file has no text.")
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

            paras.forEachIndexed { pi, p ->
                coroutineContext.ensureActive()
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
                            cs.beginText(); cs.setFont(f, size); cs.newLineAtOffset(x, y); cs.showText(t); cs.endText()
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
                report(0.2f + 0.75f * (pi + 1) / paras.size)
            }
            cs.close()
            doc.save(out)
            report(1f)
            doc.numberOfPages
        }
    }

    private fun loadSystemFont(doc: PDDocument, name: String): PDFont? =
        File("/system/fonts/$name").takeIf { it.exists() }?.let { runCatching { PDType0Font.load(doc, it) }.getOrNull() }
}
