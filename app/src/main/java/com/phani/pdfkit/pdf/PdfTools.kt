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
    data class Compressed(val pages: Int, val before: Long, val after: Long, val reached: Boolean = true)

    suspend fun compress(src: File, out: File, quality: Quality, report: (Float) -> Unit, gray: Boolean = false): Compressed {
        val pages = compressRaw(src, out, quality, gray) { report(it * 0.9f) }
        if (out.length() >= src.length() && !gray) src.copyTo(out, overwrite = true)
        report(1f)
        return Compressed(pages, src.length(), out.length())
    }

    /** From sharp to tiny. compressTo() looks for the sharpest step that fits the size limit. */
    private val LADDER = listOf(
        Quality(2400, 0.85f), Quality(2000, 0.75f), Quality(1700, 0.65f), Quality(1450, 0.55f), Quality(1250, 0.47f),
        Quality(1080, 0.4f), Quality(940, 0.33f), Quality(820, 0.27f), Quality(700, 0.22f), Quality(580, 0.17f), Quality(460, 0.12f),
    )

    /**
     * Compresses until the file is at most [target] bytes, keeping as much quality as possible.
     * If even the strongest step is too big, returns the smallest result with reached = false.
     */
    suspend fun compressTo(src: File, out: File, target: Long, gray: Boolean, report: (Float) -> Unit): Compressed {
        val before = src.length()
        if (before <= target && !gray) {
            src.copyTo(out, overwrite = true); report(1f)
            return Compressed(pageCount(src), before, before, true)
        }
        var lo = 0; var hi = LADDER.lastIndex
        var found = false; var pages = 0; var attempt = 0
        val tmp = File(out.parentFile, ".try_${System.nanoTime()}.pdf")
        try {
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val step = attempt
                pages = compressRaw(src, tmp, LADDER[mid], gray) { f -> report(((step + f) / 5f).coerceAtMost(0.95f)) }
                attempt++
                if (tmp.length() <= target) { tmp.copyTo(out, overwrite = true); found = true; hi = mid - 1 } else lo = mid + 1
            }
            if (!found) pages = compressRaw(src, out, LADDER.last(), gray) {}
        } finally { tmp.delete() }
        if (!found && out.length() > before) src.copyTo(out, overwrite = true)
        report(1f)
        return Compressed(pages, before, out.length(), found)
    }

    private suspend fun compressRaw(src: File, out: File, quality: Quality, gray: Boolean, report: (Float) -> Unit): Int =
        load(src).use { doc ->
            val seen = HashSet<COSName>()
            doc.pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                page.resources?.let { recompress(doc, it, quality, gray, seen, 0) }
                report((i + 1f) / doc.numberOfPages)
            }
            doc.save(out)
            doc.numberOfPages
        }

    private fun recompress(doc: PDDocument, res: PDResources, q: Quality, gray: Boolean, seen: HashSet<COSName>, depth: Int) {
        if (depth > 4) return
        for (name in res.xObjectNames.toList()) {
            val x = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            when (x) {
                is PDFormXObject -> x.resources?.let { recompress(doc, it, q, gray, seen, depth + 1) }
                is PDImageXObject -> {
                    if (x.width * x.height < 160 * 160) continue
                    val bmp = runCatching { x.image }.getOrNull() ?: continue
                    val scale = minOf(1f, q.maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
                    val flat = flatten(scaled, gray)
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
    private fun flatten(b: Bitmap, gray: Boolean = false): Bitmap {
        if (!b.hasAlpha() && !gray) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out); cv.drawColor(Color.WHITE)
        val paint = if (gray) android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        } else null
        cv.drawBitmap(b, 0f, 0f, paint)
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
    suspend fun toImages(src: File, outFor: (Int) -> File, scale: Float, report: (Float) -> Unit, only: List<Int>? = null): List<File> {
        val fd = try { ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY) } catch (e: Exception) { throw ToolError("Can't open this PDF.") }
        val renderer = try { PdfRenderer(fd) } catch (e: SecurityException) { fd.close(); throw ToolError("This PDF is password protected. Unlock it first.") }
        val out = ArrayList<File>()
        renderer.use { r ->
            val list = only?.filter { it in 0 until r.pageCount } ?: (0 until r.pageCount).toList()
            for ((n, i) in list.withIndex()) {
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
                report((n + 1f) / list.size)
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

    /** How page numbers look. Positions: Left/Center/Right x Top/Bottom. */
    data class NumberStyle(
        val h: String = "Center", val v: String = "Bottom",
        val format: String = "1",          // "1", "Page 1", "1 of N", "- 1 -"
        val start: Int = 1,
        val fromPage: Int = 1,             // first page (1-based) that gets a number
        val margin: Float = 28f,           // distance from the page edge in points
        val size: Float = 10f,
        val color: Int = 0xFF5A5E6B.toInt(),
        val bold: Boolean = false,
    ) {
        fun label(n: Int, last: Int) = when (format) { "Page 1" -> "Page $n"; "1 of N" -> "$n of $last"; "- 1 -" -> "- $n -"; else -> "$n" }
    }

    /** Numbers every page from [NumberStyle.fromPage] on, in one go. */
    fun pageNumbers(src: File, st: NumberStyle, out: File): Int = load(src).use { d ->
        val font = if (st.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
        val skip = (st.fromPage - 1).coerceIn(0, d.numberOfPages)
        val last = st.start + (d.numberOfPages - skip) - 1
        val r = ((st.color shr 16) and 0xFF) / 255f; val g = ((st.color shr 8) and 0xFF) / 255f; val b = (st.color and 0xFF) / 255f
        d.pages.forEachIndexed { i, page ->
            if (i < skip) return@forEachIndexed
            val box = page.cropBox
            val text = st.label(st.start + (i - skip), last)
            val tw = font.getStringWidth(text) / 1000f * st.size
            val m = st.margin
            val x = when (st.h) {
                "Left" -> box.lowerLeftX + m
                "Right" -> box.lowerLeftX + box.width - m - tw
                else -> box.lowerLeftX + (box.width - tw) / 2f
            }
            val y = if (st.v == "Top") box.lowerLeftY + box.height - m - st.size * 0.75f else box.lowerLeftY + m
            PDPageContentStream(d, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.beginText(); cs.setFont(font, st.size); cs.setNonStrokingColor(r, g, b)
                cs.newLineAtOffset(x, y); cs.showText(text); cs.endText()
            }
        }
        d.save(out); d.numberOfPages
    }

    /** One page in an organized PDF: [source] page index (or -1 for a new blank page) and extra rotation. */
    data class PageOp(val source: Int, val rotate: Int = 0)

    /** Builds a new PDF from [ops]: reorder, delete (leave out), duplicate (repeat), rotate, and blank pages. */
    fun organize(src: File, ops: List<PageOp>, out: File): Int = load(src).use { d ->
        if (ops.isEmpty()) throw ToolError("Keep at least one page")
        PDDocument().use { n ->
            var lastBox: PDRectangle = PDRectangle.A4
            ops.forEach { op ->
                if (op.source in 0 until d.numberOfPages) {
                    val orig = d.getPage(op.source)
                    val p = n.importPage(orig)
                    p.rotation = (((orig.rotation + op.rotate) % 360) + 360) % 360
                    lastBox = orig.mediaBox
                } else {
                    n.addPage(PDPage(PDRectangle(lastBox.width, lastBox.height)))
                }
            }
            n.save(out); n.numberOfPages
        }
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
