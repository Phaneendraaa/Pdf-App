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
                PdfGlyph(width = 54.dp)
                Row(
                    Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(doc?.name ?: res.file.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink, textAlign = TextAlign.Center)
                    Icon(PkIcons.Edit, "Rename", tint = c.ink3, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
                Text(
                    "${Fmt.size(doc?.bytes ?: res.file.length())} · ${res.pages} page${if (res.pages == 1) "" else "s"}",
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
