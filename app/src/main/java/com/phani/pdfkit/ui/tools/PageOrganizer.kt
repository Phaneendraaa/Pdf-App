package com.phani.pdfkit.ui.tools

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import android.graphics.Matrix
import com.phani.pdfkit.pdf.PdfTools
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pages being organized. Keys are stable ids; each maps to a source page (or -1 for a blank page) and a rotation. */
class PageItems {
    val order = mutableStateListOf<Int>()
    private val src = mutableStateMapOf<Int, Int>()
    private val rot = mutableStateMapOf<Int, Int>()
    private var next = 0

    fun reset(n: Int) {
        order.clear(); src.clear(); rot.clear()
        for (i in 0 until n) { order += i; src[i] = i }
        next = n
    }
    fun source(k: Int) = src[k] ?: -1
    fun rotation(k: Int) = rot[k] ?: 0
    fun rotate(k: Int) { rot[k] = (rotation(k) + 90) % 360 }
    fun duplicate(k: Int) { val key = next++; src[key] = source(k); rot[key] = rotation(k); order.add(order.indexOf(k) + 1, key) }
    fun addBlank(after: Int?): Int {
        val key = next++; src[key] = -1
        val at = after?.let { order.indexOf(it) + 1 } ?: order.size
        order.add(at.coerceIn(0, order.size), key)
        return key
    }
    fun remove(k: Int) { order.remove(k) }
    fun ops(): List<PdfTools.PageOp> = order.map { PdfTools.PageOp(source(it), rotation(it)) }
}

/** Thumbnail grid of a PDF's pages: long-press and drag to move; tap a page to rotate, duplicate, add a blank page after it, or delete it. */
@Composable
fun PageOrganizer(file: File, items: PageItems) {
    val c = Pk.colors
    val haptic = LocalHapticFeedback.current
    val opened by produceState<PdfDoc.Open?>(null, file) { value = withContext(Dispatchers.IO) { PdfDoc.open(file) } }
    val pdf = (opened as? PdfDoc.Open.Ok)?.doc
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    if (pdf == null) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            if (opened == null) CircularProgressIndicator(color = c.blue)
            else Text("Can't preview this PDF's pages.", color = c.ink2, fontSize = 13.5.sp)
        }
        return
    }
    val order = items.order
    val grid = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Int?>(null) }

    LazyVerticalGrid(
        GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxWidth().height(440.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(order, key = { _, k -> k }) { pos, key ->
            val dragging = dragKey == key
            val isSel = selected == key
            val lift by animateFloatAsState(if (dragging) 1.07f else if (isSel) 1.03f else 1f, label = "lift")
            val source = items.source(key)
            val rotation = items.rotation(key)
            val bmp by produceState<Bitmap?>(null, source, rotation) {
                value = if (source < 0) null else pdf.render(source, 260)?.let { b ->
                    if (rotation == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
                }
            }
            val baseRatio = if (source >= 0) pdf.ratios.getOrElse(source) { 0.707f } else 0.707f
            val ratio = if (rotation % 180 != 0) 1f / baseRatio else baseRatio
            Box(
                Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 2f else 0f)
                    .graphicsLayer { scaleX = lift; scaleY = lift; if (dragging) { translationX = dragOffset.x; translationY = dragOffset.y } }
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = key; dragOffset = Offset.Zero; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { dragKey = null; dragOffset = Offset.Zero },
                            onDragCancel = { dragKey = null; dragOffset = Offset.Zero },
                        ) { ch, amount ->
                            ch.consume(); dragOffset += amount
                            val info = grid.layoutInfo.visibleItemsInfo
                            val cur = info.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                            val center = cur.offset.toOffset() + dragOffset + Offset(cur.size.width / 2f, cur.size.height / 2f)
                            val target = info.firstOrNull {
                                it.key != key && center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                                    center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
                            }
                            if (target != null) {
                                order.add(target.index, order.removeAt(cur.index))
                                dragOffset += (cur.offset - target.offset).toOffset()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    .aspectRatio(ratio)
                    .shadow(if (dragging) 14.dp else 2.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp)).background(Color.White)
                    .border(if (isSel) 2.5.dp else 1.dp, if (isSel) c.blue else c.line, RoundedCornerShape(6.dp))
                    .clickable { selected = if (isSel) null else key },
            ) {
                if (source < 0) Text("Blank page", color = Color(0xFF9AA0AB), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                else bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                Box(
                    Modifier.align(Alignment.BottomStart).padding(5.dp).size(22.dp).clip(CircleShape).background(if (dragging || isSel) c.blue else Color(0x9E101217)),
                    contentAlignment = Alignment.Center,
                ) { Text("${pos + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                if (order.size > 1) Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
                        .clickable { if (selected == key) selected = null; items.remove(key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(PkIcons.Close, "Remove page", tint = Color(0xFF101217), modifier = Modifier.size(13.dp)) }
            }
        }
    }

    val sel = selected?.takeIf { it in order }
    Crossfade(sel, label = "pageActions") { k ->
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (k == null) {
                ActionPill(PkIcons.Plus, "Add blank page") {
                    items.addBlank(null); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                Text("Tap a page for more", color = c.ink2, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp))
            } else {
                ActionPill(PkIcons.Rotate, "Rotate") { items.rotate(k) }
                ActionPill(PkIcons.Copy, "Duplicate") { items.duplicate(k) }
                ActionPill(PkIcons.Plus, "Blank after") { items.addBlank(k) }
                if (order.size > 1) ActionPill(PkIcons.Trash, "Delete", danger = true) { items.remove(k); selected = null }
            }
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Pk.colors
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(if (danger) c.redTint else c.blueTint).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) c.red else c.blue, modifier = Modifier.size(16.dp))
        Text(label, color = if (danger) c.red else c.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}
