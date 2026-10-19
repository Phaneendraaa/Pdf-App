package com.phani.pdfkit.ui.edit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.phani.pdfkit.pdf.Frac
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.pdf.PdfIndex
import com.phani.pdfkit.pdf.Redact
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drag to black out anything; "Mask Aadhaar" hides the first 8 digits of every Aadhaar number;
 * "Find text" blacks out every match. Saving burns the boxes in, so the hidden text is truly gone.
 */
@Composable
fun RedactEditor(pdf: PdfDoc, file: File, startPage: Int, onClose: () -> Unit, onSaved: (File) -> Unit) {
    val c = Pk.colors
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))) }
    val boxes = remember { mutableStateMapOf<Int, List<Frac>>() }
    val history = remember { mutableStateListOf<Pair<Int, List<Frac>>>() }
    var drawing by remember { mutableStateOf<Frac?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, page) { value = pdf.render(page, 1400) }
    val ratio = pdf.ratios.getOrElse(page) { 0.707f }
    val total = boxes.values.sumOf { it.size }
    LaunchedEffect(Unit) { Toaster.show("Drag over anything you want to hide") }
    BackHandler { onClose() }

    fun add(map: Map<Int, List<Frac>>) {
        map.forEach { (p, l) -> if (l.isNotEmpty()) { history += p to boxes[p].orEmpty(); boxes[p] = boxes[p].orEmpty() + l } }
    }

    fun save() {
        busy = "Redacting..."
        scope.launch {
            val lib = Graph.library
            val base = file.name.replace(Regex("^in_\\d+_"), "").substringBeforeLast('.')
            val out = lib.newFile(base + "_redacted")
            val ok = runCatching { Redact.apply(file, boxes.toMap(), out) {} }.isSuccess
            busy = null
            if (!ok) { out.delete(); Toaster.show("Couldn't redact this PDF") }
            else { lib.refresh(); Toaster.show("Saved as ${out.name}. Hidden parts are removed for good."); onSaved(out) }
        }
    }

    Column(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
        Row(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Redact", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page > 0) { page-- }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronLeft, "Previous page", tint = if (page > 0) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
            Text("${page + 1} / ${pdf.pageCount}", fontSize = 13.5.sp, color = c.ink2)
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page < pdf.pageCount - 1) { page++ }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronRight, "Next page", tint = if (page < pdf.pageCount - 1) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val w = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
            val h = w / ratio
            val density = LocalDensity.current
            val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
            Box(
                Modifier.size(w, h).shadow(8.dp, RoundedCornerShape(3.dp)).background(Color.White)
                    .pointerInput(page) {
                        var start = Offset.Zero
                        detectDragGestures(
                            onDragStart = { o -> start = o; drawing = null },
                            onDragEnd = {
                                drawing?.takeIf { it.w > 0.01f && it.h > 0.005f }?.let { add(mapOf(page to listOf(it))) }
                                drawing = null
                            },
                            onDragCancel = { drawing = null },
                        ) { ch, _ ->
                            ch.consume()
                            val p = ch.position
                            val x0 = min(start.x, p.x).coerceIn(0f, wPx) / wPx; val x1 = max(start.x, p.x).coerceIn(0f, wPx) / wPx
                            val y0 = min(start.y, p.y).coerceIn(0f, hPx) / hPx; val y1 = max(start.y, p.y).coerceIn(0f, hPx) / hPx
                            drawing = Frac(x0, y0, x1 - x0, y1 - y0)
                        }
                    }
                    .pointerInput(page) {
                        detectTapGestures { o ->
                            // Tap a box to remove it.
                            val x = o.x / wPx; val y = o.y / hPx
                            val list = boxes[page].orEmpty()
                            val hit = list.lastOrNull { x in it.x..(it.x + it.w) && y in it.y..(it.y + it.h) }
                            if (hit != null) { history += page to list; boxes[page] = list - hit }
                        }
                    },
            ) {
                Crossfade(bmp, label = "redactPage") { img ->
                    if (img != null) Image(img.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue, modifier = Modifier.size(24.dp)) }
                }
                Canvas(Modifier.fillMaxSize()) {
                    boxes[page].orEmpty().forEach { f ->
                        drawRect(Color.Black, Offset(f.x * size.width, f.y * size.height), Size(f.w * size.width, f.h * size.height))
                    }
                    drawing?.let { f ->
                        drawRect(Color(0x66000000), Offset(f.x * size.width, f.y * size.height), Size(f.w * size.width, f.h * size.height))
                        drawRect(c.red, Offset(f.x * size.width, f.y * size.height), Size(f.w * size.width, f.h * size.height), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Mask Aadhaar", Modifier.weight(1f)) {
                    busy = "Looking for Aadhaar numbers..."
                    scope.launch {
                        val found = runCatching { Redact.findAadhaar(file) }.getOrDefault(emptyMap())
                        busy = null
                        val n = found.values.sumOf { it.size }
                        if (n == 0) Toaster.show("No Aadhaar number found. Drag over it to hide it.")
                        else { add(found); found.keys.minOrNull()?.let { page = it }; Toaster.show("Hid the first 8 digits in $n place${if (n == 1) "" else "s"}") }
                    }
                }
                Chip("Find text", Modifier.weight(1f)) { finding = true }
                Chip("Undo", Modifier.weight(0.7f), enabled = history.isNotEmpty()) {
                    val (p, prev) = history.removeAt(history.lastIndex)
                    boxes[p] = prev; page = p
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(14.dp))
                PrimaryButton(busy ?: if (total == 0) "Draw boxes to hide" else "Redact & save copy", Modifier.weight(1f), enabled = busy == null && total > 0) { save() }
            }
        }
    }

    if (finding) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finding = false },
            containerColor = c.card,
            title = { Text("Hide all matches", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    q, { q = it }, singleLine = true, placeholder = { Text("e.g. a phone number or name", color = c.ink3) },
                    keyboardOptions = KeyboardOptions.Default, shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finding = false
                    val query = q.trim(); if (query.isEmpty()) return@TextButton
                    busy = "Searching..."
                    scope.launch {
                        val pages = withContext(Dispatchers.IO) { runCatching { PdfIndex.build(file) }.getOrDefault(emptyList()) }
                        val found = pages.flatMap { it.find(query) }.groupBy({ it.page }, { it.rects }).mapValues { (_, v) -> v.flatten() }
                        busy = null
                        val n = found.values.sumOf { it.size }
                        if (n == 0) Toaster.show(if (pages.all { it.text.isBlank() }) "This looks like a scan. Drag over the text to hide it." else "\"$query\" not found")
                        else { add(found); found.keys.minOrNull()?.let { page = it }; Toaster.show("Hid $n match${if (n == 1) "" else "es"}") }
                    }
                }) { Text("Hide", color = c.blue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { finding = false }) { Text("Cancel", color = c.ink2) } },
        )
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Pk.colors
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).background(c.card)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c.ink else c.ink3, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}
