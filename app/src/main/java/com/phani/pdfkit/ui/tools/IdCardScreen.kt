package com.phani.pdfkit.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.pdf.Filters
import com.phani.pdfkit.pdf.ImagesToPdf
import com.phani.pdfkit.pdf.JobResult
import com.phani.pdfkit.pdf.PdfTools
import com.phani.pdfkit.pdf.Quality
import com.phani.pdfkit.pdf.rememberScanner
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Segmented
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk

/** Aadhaar, PAN or any card: front and back on one A4 page, optionally under a size limit. */
@Composable
fun IdCardScreen(onBack: () -> Unit, onStart: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    var front by remember { mutableStateOf<Uri?>(null) }
    var back by remember { mutableStateOf<Uri?>(null) }
    var slot by remember { mutableStateOf(0) }
    var size by remember { mutableStateOf("Actual size") }
    var look by remember { mutableStateOf("Auto") }
    var limit by remember { mutableStateOf("300 KB") }

    fun put(u: Uri) { if (slot == 0) front = u else back = u }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) put(u) }
    val scan = rememberScanner { uris -> uris.firstOrNull()?.let { put(it) } }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("ID Card to PDF", subtitle = "Front and back on one page", back = PkIcons.ArrowLeft, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardSlot("Front", front, Modifier.weight(1f), onScan = { slot = 0; scan() }, onPick = { slot = 0; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                CardSlot("Back", back, Modifier.weight(1f), onScan = { slot = 1; scan() }, onPick = { slot = 1; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            }
            OptHint("Scan is best: it finds the card's edges and straightens it. Back is optional.")
            OptLabel("Card size on the page")
            Segmented(listOf("Actual size", "Larger"), size) { size = it }
            OptLabel("Look")
            Segmented(Filters.NAMES, look) { look = it }
            OptLabel("Keep the PDF under")
            ChipRow(listOf("100 KB", "200 KB", "300 KB", "No limit"), limit) { limit = it }
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
            PrimaryButton("Create PDF", enabled = front != null) {
                val f = front ?: return@PrimaryButton
                val b = back; val actual = size == "Actual size"; val filter = look
                val target = limit.substringBefore(' ').toLongOrNull()?.times(1024)
                ActiveJob.set("ID card to PDF", "Placing your card...", listOf("Cleaning up", "Placing on A4", "Fitting the size")) { r ->
                    val lib = Graph.library
                    val out = lib.newFile(lib.stampedName("ID_card"))
                    val n = ImagesToPdf.idCard(ctx, listOfNotNull(f, b).map { ImagesToPdf.Card(it, 0, filter) }, out, actual, Quality(2000, 0.85f))
                    if (n == 0) { out.delete(); return@set null }
                    r(0.6f)
                    var note: String? = null
                    if (target != null && out.length() > target) {
                        val small = lib.tempFile()
                        val res = PdfTools.compressTo(out, small, target, false) { r(0.6f + it * 0.4f) }
                        lib.replaceWith(out, small)
                        note = if (res.reached) "Under ${Fmt.size(target)}" else "Smallest possible: ${Fmt.size(res.after)}"
                    }
                    r(1f); lib.refreshBlocking()
                    JobResult(out, 1, "ID card PDF ready!", note = note, again = "idcard", againLabel = "Make another")
                }
                onStart()
            }
        }
    }
}

@Composable
private fun CardSlot(label: String, uri: Uri?, modifier: Modifier, onScan: () -> Unit, onPick: () -> Unit) {
    val c = Pk.colors
    Column(modifier) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.line, RoundedCornerShape(12.dp)).background(c.surf).clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(PkIcons.Scan, null, tint = c.blue, modifier = Modifier.size(24.dp))
                Text("Scan", color = c.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(
            if (uri == null) "or pick a photo" else "Replace from photos", color = c.ink2, fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onPick).padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}
