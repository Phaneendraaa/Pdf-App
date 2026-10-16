package com.phani.pdfkit.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.IconBtn
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.SheetOption
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import com.phani.pdfkit.util.FileShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ViewerScreen(path: String, initialTool: String?, onBack: () -> Unit, onUnlock: (DocFile) -> Unit, onReplace: (String) -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val doc = remember(path) { Graph.library.find(path) }
    var more by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(doc?.name ?: File(path).name, onBack = onBack) {
            IconBtn(PkIcons.Search, onClick = { Toaster.show("Search inside PDFs arrives in a later update") })
            IconBtn(PkIcons.DotsV, size = 20.dp, onClick = { more = true })
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
            when {
                doc == null -> EmptyState(PkIcons.Info, "File not found", "It may have been moved or deleted.")
                doc.isImage -> ZoomBox(Modifier.fillMaxSize()) {
                    AsyncImage(File(doc.path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                }
                else -> PdfPages(doc, initialTool, onUnlock, onReplace)
            }
        }
    }

    if (more && doc != null) {
        val blue = tint(0xFF2463EB, 0xFFEEF3FF, c.isDark)
        PkSheet({ more = false }, doc.name) {
            SheetOption(PkIcons.Share, blue.fg, blue.bg, "Share") { more = false; FileShare.share(ctx, listOf(doc)) }
            SheetOption(PkIcons.ArrowRight, blue.fg, blue.bg, "Open with another app") { more = false; FileShare.openExternal(ctx, doc) }
        }
    }
}

@Composable
private fun PdfPages(doc: DocFile, initialTool: String?, onUnlock: (DocFile) -> Unit, onReplace: (String) -> Unit) {
    val c = Pk.colors
    val opened by produceState<PdfDoc.Open?>(null, doc.path) { value = withContext(Dispatchers.IO) { PdfDoc.open(doc.file) } }
    val pdf = (opened as? PdfDoc.Open.Ok)?.doc
    DisposableEffect(pdf) { onDispose { pdf?.close() } }

    when (opened) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue) }
        PdfDoc.Open.Locked -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(PkIcons.Lock, "This PDF is password protected", "Unlock it with its password to view and edit it.")
            PrimaryButton("Unlock PDF", Modifier.padding(horizontal = 40.dp)) { onUnlock(doc) }
        }
        PdfDoc.Open.Broken -> EmptyState(PkIcons.Info, "Can't open this file", "The PDF may be damaged or in an unsupported format.")
        is PdfDoc.Open.Ok -> pdf?.let { Pages(it, doc.file, initialTool, onReplace) }
    }
}

@Composable
private fun Pages(pdf: PdfDoc, file: File, initialTool: String?, onReplace: (String) -> Unit) {
    val c = Pk.colors
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tool by remember { mutableStateOf(initialTool) }
    var pagesSheet by remember { mutableStateOf(false) }
    val current by remember { derivedStateOf { (list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 300) 1 else 0).coerceAtMost(pdf.pageCount - 1) + 1 } }

    tool?.let { t ->
        com.phani.pdfkit.ui.edit.PageEditor(
            pdf = pdf, file = file, page = current - 1, initialTool = t,
            onClose = { tool = null },
            onSaved = { out -> tool = null; onReplace(out.absolutePath) },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val px = with(LocalDensity.current) { (maxWidth.toPx() * 1.6f).toInt().coerceIn(600, 2000) }
        ZoomBox(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(pdf.ratios) { i, ratio ->
                    val bmp by produceState<android.graphics.Bitmap?>(null, i, px) { value = pdf.render(i, px) }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(ratio).shadow(6.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                            ?: CircularProgressIndicator(Modifier.size(24.dp), color = c.blue.copy(alpha = 0.5f), strokeWidth = 2.dp)
                    }
                }
            }
        }
        Text(
            "$current / ${pdf.pageCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(99.dp)).background(Color(0x8C101217)).padding(horizontal = 12.dp, vertical = 5.dp),
        )
        val (hint, dismissHint) = com.phani.pdfkit.util.rememberHint("viewer_tools")
        com.phani.pdfkit.ui.components.HintBanner(
            hint && tool == null, PkIcons.Pencil, "Tap Highlight, Draw, Text or Sign to edit the page you're on.",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 96.dp), dismissHint,
        )
        androidx.compose.runtime.LaunchedEffect(tool) { if (tool != null) dismissHint() }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 18.dp).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(c.card).padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ToolBtn(PkIcons.Highlight, "Highlight", tool == "highlight") { tool = toggle(tool, "highlight") }
            ToolBtn(PkIcons.Pencil, "Draw", tool == "draw") { tool = toggle(tool, "draw") }
            ToolBtn(PkIcons.TextT, "Text", tool == "text") { tool = toggle(tool, "text") }
            ToolBtn(PkIcons.Signature, "Sign", tool == "sign") { tool = toggle(tool, "sign") }
            ToolBtn(PkIcons.Pages, "Pages", false) { pagesSheet = true }
        }
    }


    if (pagesSheet) PkSheet({ pagesSheet = false }, "Pages") {
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxWidth().padding(horizontal = 6.dp).heightIn(max = 520.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pdf.pageCount) { i ->
                val bmp by produceState<android.graphics.Bitmap?>(null, i) { value = pdf.render(i, 240) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    pagesSheet = false; scope.launch { list.animateScrollToItem(i) }
                }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(pdf.ratios[i]).clip(RoundedCornerShape(4.dp)).background(Color.White)
                            .border(1.5.dp, if (i + 1 == current) c.blue else c.line, RoundedCornerShape(4.dp)),
                    ) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } }
                    Text("${i + 1}", fontSize = 12.5.sp, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private fun toggle(cur: String?, t: String) = if (cur == t) null else t

@Composable
private fun ToolBtn(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Pk.colors
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) c.blueTint else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) c.blue else c.ink, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (on) c.blue else c.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Two-finger pinch zoom + pan; double tap toggles 2x. One-finger scroll still reaches the content. */
@Composable
fun ZoomBox(modifier: Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(scale, label = "zoom")
    BoxWithConstraints(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.05f) { scale = 1f; offX = 0f; offY = 0f } else scale = 2f
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val pan = event.calculatePan()
                            val maxX = size.width * (scale - 1) / 2f
                            val maxY = size.height * (scale - 1) / 2f
                            offX = (offX + pan.x).coerceIn(-maxX, maxX)
                            offY = (offY + pan.y).coerceIn(-maxY, maxY)
                            if (scale <= 1f) { offX = 0f; offY = 0f }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = shown; scaleY = shown; translationX = offX; translationY = offY }) { content() }
    }
}
