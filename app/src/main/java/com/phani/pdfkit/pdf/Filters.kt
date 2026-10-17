package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Document filters for scans and photos of paper.
 *
 * Auto, Gray and B&W first estimate the paper's own brightness across the page (a blurred
 * "background" with the text taken out) and divide by it. That removes shadows, uneven light and
 * the yellow or gray cast of a phone photo, so the paper turns white and the text stays dark.
 */
object Filters {
    val NAMES = listOf("Original", "Auto", "Gray", "B&W")

    /** Returns a filtered copy, or [src] itself for "Original". The caller recycles [src] if a new bitmap comes back. */
    fun apply(src: Bitmap, name: String): Bitmap = when (name) {
        "Auto", "Enhanced" -> process(src, Mode.AUTO)
        "Gray", "Grayscale" -> process(src, Mode.GRAY)
        "B&W" -> process(src, Mode.BW)
        else -> src
    }

    private enum class Mode { AUTO, GRAY, BW }

    private fun process(input: Bitmap, mode: Mode): Bitmap {
        val src = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        val w = src.width; val h = src.height
        val bg = Background(src)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        val bgR = FloatArray(w); val bgG = FloatArray(w); val bgB = FloatArray(w)
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            bg.row(y, bgR, bgG, bgB)
            for (x in 0 until w) {
                val p = row[x]
                // Divide by the paper: paper becomes ~1.0, ink stays low.
                var r = ((p shr 16) and 255) / bgR[x]
                var g = ((p shr 8) and 255) / bgG[x]
                var b = (p and 255) / bgB[x]
                when (mode) {
                    Mode.AUTO -> {
                        r = levels(r); g = levels(g); b = levels(b)
                        // A little more color so stamps, signatures and highlights stay clear.
                        val l = 0.299f * r + 0.587f * g + 0.114f * b
                        r = l + (r - l) * 1.3f; g = l + (g - l) * 1.3f; b = l + (b - l) * 1.3f
                        row[x] = rgb(r, g, b)
                    }
                    Mode.GRAY -> {
                        val l = levels(0.299f * r + 0.587f * g + 0.114f * b)
                        row[x] = rgb(l, l, l)
                    }
                    Mode.BW -> {
                        val l = 0.299f * r + 0.587f * g + 0.114f * b
                        // Threshold with a short soft edge, so letters stay smooth instead of jagged.
                        val v = ((l - 0.70f) / 0.12f).coerceIn(0f, 1f)
                        row[x] = rgb(v, v, v)
                    }
                }
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        if (src !== input) src.recycle()
        return out
    }

    /** Paper white at 0.9 and above, ink darkened a little. */
    private fun levels(v: Float): Float {
        val t = ((v - 0.06f) / (0.90f - 0.06f)).coerceIn(0f, 1f)
        return t.pow(1.35f)
    }

    private fun rgb(r: Float, g: Float, b: Float): Int {
        val ri = (r * 255f).toInt().coerceIn(0, 255)
        val gi = (g * 255f).toInt().coerceIn(0, 255)
        val bi = (b * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * The paper's brightness everywhere on the page: shrink the image, take the brightest nearby
     * value (which erases text and lines), smooth it, then read it back at full size.
     */
    private class Background(src: Bitmap) {
        private val sw: Int
        private val sh: Int
        private val r: FloatArray
        private val g: FloatArray
        private val b: FloatArray
        private val fx: Float
        private val fy: Float

        init {
            val target = 96
            val k = max(src.width, src.height).toFloat() / target
            sw = max(4, (src.width / k).toInt()); sh = max(4, (src.height / k).toInt())
            var small = src
            // Halve step by step so every pixel counts (a single big jump would skip most of them).
            while (small.width / 2 >= sw * 2 && small.height / 2 >= sh * 2) {
                val next = Bitmap.createScaledBitmap(small, small.width / 2, small.height / 2, true)
                if (small !== src) small.recycle()
                small = next
            }
            val last = Bitmap.createScaledBitmap(small, sw, sh, true)
            if (small !== src && small !== last) small.recycle()
            val px = IntArray(sw * sh)
            last.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (last !== src) last.recycle()
            val rr = FloatArray(px.size) { ((px[it] shr 16) and 255).toFloat() }
            val gg = FloatArray(px.size) { ((px[it] shr 8) and 255).toFloat() }
            val bb = FloatArray(px.size) { (px[it] and 255).toFloat() }
            val rad = max(2, target / 32)
            r = blur(blur(dilate(rr, rad), rad), rad)
            g = blur(blur(dilate(gg, rad), rad), rad)
            b = blur(blur(dilate(bb, rad), rad), rad)
            // Dark backgrounds (a photo, a black page) would blow up; never divide by less than this.
            for (i in r.indices) { r[i] = max(r[i], 48f); g[i] = max(g[i], 48f); b[i] = max(b[i], 48f) }
            fx = (sw - 1).toFloat() / max(1, src.width - 1)
            fy = (sh - 1).toFloat() / max(1, src.height - 1)
        }

        /** Fills one full-size row of background values, smoothly between the small grid's points. */
        fun row(y: Int, outR: FloatArray, outG: FloatArray, outB: FloatArray) {
            val sy = y * fy
            val y0 = sy.toInt().coerceIn(0, sh - 1); val y1 = min(y0 + 1, sh - 1); val ty = sy - y0
            for (x in outR.indices) {
                val sx = x * fx
                val x0 = sx.toInt().coerceIn(0, sw - 1); val x1 = min(x0 + 1, sw - 1); val tx = sx - x0
                val i00 = y0 * sw + x0; val i01 = y0 * sw + x1; val i10 = y1 * sw + x0; val i11 = y1 * sw + x1
                outR[x] = lerp2(r, i00, i01, i10, i11, tx, ty)
                outG[x] = lerp2(g, i00, i01, i10, i11, tx, ty)
                outB[x] = lerp2(b, i00, i01, i10, i11, tx, ty)
            }
        }

        private fun lerp2(a: FloatArray, i00: Int, i01: Int, i10: Int, i11: Int, tx: Float, ty: Float): Float {
            val top = a[i00] + (a[i01] - a[i00]) * tx
            val bot = a[i10] + (a[i11] - a[i10]) * tx
            return top + (bot - top) * ty
        }

        private fun dilate(a: FloatArray, rad: Int): FloatArray {
            val tmp = FloatArray(a.size); val out = FloatArray(a.size)
            for (y in 0 until sh) for (x in 0 until sw) {
                var m = 0f
                for (d in -rad..rad) { val xx = (x + d).coerceIn(0, sw - 1); m = max(m, a[y * sw + xx]) }
                tmp[y * sw + x] = m
            }
            for (y in 0 until sh) for (x in 0 until sw) {
                var m = 0f
                for (d in -rad..rad) { val yy = (y + d).coerceIn(0, sh - 1); m = max(m, tmp[yy * sw + x]) }
                out[y * sw + x] = m
            }
            return out
        }

        private fun blur(a: FloatArray, rad: Int): FloatArray {
            val tmp = FloatArray(a.size); val out = FloatArray(a.size)
            val n = 2 * rad + 1
            for (y in 0 until sh) for (x in 0 until sw) {
                var s = 0f
                for (d in -rad..rad) s += a[y * sw + (x + d).coerceIn(0, sw - 1)]
                tmp[y * sw + x] = s / n
            }
            for (y in 0 until sh) for (x in 0 until sw) {
                var s = 0f
                for (d in -rad..rad) s += tmp[(y + d).coerceIn(0, sh - 1) * sw + x]
                out[y * sw + x] = s / n
            }
            return out
        }
    }
}
