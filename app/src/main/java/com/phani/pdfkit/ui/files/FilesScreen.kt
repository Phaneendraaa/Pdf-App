package com.phani.pdfkit.ui.files

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.FileRow
import com.phani.pdfkit.ui.components.IconBtn
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import java.io.File

private val FILTERS = listOf("all" to "All", "pdf" to "PDFs", "img" to "Images", "star" to "Starred")
private val SORTS = listOf("date" to "Date modified", "name" to "Name", "size" to "Size")

@Composable
fun FilesScreen(
    files: List<DocFile>,
    starred: Set<String>,
    onOpen: (DocFile) -> Unit,
    onMore: (DocFile) -> Unit,
    onStar: (DocFile) -> Unit,
    onSearch: () -> Unit,
) {
    val c = Pk.colors
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("date") }
    var grid by rememberSaveable { mutableStateOf(false) }

    val shown = files
        .filter {
            when (filter) {
                "pdf" -> it.isPdf
                "img" -> it.isImage
                "star" -> it.path in starred
                else -> true
            }
        }
        .let { list ->
            when (sort) {
                "name" -> list.sortedBy { it.name.lowercase() }
                "size" -> list.sortedByDescending { it.bytes }
                else -> list.sortedByDescending { it.modified }
            }
        }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Files", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.weight(1f))
            IconBtn(PkIcons.Search, label = "Search", onClick = onSearch)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { (k, label) ->
                val on = filter == k
                val bg by animateColorAsState(if (on) c.blue else Color.Transparent, label = "chip")
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(99.dp)).background(bg)
                        .border(1.dp, if (on) c.blue else c.line, RoundedCornerShape(99.dp))
                        .clickable { filter = k }.padding(horizontal = 17.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 14.5.sp, color = if (on) Color.White else c.ink) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.surf).clickable {
                    sort = SORTS[(SORTS.indexOfFirst { it.first == sort } + 1) % SORTS.size].first
                }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(SORTS.first { it.first == sort }.second, fontSize = 14.5.sp, color = c.ink)
                Icon(PkIcons.ArrowDown, null, tint = c.ink, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            ViewToggle(PkIcons.List, !grid) { grid = false }
            Spacer(Modifier.width(6.dp))
            ViewToggle(PkIcons.Grid, grid) { grid = true }
        }

        val (hint, dismissHint) = com.phani.pdfkit.util.rememberHint("files_more")
        com.phani.pdfkit.ui.components.HintBanner(
            hint && files.isNotEmpty(), PkIcons.DotsV, "Tap ⋮ on a file to share, rename, save or delete it.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), dismissHint,
        )
        if (shown.isEmpty()) {
            when (filter) {
                "star" -> EmptyState(PkIcons.Star, "No starred files", "Tap the star on any file to keep it here.")
                "img" -> EmptyState(PkIcons.Image, "No images yet", "Images you export from PDFs will show up here.")
                else -> EmptyState(PkIcons.FileUp, "No files yet", "Create a PDF from photos or import one to get started.")
            }
            return@Column
        }
        AnimatedContent(grid, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "view") { isGrid ->
            if (!isGrid) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.path }) { d ->
                        Column(Modifier.animateItem()) {
                            FileRow(d, starred = d.path in starred, onStar = { onStar(d) }, onMore = { onMore(d) }) { onOpen(d) }
                            HorizontalDivider(Modifier.padding(start = 80.dp, end = 22.dp), color = c.line)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.path }) { d -> androidx.compose.foundation.layout.Box(Modifier.animateItem()) { GridCard(d, d.path in starred, { onStar(d) }, { onMore(d) }) { onOpen(d) } } }
                }
            }
        }
    }
}

@Composable
private fun ViewToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    val c = Pk.colors
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) c.blueTint else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) c.blue else c.ink2, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun GridCard(d: DocFile, starred: Boolean, onStar: () -> Unit, onMore: () -> Unit, onClick: () -> Unit) {
    val c = Pk.colors
    Column(
        Modifier.pressable(scaleTo = 0.97f, onClick = onClick).clip(RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).background(c.card).padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(9.dp)).background(c.surf), contentAlignment = Alignment.Center) {
            if (d.isImage) {
                AsyncImage(File(d.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                PageThumb(d, Modifier.fillMaxWidth(0.62f).height(102.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).clip(CircleShape).background(c.card.copy(alpha = 0.9f)).clickable(onClick = onStar),
                contentAlignment = Alignment.Center,
            ) { Icon(if (starred) PkIcons.StarFilled else PkIcons.Star, null, tint = if (starred) c.amber else c.ink3, modifier = Modifier.size(17.dp)) }
        }
        Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.size(d.bytes)} · ${Fmt.date(d.modified)}", fontSize = 12.5.sp, color = c.ink2, maxLines = 1)
            }
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(PkIcons.DotsV, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** First page of a PDF rendered as a thumbnail; falls back to a paper glyph. */
@Composable
fun PageThumb(d: DocFile, modifier: Modifier = Modifier) {
    val c = Pk.colors
    val bmp = com.phani.pdfkit.util.rememberPdfThumb(d)
    Box(modifier.shadow(4.dp, RoundedCornerShape(3.dp)).clip(RoundedCornerShape(3.dp)).background(Color.White)) {
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFC9CDD5)))
                repeat(6) { i -> Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.7f else 1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE4E7EC))) }
            }
        }
    }
}
