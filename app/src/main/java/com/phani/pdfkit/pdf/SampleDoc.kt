package com.phani.pdfkit.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/** Builds the "Welcome to Pdf Kit" sample so first-time users have a file to try every tool on. */
object SampleDoc {
    private val pages = listOf(
        "Welcome to Pdf Kit" to listOf(
            "This sample PDF lives on your phone. Try any tool on it:",
            "",
            "-  Open it and tap Highlight, Draw, Text or Sign to edit a page.",
            "-  Compress it from Quick tools and see how much space you save.",
            "-  Add a password with Protect PDF, then remove it with Unlock.",
            "-  Merge it with another PDF, or split out the pages you need.",
            "-  Turn it into JPG images or a Word file.",
            "",
            "Everything happens on your device. Nothing is uploaded, ever.",
        ),
        "Quick tips" to listOf(
            "-  Tap + on Home to scan a document or turn photos into a PDF.",
            "-  In JPG to PDF, hold the dots on a photo and drag to reorder.",
            "-  Tap the star on any file to find it fast under Starred.",
            "-  Deleted something by mistake? Tap Undo right away.",
            "-  Settings > Theme switches to dark mode for night reading.",
            "",
            "You can delete this file anytime from Files.",
        ),
    )

    fun create(out: File) {
        PDDocument().use { doc ->
            val bold = PDType1Font.HELVETICA_BOLD; val reg = PDType1Font.HELVETICA
            pages.forEachIndexed { i, (title, lines) ->
                val page = PDPage(PDRectangle.A4); doc.addPage(page)
                val w = page.mediaBox.width; val h = page.mediaBox.height
                PDPageContentStream(doc, page).use { cs ->
                    cs.setNonStrokingColor(0.141f, 0.388f, 0.922f)
                    cs.addRect(0f, h - 120f, w, 120f); cs.fill()
                    cs.beginText(); cs.setFont(bold, 28f); cs.setNonStrokingColor(1f, 1f, 1f)
                    cs.newLineAtOffset(56f, h - 78f); cs.showText(title); cs.endText()
                    var y = h - 180f
                    lines.forEach { line ->
                        if (line.isNotEmpty()) {
                            cs.beginText(); cs.setFont(reg, 13f); cs.setNonStrokingColor(0.17f, 0.18f, 0.21f)
                            cs.newLineAtOffset(56f, y); cs.showText(line); cs.endText()
                        }
                        y -= 26f
                    }
                    cs.beginText(); cs.setFont(reg, 10f); cs.setNonStrokingColor(0.6f, 0.62f, 0.66f)
                    cs.newLineAtOffset(56f, 40f); cs.showText("Pdf Kit  -  page ${i + 1} of ${pages.size}"); cs.endText()
                }
            }
            doc.save(out)
        }
    }
}
