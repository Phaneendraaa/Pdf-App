package com.phani.pdfkit.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.phani.pdfkit.pdf.Match
import com.phani.pdfkit.pdf.PdfIndex
import com.phani.pdfkit.pdf.PdfMeta
import com.phani.pdfkit.pdf.PageText
import com.phani.pdfkit.pdf.LinkBox
import com.phani.pdfkit.ui.components.Segmented
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.collectAsState
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
fun ViewerScreen(
    path: String, initialTool: String?, onBack: () -> Unit, onUnlock: (DocFile) -> Unit, onReplace: (String) -> Unit,
    onTool: (String, String) -> Unit = { _, _ -> },
) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val doc = remember(path) { Graph.library.find(path) }
    var more by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var ai by remember { mutableStateOf(com.phani.pdfkit.ai.NanoAi.State.Unavailable) }
    var summary by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var copyOpen by remember { mutableStateOf(false) }
    var pageNow by remember { mutableIntStateOf(0) }
    val settings by Graph.prefs.settings.collectAsState(initial = com.phani.pdfkit.data.Settings())
    val night = settings.nightReading
    LaunchedEffect(path) { if (doc?.isPdf == true) ai = com.phani.pdfkit.ai.NanoAi.state(ctx) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(doc?.name ?: File(path).name, onBack = onBack) {
            if (ai != com.phani.pdfkit.ai.NanoAi.State.Unavailable) IconBtn(PkIcons.Sparkle, tint = c.blue, label = "Summarize", onClick = { summary = true })
            if (doc?.isPdf == true) IconBtn(PkIcons.Search, tint = if (searching) c.blue else c.ink, label = "Search", onClick = { searching = !searching })
            IconBtn(PkIcons.DotsV, size = 20.dp, onClick = { more = true })
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
            when {
                doc == null -> EmptyState(PkIcons.Info, "File not found", "It may have been moved or deleted.")
                doc.isImage -> Box(Modifier.fillMaxSize()) {
                    ZoomBox(Modifier.fillMaxSize()) {
                        AsyncImage(File(doc.path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                    }
                    ImageActions(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp),
                        onToPdf = { onTool("jpg2pdf", doc.path) },
                        onOcr = { onTool("ocrimage", doc.path) },
                    )
                }
                doc.isDoc -> DocxPages(doc, onReplace)
                else -> PdfPages(doc, initialTool, searching, { searching = false }, { pageNow = it }, onUnlock, onReplace, night)
            }
        }
    }

    if (summary && doc != null) SummarySheet(doc, ai) { summary = false }
    if (copyOpen && doc != null) CopyTextSheet(doc, pageNow) { copyOpen = false }

    if (more && doc != null) {
        val blue = tint(0xFF2463EB, 0xFFEEF3FF, c.isDark)
        PkSheet({ more = false }, doc.name) {
            SheetOption(PkIcons.Share, blue.fg, blue.bg, "Share") { more = false; FileShare.share(ctx, listOf(doc)) }
            if (doc.isPdf && doc.path.startsWith(Graph.library.dir.absolutePath)) {
                val teal = tint(0xFF0D9488, 0xFFE9F8F6, c.isDark)
                SheetOption(PkIcons.Reorder, teal.fg, teal.bg, "Organize pages", "Rotate, add blank, duplicate, delete") { more = false; onTool("reorder", doc.path) }
                SheetOption(PkIcons.Hash, teal.fg, teal.bg, "Add page numbers") { more = false; onTool("pagenum", doc.path) }
                SheetOption(PkIcons.Compress, teal.fg, teal.bg, "Compress", "Make it smaller than 200 KB, 500 KB...") { more = false; onTool("compress", doc.path) }
                SheetOption(PkIcons.Crop, teal.fg, teal.bg, "Crop pages") { more = false; onTool("crop", doc.path) }
            }
            if (doc.isPdf) SheetOption(PkIcons.Moon, blue.fg, blue.bg, if (night) "Turn off night reading" else "Night reading", "Dark pages, easier on the eyes") {
                more = false; scope.launch { Graph.prefs.setNightReading(!night) }
            }
            if (doc.isPdf) SheetOption(PkIcons.Copy, blue.fg, blue.bg, "Select and copy text", "Text on the page you are reading") { more = false; copyOpen = true }
            if (doc.isPdf) SheetOption(PkIcons.Printer, blue.fg, blue.bg, "Print", "Pick pages in the print screen") { more = false; com.phani.pdfkit.util.PrintPdf.print(ctx, doc.file, doc.name) }
            SheetOption(PkIcons.ArrowRight, blue.fg, blue.bg, "Open with another app") { more = false; FileShare.openExternal(ctx, doc) }
            if (!doc.path.startsWith(Graph.library.dir.absolutePath)) {
                val green = tint(0xFF16A34A, 0xFFECF8F1, c.isDark)
                SheetOption(PkIcons.Download, green.fg, green.bg, "Save to Pdf Kit", "Keep a copy in your Files") {
                    more = false
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching { doc.file.copyTo(Graph.library.newFile(doc.name.substringBeforeLast('.'))).also { Graph.library.refreshBlocking() } }.getOrNull()
                        }
                        Toaster.show(if (saved != null) "Saved to Files" else "Couldn't save the file")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPages(
    doc: DocFile, initialTool: String?, searching: Boolean, onSearchClose: () -> Unit, onPage: (Int) -> Unit,
    onUnlock: (DocFile) -> Unit, onReplace: (String) -> Unit, night: Boolean = false,
) {
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
        is PdfDoc.Open.Ok -> pdf?.let { Pages(it, doc.file, initialTool, searching, onSearchClose, onPage, onReplace, night) }
    }
}

@Composable
private fun Pages(
    pdf: PdfDoc, file: File, initialTool: String?, searching: Boolean, onSearchClose: () -> Unit, onPage: (Int) -> Unit,
    onReplace: (String) -> Unit, night: Boolean = false,
) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var tool by remember { mutableStateOf(initialTool) }
    var pagesSheet by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    // Keep the screen awake while reading.
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val invert = remember {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(-0.9f, 0f, 0f, 0f, 235f, 0f, -0.9f, 0f, 0f, 235f, 0f, 0f, -0.9f, 0f, 235f, 0f, 0f, 0f, 1f, 0f)),
        )
    }
    val current by remember { derivedStateOf { (list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 300) 1 else 0).coerceAtMost(pdf.pageCount - 1) + 1 } }

    // Links and bookmarks are light; the text index is built only once search is opened.
    val meta by produceState<PdfMeta?>(null, file.path) { value = withContext(Dispatchers.IO) { runCatching { PdfIndex.meta(file) }.getOrNull() } }
    var wantIndex by remember { mutableStateOf(false) }
    val index by produceState<List<PageText>?>(null, wantIndex, file.path) {
        if (wantIndex) value = withContext(Dispatchers.IO) { runCatching { PdfIndex.build(file) }.getOrDefault(emptyList()) }
    }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var cur by remember { mutableIntStateOf(0) }
    val byPage = remember(matches) { matches.withIndex().groupBy { it.value.page } }

    LaunchedEffect(searching) {
        if (searching) wantIndex = true else { query = ""; matches = emptyList(); cur = 0 }
    }
    LaunchedEffect(query, index) {
        val q = query.trim()
        val idx = index
        if (q.length < 2 || idx == null) { matches = emptyList(); cur = 0; return@LaunchedEffect }
        delay(300)
        val found = withContext(Dispatchers.Default) { idx.flatMap { it.find(q) }.take(1000) }
        matches = found; cur = 0
        if (found.isNotEmpty()) list.animateScrollToItem(found[0].page)
    }
    fun step(d: Int) {
        if (matches.isEmpty()) return
        cur = (cur + d + matches.size) % matches.size
        scope.launch { list.animateScrollToItem(matches[cur].page) }
    }

    // Come back to the page you were reading.
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(pdf) {
        if (initialTool == null) {
            val p = Graph.prefs.lastPage(file.absolutePath)
            if (p in 1 until pdf.pageCount) list.scrollToItem(p)
        }
        restored = true
    }
    LaunchedEffect(current, restored) {
        onPage(current - 1)
        if (restored) { delay(700); Graph.prefs.setLastPage(file.absolutePath, current - 1) }
    }

    fun openLink(l: LinkBox) {
        val u = l.uri
        if (u != null) {
            val uri = runCatching { Uri.parse(u) }.getOrNull()
            if (uri?.scheme?.lowercase() in setOf("http", "https", "mailto", "tel")) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toaster.show("No app can open this link") }
            } else Toaster.show("This link type isn't supported")
        } else if (l.page >= 0) scope.launch { list.animateScrollToItem(l.page.coerceIn(0, pdf.pageCount - 1)) }
    }

    tool?.let { t ->
        if (t == "form") com.phani.pdfkit.ui.forms.FormFiller(
            pdf = pdf, file = file, startPage = current - 1,
            onClose = { tool = null },
            onSaved = { out -> tool = null; onReplace(out.absolutePath) },
            onNoFields = { tool = "text" },
        ) else if (t == "crop") com.phani.pdfkit.ui.edit.CropEditor(
            pdf = pdf, file = file, startPage = current - 1,
            onClose = { tool = null },
            onSaved = { out -> tool = null; onReplace(out.absolutePath) },
        ) else com.phani.pdfkit.ui.edit.PageEditor(
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
                    val hits = byPage[i]
                    val links = meta?.links?.get(i)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(ratio).shadow(6.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(if (night) Color(0xFF141414) else Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(bmp, label = "page") { b ->
                            if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = if (night) invert else null)
                            else CircularProgressIndicator(Modifier.size(24.dp), color = c.blue.copy(alpha = 0.5f), strokeWidth = 2.dp)
                        }
                        if (hits != null) Canvas(Modifier.fillMaxSize()) {
                            hits.forEach { (mi, m) ->
                                m.rects.forEach { r ->
                                    val pad = 1.5f
                                    drawRect(
                                        color = if (mi == cur) Color(0x88FF8A00) else Color(0x66FFD54A),
                                        topLeft = Offset(r.x * size.width - pad, r.y * size.height - pad),
                                        size = Size(r.w * size.width + pad * 2, r.h * size.height + pad * 2),
                                    )
                                }
                            }
                        }
                        if (links != null) BoxWithConstraints(Modifier.fillMaxSize()) {
                            links.forEach { l ->
                                Box(
                                    Modifier.offset(maxWidth * l.box.x, maxHeight * l.box.y)
                                        .size(maxWidth * l.box.w, maxHeight * l.box.h)
                                        .clickable { openLink(l) },
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            !searching, modifier = Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut(),
        ) {
            Text(
                "$current / ${pdf.pageCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(99.dp)).background(Color(0x8C101217)).clickable { goTo = true }.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        AnimatedVisibility(
            meta?.hasForm == true && !searching, modifier = Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut(),
        ) {
            Row(
                Modifier.padding(12.dp).clip(RoundedCornerShape(99.dp)).background(c.blue).clickable { tool = "form" }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Pencil, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text("Fill form", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
        AnimatedVisibility(
            searching, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val idx = index
                val status = when {
                    query.trim().length < 2 -> ""
                    idx == null -> "..."
                    matches.isEmpty() -> "None"
                    else -> "${cur + 1}/${matches.size}"
                }
                SearchBar(query, { query = it }, status, { step(-1) }, { step(1) }, onSearchClose)
                if (idx != null && idx.all { it.text.isBlank() }) Text(
                    "No searchable text. For scans, use Make Searchable in All Tools.", color = c.ink2, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.card).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        val (hint, dismissHint) = com.phani.pdfkit.util.rememberHint("viewer_tools")
        com.phani.pdfkit.ui.components.HintBanner(
            hint && tool == null && !searching, PkIcons.Pencil, "Tap Edit to change the text on this page, or Highlight, Draw and Sign.",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 96.dp), dismissHint,
        )
        androidx.compose.runtime.LaunchedEffect(tool) { if (tool != null) dismissHint() }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 18.dp).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(c.card).padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ToolBtn(PkIcons.Edit, "Edit", tool == "edit") { tool = toggle(tool, "edit") }
            ToolBtn(PkIcons.Highlight, "Highlight", tool == "highlight") { tool = toggle(tool, "highlight") }
            ToolBtn(PkIcons.Pencil, "Draw", tool == "draw") { tool = toggle(tool, "draw") }
            ToolBtn(PkIcons.Signature, "Sign", tool == "sign") { tool = toggle(tool, "sign") }
            ToolBtn(PkIcons.Pages, "Pages", false) { pagesSheet = true }
        }
    }

    if (goTo) {
        var value by remember { mutableStateOf("") }
        val go = {
            val n = value.toIntOrNull()
            if (n != null && n in 1..pdf.pageCount) { goTo = false; scope.launch { list.animateScrollToItem(n - 1) } }
            else Toaster.show("Pick a page from 1 to ${pdf.pageCount}")
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { goTo = false }, containerColor = c.card, shape = RoundedCornerShape(20.dp),
            title = { Text("Go to page", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value, { value = it.filter(Char::isDigit).take(5) }, singleLine = true,
                    placeholder = { Text("1 to ${pdf.pageCount}", color = c.ink3) }, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { go() }) { Text("Go", color = c.blue, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { goTo = false }) { Text("Cancel", color = c.ink2) } },
        )
    }

    if (pagesSheet) PkSheet({ pagesSheet = false }, "Pages") {
        val marks = meta?.outline.orEmpty()
        var tab by remember { mutableStateOf("Pages") }
        if (marks.isNotEmpty()) Segmented(listOf("Pages", "Bookmarks"), tab, Modifier.padding(bottom = 12.dp)) { tab = it }
        Crossfade(if (marks.isEmpty()) "Pages" else tab, Modifier.animateContentSize(), label = "pagesTab") { t ->
            if (t == "Pages") LazyVerticalGrid(
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
            } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(marks) { _, m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { pagesSheet = false; scope.launch { list.animateScrollToItem(m.page.coerceIn(0, pdf.pageCount - 1)) } }
                            .padding(start = (10 + 18 * m.level).dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PkIcons.Bookmark, null, tint = c.blue, modifier = Modifier.size(18.dp))
                        Text(
                            m.title, color = c.ink, fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("${m.page + 1}", color = c.ink2, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, status: String, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val c = Pk.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().height(52.dp).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PkIcons.Search, null, tint = c.ink3, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(c.blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search in this PDF", color = c.ink3, fontSize = 16.sp)
                    inner()
                }
            },
        )
        Text(status, fontSize = 12.5.sp, color = c.ink2)
        IconBtn(PkIcons.ChevronUp, size = 20.dp, label = "Previous match", onClick = onPrev)
        IconBtn(PkIcons.ChevronDown, size = 20.dp, label = "Next match", onClick = onNext)
        IconBtn(PkIcons.Close, tint = c.ink2, size = 18.dp, label = "Close search", onClick = onClose)
    }
}

/** Shows the text of the page you are reading so you can select part of it or copy it all. */
@Composable
private fun CopyTextSheet(doc: DocFile, page: Int, onDismiss: () -> Unit) {
    val c = Pk.colors
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val loaded by produceState<String?>(null, doc.path, page) {
        value = withContext(Dispatchers.IO) { runCatching { PdfIndex.pageText(doc.file, page) }.getOrDefault("") }
    }
    PkSheet(onDismiss, "Text on page ${page + 1}") {
        val text = loaded
        when {
            text == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue) }
            text.isBlank() -> EmptyState(PkIcons.Info, "No selectable text here", "This page looks like a picture. Use Text Recognition from All tools to copy its text.")
            else -> Column(Modifier.animateContentSize()) {
                Box(Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(c.surf).verticalScroll(rememberScrollState()).padding(14.dp)) {
                    SelectionContainer { Text(text, fontSize = 15.sp, color = c.ink, lineHeight = 22.sp) }
                }
                Text("Touch and hold the text to select just a part of it.", fontSize = 12.5.sp, color = c.ink3, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 4.dp))
                PrimaryButton("Copy this page") { clip.setText(AnnotatedString(text)); Toaster.show("Copied"); onDismiss() }
                Text(
                    "Copy the whole document", color = c.blue, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable {
                        scope.launch {
                            val all = withContext(Dispatchers.IO) { runCatching { PdfIndex.allText(doc.file) }.getOrDefault("") }
                            clip.setText(AnnotatedString(all)); Toaster.show("Copied"); onDismiss()
                        }
                    }.padding(14.dp),
                )
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
