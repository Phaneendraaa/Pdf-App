package com.phani.pdfkit.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.RenameDialog
import com.phani.pdfkit.ui.components.SheetOption
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.files.FilesScreen
import com.phani.pdfkit.ui.home.HomeScreen
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.settings.SettingsScreen
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import com.phani.pdfkit.util.FileShare
import com.phani.pdfkit.util.Importer
import kotlinx.coroutines.launch

enum class Tab { Home, Files, Settings }

@Composable
fun MainScreen(
    files: List<DocFile>,
    settings: Settings,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onTool: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (DocFile) -> Unit,
    onTour: () -> Unit,
    showTour: Boolean = false,
    onTourDone: () -> Unit = {},
) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var createSheet by remember { mutableStateOf(false) }
    var moreFor by remember { mutableStateOf<DocFile?>(null) }
    var renameFor by remember { mutableStateOf<DocFile?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val got = Importer.import(ctx, uris)
            Toaster.show(if (got.size == 1) "Imported ${got[0].name}" else "Imported ${got.size} files")
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 40 }) togetherWith fadeOut(tween(150)) },
                    label = "tabs",
                ) { t ->
                    when (t) {
                        Tab.Home -> HomeScreen(
                            recent = files.filter { it.isPdf }.take(3),
                            onSearch = onSearch, onTool = onTool, onOpen = onOpen, onMore = { moreFor = it },
                            onSeeAll = { onTab(Tab.Files) }, onCreate = { createSheet = true },
                            onBell = { Toaster.show("You're all caught up") },
                        )
                        Tab.Files -> FilesScreen(
                            files = files, starred = settings.starred, onOpen = onOpen, onMore = { moreFor = it },
                            onStar = { d -> scope.launch { Graph.prefs.toggleStar(d.path) } }, onSearch = onSearch,
                        )
                        Tab.Settings -> SettingsScreen(settings, onTour)
                    }
                }
            }
            HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp)) {
                NavItem(PkIcons.Home, "Home", tab == Tab.Home, Modifier.weight(1f)) { onTab(Tab.Home) }
                NavItem(
                    PkIcons.Folder, "Files", tab == Tab.Files,
                    Modifier.weight(1f).onGloballyPositioned { com.phani.pdfkit.ui.home.HomeAnchors.bounds["navfiles"] = it.boundsInRoot() },
                ) { onTab(Tab.Files) }
                NavItem(PkIcons.Gear, "Settings", tab == Tab.Settings, Modifier.weight(1f)) { onTab(Tab.Settings) }
            }
        }
        if (showTour && tab == Tab.Home) {
            var ready by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(500); ready = true }
            if (ready) com.phani.pdfkit.ui.onboarding.QuickTour(onTourDone)
        }
    }

    if (createSheet) PkSheet({ createSheet = false }, "Create new") {
        val dark = c.isDark
        val rose = tint(0xFFE11D48, 0xFFFEEFF2, dark); val blue = tint(0xFF2463EB, 0xFFEEF3FF, dark); val green = tint(0xFF16A34A, 0xFFECF8F1, dark)
        SheetOption(PkIcons.Scan, rose.fg, rose.bg, "Scan document", "Use your camera") { createSheet = false; onTool("scan") }
        SheetOption(PkIcons.Image, blue.fg, blue.bg, "Images to PDF", "Pick photos from your gallery") { createSheet = false; onTool("jpg2pdf") }
        SheetOption(PkIcons.FileUp, green.fg, green.bg, "Import PDF", "Open a file from your phone") {
            createSheet = false; importer.launch(arrayOf("application/pdf", "image/*"))
        }
    }

    moreFor?.let { d ->
        val starred = d.path in settings.starred
        val dark = c.isDark
        val blue = tint(0xFF2463EB, 0xFFEEF3FF, dark); val amber = tint(0xFFD97706, 0xFFFFF7E6, dark)
        val red = tint(0xFFEF3B3B, 0xFFFFEEEE, dark)
        PkSheet({ moreFor = null }, d.name) {
            SheetOption(PkIcons.Eye, blue.fg, blue.bg, "Open") { moreFor = null; onOpen(d) }
            SheetOption(PkIcons.Share, blue.fg, blue.bg, "Share") { moreFor = null; FileShare.share(ctx, listOf(d)) }
            SheetOption(PkIcons.Download, blue.fg, blue.bg, "Save to Downloads") {
                moreFor = null
                scope.launch { Toaster.show(FileShare.saveToDownloads(ctx, d)?.let { "Saved to $it" } ?: "Couldn't save the file") }
            }
            SheetOption(if (starred) PkIcons.StarFilled else PkIcons.Star, amber.fg, amber.bg, if (starred) "Remove from starred" else "Add to starred") {
                moreFor = null; scope.launch { Graph.prefs.toggleStar(d.path) }
            }
            SheetOption(PkIcons.Edit, c.ink2, c.surf, "Rename") { moreFor = null; renameFor = d }
            SheetOption(PkIcons.Trash, red.fg, red.bg, "Delete", danger = true) {
                moreFor = null
                scope.launch {
                    val trash = Graph.library.softDelete(d)
                    if (trash != null) Toaster.show("Deleted ${d.name}", "Undo") { scope.launch { Graph.library.restore(trash, d.name) } }
                }
            }
        }
    }

    renameFor?.let { d ->
        RenameDialog(d.name, { renameFor = null }) { newName ->
            renameFor = null
            scope.launch {
                val wasStarred = d.path in settings.starred
                val r = Graph.library.rename(d, newName)
                if (r != null && wasStarred) { Graph.prefs.toggleStar(d.path); Graph.prefs.toggleStar(r.path) }
                Toaster.show(if (r != null) "Renamed to ${r.name}" else "Couldn't rename the file")
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (on) c.blue else c.ink2, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) c.blue else c.ink2, modifier = Modifier.padding(top = 4.dp))
    }
}
