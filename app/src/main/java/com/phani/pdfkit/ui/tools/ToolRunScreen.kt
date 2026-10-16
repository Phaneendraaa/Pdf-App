package com.phani.pdfkit.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.pdf.JobResult
import com.phani.pdfkit.pdf.PdfTools
import com.phani.pdfkit.pdf.Quality
import com.phani.pdfkit.ui.components.PdfGlyph
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Segmented
import com.phani.pdfkit.ui.components.TintTile
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import com.phani.pdfkit.util.Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Tools handled by [ToolRunScreen]. */
val RUNNABLE_TOOLS = setOf("merge", "compress", "unlock", "protect", "pdf2jpg", "split", "rotate", "reorder", "pagenum", "watermark")

@Composable
fun ToolRunScreen(key: String, presetPath: String?, onBack: () -> Unit, onStart: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tool = ToolCatalog.byKey(key) ?: return
    val multi = key == "merge"
    val files by Graph.library.files.collectAsState()

    val fromPhone = remember { mutableStateListOf<File>() }
    val selected = remember { mutableStateListOf<File>() }
    LaunchedEffect(presetPath) {
        if (!presetPath.isNullOrBlank()) File(presetPath).takeIf { it.exists() }?.let { if (it !in selected) selected += it }
    }
    val candidates = (fromPhone + files.filter { it.isPdf }.map { it.file }).distinctBy { it.absolutePath }
        .let { list -> presetPath?.let { p -> list.sortedByDescending { it.absolutePath == p } } ?: list }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch {
            val got = uris.mapNotNull { Importer.toCache(ctx, it) }.filter { it.name.endsWith(".pdf", true) }
            if (got.isEmpty() && uris.isNotEmpty()) Toaster.show("Please pick a PDF file")
            fromPhone.addAll(0, got)
            if (multi) selected.addAll(got) else got.firstOrNull()?.let { selected.clear(); selected += it }
        }
    }

    // Options
    var level by remember { mutableStateOf("Balanced") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var imgQuality by remember { mutableStateOf("Normal") }
    var splitMode by remember { mutableStateOf("Extract pages") }
    var range by remember { mutableStateOf("") }
    var rotation by remember { mutableStateOf("Right") }
    var numPos by remember { mutableStateOf("Bottom") }
    var wmText by remember { mutableStateOf("CONFIDENTIAL") }
    var wmStrength by remember { mutableStateOf("Medium") }
    val pageOrder = remember { mutableStateListOf<Int>() }

    val first = selected.firstOrNull()
    var pages by remember { mutableStateOf(0) }
    LaunchedEffect(first) {
        pages = first?.let { f -> withContext(Dispatchers.IO) { com.phani.pdfkit.util.PdfThumbs.pageCount(f) } } ?: 0
        pageOrder.clear(); pageOrder.addAll(0 until pages)
    }

    val ready = when (key) {
        "merge" -> selected.size >= 2
        "unlock" -> first != null && password.isNotEmpty()
        "protect" -> first != null && password.length >= 4 && password == confirm
        "split" -> first != null && (splitMode == "Every page" || range.isNotBlank())
        "watermark" -> first != null && wmText.isNotBlank()
        "reorder" -> first != null && pageOrder.isNotEmpty()
        else -> first != null
    }
    val action = when (key) {
        "merge" -> if (selected.size >= 2) "Merge ${selected.size} PDFs" else "Pick at least 2 PDFs"
        "compress" -> "Compress PDF"
        "unlock" -> "Unlock PDF"
        "protect" -> "Protect PDF"
        "pdf2jpg" -> "Convert to JPG"
        "split" -> if (splitMode == "Every page") "Split into pages" else "Extract pages"
        "rotate" -> "Rotate pages"
        "reorder" -> "Save page order"
        "pagenum" -> "Add page numbers"
        "watermark" -> "Add watermark"
        else -> "Continue"
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(tool.name, subtitle = tool.desc, back = PkIcons.ArrowLeft, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                if (multi) "Choose PDFs, in the order to merge" else "Choose a PDF",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            val t = tint(0xFF16A34A, 0xFFECF8F1, c.isDark)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable { picker.launch(arrayOf("application/pdf")) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TintTile(PkIcons.FileUp, t.fg, t.bg, size = 40.dp, iconSize = 21.dp, radius = 11.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Browse your phone", fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
                    Text("Downloads, WhatsApp, Drive and more", fontSize = 13.sp, color = c.ink2)
                }
                Icon(PkIcons.ChevronRight, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            }
            if (candidates.isEmpty()) {
                Text("No PDFs in Pdf Kit yet. Browse your phone to pick one.", fontSize = 13.5.sp, color = c.ink2, modifier = Modifier.padding(vertical = 14.dp))
            } else {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.take(30).forEach { f ->
                        val order = selected.indexOf(f)
                        val on = order >= 0
                        val border by animateColorAsState(if (on) c.blue else c.line, label = "sel")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(if (on) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp))
                                .background(if (on) c.blueTint.copy(alpha = 0.5f) else c.card)
                                .clickable {
                                    if (multi) { if (on) selected.remove(f) else selected += f }
                                    else { selected.clear(); selected += f }
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PdfGlyph(width = 30.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(displayName(f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Fmt.size(f.length()) + if (f in fromPhone) " · From phone" else "",
                                    fontSize = 12.5.sp, color = c.ink2,
                                )
                            }
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(if (on) c.blue else Color.Transparent)
                                    .border(1.5.dp, if (on) c.blue else c.line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) {
                                    if (multi) Text("${order + 1}", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    else Icon(PkIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(first != null) {
                Column(Modifier.padding(top = 18.dp)) {
                    when (key) {
                        "compress" -> {
                            Label("Compression level")
                            Segmented(listOf("Strong", "Balanced", "Light"), level) { level = it }
                            Hint(
                                when (level) {
                                    "Strong" -> "Smallest file. Images get softer."
                                    "Light" -> "Keeps images sharp. Smaller savings."
                                    else -> "Good balance of size and quality."
                                } + (first?.let { " Now ${Fmt.size(it.length())}." } ?: ""),
                            )
                        }
                        "unlock" -> {
                            Label("Password")
                            PasswordField(password, "Enter the PDF's password") { password = it }
                            Hint("Pdf Kit removes the password and saves an unlocked copy. You need the current password.")
                        }
                        "protect" -> {
                            Label("New password")
                            PasswordField(password, "At least 4 characters") { password = it }
                            Spacer(Modifier.height(10.dp))
                            PasswordField(confirm, "Type it again") { confirm = it }
                            if (confirm.isNotEmpty() && confirm != password) Hint("Passwords don't match yet.", c.red)
                            else Hint("Anyone opening the file will need this password. Keep it safe; it can't be recovered.")
                        }
                        "pdf2jpg" -> {
                            Label("Image quality")
                            Segmented(listOf("Normal", "High"), imgQuality) { imgQuality = it }
                            Hint("Each page becomes one JPG${if (pages > 0) " ($pages images)" else ""}.")
                        }
                        "split" -> {
                            Label("How to split")
                            Segmented(listOf("Extract pages", "Every page"), splitMode) { splitMode = it }
                            if (splitMode == "Extract pages") {
                                Spacer(Modifier.height(10.dp))
                                TextField(range, "Pages, e.g. 1-3, 5", KeyboardType.Text) { range = it }
                                Hint("Pages you list go into one new PDF${if (pages > 0) " (this file has $pages pages)" else ""}.")
                            } else Hint("Each page becomes its own PDF${if (pages > 0) " ($pages files)" else ""}.")
                        }
                        "rotate" -> {
                            Label("Rotate all pages")
                            Segmented(listOf("Left", "Right", "180°"), rotation) { rotation = it }
                        }
                        "pagenum" -> {
                            Label("Position")
                            Segmented(listOf("Bottom", "Bottom right", "Top right"), numPos) { numPos = it }
                        }
                        "watermark" -> {
                            Label("Watermark text")
                            TextField(wmText, "e.g. CONFIDENTIAL", KeyboardType.Text) { wmText = it }
                            Label("Strength")
                            Segmented(listOf("Light", "Medium", "Strong"), wmStrength) { wmStrength = it }
                        }
                        "reorder" -> {
                            Label("Drag to reorder. Tap × to remove a page.")
                            if (first != null) PageOrganizer(first, pageOrder)
                        }
                        "merge" -> Hint("Tap files in the order you want them. Numbers show the merge order.")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
            PrimaryButton(action, enabled = ready) {
                val src = selected.toList()
                val again = "tool/$key"
                fun base() = displayName(src.first()).substringBeforeLast('.')
                val lib = Graph.library
                when (key) {
                    "merge" -> ActiveJob.set("Merging PDFs", "Merging your PDFs...", listOf("Reading files", "Combining pages", "Saving PDF")) { r ->
                        val out = lib.newFile(lib.stampedName("Merged"))
                        val n = PdfTools.merge(src, out, r)
                        JobResult(out, n, "PDFs merged!", again = again, againLabel = "Merge more PDFs")
                    }
                    "compress" -> {
                        val q = when (level) { "Strong" -> Quality(1200, 0.5f); "Light" -> Quality(2400, 0.85f); else -> Quality(1800, 0.7f) }
                        ActiveJob.set("Compressing PDF", "Compressing your PDF...", listOf("Reading pages", "Optimizing images", "Saving PDF")) { r ->
                            val out = lib.newFile(base() + "_compressed")
                            val res = PdfTools.compress(src.first(), out, q, r)
                            val saved = 1f - res.after.toFloat() / res.before
                            val note = if (saved > 0.01f) "${Fmt.size(res.before)} → ${Fmt.size(res.after)} · ${String.format(Locale.US, "%.0f", saved * 100)}% smaller"
                            else "Already well optimized"
                            JobResult(out, res.pages, "PDF compressed!", note = note, again = again, againLabel = "Compress another PDF")
                        }
                    }
                    "unlock" -> ActiveJob.set("Unlocking PDF", "Unlocking your PDF...", listOf("Checking password", "Removing protection", "Saving PDF")) { r ->
                        r(0.3f); val out = lib.newFile(base() + "_unlocked")
                        val n = try { PdfTools.unlock(src.first(), password, out) } catch (e: Exception) { out.delete(); throw e }
                        JobResult(out, n, "PDF unlocked!", again = again, againLabel = "Unlock another PDF")
                    }
                    "protect" -> ActiveJob.set("Protecting PDF", "Adding a password...", listOf("Reading PDF", "Encrypting", "Saving PDF")) { r ->
                        r(0.3f); val out = lib.newFile(base() + "_protected")
                        val n = PdfTools.protect(src.first(), password, out)
                        JobResult(out, n, "PDF protected!", note = "Password required to open", again = again, againLabel = "Protect another PDF")
                    }
                    "pdf2jpg" -> ActiveJob.set("PDF to JPG", "Converting pages...", listOf("Opening PDF", "Rendering pages", "Saving images")) { r ->
                        val b = base()
                        val outs = PdfTools.toImages(src.first(), { i -> lib.newFile("${b}_p$i", "jpg") }, if (imgQuality == "High") 2.8f else 1.8f, r)
                        if (outs.isEmpty()) null else JobResult(outs.first(), outs.size, "${outs.size} image${if (outs.size == 1) "" else "s"} saved!", detail = "Saved to Files › Images", again = again, againLabel = "Convert another PDF")
                    }
                    "split" -> if (splitMode == "Every page") {
                        ActiveJob.set("Splitting PDF", "Splitting your PDF...", listOf("Reading pages", "Creating files", "Saving")) { r ->
                            val b = base()
                            val outs = PdfTools.splitEvery(src.first(), { i -> lib.newFile("${b}_page$i") }, r)
                            if (outs.isEmpty()) null else JobResult(outs.first(), outs.size, "Split into ${outs.size} files!", detail = "${outs.size} PDFs saved to Files", again = again, againLabel = "Split another PDF")
                        }
                    } else {
                        val total = pages
                        val idx = try { PdfTools.parseRange(range, total) } catch (e: Exception) { Toaster.show(e.message ?: "Check the page numbers"); return@PrimaryButton }
                        ActiveJob.set("Extracting pages", "Extracting pages...", listOf("Reading pages", "Copying pages", "Saving PDF")) { r ->
                            r(0.3f); val out = lib.newFile(base() + "_pages")
                            val n = PdfTools.extract(src.first(), idx, out)
                            JobResult(out, n, "Pages extracted!", again = again, againLabel = "Split another PDF")
                        }
                    }
                    "rotate" -> ActiveJob.set("Rotating PDF", "Rotating pages...", listOf("Reading pages", "Rotating", "Saving PDF")) { r ->
                        r(0.3f); val out = lib.newFile(base() + "_rotated")
                        val deg = when (rotation) { "Left" -> -90; "180°" -> 180; else -> 90 }
                        JobResult(out, PdfTools.rotate(src.first(), deg, out), "Pages rotated!", again = again, againLabel = "Rotate another PDF")
                    }
                    "reorder" -> {
                        val order = pageOrder.toList()
                        ActiveJob.set("Organizing pages", "Saving the new order...", listOf("Reading pages", "Arranging", "Saving PDF")) { r ->
                            r(0.3f); val out = lib.newFile(base() + "_organized")
                            JobResult(out, PdfTools.reorder(src.first(), order, out), "Pages organized!", again = again, againLabel = "Organize another PDF")
                        }
                    }
                    "pagenum" -> ActiveJob.set("Page numbers", "Numbering pages...", listOf("Reading pages", "Adding numbers", "Saving PDF")) { r ->
                        r(0.3f); val out = lib.newFile(base() + "_numbered")
                        JobResult(out, PdfTools.pageNumbers(src.first(), numPos, 1, out), "Page numbers added!", again = again, againLabel = "Number another PDF")
                    }
                    "watermark" -> ActiveJob.set("Watermark", "Adding watermark...", listOf("Reading pages", "Stamping", "Saving PDF")) { r ->
                        r(0.3f); val out = lib.newFile(base() + "_watermarked")
                        val a = when (wmStrength) { "Light" -> 0.12f; "Strong" -> 0.35f; else -> 0.22f }
                        JobResult(out, PdfTools.watermark(src.first(), wmText.trim(), a, out), "Watermark added!", again = again, againLabel = "Watermark another PDF")
                    }
                }
                onStart()
            }
        }
    }
}

/** Library files show their name; cache copies drop the "in_<n>_" prefix. */
fun displayName(f: File): String = f.name.replace(Regex("^in_\\d+_"), "")

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Pk.colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String, color: Color = Pk.colors.ink2) {
    Text(text, fontSize = 13.sp, color = color, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PasswordField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val c = Pk.colors
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = c.ink3) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
    )
}

@Composable
private fun TextField(value: String, placeholder: String, type: KeyboardType, onChange: (String) -> Unit) {
    val c = Pk.colors
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = c.ink3) },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
    )
}
