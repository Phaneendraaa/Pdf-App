package com.phani.pdfkit.ui.viewer

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.ui.components.IconBtn
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** One paragraph of reflowed text and the page it came from (0-based). */
private data class Para(val page: Int, val text: String)

/** Turns a page's raw lines into paragraphs: joins wrapped lines and hyphenated words. */
private fun paragraphs(page: Int, raw: String): List<Para> {
    val lines = raw.replace("\r", "").split('\n').map { it.trim() }
    val out = ArrayList<Para>(); val cur = StringBuilder()
    fun flush() { if (cur.isNotBlank()) out += Para(page, cur.toString().trim()); cur.clear() }
    lines.forEach { l ->
        if (l.isEmpty()) { flush(); return@forEach }
        if (cur.isEmpty()) cur.append(l)
        else if (cur.endsWith("-") && l.firstOrNull()?.isLowerCase() == true) { cur.setLength(cur.length - 1); cur.append(l) }
        else cur.append(' ').append(l)
        // A short line ending a sentence usually ends the paragraph.
        if (l.length < 45 && l.last() in ".!?:") flush()
    }
    flush()
    return out
}

/**
 * Reading mode: the PDF's text reflowed to fit the phone, with a size control, and read aloud with
 * the phone's own voice (works offline when the voice is installed). Starts at [startPage].
 */
@Composable
fun ReadMode(doc: DocFile, startPage: Int, speak: Boolean, onClose: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    var paras by remember { mutableStateOf<List<Para>?>(null) }
    var size by remember { mutableFloatStateOf(18f) }
    var serif by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var at by remember { mutableIntStateOf(0) }
    var rate by remember { mutableFloatStateOf(1f) }
    val list = rememberLazyListState()
    val reader = remember { Reader(ctx) }

    LaunchedEffect(doc.path) {
        paras = withContext(Dispatchers.IO) {
            runCatching {
                com.phani.pdfkit.pdf.PdfTools.load(doc.file).use { d ->
                    val st = com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }
                    (0 until d.numberOfPages).flatMap { p ->
                        st.startPage = p + 1; st.endPage = p + 1
                        paragraphs(p, runCatching { st.getText(d) }.getOrDefault(""))
                    }
                }
            }.getOrDefault(emptyList())
        }
        val first = paras.orEmpty().indexOfFirst { it.page >= startPage }.coerceAtLeast(0)
        at = first
        if (first > 0) list.scrollToItem(first)
        if (speak && paras.orEmpty().isNotEmpty()) { reader.play(paras.orEmpty().map { it.text }, first, rate) { at = it }; playing = true }
    }
    LaunchedEffect(at, playing) { if (playing && paras != null) list.animateScrollToItem(at.coerceAtLeast(0)) }
    DisposableEffect(Unit) { onDispose { reader.shutdown() } }
    reader.onFinished = { playing = false }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(PkIcons.Close, label = "Close reading mode", onClick = onClose)
                Text(doc.name.removeSuffix(".pdf"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
                Text("Aa", color = c.ink, fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { serif = !serif }.padding(10.dp))
                Text("A−", color = c.ink, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { size = (size - 2f).coerceAtLeast(13f) }.padding(10.dp))
                Text("A+", color = c.ink, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { size = (size + 2f).coerceAtMost(30f) }.padding(10.dp))
            }
            val ps = paras
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ps == null -> CircularProgressIndicator(color = c.blue, modifier = Modifier.align(Alignment.Center).size(28.dp))
                    ps.isEmpty() -> Text(
                        "This PDF has no text to read. If it's a scan, use Make Searchable first.",
                        color = c.ink2, fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                        itemsIndexed(ps) { i, p ->
                            if (i == 0 || ps[i - 1].page != p.page) Text(
                                "Page ${p.page + 1}", color = c.ink3, fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = if (i == 0) 0.dp else 18.dp, bottom = 6.dp),
                            )
                            val bg by animateColorAsState(if (playing && i == at) c.blueTint else Color.Transparent, label = "readAt")
                            Text(
                                p.text, color = c.ink, fontSize = size.sp, lineHeight = (size * 1.55f).sp,
                                fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg)
                                    .clickable { at = i; if (playing) reader.play(ps.map { it.text }, i, rate) { at = it } }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
            // Read-aloud bar
            if (!ps.isNullOrEmpty()) Row(
                Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(c.blue).clickable {
                        if (playing) { reader.stop(); playing = false }
                        else { reader.play(ps.map { it.text }, at, rate) { at = it }; playing = true }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(4.dp, 16.dp).background(Color.White)); Box(Modifier.size(4.dp, 16.dp).background(Color.White))
                    } else Icon(PkIcons.Play, "Read aloud", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(if (playing) "Reading aloud" else "Read aloud", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Page ${ps.getOrNull(at)?.page?.plus(1) ?: 1} · tap any paragraph to start there", color = c.ink2, fontSize = 12.5.sp, maxLines = 1)
                }
                Text(
                    "${if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()}×", color = c.blue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        rate = when (rate) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 0.75f; else -> 1f }
                        if (playing) reader.play(ps.map { it.text }, at, rate) { at = it }
                    }.padding(10.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}

/** Phone text-to-speech, one paragraph at a time so it can follow along and resume anywhere. */
private class Reader(ctx: Context) {
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var texts: List<String> = emptyList()
    private var onAt: (Int) -> Unit = {}
    var onFinished: () -> Unit = {}
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) { pending?.invoke(); pending = null }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { id?.toIntOrNull()?.let { i -> main.post { onAt(i) } } }
            override fun onDone(id: String?) { if (id?.toIntOrNull() == texts.lastIndex) main.post { onFinished() } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) {}
        })
    }

    fun play(all: List<String>, from: Int, rate: Float, onAt: (Int) -> Unit) {
        texts = all; this.onAt = onAt
        val go = {
            tts.stop()
            tts.setSpeechRate(rate)
            val hindi = all.drop(from).take(3).joinToString(" ").any { it in 'ऀ'..'ॿ' }
            tts.language = if (hindi) Locale("hi", "IN") else Locale.getDefault()
            for (i in from until all.size) {
                // Long paragraphs are split; the engine has a length limit.
                all[i].chunked(3500).forEachIndexed { k, part ->
                    tts.speak(part, TextToSpeech.QUEUE_ADD, null, if (k == 0) i.toString() else "$i-$k")
                }
            }
        }
        if (ready) go() else pending = go
    }

    fun stop() { tts.stop() }
    fun shutdown() { runCatching { tts.stop(); tts.shutdown() } }
}
