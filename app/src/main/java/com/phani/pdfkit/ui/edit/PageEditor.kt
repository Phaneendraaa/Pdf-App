package com.phani.pdfkit.ui.edit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.pdf.ImageMark
import com.phani.pdfkit.pdf.InkStroke
import com.phani.pdfkit.pdf.PageEdits
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.pdf.TextMark
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.tools.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val INKS = listOf(0xFF101217.toInt(), 0xFF2463EB.toInt(), 0xFFE11D48.toInt())
private const val HIGHLIGHT = 0xFFFFD43B.toInt()

private class Mark(text: String, x: Float, y: Float, size: Float, color: Int) {
    var text by mutableStateOf(text); var x by mutableStateOf(x); var y by mutableStateOf(y)
    var size by mutableStateOf(size); var color by mutableStateOf(color)
}

/** Single-page editor: ink, highlight, text and signature, flattened into a new PDF on save. */
@Composable
fun PageEditor(pdf: PdfDoc, file: File, page: Int, initialTool: String, onClose: () -> Unit, onSaved: (File) -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tool by remember { mutableStateOf(initialTool) }
    var ink by remember { mutableStateOf(INKS[1]) }
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val live = remember { mutableStateListOf<Pair<Float, Float>>() }
    val texts = remember { mutableStateListOf<Mark>() }
    var sig by remember { mutableStateOf<Bitmap?>(null) }
    var sigX by remember { mutableStateOf(0.55f) }
    var sigY by remember { mutableStateOf(0.78f) }
    var sigW by remember { mutableStateOf(0.3f) }
    var padOpen by remember { mutableStateOf(false) }
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var saving by remember { mutableStateOf(false) }
    var askSave by remember { mutableStateOf(false) }
    val inLibrary = file.absolutePath.startsWith(Graph.library.dir.absolutePath)
    val history = remember { mutableStateListOf<String>() } // "s", "t", "g", "e<id>" for undo order
    val redo = remember { mutableStateListOf<Pair<String, Any?>>() }
    fun record(h: String) { history += h; redo.clear() }
    val lineEdits = remember { androidx.compose.runtime.mutableStateMapOf<Int, LineEdit>() }
    var editing by remember { mutableStateOf<com.phani.pdfkit.pdf.TextBlock?>(null) }
    val layout by produceState<com.phani.pdfkit.pdf.TextEdit.Page?>(null, page) {
        value = withContext(Dispatchers.IO) { runCatching { com.phani.pdfkit.pdf.TextEdit.read(file, page) }.getOrNull() }
    }
    LaunchedEffect(tool, layout) {
        if (tool != "edit") return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        Toaster.show(l.problem ?: "Tap any line of text to edit it")
    }

    LaunchedEffect(tool) {
        if (tool == "sign" && sig == null) {
            val saved = withContext(Dispatchers.IO) { SignatureStore.load(ctx) }
            if (saved != null) { sig = saved; record("g") } else padOpen = true
        }
        if (tool == "text" && texts.isEmpty()) Toaster.show("Tap the page where you want text")
    }
    val dirty = strokes.isNotEmpty() || texts.isNotEmpty() || sig != null || lineEdits.isNotEmpty()
    BackHandler { onClose() }

    /** [replace] overwrites this PDF with the edited one; otherwise the edit goes to a new copy. */
    fun save(replace: Boolean) {
        saving = true
        val s = strokes.toList()
        val t = texts.map { TextMark(it.text, it.x, it.y, it.size, it.color) }
        val g = sig?.let { listOf(ImageMark(it, sigX, sigY, sigW)) } ?: emptyList()
        val blocksById = layout?.blocks?.associateBy { it.id }.orEmpty()
        val te = lineEdits.mapNotNull { (id, e) ->
            blocksById[id]?.let { com.phani.pdfkit.pdf.TextEditOp(it, e.text, e.sizePt, e.bold, e.color, e.background) }
        }
        scope.launch {
            val lib = Graph.library
            val out = if (replace) lib.tempFile() else {
                val suffix = if (g.isNotEmpty()) "_signed" else "_edited"
                lib.newFile(displayName(file).substringBeforeLast('.').removeSuffix("_edited").removeSuffix("_signed") + suffix)
            }
            val ok = withContext(Dispatchers.IO) { runCatching { PageEdits.apply(file, page, s, t, g, out, textEdits = te) }.isSuccess }
            if (!ok) { out.delete(); saving = false; Toaster.show("Couldn't save the changes"); return@launch }
            if (replace) {
                val done = lib.replaceWith(file, out)
                saving = false
                if (done) { Toaster.show("Saved to ${file.name}"); onSaved(file) } else { out.delete(); Toaster.show("Couldn't update the file") }
            } else {
                lib.refresh(); saving = false
                Toaster.show("Saved as ${out.name}"); onSaved(out)
            }
        }
    }

    val bmp by produceState<Bitmap?>(null, page) { value = pdf.render(page, 1400) }
    val ratio = pdf.ratios.getOrElse(page) { 0.707f }

    Column(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
        Row(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Editing page ${page + 1}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(enabled = history.isNotEmpty()) {
                    val h = history.removeAt(history.lastIndex)
                    when {
                        h == "s" -> if (strokes.isNotEmpty()) redo += h to strokes.removeAt(strokes.lastIndex)
                        h == "t" -> if (texts.isNotEmpty()) redo += h to texts.removeAt(texts.lastIndex)
                        h == "g" -> { redo += h to sig; sig = null }
                        h.startsWith("e") -> h.drop(1).toIntOrNull()?.let { id -> redo += h to lineEdits.remove(id) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(PkIcons.Rotate, "Undo", tint = if (history.isNotEmpty()) c.ink else c.ink3, modifier = Modifier.size(20.dp).graphicsFlip()) }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(enabled = redo.isNotEmpty()) {
                    val (h, v) = redo.removeAt(redo.lastIndex)
                    when {
                        h == "s" -> (v as? InkStroke)?.let { strokes += it }
                        h == "t" -> (v as? Mark)?.let { texts += it }
                        h == "g" -> (v as? Bitmap)?.let { sig = it }
                        h.startsWith("e") -> h.drop(1).toIntOrNull()?.let { id -> (v as? LineEdit)?.let { lineEdits[id] = it } }
                    }
                    history += h
                },
                contentAlignment = Alignment.Center,
            ) { Icon(PkIcons.Rotate, "Redo", tint = if (redo.isNotEmpty()) c.ink else c.ink3, modifier = Modifier.size(20.dp)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val maxW = maxWidth; val maxH = maxHeight
            val w = if (maxW / maxH > ratio) maxH * ratio else maxW
            val h = w / ratio
            val density = LocalDensity.current
            val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
            Box(
                Modifier.size(w, h).shadow(8.dp, RoundedCornerShape(3.dp)).background(Color.White)
                    .pointerInput(tool) {
                        if (tool == "draw" || tool == "highlight") {
                            detectDragGestures(
                                onDragStart = { o -> live.clear(); live += (o.x / wPx) to (o.y / hPx) },
                                onDragEnd = {
                                    if (live.size > 1) { strokes += InkStroke(live.toList(), tool == "highlight", if (tool == "highlight") HIGHLIGHT else ink); record("s") }
                                    live.clear()
                                },
                                onDragCancel = { live.clear() },
                            ) { ch, _ -> ch.consume(); live += (ch.position.x / wPx).coerceIn(0f, 1f) to (ch.position.y / hPx).coerceIn(0f, 1f) }
                        } else if (tool == "text") {
                            detectTapGestures { o -> textAt = Offset(o.x / wPx, o.y / hPx) }
                        }
                    },
            ) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    ?: CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = c.blue)
                Canvas(Modifier.fillMaxSize()) {
                    fun drawInk(pts: List<Pair<Float, Float>>, hl: Boolean, color: Int) {
                        if (pts.size < 2) return
                        val p = Path().apply { moveTo(pts[0].first * size.width, pts[0].second * size.height); pts.drop(1).forEach { lineTo(it.first * size.width, it.second * size.height) } }
                        drawPath(
                            p, Color(color).copy(alpha = if (hl) 0.38f else 1f),
                            style = Stroke(width = size.width * if (hl) 0.025f else 0.0045f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    strokes.forEach { drawInk(it.points, it.highlight, it.color) }
                    if (tool == "draw" || tool == "highlight") drawInk(live.toList(), tool == "highlight", if (tool == "highlight") HIGHLIGHT else ink)
                }
                layout?.let { l ->
                    TextEditLayer(l.blocks, lineEdits, l.widthPt, wPx, hPx, active = tool == "edit") { editing = it }
                }
                texts.forEach { m ->
                    val fontPx = m.size * wPx
                    Text(
                        m.text, color = Color(m.color), fontSize = with(density) { fontPx.toSp() },
                        modifier = Modifier
                            .offset { IntOffset((m.x * wPx).roundToInt(), (m.y * hPx).roundToInt()) }
                            .border(1.dp, if (tool == "text") c.blue.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(3.dp))
                            .pointerInput(m) {
                                detectDragGestures { ch, d -> ch.consume(); m.x = (m.x + d.x / wPx).coerceIn(0f, 0.98f); m.y = (m.y + d.y / hPx).coerceIn(0f, 0.98f) }
                            },
                    )
                }
                sig?.let { s ->
                    val sw = sigW * wPx; val sh = sw * s.height / s.width
                    Image(
                        s.asImageBitmap(), "Signature",
                        Modifier.offset { IntOffset((sigX * wPx).roundToInt(), (sigY * hPx).roundToInt()) }
                            .size(with(density) { sw.toDp() }, with(density) { sh.toDp() })
                            .border(1.dp, if (tool == "sign") c.blue.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(4.dp))
                            .pointerInput(s) {
                                detectDragGestures { ch, d ->
                                    ch.consume()
                                    sigX = (sigX + d.x / wPx).coerceIn(0f, 1f - sigW)
                                    sigY = (sigY + d.y / hPx).coerceIn(0f, 1f - sh / hPx)
                                }
                            },
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                ToolChip(PkIcons.Edit, "Edit text", tool == "edit") { tool = "edit" }
                ToolChip(PkIcons.TextT, "Add text", tool == "text") { tool = "text" }
                ToolChip(PkIcons.Highlight, "Highlight", tool == "highlight") { tool = "highlight" }
                ToolChip(PkIcons.Pencil, "Draw", tool == "draw") { tool = "draw" }
                ToolChip(PkIcons.Signature, "Sign", tool == "sign") { tool = "sign" }
            }
            when (tool) {
                "draw", "text" -> Row(Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    INKS.forEach { col ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                                .border(3.dp, if (ink == col) c.blueTint else Color.Transparent, CircleShape)
                                .clickable { ink = col; if (tool == "text") texts.lastOrNull()?.color = col },
                        )
                    }
                }
                "sign" -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", fontSize = 13.5.sp, color = c.ink2)
                    Slider(
                        sigW, { sigW = it }, valueRange = 0.12f..0.6f, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        colors = SliderDefaults.colors(thumbColor = c.blue, activeTrackColor = c.blue, inactiveTrackColor = c.blueTint),
                    )
                    Text("New", color = c.blue, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { padOpen = true }.padding(8.dp))
                }
                else -> Spacer(Modifier.size(10.dp))
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(14.dp))
                PrimaryButton(if (saving) "Saving..." else "Save", Modifier.weight(1f), enabled = dirty && !saving) {
                    if (inLibrary) askSave = true else save(false)
                }
            }
        }
    }

    if (askSave) {
        val blue = com.phani.pdfkit.ui.theme.tint(0xFF2463EB, 0xFFEEF3FF, c.isDark)
        val green = com.phani.pdfkit.ui.theme.tint(0xFF16A34A, 0xFFECF8F1, c.isDark)
        com.phani.pdfkit.ui.components.PkSheet({ askSave = false }, "Save your changes") {
            com.phani.pdfkit.ui.components.SheetOption(PkIcons.Check, blue.fg, blue.bg, "Update this PDF", "Replaces the current file, no extra copy") { askSave = false; save(true) }
            com.phani.pdfkit.ui.components.SheetOption(PkIcons.Copy, green.fg, green.bg, "Save as a new copy", "Keeps the original untouched") { askSave = false; save(false) }
        }
    }

    editing?.let { b ->
        val sampled = remember(b.id, bmp) { sampleColors(bmp, b) }
        EditLineSheet(b, lineEdits[b.id], sampled, { editing = null }) { e ->
            lineEdits[b.id] = e; record("e${b.id}"); editing = null
        }
    }

    if (padOpen) SignaturePad({ padOpen = false; if (sig == null) tool = "draw" }) { b ->
        padOpen = false
        scope.launch { withContext(Dispatchers.IO) { SignatureStore.save(ctx, b) } }
        if (sig == null) record("g")
        sig = b; sigW = 0.3f
    }

    textAt?.let { at ->
        var value by remember(at) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textAt = null }, containerColor = c.card, shape = RoundedCornerShape(20.dp),
            title = { Text("Add text", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value, { value = it }, singleLine = true, placeholder = { Text("Type here", color = c.ink3) }, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
                )
            },
            confirmButton = {
                TextButton(enabled = value.isNotBlank(), onClick = {
                    texts += Mark(value.trim(), at.x.coerceAtMost(0.9f), (at.y - 0.012f).coerceIn(0f, 0.97f), 0.032f, if (ink == INKS[2] || ink == INKS[1]) ink else INKS[0])
                    record("t"); textAt = null
                }) { Text("Add", color = c.blue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { textAt = null }) { Text("Cancel", color = c.ink2) } },
        )
    }
}

/** Mirrors the rotate icon so it reads as "undo". */
private fun Modifier.graphicsFlip() = this.graphicsLayer { scaleX = -1f }

@Composable
private fun ToolChip(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Pk.colors
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.blueTint else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) c.blue else c.ink, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (on) c.blue else c.ink, modifier = Modifier.padding(top = 3.dp))
    }
}
