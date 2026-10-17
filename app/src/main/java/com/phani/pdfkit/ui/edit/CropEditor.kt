package com.phani.pdfkit.ui.edit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.pdf.PdfTools
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.SaveChoiceSheet
import com.phani.pdfkit.ui.components.Segmented
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private const val MIN = 0.08f

/** Drag the frame to choose what to keep, then crop one page or all of them. */
@Composable
fun CropEditor(pdf: PdfDoc, file: File, startPage: Int, onClose: () -> Unit, onSaved: (File) -> Unit) {
    val c = Pk.colors
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))) }
    var l by remember { mutableFloatStateOf(0.05f) }
    var t by remember { mutableFloatStateOf(0.05f) }
    var r by remember { mutableFloatStateOf(0.95f) }
    var b by remember { mutableFloatStateOf(0.95f) }
    var scopeAll by remember { mutableStateOf("All pages") }
    var ask by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val inLibrary = file.absolutePath.startsWith(Graph.library.dir.absolutePath)
    val bmp by produceState<Bitmap?>(null, page) { value = pdf.render(page, 1400) }
    val ratio = pdf.ratios.getOrElse(page) { 0.707f }
    LaunchedEffect(Unit) { Toaster.show("Drag the corners to choose what to keep") }
    BackHandler { onClose() }

    fun save(replace: Boolean) {
        saving = true
        val targets = if (scopeAll == "All pages") (0 until pdf.pageCount).toList() else listOf(page)
        val (cl, ct, cr, cb) = listOf(l, t, r, b)
        scope.launch {
            val out = Graph.library.saveEdit(file, replace, "_cropped") { PdfTools.crop(file, targets, cl, ct, cr, cb, it) }
            saving = false
            if (out == null) Toaster.show("Couldn't crop the pages")
            else { Toaster.show(if (replace) "Cropped ${file.name}" else "Saved as ${out.name}"); onSaved(out) }
        }
    }

    Column(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
        Row(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Crop pages", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page > 0) { page-- }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronLeft, "Previous page", tint = if (page > 0) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
            Text("${page + 1} / ${pdf.pageCount}", fontSize = 13.5.sp, color = c.ink2)
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page < pdf.pageCount - 1) { page++ }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronRight, "Next page", tint = if (page < pdf.pageCount - 1) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val maxW = maxWidth; val maxH = maxHeight
            val w = if (maxW / maxH > ratio) maxH * ratio else maxW
            val h = w / ratio
            val density = LocalDensity.current
            val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
            val tolX = with(density) { 34.dp.toPx() } / wPx
            val tolY = with(density) { 34.dp.toPx() } / hPx
            Box(
                Modifier.size(w, h).shadow(8.dp, RoundedCornerShape(3.dp)).background(Color.White).pointerInput(Unit) {
                    var mode = 0 // 1 = move; otherwise bits: 2 left, 4 top, 8 right, 16 bottom
                    detectDragGestures(
                        onDragStart = { o ->
                            val x = o.x / wPx; val y = o.y / hPx
                            var m = 0
                            if (abs(x - l) < tolX) m = m or 2 else if (abs(x - r) < tolX) m = m or 8
                            if (abs(y - t) < tolY) m = m or 4 else if (abs(y - b) < tolY) m = m or 16
                            if (m == 0 && x in l..r && y in t..b) m = 1
                            mode = m
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val dx = drag.x / wPx; val dy = drag.y / hPx
                            if (mode == 1) {
                                val mx = dx.coerceIn(-l, 1f - r); val my = dy.coerceIn(-t, 1f - b)
                                l += mx; r += mx; t += my; b += my
                            } else {
                                if (mode and 2 != 0) l = (l + dx).coerceIn(0f, r - MIN)
                                if (mode and 8 != 0) r = (r + dx).coerceIn(l + MIN, 1f)
                                if (mode and 4 != 0) t = (t + dy).coerceIn(0f, b - MIN)
                                if (mode and 16 != 0) b = (b + dy).coerceIn(t + MIN, 1f)
                            }
                        },
                    )
                },
            ) {
                Crossfade(bmp, label = "cropPage") { img ->
                    if (img != null) Image(img.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue, modifier = Modifier.size(24.dp)) }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val dim = Color(0x99101217)
                    val x0 = l * size.width; val x1 = r * size.width; val y0 = t * size.height; val y1 = b * size.height
                    drawRect(dim, Offset.Zero, Size(size.width, y0))
                    drawRect(dim, Offset(0f, y1), Size(size.width, size.height - y1))
                    drawRect(dim, Offset(0f, y0), Size(x0, y1 - y0))
                    drawRect(dim, Offset(x1, y0), Size(size.width - x1, y1 - y0))
                    drawRect(c.blue, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
                    listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1, (x0 + x1) / 2f to y0, (x0 + x1) / 2f to y1, x0 to (y0 + y1) / 2f, x1 to (y0 + y1) / 2f).forEach { (cx, cy) ->
                        drawCircle(Color.White, 8.dp.toPx(), Offset(cx, cy))
                        drawCircle(c.blue, 8.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Segmented(listOf("All pages", "This page"), scopeAll) { scopeAll = it }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(14.dp))
                PrimaryButton(if (saving) "Cropping..." else "Crop", Modifier.weight(1f), enabled = !saving) {
                    if (inLibrary) ask = true else save(false)
                }
            }
        }
    }

    if (ask) SaveChoiceSheet("Save the cropped PDF", { ask = false }, { save(true) }, { save(false) })
}
