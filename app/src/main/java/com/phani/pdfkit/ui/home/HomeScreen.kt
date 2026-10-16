package com.phani.pdfkit.ui.home

import androidx.compose.foundation.background
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
    onCreate: () -> Unit,
    onBell: () -> Unit,
) {
    val c = Pk.colors
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), fontSize = 17.sp, color = c.ink2)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pdf Kit", style = MaterialTheme.typography.displaySmall, color = c.ink)
                        Pill("Offline", c.green, c.greenTint, dot = true)
                    }
                }
                IconBtn(PkIcons.Bell, size = 24.dp, label = "Notifications", onClick = onBell)
            }
            Row(
                Modifier.anchor("search").padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().height(48.dp)
                    .pressable(scaleTo = 0.98f, onClick = onSearch)
                    .clip(RoundedCornerShape(14.dp)).background(c.surf).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Search, null, tint = c.ink3, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search files, tools...", color = c.ink3, fontSize = 15.5.sp)
            }
            SectionTitle("Quick tools", Modifier.padding(start = 22.dp, top = 26.dp, bottom = 14.dp))
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
                    EmptyState(PkIcons.FileUp, "No files yet", "Tap + to scan, turn photos into a PDF, or import a file.")
                } else {
                    recent.forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 80.dp, end = 22.dp), color = c.line)
                        FileRow(d, onMore = { onMore(d) }) { onOpen(d) }
                    }
                }
            }
            Spacer(Modifier.height(110.dp))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 22.dp).anchor("fab")
                .size(64.dp)
                .shadow(14.dp, CircleShape, ambientColor = c.blue, spotColor = c.blue)
                .clip(CircleShape).background(c.blue)
                .pressable(scaleTo = 0.9f, haptic = true, onClick = onCreate),
            contentAlignment = Alignment.Center,
        ) { Icon(PkIcons.Plus, "Create", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, more: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    Column(modifier.pressable(scaleTo = 0.93f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(16.dp)).background(c.surf), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (more) c.ink else c.blue, modifier = Modifier.size(28.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
    }
}
