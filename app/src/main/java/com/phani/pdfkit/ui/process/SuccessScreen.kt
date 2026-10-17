package com.phani.pdfkit.ui.process

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.ui.components.PdfGlyph
import com.phani.pdfkit.ui.components.Pill
import com.phani.pdfkit.ui.components.RenameDialog
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.util.FileShare
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SuccessScreen(onDone: () -> Unit, onOpen: (DocFile) -> Unit, onAgain: (String) -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val res = ActiveJob.result
    if (res == null) { LaunchedEffect(Unit) { onDone() }; return }
    var doc by remember { mutableStateOf(Graph.library.find(res.file.absolutePath)) }
    var renaming by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }

    val pop = remember { Animatable(0.3f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        delay(150); reveal.animateTo(1f, tween(500))
    }
    BackHandler { onDone() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(end = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Done", color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 20.dp).size(100.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = (pop.value * 1.4f).coerceAtMost(1f) }
                    .shadow(20.dp, CircleShape, spotColor = c.blue).clip(CircleShape).background(c.blue),
                contentAlignment = Alignment.Center,
            ) { Icon(PkIcons.Check, null, tint = Color.White, modifier = Modifier.size(50.dp)) }

            Column(
                Modifier.graphicsLayer { alpha = reveal.value; translationY = (1 - reveal.value) * 30f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(res.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 30.dp))
                HorizontalDivider(Modifier.padding(vertical = 26.dp), color = c.line)
                if (doc?.isImage == true) {
                    coil.compose.AsyncImage(
                        doc!!.file, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else if (doc?.isDoc == true) com.phani.pdfkit.ui.components.WordGlyph(width = 54.dp) else PdfGlyph(width = 54.dp)
                Row(
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(doc?.name ?: res.file.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center)
                    Icon(PkIcons.Edit, "Rename", tint = c.ink3, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
                val (renameHint, dismissRename) = com.phani.pdfkit.util.rememberHint("success_rename")
                androidx.compose.animation.AnimatedVisibility(renameHint) {
                    Text(
                        "Tip: tap the name to rename it", fontSize = 12.5.sp, color = c.blue,
                        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).clickable { dismissRename() }.padding(4.dp),
                    )
                }
                Text(
                    res.detail ?: "${Fmt.size(doc?.bytes ?: res.file.length())} · ${res.pages} page${if (res.pages == 1) "" else "s"}",
                    fontSize = 15.sp, color = c.ink2, modifier = Modifier.padding(top = 6.dp),
                )
                if (res.note != null) Pill(res.note, c.green, c.greenTint, Modifier.padding(top = 12.dp))
                Row(Modifier.padding(top = 30.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action(PkIcons.Eye, "Open", Modifier.weight(1f)) { doc?.let(onOpen) }
                    Action(PkIcons.Share, "Share", Modifier.weight(1f)) { doc?.let { FileShare.share(ctx, listOf(it)) } }
                    Action(PkIcons.Download, "Save", Modifier.weight(1f)) {
                        doc?.let { d -> scope.launch { Toaster.show(FileShare.saveToDownloads(ctx, d)?.let { "Saved to $it" } ?: "Couldn't save the file") } }
                    }
                }
                val src = res.source
                if (src != null && src.exists() && src.absolutePath != res.file.absolutePath && src.absolutePath.startsWith(Graph.library.dir.absolutePath)) {
                    Text(
                        "Replace the original with this", color = c.blue, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable { replacing = true }.padding(8.dp),
                    )
                }
                Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PkIcons.Shield, null, tint = c.green, modifier = Modifier.size(15.dp))
                    Text("Created on your phone. Never uploaded.", fontSize = 13.sp, color = c.ink2, modifier = Modifier.padding(start = 6.dp))
                }
                if (res.again != null) {
                    Text(
                        res.againLabel, color = c.blue, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 30.dp).clip(RoundedCornerShape(8.dp)).clickable { onAgain(res.again) }.padding(8.dp),
                    )
                }
            }
        }
    }

    // Smart rename for scans: read the first page and suggest a name.
    var suggestion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(res.file) {
        if (res.again != "scan") return@LaunchedEffect
        suggestion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bmp = com.phani.pdfkit.util.PdfThumbs.render(res.file, 0, 1400) ?: return@runCatching null
                val text = com.phani.pdfkit.pdf.Ocr.read(bmp).also { bmp.recycle() }
                com.phani.pdfkit.ai.SmartName.suggest(text)
            }.getOrNull()
        }
    }
    suggestion?.let { name ->
        if (doc?.name?.startsWith(name) == true) return@let
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.align(Alignment.BottomCenter).androidxNavPad().padding(16.dp).fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Sparkle, null, tint = c.blue, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Suggested name", fontSize = 12.5.sp, color = c.ink2)
                    Text("$name.pdf", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1)
                }
                Text("Not now", color = c.ink2, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { suggestion = null }.padding(8.dp))
                Text(
                    "Use", color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        val d = doc ?: return@clickable
                        suggestion = null
                        scope.launch {
                            val r = Graph.library.rename(d, name)
                            if (r != null) { doc = r; ActiveJob.result = res.copy(file = r.file); Toaster.show("Renamed to ${r.name}") }
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    val settings by Graph.prefs.settings.androidxCollect()
    var askRate by remember { mutableStateOf(false) }
    LaunchedEffect(settings?.successCount) {
        val st = settings ?: return@LaunchedEffect
        if (st.successCount >= 3 && !st.ratePrompted) { delay(1200); askRate = true }
    }
    if (askRate) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askRate = false; scope.launch { Graph.prefs.setRatePrompted() } },
            containerColor = c.card, shape = RoundedCornerShape(20.dp),
            title = { Text("Enjoying Pdf Kit?", color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("A quick rating helps other people find a private, offline PDF app.", color = c.ink2, fontSize = 15.sp, lineHeight = 21.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askRate = false; scope.launch { Graph.prefs.setRatePrompted() }; com.phani.pdfkit.util.Store.openListing(ctx)
                }) { Text("Rate now", color = c.blue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askRate = false; scope.launch { Graph.prefs.setRatePrompted() } }) { Text("Not now", color = c.ink2) }
            },
        )
    }

    if (replacing) res.source?.let { src ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { replacing = false }, containerColor = c.card, shape = RoundedCornerShape(20.dp),
            title = { Text("Replace the original?", color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("${src.name} will be overwritten with this version, so you keep one file instead of two.", color = c.ink2, fontSize = 15.sp, lineHeight = 21.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    replacing = false
                    scope.launch {
                        val done = Graph.library.replaceWith(src, res.file)
                        if (done) {
                            doc = Graph.library.find(src.absolutePath)
                            ActiveJob.result = res.copy(file = src, source = null)
                            Toaster.show("Replaced ${src.name}")
                        } else Toaster.show("Couldn't replace the file")
                    }
                }) { Text("Replace", color = c.blue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { replacing = false }) { Text("Cancel", color = c.ink2) } },
        )
    }

    if (renaming) doc?.let { d ->
        RenameDialog(d.name, { renaming = false }) { n ->
            renaming = false
            scope.launch {
                val r = Graph.library.rename(d, n)
                if (r != null) { doc = r; ActiveJob.result = res.copy(file = r.file) }
                Toaster.show(if (r != null) "Renamed to ${r.name}" else "Couldn't rename the file")
            }
        }
    }
}

private fun Modifier.androidxNavPad() = this.then(Modifier.navigationBarsPadding())

@Composable
private fun kotlinx.coroutines.flow.Flow<com.phani.pdfkit.data.Settings>.androidxCollect() =
    collectAsState(initial = null as com.phani.pdfkit.data.Settings?)

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    Column(
        modifier.height(96.dp).pressable(onClick = onClick).clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).background(c.card),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.blue, modifier = Modifier.size(27.dp))
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
    }
}
