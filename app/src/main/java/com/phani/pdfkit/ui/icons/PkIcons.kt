package com.phani.pdfkit.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline icon set (24x24, 1.8 stroke) matching the prototype.
 * Strokes and fills are black; tint them with Icon(tint = ...).
 */
object PkIcons {
    private fun c(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r} z"

    private fun icon(name: String, vararg stroke: String, fill: List<String> = emptyList(), width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            stroke.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fill.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Image = icon("image", rr(3f, 3f, 18f, 18f, 2.5f), c(9f, 9f, 2f), "M21 15l-3.1-3.1a2 2 0 0 0-2.8 0L6 21")
    val ImageOut = icon("imgout", "M21 11V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7", c(9f, 9f, 2f), "M14 14l-2.5-2.5a2 2 0 0 0-2.8 0L3 17M16 19h6M19 16l3 3-3 3")
    val Scan = icon("scan", "M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10")
    val Layers = icon("layers", "M12.8 2.2a2 2 0 0 0-1.6 0L2.6 6.1a1 1 0 0 0 0 1.8l8.6 3.9a2 2 0 0 0 1.6 0l8.6-3.9a1 1 0 0 0 0-1.8Z", "M22 12.6l-9.2 4.2a2 2 0 0 1-1.6 0L2 12.6M22 17.6l-9.2 4.2a2 2 0 0 1-1.6 0L2 17.6")
    val Compress = icon("compress", "M12 22v-6M12 8V2M4 12h16M9 19l3-3 3 3M9 5l3 3 3-3")
    val Pen = icon("pen", "M15.5 4.5a2.1 2.1 0 1 1 3 3L8 18l-4 1 1-4Z", "M13 20h7")
    val Scissors = icon("scissors", c(6f, 6f, 3f), c(6f, 18f, 3f), "M8.1 8.1L12 12M20 4L8.1 15.9M14.8 14.8L20 20")
    val Dots = icon("dots", fill = listOf(c(5f, 12f, 1.5f), c(12f, 12f, 1.5f), c(19f, 12f, 1.5f)))
    val DotsV = icon("dotsv", fill = listOf(c(12f, 5f, 1.5f), c(12f, 12f, 1.5f), c(12f, 19f, 1.5f)))
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.9 1.9 0 0 0 3.4 0")
    val Search = icon("search", c(11f, 11f, 7.5f), "M20.5 20.5l-4.2-4.2")
    val ChevronRight = icon("chevr", "M9 18l6-6-6-6")
    val ChevronLeft = icon("chevl", "M15 18l-6-6 6-6")
    val ArrowLeft = icon("arrowl", "M19 12H5M12 19l-7-7 7-7")
    val ArrowRight = icon("arrowr", "M5 12h14M12 5l7 7-7 7")
    val ArrowDown = icon("arrowd", "M12 5v14M18 13l-6 6-6-6")
    val Home = icon("home", "M3 10L12 3l9 7v10a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 20Z", "M9.5 21.5V14h5v7.5")
    val Folder = icon("folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.7-0.9l-0.8-1.2A2 2 0 0 0 7.9 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
    val Gear = icon(
        "gear",
        "M12.2 2h-0.4a2 2 0 0 0-2 2v0.2a2 2 0 0 1-1 1.7l-0.4 0.3a2 2 0 0 1-2 0l-0.2-0.1a2 2 0 0 0-2.7 0.7l-0.2 0.4a2 2 0 0 0 0.7 2.7l0.2 0.1a2 2 0 0 1 1 1.7v0.5a2 2 0 0 1-1 1.8l-0.2 0.1a2 2 0 0 0-0.7 2.7l0.2 0.4a2 2 0 0 0 2.7 0.7l0.2-0.1a2 2 0 0 1 2 0l0.4 0.3a2 2 0 0 1 1 1.7v0.2a2 2 0 0 0 2 2h0.4a2 2 0 0 0 2-2v-0.2a2 2 0 0 1 1-1.7l0.4-0.3a2 2 0 0 1 2 0l0.2 0.1a2 2 0 0 0 2.7-0.7l0.2-0.4a2 2 0 0 0-0.7-2.7l-0.2-0.1a2 2 0 0 1-1-1.7v-0.5a2 2 0 0 1 1-1.8l0.2-0.1a2 2 0 0 0 0.7-2.7l-0.2-0.4a2 2 0 0 0-2.7-0.7l-0.2 0.1a2 2 0 0 1-2 0l-0.4-0.3a2 2 0 0 1-1-1.7V4a2 2 0 0 0-2-2z",
        c(12f, 12f, 3f),
    )
    val Plus = icon("plus", "M12 5v14M5 12h14", width = 2.2f)
    val Close = icon("x", "M18 6L6 18M6 6l12 12", width = 2.2f)
    val Check = icon("check", "M20 6L9 17l-5-5", width = 2.4f)
    val Eye = icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z", c(12f, 12f, 3f))
    val Share = icon("share", c(18f, 5f, 3f), c(6f, 12f, 3f), c(18f, 19f, 3f), "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4")
    val Star = icon("star", "M12 2.5l2.9 6 6.6 0.9-4.8 4.6 1.2 6.5L12 17.4l-5.9 3.1 1.2-6.5L2.5 9.4l6.6-0.9z")
    val StarFilled = icon("starf", fill = listOf("M12 2.5l2.9 6 6.6 0.9-4.8 4.6 1.2 6.5L12 17.4l-5.9 3.1 1.2-6.5L2.5 9.4l6.6-0.9z"))
    val Grid = icon("grid", rr(3f, 3f, 7f, 7f, 1.5f), rr(14f, 3f, 7f, 7f, 1.5f), rr(14f, 14f, 7f, 7f, 1.5f), rr(3f, 14f, 7f, 7f, 1.5f))
    val List = icon("list", rr(3f, 4f, 18f, 6f, 1.5f), rr(3f, 14f, 18f, 6f, 1.5f))
    val Grip = icon("grip", fill = listOf(c(9f, 6f, 1.5f), c(15f, 6f, 1.5f), c(9f, 12f, 1.5f), c(15f, 12f, 1.5f), c(9f, 18f, 1.5f), c(15f, 18f, 1.5f)))
    val Highlight = icon("highlight", "M9 11l-6 6v3h9l3-3", "M22 12l-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4")
    val Pencil = icon("pencil", "M21.2 6.8a2.8 2.8 0 0 0-4-4L3.8 16.2a2 2 0 0 0-0.5 0.8L2 21.4a0.5 0.5 0 0 0 0.6 0.6l4.4-1.3a2 2 0 0 0 0.8-0.5z", "M14 20h7")
    val TextT = icon("type", "M4 7V4h16v3M9 20h6M12 4v16")
    val Signature = icon("signature", "M3 17c3-1 4-8 6.5-8S10 16 12 16s2.5-3 4-3 1.5 3 3 3 2-1 2-1", "M3 21h18")
    val Pages = icon("pages", rr(3f, 3f, 18f, 18f, 2f), "M3 12h18M12 3v18")
    val Shield = icon("shield", "M20 13c0 5-3.5 7.5-7.7 9a1 1 0 0 1-0.6 0C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.2-2.7a1.2 1.2 0 0 1 1.6 0C14.5 3.8 17 5 19 5a1 1 0 0 1 1 1z", "M9 12l2 2 4-4")
    val Sliders = icon("sliders", "M21 4h-7M10 4H3M21 12h-9M8 12H3M21 20h-5M12 20H3M14 2v4M8 10v4M16 18v4")
    val Palette = icon("palette", "M12 2a10 10 0 0 0 0 20c1 0 1.6-0.7 1.6-1.7 0-0.4-0.2-0.8-0.4-1.1-0.3-0.3-0.5-0.7-0.5-1.1 0-0.9 0.8-1.7 1.7-1.7h2a5.6 5.6 0 0 0 5.6-5.6C22 6 17.5 2 12 2z", fill = listOf(c(13.5f, 6.5f, 1f), c(17.5f, 10.5f, 1f), c(8.5f, 7.5f, 1f), c(6.5f, 12.5f, 1f)))
    val Lock = icon("lock", rr(3f, 11f, 18f, 11f, 2.5f), "M7 11V7a5 5 0 0 1 10 0v4")
    val Unlock = icon("unlock", rr(3f, 11f, 18f, 11f, 2.5f), "M7 11V7a5 5 0 0 1 9.9-1")
    val Info = icon("info", c(12f, 12f, 10f), "M12 16v-4M12 8h0.01")
    val Word = icon("word", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v5h6", "M8 12l1.3 5L11 13l1.7 4L14 12")
    val DocPdf = icon("docpdf", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v5h6M8 12h8M8 16h8M8 8h2")
    val Merge = icon("merge", "M9 18H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h7l4 4v3", rr(11f, 11f, 10f, 11f, 2f), "M13 2v4h4")
    val Rotate = icon("rotate", "M21 12a9 9 0 1 1-9-9c2.5 0 4.9 1 6.7 2.7L21 8", "M21 3v5h-5")
    val Reorder = icon("reorder", "M21 16l-4 4-4-4M17 20V4M3 8l4-4 4 4M7 4v16")
    val Droplet = icon("droplet", "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5S12.5 5 12 2.5c-0.5 2.5-2 4.9-4 6.5S5 13 5 15a7 7 0 0 0 7 7z")
    val Hash = icon("hash", "M4 9h16M4 15h16M10 3L8 21M16 3l-2 18")
    val Ocr = icon("ocr", "M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M8 8h8M8 12h8M8 16h5")
    val Download = icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3")
    val Trash = icon("trash", "M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    val Edit = icon("edit", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z")
    val FileUp = icon("fileup", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v5h6M12 18v-6M9 15l3-3 3 3")
    val CloudOff = icon("cloudoff", "M2 2l20 20", "M5.8 5.8A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.3-0.2M21.5 15.5A4.5 4.5 0 0 0 17.5 10h-1.8A7 7 0 0 0 9.2 5.1")
    val WifiOff = icon("wifioff", "M12 20h0.01M8.5 16.4a5 5 0 0 1 7 0M2 8.8a15 15 0 0 1 4.2-2.8M22 8.8a15 15 0 0 0-11.3-3.8M16.8 11.6a10 10 0 0 1 2 1.3M5 12.9a10 10 0 0 1 5.2-2.4M2 2l20 20")
    val UserX = icon("userx", c(9f, 7f, 4f), "M2 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2M17 8l5 5M22 8l-5 5")
    val Play = icon("play", "M6 3.5v17l14-8.5z")
    val Sparkle = icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 3v4M17 5h4")
    val ChevronUp = icon("chevu", "M18 15l-6-6-6 6")
    val ChevronDown = icon("chevd", "M6 9l6 6 6-6")
    val Bookmark = icon("bookmark", "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    val Copy = icon("copy", rr(9f, 9f, 13f, 13f, 2f), "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")
    val Crop = icon("crop", "M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2")
    val Printer = icon("printer", "M6 9V2h12v7", "M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", rr(6f, 14f, 12f, 8f, 1f))
}
