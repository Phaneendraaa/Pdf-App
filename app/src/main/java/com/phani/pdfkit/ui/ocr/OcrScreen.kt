package com.phani.pdfkit.ui.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.pdf.Ocr
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.TintTile
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import com.phani.pdfkit.util.Importer
import com.phani.pdfkit.util.PdfThumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An image another screen wants read as soon as this screen opens. */
object OcrInput { var pending: Uri? = null }

@Composable
fun OcrScreen(onBack: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf<String?>(null) }

    fun recognize(block: suspend () -> String) {
        scope.launch {
            busy = "Reading text..."
            text = runCatching { block() }.getOrElse { Toaster.show("Couldn't read text from this file"); null }
            busy = null
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        OcrInput.pending?.let { u -> OcrInput.pending = null; recognize { Ocr.read(ctx, u) } }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) recognize { Ocr.read(ctx, uri) }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) recognize {
            val f = Importer.toCache(ctx, uri) ?: error("copy failed")
            val n = withContext(Dispatchers.IO) { PdfThumbs.pageCount(f) }.coerceAtMost(25)
            val sb = StringBuilder()
            for (i in 0 until n) {
                busy = "Reading page ${i + 1} of $n..."
                val bmp = withContext(Dispatchers.IO) { PdfThumbs.render(f, i, 1600) }
                if (bmp != null) {
                    val t = Ocr.read(bmp); bmp.recycle()
                    if (n > 1) sb.append("— Page ${i + 1} —\n")
                    sb.append(t.trim()).append("\n\n")
                }
            }
            sb.toString().trim()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Text Recognition", subtitle = "Copy text from photos and scans", back = PkIcons.ArrowLeft, onBack = onBack)
        when {
            busy != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = c.blue)
                Text(busy!!, color = c.ink2, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp))
            }
            text == null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val blue = tint(0xFF2463EB, 0xFFEEF3FF, c.isDark); val red = tint(0xFFEF3B3B, 0xFFFFEEEE, c.isDark)
                Source(PkIcons.Image, blue.fg, blue.bg, "From a photo", "Pick an image from your gallery") {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Source(PkIcons.DocPdf, red.fg, red.bg, "From a PDF", "Reads up to 25 pages of a scanned PDF") { pdfPicker.launch(arrayOf("application/pdf")) }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PkIcons.Shield, null, tint = c.green, modifier = Modifier.size(15.dp))
                    Text("Text is read on your phone. Nothing is uploaded.", fontSize = 13.sp, color = c.ink2, modifier = Modifier.padding(start = 6.dp))
                }
            }
            text!!.isBlank() -> Column {
                EmptyState(PkIcons.Ocr, "No text found", "Try a sharper photo with good light.")
                Text("Try another file", color = c.blue, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { text = null }.padding(10.dp))
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, c.line, RoundedCornerShape(14.dp)).background(c.card),
                ) {
                    SelectionContainer {
                        Text(text!!, color = c.ink, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp))
                    }
                }
                Row(Modifier.navigationBarsPadding().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn(PkIcons.Check, "Copy all", Modifier.weight(1f)) { copy(ctx, text!!); Toaster.show("Copied to clipboard") }
                    Btn(PkIcons.Share, "Share", Modifier.weight(1f)) {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share text"))
                    }
                    Btn(PkIcons.Plus, "New", Modifier.weight(1f)) { text = null }
                }
            }
        }
    }
}

private fun copy(ctx: Context, s: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pdf Kit text", s))
}

@Composable
private fun Source(icon: ImageVector, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, title: String, sub: String, onClick: () -> Unit) {
    val c = Pk.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).background(c.card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintTile(icon, fg, bg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.ink)
            Text(sub, fontSize = 13.5.sp, color = c.ink2)
        }
        Icon(PkIcons.ChevronRight, null, tint = c.ink2, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Btn(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Pk.colors
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.blueTint).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.blue, modifier = Modifier.size(18.dp))
        Text(label, color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
