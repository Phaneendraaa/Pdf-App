package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/** Friendly error the UI can show as-is. */
class ToolError(message: String) : Exception(message)

/** On-device PDF operations built on PdfBox-Android and PdfRenderer. */
object PdfTools {
    private fun mem() = MemoryUsageSetting.setupTempFileOnly()

    fun load(file: File, password: String? = null): PDDocument = try {
        if (password != null) PDDocument.load(file, password, mem()) else PDDocument.load(file, mem())
    } catch (e: InvalidPasswordException) {
        throw ToolError(if (password == null) "This PDF is password protected. Unlock it first." else "Wrong password. Please try again.")
    } catch (e: ToolError) {
        throw e
    } catch (e: Exception) {
        throw ToolError("Can't read this PDF. It may be damaged.")
    }

    fun pageCount(file: File): Int = runCatching { load(file).use { it.numberOfPages } }.getOrDefault(0)

    // ---------- Merge ----------
    fun merge(files: List<File>, out: File, report: (Float) -> Unit): Int {
        val m = PDFMergerUtility()
        files.forEach { f -> load(f).close(); m.addSource(f) } // load() validates each file first
        report(0.4f)
        m.destinationFileName = out.absolutePath
        m.mergeDocuments(mem())
        report(1f)
        return pageCount(out)
    }

    // ---------- Compress ----------
    data class Compressed(val pages: Int, val before: Long, val after: Long)

    suspend fun compress(src: File, out: File, quality: Quality, report: (Float) -> Unit): Compressed {
        return load(src).use { doc ->
            val seen = HashSet<COSName>()
            doc.pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                page.resources?.let { recompress(doc, it, quality, seen, 0) }
                report((i + 1f) / doc.numberOfPages * 0.9f)
            }
            doc.save(out)
            val pages = doc.numberOfPages
            if (out.length() >= src.length()) src.copyTo(out, overwrite = true)
            report(1f)
            Compressed(pages, src.length(), out.length())
        }
    }

    private fun recompress(doc: PDDocument, res: PDResources, q: Quality, seen: HashSet<COSName>, depth: Int) {
        if (depth > 4) return
        for (name in res.xObjectNames.toList()) {
            val x = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            when (x) {
                is PDFormXObject -> x.resources?.let { recompress(doc, it, q, seen, depth + 1) }
                is PDImageXObject -> {
                    if (x.width * x.height < 160 * 160) continue
                    val bmp = runCatching { x.image }.getOrNull() ?: continue
                    val scale = minOf(1f, q.maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
                    val flat = flatten(scaled)
                    val jpg = runCatching { JPEGFactory.createFromImage(doc, flat, q.jpeg) }.getOrNull()
                    if (jpg != null) res.put(name, jpg)
                    if (flat !== scaled) flat.recycle()
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()
                }
            }
        }
    }

    /** JPEG has no alpha: draw onto white so transparent areas don't turn black. */
    private fun flatten(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out); cv.drawColor(Color.WHITE); cv.drawBitmap(b, 0f, 0f, null)
        return out
    }

    // ---------- Security ----------
    fun unlock(src: File, password: String, out: File): Int = load(src, password).use { d ->
        d.isAllSecurityToBeRemoved = true
        d.save(out); d.numberOfPages
    }

    fun protect(src: File, password: String, out: File): Int = load(src).use { d ->
        val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 128 }
        d.protect(policy)
        d.save(out); d.numberOfPages
    }

    // ---------- PDF to images ----------
    suspend fun toImages(src: File, outFor: (Int) -> File, scale: Float, report: (Float) -> Unit): List<File> {
        val fd = try { ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY) } catch (e: Exception) { throw ToolError("Can't open this PDF.") }
        val renderer = try { PdfRenderer(fd) } catch (e: SecurityException) { fd.close(); throw ToolError("This PDF is password protected. Unlock it first.") }
        val out = ArrayList<File>()
        renderer.use { r ->
            for (i in 0 until r.pageCount) {
                coroutineContext.ensureActive()
                r.openPage(i).use { p ->
                    val w = (p.width * scale).toInt(); val h = (p.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    val f = outFor(i + 1)
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    bmp.recycle(); out += f
                }
                report((i + 1f) / r.pageCount)
            }
        }
        fd.close()
        return out
    }

    // ---------- Split ----------
    /** Parses "1-3, 5, 8-10" into zero-based page indexes. */
    fun parseRange(text: String, pageCount: Int): List<Int> {
        val out = LinkedHashSet<Int>()
        text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val bits = part.split('-').map { it.trim().toIntOrNull() }
            when {
                bits.size == 1 && bits[0] != null -> out += bits[0]!!
                bits.size == 2 && bits[0] != null && bits[1] != null -> {
                    val a = bits[0]!!; val b = bits[1]!!
                    (minOf(a, b)..maxOf(a, b)).forEach { out += it }
                }
                else -> throw ToolError("Couldn't read \"$part\". Use pages like 1-3, 5")
            }
        }
        val idx = out.filter { it in 1..pageCount }.map { it - 1 }
        if (idx.isEmpty()) throw ToolError("Pick pages between 1 and $pageCount")
        return idx
    }

    fun extract(src: File, pages: List<Int>, out: File): Int = load(src).use { d ->
        PDDocument().use { n ->
            pages.forEach { n.importPage(d.getPage(it)) }
            n.save(out); n.numberOfPages
        }
    }

    suspend fun splitEvery(src: File, outFor: (Int) -> File, report: (Float) -> Unit): List<File> = load(src).use { d ->
        val parts = Splitter().split(d)
        parts.mapIndexed { i, p ->
            coroutineContext.ensureActive()
            val f = outFor(i + 1)
            p.use { it.save(f) }
            report((i + 1f) / parts.size); f
        }
    }

    // ---------- Page edits ----------
    fun rotate(src: File, degrees: Int, out: File): Int = load(src).use { d ->
        d.pages.forEach { it.rotation = ((it.rotation + degrees) % 360 + 360) % 360 }
        d.save(out); d.numberOfPages
    }

    /** Writes pages in [order] (zero-based); pages not listed are dropped. */
    fun reorder(src: File, order: List<Int>, out: File): Int = load(src).use { d ->
        PDDocument().use { n ->
            order.forEach { n.importPage(d.getPage(it)) }
            n.save(out); n.numberOfPages
        }
    }

    /**
     * Numbers every page in one go. [h] is Left, Center or Right; [v] is Top or Bottom;
     * [format] is "1", "Page 1" or "1 of N". With [skipFirst] a cover page gets no number and page 2 starts at [start].
     */
    fun pageNumbers(src: File, h: String, v: String, start: Int, format: String, skipFirst: Boolean, out: File): Int = load(src).use { d ->
        val font = PDType1Font.HELVETICA
        val size = 10f
        val skip = if (skipFirst) 1 else 0
        val last = start + (d.numberOfPages - skip) - 1
        d.pages.forEachIndexed { i, page ->
            if (i < skip) return@forEachIndexed
            val box = page.mediaBox
            val n = start + (i - skip)
            val text = when (format) { "Page 1" -> "Page $n"; "1 of N" -> "$n of $last"; else -> "$n" }
            val tw = font.getStringWidth(text) / 1000f * size
            val margin = 28f
            val x = when (h) {
                "Left" -> box.lowerLeftX + margin
                "Right" -> box.lowerLeftX + box.width - margin - tw
                else -> box.lowerLeftX + (box.width - tw) / 2f
            }
            val y = if (v == "Top") box.lowerLeftY + box.height - margin else box.lowerLeftY + margin - 6f
            PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.beginText(); cs.setFont(font, size); cs.setNonStrokingColor(0.35f, 0.37f, 0.42f)
                cs.newLineAtOffset(x, y); cs.showText(text); cs.endText()
            }
        }
        d.save(out); d.numberOfPages
    }

    /**
     * Crops the listed pages to the kept area, given as fractions of the page as it is displayed
     * (left, top, right, bottom; origin top-left). What was cropped away is hidden, not erased.
     */
    fun crop(src: File, pages: List<Int>, l: Float, t: Float, r: Float, b: Float, out: File): Int = load(src).use { d ->
        for (i in pages) {
            if (i !in 0 until d.numberOfPages) continue
            val page = d.getPage(i)
            val box = page.cropBox
            val rot = ((page.rotation % 360) + 360) % 360
            // Map the displayed rectangle back to the page's unrotated space.
            val (x0, x1, y0, y1) = when (rot) {
                90 -> listOf(t, b, 1f - r, 1f - l)
                180 -> listOf(1f - r, 1f - l, 1f - b, 1f - t)
                270 -> listOf(1f - b, 1f - t, l, r)
                else -> listOf(l, r, t, b)
            }
            val rect = PDRectangle(
                box.lowerLeftX + x0 * box.width, box.lowerLeftY + (1f - y1) * box.height,
                (x1 - x0) * box.width, (y1 - y0) * box.height,
            )
            page.cropBox = rect
            page.mediaBox = rect
        }
        d.save(out); d.numberOfPages
    }

    fun watermark(src: File, text: String, opacity: Float, out: File): Int = load(src).use { d ->
        val font = PDType1Font.HELVETICA_BOLD
        val clean = text.filter { it.code in 32..126 }.ifBlank { throw ToolError("Use letters and numbers (A-Z, 0-9) for the watermark") }
        d.pages.forEach { page ->
            val box = page.mediaBox
            val size = (minOf(box.width, box.height) / (clean.length * 0.55f + 1)).coerceIn(18f, 72f)
            val tw = font.getStringWidth(clean) / 1000f * size
            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity }
            PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(0.86f, 0.15f, 0.15f)
                cs.beginText(); cs.setFont(font, size)
                val cx = box.lowerLeftX + box.width / 2f; val cy = box.lowerLeftY + box.height / 2f
                val m = Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy)
                m.translate(-tw / 2f, -size / 3f)
                cs.setTextMatrix(m)
                cs.showText(clean); cs.endText()
            }
        }
        d.save(out); d.numberOfPages
    }

    @Suppress("unused")
    private fun PDPage.safeRotation() = rotation
}
