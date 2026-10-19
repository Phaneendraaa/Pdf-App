package com.phani.pdfkit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.FileRow
import com.phani.pdfkit.ui.components.IconBtn
import com.phani.pdfkit.ui.components.Pill
import com.phani.pdfkit.ui.components.SectionTitle
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.tools.ToolCatalog
import java.util.Calendar

/** Anchors for the quick tour spotlight, reported in root coordinates. */
object HomeAnchors {
    val bounds = androidx.compose.runtime.mutableStateMapOf<String, Rect>()
}

private fun Modifier.anchor(id: String) = onGloballyPositioned { HomeAnchors.bounds[id] = it.boundsInRoot() }

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeScreen(
    recent: List<DocFile>,
    onSearch: () -> Unit,
    onTool: (String) -> Unit,
    onOpen: (DocFile) -> Unit,
    onMore: (DocFile) -> Unit,
    onSeeAll: () -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    draftCount: Int = 0,
    onResumeDraft: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
) {
    val c = Pk.colors
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.blue), contentAlignment = Alignment.Center) {
                    Icon(PkIcons.DocPdf, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Pdf Kit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
            }
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp)) {
                Text(greeting(), style = MaterialTheme.typography.displaySmall, color = c.ink)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.green))
                    Spacer(Modifier.width(8.dp))
                    Text("Works offline. Files stay on your phone.", fontSize = 14.5.sp, color = c.ink2)
                }
            }
            Row(
                Modifier.anchor("search").padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().height(50.dp)
                    .pressable(scaleTo = 0.98f, onClick = onSearch)
                    .clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Search, null, tint = c.ink3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search files and tools", color = c.ink3, fontSize = 15.5.sp)
            }
            Row(Modifier.anchor("fab").padding(start = 20.dp, end = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(PkIcons.Scan, "Scan", "Camera to PDF", filled = true, modifier = Modifier.weight(1f), onClick = onScan)
                ActionCard(PkIcons.FileUp, "Import", "PDF, Word or photo", filled = false, modifier = Modifier.weight(1f), onClick = onImport)
            }
            androidx.compose.animation.AnimatedVisibility(draftCount > 0) {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
                        .pressable(scaleTo = 0.98f, onClick = onResumeDraft)
                        .clip(RoundedCornerShape(16.dp)).background(c.blueTint).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(c.card), contentAlignment = Alignment.Center) {
                        Icon(PkIcons.Image, null, tint = c.blue, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Continue your PDF", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text("$draftCount photo${if (draftCount == 1) "" else "s"} selected", fontSize = 13.sp, color = c.ink2)
                    }
                    IconBtn(PkIcons.Close, tint = c.ink2, size = 16.dp, label = "Discard", onClick = onDiscardDraft)
                }
            }
            SectionTitle("Quick tools", Modifier.padding(start = 22.dp, top = 28.dp, bottom = 14.dp))
            val keys = ToolCatalog.quick + "more"
            Column(Modifier.anchor("quick").padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                keys.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            val t = ToolCatalog.byKey(k)
                            QuickTile(
                                icon = t?.icon ?: PkIcons.Dots,
                                label = ToolCatalog.quickLabels[k] ?: t?.name ?: "More",
                                more = k == "more",
                                modifier = Modifier.weight(1f),
                            ) { onTool(k) }
                        }
                        // Keep the grid even when the last row is short.
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 30.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent files", Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("See all", fontSize = 14.5.sp, color = c.ink2)
                    Icon(PkIcons.ChevronRight, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                }
            }
            Column(Modifier.anchor("recent")) {
                if (recent.isEmpty()) {
                    EmptyState(PkIcons.FileUp, "No files yet", "Scan a document or import a file to get started.")
                } else {
                    recent.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 80.dp, end = 22.dp), color = c.line)
                        FileRow(d, onMore = { onMore(d) }) { onOpen(d) }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** One of the two main starting points on Home. */
@Composable
private fun ActionCard(icon: ImageVector, title: String, sub: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    val fg = if (filled) Color.White else c.ink
    Column(
        modifier.pressable(scaleTo = 0.97f, haptic = true, onClick = onClick).clip(RoundedCornerShape(16.dp))
            .then(if (filled) Modifier.background(c.blue) else Modifier.border(1.dp, c.line, RoundedCornerShape(16.dp)).background(c.card))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = if (filled) Color.White else c.blue, modifier = Modifier.size(24.dp))
        Text(title, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        Text(sub, color = if (filled) Color.White.copy(alpha = 0.8f) else c.ink2, fontSize = 12.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, more: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    Column(modifier.pressable(scaleTo = 0.93f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(16.dp)).background(c.surf), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (more) c.ink else c.blue, modifier = Modifier.size(28.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).heightIn(min = 32.dp))
    }
}
