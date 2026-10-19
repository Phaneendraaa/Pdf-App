package com.phani.pdfkit.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.theme.Pk
import java.io.File

private const val INK = 0xFF1A2B6B.toInt()

object SignatureStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "signature.png")
    fun load(ctx: Context): Bitmap? = file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    fun save(ctx: Context, b: Bitmap) { file(ctx).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
}

/** Dialog to make a signature or initials: draw it, type it, or take it from a photo of paper. */
@Composable
fun SignaturePad(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val c = Pk.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var mode by remember { mutableStateOf("Draw") }
    var typed by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch { photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { fromPhoto(ctx, u) } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your signature", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                if (mode == "Draw") Text("Clear", color = c.blue, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { strokes.clear() }.padding(8.dp))
            }
            com.phani.pdfkit.ui.components.Segmented(listOf("Draw", "Type", "Photo"), mode, Modifier.padding(top = 10.dp)) { mode = it }
            val area = Modifier.padding(top = 12.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, c.line, RoundedCornerShape(14.dp))
            when (mode) {
                "Draw" -> Box(
                    area.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current.clear(); current += it },
                            onDragEnd = { if (current.size > 1) strokes += current.toList(); current.clear() },
                            onDragCancel = { current.clear() },
                        ) { ch, _ -> ch.consume(); current += ch.position }
                    },
                ) {
                    Text("Sign here", color = Color(0xFFB8BCC4), fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                    Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 40.dp).fillMaxWidth().height(1.dp).background(Color(0xFFE4E7EC)))
                    Canvas(Modifier.matchParentSize()) {
                        (strokes + listOf(current.toList())).filter { it.size > 1 }.forEach { pts ->
                            val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(p, Color(INK), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                "Type" -> Column {
                    androidx.compose.material3.OutlinedTextField(
                        typed, { typed = it.take(40) }, singleLine = true, modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        placeholder = { Text("Your name or initials", color = c.ink3) }, shape = RoundedCornerShape(12.dp),
                    )
                    Box(area.height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            typed.ifBlank { "Your name" }, color = if (typed.isBlank()) Color(0xFFB8BCC4) else Color(INK), fontSize = 40.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive, maxLines = 1,
                        )
                    }
                }
                else -> Box(area.clickable { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                    val p = photo
                    if (p == null) Text("Tap to pick a photo of your signature on white paper", color = c.ink2, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                    else androidx.compose.foundation.Image(p.asImageBitmap(), null, Modifier.padding(16.dp).fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(14.dp))
                val ok = when (mode) { "Draw" -> strokes.isNotEmpty(); "Type" -> typed.isNotBlank(); else -> photo != null }
                PrimaryButton("Use signature", Modifier.weight(1f), enabled = ok) {
                    when (mode) {
                        "Draw" -> render(strokes)?.let(onDone)
                        "Type" -> onDone(renderTyped(typed.trim()))
                        else -> photo?.let(onDone)
                    }
                }
            }
        }
    }
}

/** Typed name in a handwriting-style font, tightly cropped, transparent background. */
private fun renderTyped(text: String): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; textSize = 140f
        typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
    }
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val pad = 16
    val bmp = Bitmap.createBitmap(bounds.width() + pad * 2, bounds.height() + pad * 2, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bmp).drawText(text, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)
    return bmp
}

/** Signature from a photo: paper becomes transparent, ink becomes dark blue, cropped to the ink. */
private fun fromPhoto(ctx: Context, uri: android.net.Uri): Bitmap? {
    val raw = com.phani.pdfkit.pdf.Bitmaps.decode(ctx, uri, 1600) ?: return null
    val g = com.phani.pdfkit.pdf.Filters.apply(raw, "Gray")
    if (g !== raw) raw.recycle()
    val w = g.width; val h = g.height
    val px = IntArray(w * h); g.getPixels(px, 0, w, 0, 0, w, h); g.recycle()
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        val l = (px[y * w + x] and 255) / 255f
        val a = ((0.72f - l) / 0.32f).coerceIn(0f, 1f)
        px[y * w + x] = ((a * 255).toInt() shl 24) or (INK and 0xFFFFFF)
        if (a > 0.25f) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
    }
    if (maxX < 0) return null
    val pad = 10
    val x0 = (minX - pad).coerceAtLeast(0); val y0 = (minY - pad).coerceAtLeast(0)
    val x1 = (maxX + pad).coerceAtMost(w - 1); val y1 = (maxY + pad).coerceAtMost(h - 1)
    val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    val cut = Bitmap.createBitmap(full, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    if (cut !== full) full.recycle()
    return cut
}

private fun render(strokes: List<List<Offset>>): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = 12f
    val minX = all.minOf { it.x } - pad; val minY = all.minOf { it.y } - pad
    val w = (all.maxOf { it.x } + pad - minX).toInt().coerceAtLeast(10)
    val h = (all.maxOf { it.y } + pad - minY).toInt().coerceAtLeast(10)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK; style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.filter { it.size > 1 }.forEach { pts ->
        val p = android.graphics.Path().apply { moveTo(pts[0].x - minX, pts[0].y - minY); pts.drop(1).forEach { lineTo(it.x - minX, it.y - minY) } }
        cv.drawPath(p, paint)
    }
    return bmp
}
