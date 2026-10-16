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

/** Thumbnail grid of a PDF's pages; long-press and drag to reorder, × to remove. Edits [order] in place. */
@Composable
fun PageOrganizer(file: File, order: SnapshotStateList<Int>) {
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
    val grid = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxWidth().height(470.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(order, key = { _, p -> p }) { pos, page ->
            val dragging = dragKey == page
            val lift by animateFloatAsState(if (dragging) 1.07f else 1f, label = "lift")
            val bmp by produceState<Bitmap?>(null, page) { value = pdf.render(page, 260) }
            Box(
                Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 2f else 0f)
                    .graphicsLayer { scaleX = lift; scaleY = lift; if (dragging) { translationX = dragOffset.x; translationY = dragOffset.y } }
                    .pointerInput(page) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragKey = page; dragOffset = Offset.Zero; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { dragKey = null; dragOffset = Offset.Zero },
                            onDragCancel = { dragKey = null; dragOffset = Offset.Zero },
                        ) { ch, amount ->
                            ch.consume(); dragOffset += amount
                            val info = grid.layoutInfo.visibleItemsInfo
                            val cur = info.firstOrNull { it.key == page } ?: return@detectDragGesturesAfterLongPress
                            val center = cur.offset.toOffset() + dragOffset + Offset(cur.size.width / 2f, cur.size.height / 2f)
                            val target = info.firstOrNull {
                                it.key != page && center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                                    center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
                            }
                            if (target != null) {
                                order.add(target.index, order.removeAt(cur.index))
                                dragOffset += (cur.offset - target.offset).toOffset()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    .aspectRatio(pdf.ratios.getOrElse(page) { 0.7f })
                    .shadow(if (dragging) 14.dp else 2.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp)).background(Color.White)
                    .border(1.dp, c.line, RoundedCornerShape(6.dp)),
            ) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                Box(
                    Modifier.align(Alignment.BottomStart).padding(5.dp).size(22.dp).clip(CircleShape).background(if (dragging) c.blue else Color(0x9E101217)),
                    contentAlignment = Alignment.Center,
                ) { Text("${pos + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                if (order.size > 1) Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
                        .clickable { order.remove(page) },
                    contentAlignment = Alignment.Center,
                ) { Icon(PkIcons.Close, "Remove page", tint = Color(0xFF101217), modifier = Modifier.size(13.dp)) }
            }
        }
    }
}
