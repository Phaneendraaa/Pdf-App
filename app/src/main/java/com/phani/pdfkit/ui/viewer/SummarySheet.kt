package com.phani.pdfkit.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ai.NanoAi
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.pdf.PdfText
import com.phani.pdfkit.ui.components.Pill
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** On-device summary with Gemini Nano. Only shown on phones where [NanoAi] is available. */
@Composable
fun SummarySheet(doc: DocFile, initial: NanoAi.State, onDismiss: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun summarizeDoc() {
        error = null; summary = ""
        status = "Reading the document..."
        val pages = withContext(Dispatchers.IO) { runCatching { PdfText.pages(doc.file) }.getOrElse { error = it.message; null } } ?: run { status = null; return }
        val chunks = PdfText.chunks(pages)
        if (chunks.isEmpty() || chunks.sumOf { it.length } < 400) {
            status = null
            error = "There isn't enough text to summarize. If this is a scan, use Text Recognition first."
            return
        }
        chunks.forEachIndexed { i, chunk ->
            status = if (chunks.size > 1) "Summarizing part ${i + 1} of ${chunks.size}..." else "Summarizing..."
            val s = runCatching { NanoAi.summarize(ctx, chunk) }.getOrElse { error = "The on-device AI is busy. Try again in a moment."; status = null; return }
            summary = (summary + "\n" + s).trim()
        }
        status = null
    }

    LaunchedEffect(Unit) { if (state == NanoAi.State.Ready || state == NanoAi.State.Downloading) summarizeDoc() }

    PkSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PkIcons.Sparkle, null, tint = c.blue, modifier = Modifier.size(20.dp))
            Text("Summary", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Pill("On-device AI · Beta", c.green, c.greenTint)
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp).fillMaxWidth()) {
            when {
                state == NanoAi.State.Downloadable && summary.isEmpty() && status == null -> {
                    Text(
                        "Your phone can summarize PDFs offline with Gemini Nano. Android downloads the AI model once; Pdf Kit's size doesn't change.",
                        color = c.ink2, fontSize = 14.5.sp, lineHeight = 21.sp,
                    )
                    PrimaryButton("Download and summarize", Modifier.padding(top = 16.dp)) {
                        scope.launch {
                            status = "Downloading the AI model..."
                            if (NanoAi.download(ctx)) { state = NanoAi.State.Ready; summarizeDoc() }
                            else { status = null; error = "Download didn't finish. Check your connection and try again." }
                        }
                    }
                }
                status != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.blue, strokeWidth = 2.5.dp)
                    Text(status!!, color = c.ink2, fontSize = 14.5.sp, modifier = Modifier.padding(start = 12.dp))
                }
                error != null -> Text(error!!, color = c.ink2, fontSize = 14.5.sp, lineHeight = 21.sp)
            }
            if (summary.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        summary, color = c.ink, fontSize = 15.sp, lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 6.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(12.dp)).background(c.surf).padding(14.dp),
                    )
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Copy", color = c.blue, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Summary", summary))
                            Toaster.show("Summary copied")
                        }.padding(8.dp),
                    )
                    Text("AI can make mistakes. Check important details.", color = c.ink3, fontSize = 12.5.sp)
                }
            }
        }
    }
}

