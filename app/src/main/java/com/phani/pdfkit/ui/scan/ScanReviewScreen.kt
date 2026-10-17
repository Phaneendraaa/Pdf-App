package com.phani.pdfkit.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.pdf.Bitmaps
import com.phani.pdfkit.pdf.Filters
import com.phani.pdfkit.pdf.ScanDraft
import com.phani.pdfkit.pdf.ScanPage
import com.phani.pdfkit.pdf.rememberScanner
import com.phani.pdfkit.ui.components.IconBtn
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.SheetOption
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * After the camera: swipe through the pages, pick a filter (for every page or just this one),
 * rotate, delete, add more pages, then save. Previews show exactly what goes into the PDF.
 */
@Composable
fun ScanReviewScreen(onDiscard: () -> Unit, onSave: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = ScanDraft.pages
    val pager = rememberPagerState { pages.size }
    var allPages by remember { mutableStateOf(true) }
    var leaving by remember { mutableStateOf(false) }

    val addMore = rememberScanner { uris ->
        val start = pages.size
        ScanDraft.add(uris)
        scope.launch { pager.animateScrollToPage(start) }
    }
    LaunchedEffect(pages.isEmpty()) { if (pages.isEmpty()) onDiscard() }
    BackHandler { leaving = true }

    val index = pager.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val current = pages.getOrNull(index)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(
            if (ScanDraft.appendTo != null) "Add pages" else "Review scan",
            subtitle = ScanDraft.appendTo?.let { "To ${it.name.removeSuffix(".pdf")}" } ?: "${pages.size} page${if (pages.size == 1) "" else "s"}",
            back = PkIcons.ArrowLeft, onBack = { leaving = true },
        ) {
            Row(
                Modifier.padding(end = 6.dp).clip(RoundedCornerShape(99.dp)).background(c.blueTint).clickable { addMore() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Plus, null, tint = c.blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add pages", color = c.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        HorizontalPager(
            pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
            pageSpacing = 16.dp,
            key = { pages.getOrNull(it)?.id ?: it.toLong() },
        ) { i ->
            val p = pages.getOrNull(i)
            if (p != null) PagePreview(ctx, p)
        }

        // Page actions
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(PkIcons.Rotate, "Rotate") {
                if (current != null) pages[index] = current.copy(turn = (current.turn + 90) % 360)
            }
            Text(
                if (pages.isEmpty()) "" else "${index + 1} of ${pages.size}",
                color = c.ink2, fontSize = 13.5.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            RoundAction(PkIcons.Trash, "Delete page") {
                if (current == null) return@RoundAction
                if (pages.size == 1) { leaving = true; return@RoundAction }
                pages.removeAt(index)
                Toaster.show("Page ${index + 1} deleted", "Undo") { pages.add(index.coerceAtMost(pages.size), current) }
            }
        }

        // Filters
        Column(
            Modifier.fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = Color(0x22101217), spotColor = Color(0x33101217))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.card)
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                ScopeToggle("All pages", allPages) { allPages = true }
                Spacer(Modifier.width(6.dp))
                ScopeToggle("This page", !allPages) { allPages = false }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Filters.NAMES.forEach { name ->
                    FilterTile(ctx, current, name, selected = current?.filter == name, modifier = Modifier.weight(1f)) {
                        if (allPages) {
                            for (k in pages.indices) pages[k] = pages[k].copy(filter = name)
                            ScanDraft.filter = name
                        } else if (current != null) pages[index] = current.copy(filter = name)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                if (ScanDraft.appendTo != null) "Add ${pages.size} page${if (pages.size == 1) "" else "s"} to PDF"
                else "Save PDF · ${pages.size} page${if (pages.size == 1) "" else "s"}",
                enabled = pages.isNotEmpty(), onClick = onSave,
            )
        }
    }

    if (leaving) PkSheet({ leaving = false }, "Discard this scan?") {
        SheetOption(PkIcons.Check, c.blue, c.blueTint, "Keep reviewing") { leaving = false }
        SheetOption(PkIcons.Trash, c.red, c.redTint, "Discard scan", "The pages won't be saved", danger = true) {
            leaving = false; onDiscard()
        }
    }
}

@Composable
private fun PagePreview(ctx: Context, p: ScanPage) {
    val c = Pk.colors
    var shown by remember(p.id) { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(p.filter, p.turn) {
        busy = true
        ScanPreviews.get(ctx, p.uri, p.filter, p.turn, 1400)?.let { shown = it }
        busy = false
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = shown
        if (bmp == null) {
            CircularProgressIndicator(color = c.blue, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
        } else {
            val ratio = bmp.width.toFloat() / bmp.height
            val boxRatio = maxWidth / maxHeight
            val w = if (ratio > boxRatio) maxWidth else maxHeight * ratio
            val h = if (ratio > boxRatio) maxWidth / ratio else maxHeight
            Box(Modifier.size(w, h)) {
                Image(
                    bmp.asImageBitmap(), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().shadow(10.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color.White),
                )
                val a by animateFloatAsState(if (busy) 1f else 0f, label = "busy")
                if (a > 0.01f) CircularProgressIndicator(
                    color = c.blue, strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterTile(ctx: Context, page: ScanPage?, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    var thumb by remember(page?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page?.id, page?.turn, name) {
        if (page != null) thumb = ScanPreviews.get(ctx, page.uri, name, page.turn, 260)
    }
    val border by animateColorAsState(if (selected) c.blue else c.line, label = "tile")
    Column(modifier.pressable(scaleTo = 0.96f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(10.dp))
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(10.dp)).background(c.surf).padding(if (selected) 2.dp else 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
            }
        }
        Text(
            name, fontSize = 12.5.sp, color = if (selected) c.blue else c.ink2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ScopeToggle(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Pk.colors
    val bg by animateColorAsState(if (on) c.ink else c.surf, label = "scope")
    val fg by animateColorAsState(if (on) c.card else c.ink2, label = "scopeText")
    Text(
        text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Pk.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

/** Filtered, rotated previews, kept in memory so swiping and switching filters stay instant. */
private object ScanPreviews {
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun get(ctx: Context, uri: Uri, filter: String, turn: Int, side: Int): Bitmap? = withContext(Dispatchers.Default) {
        val key = "$uri|$filter|$turn|$side"
        cache.get(key) ?: run {
            val baseKey = "$uri|base|$side"
            val base = cache.get(baseKey) ?: Bitmaps.decode(ctx, uri, side)?.also { cache.put(baseKey, it) } ?: return@run null
            val filtered = runCatching { Filters.apply(base, filter) }.getOrDefault(base)
            val out = if (turn % 360 == 0) filtered else Bitmap.createBitmap(
                filtered, 0, 0, filtered.width, filtered.height, Matrix().apply { postRotate(turn.toFloat()) }, true,
            )
            cache.put(key, out)
            out
        }
    }
}
