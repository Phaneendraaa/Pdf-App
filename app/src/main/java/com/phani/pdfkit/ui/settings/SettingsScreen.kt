package com.phani.pdfkit.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.BuildConfigInfo
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.ui.components.ChoiceOption
import com.phani.pdfkit.ui.components.PdfGlyph
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.util.Store
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings, onTour: () -> Unit) {
    val c = Pk.colors
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.padding(start = 22.dp, top = 12.dp))
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(c.greenTint, c.surf)))
                .border(1.dp, c.green.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).shadow(4.dp, RoundedCornerShape(12.dp), spotColor = c.green).clip(RoundedCornerShape(12.dp)).background(c.card), contentAlignment = Alignment.Center) {
                Icon(PkIcons.Shield, null, tint = c.green, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("100% offline", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text("Your files never leave your phone.", fontSize = 14.sp, color = c.ink2)
            }
        }

        Group("General") {
            SRow(PkIcons.Folder, "Default save folder", sub = "Downloads / Pdf Kit") { Toaster.show("Use Save on any file to copy it to Downloads / Pdf Kit") }
            Divider()
            SRow(PkIcons.Sliders, "Compression quality", value = settings.compression) { sheet = "comp" }
            Divider()
            SRow(PkIcons.Palette, "Theme", value = settings.theme) { sheet = "theme" }
            Divider()
            SRow(PkIcons.Pencil, "Autofill details", sub = "Name, email and phone for forms") { sheet = "autofill" }
            Divider()
            SRow(PkIcons.Ocr, "Searchable scans", sub = "Make scans searchable after scanning", trailing = { Switch(settings.searchableScans) }) {
                scope.launch { Graph.prefs.setSearchableScans(!settings.searchableScans) }
            }
        }
        Group("Security") {
            SRow(PkIcons.Lock, "App lock", sub = "Fingerprint or PIN to open", trailing = { Switch(settings.appLock) }) {
                if (!settings.appLock && !com.phani.pdfkit.util.AppLock.available(ctx)) {
                    Toaster.show("Set up a screen lock or fingerprint on your phone first")
                } else {
                    scope.launch { Graph.prefs.setAppLock(!settings.appLock) }
                    Toaster.show(if (!settings.appLock) "App lock on. It asks after 30 seconds away." else "App lock off")
                }
            }
        }
        Group("About") {
            SRow(PkIcons.Download, "Get the latest version", sub = "You have v${BuildConfigInfo.versionName(ctx)}") {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Phaneendraaa/Pdf-App/releases/latest"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Divider()
            SRow(PkIcons.Play, "Welcome tour", onClick = onTour)
            Divider()
            SRow(PkIcons.Star, "Rate Pdf Kit") { Store.openListing(ctx) }
            Divider()
            SRow(PkIcons.Info, "About Pdf Kit") { sheet = "about" }
        }
        Text("Pdf Kit · v${BuildConfigInfo.versionName(ctx)}", fontSize = 12.5.sp, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 30.dp))
    }

    when (sheet) {
        "comp" -> PkSheet({ sheet = null }, "Compression quality") {
            listOf("Low" to "Smallest files, lower image quality", "Balanced" to "Recommended for most documents", "High" to "Best quality, larger files").forEach { (v, s) ->
                ChoiceOption(v, s, settings.compression == v) { scope.launch { Graph.prefs.setCompression(v) }; sheet = null }
            }
        }
        "autofill" -> com.phani.pdfkit.ui.forms.AutofillSheet({ sheet = null })
        "theme" -> PkSheet({ sheet = null }, "Theme") {
            listOf("Light" to "Clean white interface", "Dark" to "Easier on the eyes at night", "System" to "Match your phone setting").forEach { (v, s) ->
                ChoiceOption(v, s, settings.theme == v) { scope.launch { Graph.prefs.setTheme(v) }; sheet = null }
            }
        }
        "about" -> PkSheet({ sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PdfGlyph(width = 48.dp)
                Text("Pdf Kit", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(top = 14.dp))
                Text("Version ${BuildConfigInfo.versionName(ctx)}", fontSize = 14.sp, color = c.ink2)
                Text(
                    "Everything happens on your phone.\nNothing is uploaded. Ever.",
                    fontSize = 14.5.sp, color = c.ink2, textAlign = TextAlign.Center, lineHeight = 21.sp, modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = Pk.colors
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 10.dp))
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).background(c.card),
        content = content,
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 54.dp), color = Pk.colors.line)

@Composable
private fun SRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Pk.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.ink2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        if (trailing != null) trailing() else {
            if (value != null) Text(value, fontSize = 14.5.sp, color = c.ink2, modifier = Modifier.padding(end = 4.dp))
            Icon(PkIcons.ChevronRight, null, tint = c.ink2, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Switch(on: Boolean) {
    val c = Pk.colors
    val bg by animateColorAsState(if (on) c.blue else Color(0xFFDADDE3), label = "sw")
    val x by animateDpAsState(if (on) 18.dp else 0.dp, label = "swx")
    Box(Modifier.width(46.dp).height(28.dp).clip(RoundedCornerShape(99.dp)).background(bg).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(22.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}
