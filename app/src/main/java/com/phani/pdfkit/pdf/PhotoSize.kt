package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Makes a JPG that fits an exam or job form: exact pixel size and a file size between a minimum
 * and maximum (for example 20-50 KB). Keeps the best quality that fits the maximum; if even the
 * best quality is under the minimum, the picture is made a little larger until it fits.
 */
object PhotoSize {
    data class Result(val file: File, val bytes: Int, val width: Int, val height: Int, val resized: Boolean)

    fun make(src: Bitmap, width: Int, height: Int, minKb: Int, maxKb: Int, out: File): Result {
        val minB = minKb * 1024; val maxB = maxKb.coerceAtLeast(minKb + 1) * 1024
        var w = width.coerceIn(40, 4000); var h = height.coerceIn(40, 4000)
        var resized = false
        repeat(12) {
            val scaled = flat(scaledCopy(src, w, h))
            val best = bestUnder(scaled, maxB)
            when {
                best == null -> { // too big even at the lowest quality: shrink
                    scaled.recycle(); w = (w * 0.85f).roundToInt(); h = (h * 0.85f).roundToInt(); resized = true
                }
                best.size < minB && w < 4000 -> { // too small even at best quality: grow
                    scaled.recycle(); w = (w * 1.2f).roundToInt(); h = (h * 1.2f).roundToInt(); resized = true
                }
                else -> {
                    out.writeBytes(best); scaled.recycle()
                    return Result(out, best.size, w, h, resized)
                }
            }
        }
        val scaled = flat(scaledCopy(src, w, h))
        val bytes = jpeg(scaled, 60); scaled.recycle(); out.writeBytes(bytes)
        return Result(out, bytes.size, w, h, true)
    }

    /** Always a new bitmap, so recycling it never touches [src]. */
    private fun scaledCopy(src: Bitmap, w: Int, h: Int): Bitmap {
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        return if (s === src) src.copy(Bitmap.Config.ARGB_8888, false) else s
    }

    /** Highest JPEG quality whose file is at most [maxB] bytes, or null if none is. */
    private fun bestUnder(b: Bitmap, maxB: Int): ByteArray? {
        var lo = 5; var hi = 100; var best: ByteArray? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val bytes = jpeg(b, mid)
            if (bytes.size <= maxB) { best = bytes; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    private fun jpeg(b: Bitmap, q: Int): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

    /** Transparent PNGs become white, not black, in a JPG. */
    private fun flat(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val o = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(o).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        b.recycle(); return o
    }
}
