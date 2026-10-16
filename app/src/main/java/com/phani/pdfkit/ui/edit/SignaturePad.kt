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

/** Full-screen dialog to draw a signature. Returns a transparent, tightly cropped bitmap. */
@Composable
fun SignaturePad(onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val c = Pk.colors
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Draw your signature", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text("Clear", color = c.blue, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { strokes.clear() }.padding(8.dp))
            }
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
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
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(14.dp))
                PrimaryButton("Use signature", Modifier.weight(1f), enabled = strokes.isNotEmpty()) { render(strokes)?.let(onDone) }
            }
        }
    }
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
