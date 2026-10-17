package com.phani.pdfkit.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/** Simple document look filters for photos turned into PDFs. */
object Filters {
    val NAMES = listOf("Original", "Enhanced", "B&W")

    /** Returns a filtered copy, or [src] itself for "Original". The caller recycles [src] if a new bitmap comes back. */
    fun apply(src: Bitmap, name: String): Bitmap {
        val m = when (name) {
            "Enhanced" -> ColorMatrix().apply { setSaturation(1.05f); postConcat(contrast(1.25f, 15f)) }
            "B&W" -> ColorMatrix().apply { setSaturation(0f); postConcat(contrast(1.9f, 30f)) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    /** Contrast around mid-gray plus a brightness lift. */
    private fun contrast(c: Float, lift: Float): ColorMatrix {
        val t = 128f * (1f - c) + lift
        return ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}
