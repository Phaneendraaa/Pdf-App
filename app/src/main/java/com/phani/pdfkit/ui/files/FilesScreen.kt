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
import kotlinx.coroutines.launch

private val FILTERS = listOf("all" to "All", "pdf" to "PDFs", "doc" to "Word", "img" to "Images", "star" to "Starred", "trash" to "Trash")
private val SORTS = listOf("date" to "Date modified", "name" to "Name", "size" to "Size")

@Composable
fun FilesScreen(
    files: List<DocFile>,
    starred: Set<String>,
    onOpen: (DocFile) -> Unit,
    onMore: (DocFile) -> Unit,
    onStar: (DocFile) -> Unit,
    onSearch: () -> Unit,
    onMerge: (List<DocFile>) -> Unit = {},
    folders: List<String> = emptyList(),
    onBatch: (String, List<DocFile>) -> Unit = { _, _ -> },
) {
    val c = Pk.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picked = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val selecting = picked.isNotEmpty()
    androidx.activity.compose.BackHandler(selecting) { picked.clear() }
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("date") }
    var grid by rememberSaveable { mutableStateOf(false) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolder by androidx.compose.runtime.remember { mutableStateOf(false) }
    var moving by androidx.compose.runtime.remember { mutableStateOf<List<DocFile>?>(null) }
    var batchCompress by androidx.compose.runtime.remember { mutableStateOf<List<DocFile>?>(null) }
    androidx.activity.compose.BackHandler(folder != null && !selecting) { folder = null }

    val shown = files
        .filter {
            when (filter) {
                "pdf" -> it.isPdf
                "img" -> it.isImage
                "doc" -> it.isDoc
                "star" -> it.path in starred
                else -> it.folder == (folder ?: "")
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
        AnimatedContent(selecting, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "filesHeader") { sel ->
            if (!sel && folder != null) Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(PkIcons.ArrowLeft, label = "Back to all files") { folder = null }
                Text(folder.orEmpty(), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconBtn(PkIcons.Trash, tint = c.ink2, label = "Delete folder") {
                    val name = folder ?: return@IconBtn
                    folder = null
                    scope.launch { com.phani.pdfkit.Graph.library.deleteFolder(name); com.phani.pdfkit.ui.components.Toaster.show("Folder removed. Its files are in All.") }
                }
            } else if (!sel) Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 22.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Files", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.weight(1f))
                IconBtn(PkIcons.Search, label = "Search", onClick = onSearch)
            } else Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 8.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(PkIcons.Close, label = "Cancel selection") { picked.clear() }
                Text("${picked.size} selected", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                Text(
                    if (picked.size == shown.size) "None" else "All", color = c.blue, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        if (picked.size == shown.size) picked.clear() else { picked.clear(); picked.addAll(shown.map { it.path }) }
                    }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
                IconBtn(PkIcons.Share, label = "Share") {
                    val docs = files.filter { it.path in picked }
                    com.phani.pdfkit.util.FileShare.share(ctx, docs); picked.clear()
                }
                val pdfs = files.filter { it.path in picked && it.isPdf }
                val imgs = files.filter { it.path in picked && it.isImage }
                if (pdfs.size >= 2) IconBtn(PkIcons.Merge, label = "Merge") { onMerge(pdfs); picked.clear() }
                if (pdfs.isNotEmpty()) IconBtn(PkIcons.Compress, label = "Compress") { batchCompress = pdfs; picked.clear() }
                if (imgs.isNotEmpty() && imgs.size == picked.size) IconBtn(PkIcons.DocPdf, label = "Make PDF") { onBatch("imgs2pdf", imgs); picked.clear() }
                IconBtn(PkIcons.Folder, label = "Move to folder") { moving = files.filter { it.path in picked }; picked.clear() }
                IconBtn(PkIcons.Trash, tint = c.red, label = "Delete") {
                    val docs = files.filter { it.path in picked }
                    picked.clear()
                    scope.launch {
                        val trashed = docs.mapNotNull { d -> com.phani.pdfkit.Graph.library.softDelete(d)?.let { it to d.name } }
                        if (trashed.isNotEmpty()) com.phani.pdfkit.ui.components.Toaster.show("Deleted ${trashed.size} file${if (trashed.size == 1) "" else "s"}", "Undo") {
                            scope.launch { trashed.forEach { (t, n) -> com.phani.pdfkit.Graph.library.restore(t, n) } }
                        }
                    }
                }
            }
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
        if (filter == "trash") { TrashList(); return@Column }
        if (filter == "all" && folder == null && !selecting) FolderStrip(folders, files, { folder = it }) { newFolder = true }
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
            if (!selecting && shown.isNotEmpty()) Text(
                "Select", color = c.blue, fontWeight = FontWeight.Medium, fontSize = 14.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { grid = false; picked.add(shown.first().path) }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
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
                "doc" -> EmptyState(PkIcons.Word, "No Word files yet", "Word files you convert or import will show up here.")
                "all" -> if (folder != null) EmptyState(PkIcons.Folder, "This folder is empty", "Select files, then tap the folder icon to move them here.")
                    else EmptyState(PkIcons.FileUp, "No files yet", "Create a PDF from photos or import one to get started.")
                else -> EmptyState(PkIcons.FileUp, "No files yet", "Create a PDF from photos or import one to get started.")
            }
            return@Column
        }
        AnimatedContent(grid && !selecting, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "view") { isGrid ->
            if (!isGrid) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.path }) { d ->
                        Column(Modifier.animateItem()) {
                            val isPicked = d.path in picked
                            FileRow(
                                d, starred = d.path in starred, onStar = { onStar(d) }, onMore = { onMore(d) },
                                selected = if (selecting) isPicked else null,
                                onLongClick = { if (isPicked) picked.remove(d.path) else picked.add(d.path) },
                            ) { if (selecting) { if (isPicked) picked.remove(d.path) else picked.add(d.path) } else onOpen(d) }
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

    if (newFolder) FolderNameDialog({ newFolder = false }) { name ->
        newFolder = false
        scope.launch { com.phani.pdfkit.Graph.library.createFolder(name)?.let { folder = it } }
    }
    moving?.let { docs -> MoveSheet(folders, { moving = null }) { target ->
        moving = null
        scope.launch {
            docs.forEach { com.phani.pdfkit.Graph.library.move(it, target) }
            com.phani.pdfkit.ui.components.Toaster.show("Moved ${docs.size} file${if (docs.size == 1) "" else "s"} to ${target.ifEmpty { "All files" }}")
        }
    } }
    batchCompress?.let { docs ->
        com.phani.pdfkit.ui.components.PkSheet({ batchCompress = null }, "Compress ${docs.size} PDF${if (docs.size == 1) "" else "s"}") {
            listOf("100 KB", "200 KB", "500 KB", "1 MB").forEach { label ->
                com.phani.pdfkit.ui.components.ChoiceOption("Under $label each", "Keeps the best quality that fits", false) {
                    batchCompress = null; onBatch("compress:" + label, docs)
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

/** Folders as small cards above the file list, plus "New folder". */
@Composable
private fun FolderStrip(folders: List<String>, files: List<DocFile>, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val c = Pk.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        folders.forEach { f ->
            val n = files.count { it.folder == f }
            Column(
                Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).background(c.card)
                    .clickable { onOpen(f) }.padding(12.dp),
            ) {
                Icon(PkIcons.Folder, null, tint = c.blue, modifier = Modifier.size(22.dp))
                Text(f, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text("$n file${if (n == 1) "" else "s"}", fontSize = 12.5.sp, color = c.ink2)
            }
        }
        Column(
            Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(c.surf).clickable(onClick = onNew).padding(12.dp),
        ) {
            Icon(PkIcons.Plus, null, tint = c.ink2, modifier = Modifier.size(22.dp))
            Text("New folder", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(top = 8.dp))
            Text("Keep files tidy", fontSize = 12.5.sp, color = c.ink2)
        }
    }
}

@Composable
fun FolderNameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val c = Pk.colors
    var text by androidx.compose.runtime.remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card, shape = RoundedCornerShape(20.dp),
        title = { Text("New folder", color = c.ink, style = MaterialTheme.typography.titleMedium) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                text, { text = it.take(40) }, singleLine = true, placeholder = { Text("e.g. Job forms", color = c.ink3) }, shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(enabled = text.isNotBlank(), onClick = { onCreate(text.trim()) }) { Text("Create", color = c.blue, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

/** Pick where files go: the top level, a folder, or a new folder. */
@Composable
fun MoveSheet(folders: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Pk.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var naming by androidx.compose.runtime.remember { mutableStateOf(false) }
    com.phani.pdfkit.ui.components.PkSheet(onDismiss, "Move to") {
        com.phani.pdfkit.ui.components.SheetOption(PkIcons.Home, c.ink2, c.surf, "All files", "No folder") { onPick("") }
        folders.forEach { f -> com.phani.pdfkit.ui.components.SheetOption(PkIcons.Folder, c.blue, c.blueTint, f) { onPick(f) } }
        com.phani.pdfkit.ui.components.SheetOption(PkIcons.Plus, c.ink2, c.surf, "New folder") { naming = true }
    }
    if (naming) FolderNameDialog({ naming = false }) { name ->
        naming = false
        scope.launch { com.phani.pdfkit.Graph.library.createFolder(name)?.let(onPick) }
    }
}

/** Deleted files, kept for 30 days: restore or delete for good. */
@Composable
private fun TrashList() {
    val c = Pk.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lib = com.phani.pdfkit.Graph.library
    var items by androidx.compose.runtime.remember { mutableStateOf(lib.trash()) }
    fun reload() { items = lib.trash() }
    if (items.isEmpty()) { EmptyState(PkIcons.Trash, "Trash is empty", "Deleted files stay here for 30 days."); return }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kept for 30 days, then removed", fontSize = 13.sp, color = c.ink2, modifier = Modifier.weight(1f))
            Text(
                "Empty trash", color = c.red, fontWeight = FontWeight.Medium, fontSize = 14.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { lib.emptyTrash(); reload() } }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.file.name }) { t ->
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${Fmt.size(t.file.length())} · ${t.daysLeft} day${if (t.daysLeft == 1) "" else "s"} left" + (if (t.folder.isNotEmpty()) " · ${t.folder}" else ""), fontSize = 13.sp, color = c.ink2)
                    }
                    Text(
                        "Restore", color = c.blue, fontWeight = FontWeight.Medium, fontSize = 14.5.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { scope.launch { lib.restore(t.file); reload(); com.phani.pdfkit.ui.components.Toaster.show("Restored ${t.name}") } }.padding(10.dp),
                    )
                    IconBtn(PkIcons.Trash, tint = c.ink3, size = 19.dp, label = "Delete for good") { scope.launch { lib.deleteForever(t.file); reload() } }
                }
                HorizontalDivider(Modifier.padding(start = 22.dp, end = 22.dp), color = c.line)
            }
        }
    }
}
