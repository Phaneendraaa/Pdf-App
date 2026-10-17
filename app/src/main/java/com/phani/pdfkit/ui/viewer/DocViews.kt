package com.phani.pdfkit.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.pdf.WordTools
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.util.FileShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Word files are shown by laying them out as PDF pages on the phone (cached per file version). */
object DocPreview {
    suspend fun pdfFor(context: Context, docx: File): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "docview").apply { mkdirs() }
        val out = File(dir, "${docx.absolutePath.hashCode()}_${docx.lastModified()}_${docx.length()}.pdf")
        if (!out.exists() || out.length() == 0L) {
            dir.listFiles()?.filter { it.name.startsWith("${docx.absolutePath.hashCode()}_") }?.forEach { it.delete() }
            val tmp = File(dir, "tmp_${System.nanoTime()}.pdf")
            try { WordTools.docxToPdf(docx, tmp) {}; tmp.renameTo(out) } finally { tmp.delete() }
        }
        out
    }
}

/** Read-only page view for a Word file, with Save as PDF. */
@Composable
fun DocxPages(doc: DocFile, onOpenPdf: (String) -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by produceState<Result<File>?>(null, doc.path, doc.modified) {
        value = runCatching { DocPreview.pdfFor(ctx, doc.file) }
    }
    val r = result
    when {
        r == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = c.blue)
            Text("Opening Word file...", color = c.ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
        }
        r.isFailure -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(PkIcons.Info, "Can't show this Word file", r.exceptionOrNull()?.message ?: "It may be damaged or an old .doc file.")
            PrimaryButton("Open in another app", Modifier.padding(horizontal = 40.dp)) { FileShare.openExternal(ctx, doc) }
        }
        else -> {
            val pdfFile = r.getOrThrow()
            val opened by produceState<PdfDoc.Open?>(null, pdfFile) { value = withContext(Dispatchers.IO) { PdfDoc.open(pdfFile) } }
            val pdf = (opened as? PdfDoc.Open.Ok)?.doc
            DisposableEffect(pdf) { onDispose { pdf?.close() } }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(c.card).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Word document", color = c.ink2, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Open in Word", color = c.ink2, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { FileShare.openExternal(ctx, doc) }.padding(8.dp),
                    )
                    Text(
                        "Save as PDF", color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    runCatching { pdfFile.copyTo(Graph.library.newFile(doc.name.substringBeforeLast('.'))).also { Graph.library.refreshBlocking() } }.getOrNull()
                                }
                                if (saved != null) { Toaster.show("Saved ${saved.name}"); onOpenPdf(saved.absolutePath) } else Toaster.show("Couldn't save the PDF")
                            }
                        }.padding(8.dp),
                    )
                }
                if (pdf == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.blue) }
                else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val px = with(LocalDensity.current) { (maxWidth.toPx() * 1.6f).toInt().coerceIn(600, 2000) }
                    ZoomBox(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(pdf.ratios) { i, ratio ->
                                val bmp by produceState<Bitmap?>(null, i, px) { value = pdf.render(i, px) }
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(ratio).shadow(6.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Crossfade(bmp, label = "docPage") { b ->
                                        if (b != null) Image(b.asImageBitmap(), "Page ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                                        else CircularProgressIndicator(Modifier.size(24.dp), color = c.blue.copy(alpha = 0.5f), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bar shown over an image: turn it into a PDF or read its text. */
@Composable
fun ImageActions(modifier: Modifier, onToPdf: () -> Unit, onOcr: () -> Unit) {
    val c = Pk.colors
    Row(
        modifier.shadow(14.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(c.card).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(c.blue).clickable(onClick = onToPdf).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PkIcons.DocPdf, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Convert to PDF", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(c.blueTint).clickable(onClick = onOcr).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PkIcons.Ocr, null, tint = c.blue, modifier = Modifier.size(18.dp))
            Text("Copy text", color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
