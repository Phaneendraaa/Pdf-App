package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfText {
    /** Text of each page (empty strings for scanned pages). */
    fun pages(file: File, max: Int = 200): List<String> = PdfTools.load(file).use { doc ->
        val s = PDFTextStripper().apply { sortByPosition = true }
        (1..minOf(doc.numberOfPages, max)).map { i -> s.startPage = i; s.endPage = i; s.getText(doc).trim() }
    }

    /** Groups pages into chunks under [maxWords] words, for models with a small input window. */
    fun chunks(pages: List<String>, maxWords: Int = 2500): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var words = 0
        pages.filter { it.isNotBlank() }.forEach { p ->
            val w = p.split(Regex("\\s+")).size
            if (words + w > maxWords && cur.isNotEmpty()) { out += cur.toString(); cur.clear(); words = 0 }
            if (w > maxWords) { out += p.split(Regex("\\s+")).chunked(maxWords).map { it.joinToString(" ") }; return@forEach }
            cur.append(p).append("\n\n"); words += w
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
