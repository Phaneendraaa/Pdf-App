package com.phani.pdfkit.ui.forms

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.pdf.Autofill
import com.phani.pdfkit.pdf.FormFill
import com.phani.pdfkit.pdf.FormWidget
import com.phani.pdfkit.pdf.PdfDoc
import com.phani.pdfkit.ui.components.ChoiceOption
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.SheetOption
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import com.phani.pdfkit.ui.tools.displayName
import com.phani.pdfkit.ui.edit.announceSaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Fill a fillable PDF form: tap a field on the page, type or pick, then save. */
@Composable
fun FormFiller(pdf: PdfDoc, file: File, startPage: Int, onClose: () -> Unit, onSaved: (File) -> Unit, onNoFields: () -> Unit) {
    val c = Pk.colors
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val loaded by produceState<List<FormWidget>?>(null, file.path) {
        value = withContext(Dispatchers.IO) { runCatching { FormFill.read(file) }.getOrDefault(emptyList()) }
    }
    val widgets = loaded.orEmpty()
    val values = remember { mutableStateMapOf<String, String>() }
    val initial = remember { HashMap<String, String>() }
    var page by remember { mutableIntStateOf(startPage.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))) }
    var active by remember { mutableStateOf<FormWidget?>(null) }
    var keepEditable by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val inLibrary = file.absolutePath.startsWith(Graph.library.dir.absolutePath)

    LaunchedEffect(Unit) { saved = Graph.prefs.autofill() }
    LaunchedEffect(loaded) {
        val l = loaded ?: return@LaunchedEffect
        if (l.isEmpty()) {
            Toaster.show("No fillable fields here. Tap the page to type on it instead.")
            onNoFields()
            return@LaunchedEffect
        }
        l.forEach { if (it.field !in values) { values[it.field] = it.value; initial[it.field] = it.value } }
        if (l.none { it.page == page }) page = l.first().page
        Toaster.show("Tap a blue field to fill it")
    }
    BackHandler { onClose() }

    val dirty = values.any { (k, v) -> initial[k] != v }
    val fields = widgets.map { it.field }.distinct()
    val filled = fields.count { !values[it].isNullOrEmpty() && values[it] != "false" }
    val bmp by produceState<Bitmap?>(null, page) { value = pdf.render(page, 1400) }
    val ratio = pdf.ratios.getOrElse(page) { 0.707f }

    fun nextAfter(w: FormWidget): FormWidget? {
        val i = widgets.indexOfFirst { it.key == w.key }
        return widgets.drop(i + 1).firstOrNull { (it.kind == FormWidget.Kind.TEXT || it.kind == FormWidget.Kind.CHOICE) && !it.readOnly && values[it.field].isNullOrEmpty() }
    }

    fun autofillAll() {
        if (saved.isEmpty()) { details = true; return }
        var n = 0
        widgets.forEach { w ->
            if (w.kind == FormWidget.Kind.TEXT && !w.readOnly && values[w.field].isNullOrEmpty()) {
                val v = Autofill.guess(w.label + " " + w.field)?.let { saved[it] }
                if (v != null) { values[w.field] = if (w.maxLen > 0) v.take(w.maxLen) else v; n++ }
            }
        }
        Toaster.show(if (n > 0) "Filled $n field${if (n == 1) "" else "s"}" else "No matching fields found")
    }

    fun tap(w: FormWidget) {
        when (w.kind) {
            FormWidget.Kind.CHECK -> {
                values[w.field] = if (values[w.field] == "true") "false" else "true"
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            FormWidget.Kind.RADIO -> {
                if (w.onValue.isNotEmpty()) values[w.field] = w.onValue
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> active = w
        }
    }

    fun save(replace: Boolean) {
        saving = true
        val changes = values.filter { (k, v) -> initial[k] != v }
        val editable = keepEditable
        scope.launch {
            var skipped = 0
            val res = Graph.library.saveEdit(file, replace, "_filled") { out -> skipped = FormFill.apply(file, changes, out, editable) }
            saving = false
            if (res == null) { Toaster.show("Couldn't save the form"); return@launch }
            announceSaved(file, res, onSaved)
            if (skipped > 0 && res.backup == null) Toaster.show("$skipped field${if (skipped == 1) "" else "s"} skipped (unsupported characters)")
            onSaved(res.file)
        }
    }
    val onSave = com.phani.pdfkit.ui.edit.rememberSave(inLibrary) { save(it) }

    Column(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF1F2F5))) {
        Row(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Fill form", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                if (fields.isNotEmpty()) Text("$filled of ${fields.size} filled", fontSize = 12.5.sp, color = c.ink2)
            }
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(c.blueTint).clickable { autofillAll() }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Pencil, null, tint = c.blue, modifier = Modifier.size(15.dp))
                Text("Autofill", color = c.blue, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page > 0) { page-- }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronLeft, "Previous page", tint = if (page > 0) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
            Text("Page ${page + 1} of ${pdf.pageCount}", fontSize = 13.5.sp, color = c.ink2, modifier = Modifier.padding(horizontal = 10.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = page < pdf.pageCount - 1) { page++ }, contentAlignment = Alignment.Center) {
                Icon(PkIcons.ChevronRight, "Next page", tint = if (page < pdf.pageCount - 1) c.ink else c.ink3, modifier = Modifier.size(22.dp))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val maxW = maxWidth; val maxH = maxHeight
            val w = if (maxW / maxH > ratio) maxH * ratio else maxW
            val h = w / ratio
            if (loaded == null) CircularProgressIndicator(color = c.blue)
            else Box(Modifier.size(w, h).shadow(8.dp, RoundedCornerShape(3.dp)).background(Color.White)) {
                Crossfade(bmp, label = "formPage") { b ->
                    if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                }
                widgets.filter { it.page == page }.forEach { fw -> FieldBox(fw, w, h, values[fw.field].orEmpty()) { tap(fw) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.bg).padding(start = 20.dp, end = 14.dp, top = 6.dp).clickable { keepEditable = !keepEditable },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keep fields editable after saving", fontSize = 13.5.sp, color = c.ink2, modifier = Modifier.weight(1f))
            Switch(
                keepEditable, { keepEditable = it },
                colors = SwitchDefaults.colors(checkedTrackColor = c.blue, checkedThumbColor = Color.White, uncheckedTrackColor = c.line, uncheckedBorderColor = c.line, uncheckedThumbColor = Color.White),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Cancel", color = c.ink2, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(14.dp))
            PrimaryButton(if (saving) "Saving..." else "Save", Modifier.weight(1f), enabled = dirty && !saving) {
                onSave()
            }
        }
    }

    active?.let { w ->
        FieldSheet(
            w, values[w.field].orEmpty(), saved,
            onCommit = { v, next ->
                values[w.field] = v
                if (next) {
                    val n = nextAfter(w)
                    if (n != null) { page = n.page; active = n } else { active = null; Toaster.show("That was the last empty field") }
                } else active = null
            },
            onDetails = { active = null; details = true },
        )
    }

    if (details) AutofillSheet({ details = false }) { saved = it }

}

@Composable
private fun FieldBox(fw: FormWidget, pageW: Dp, pageH: Dp, value: String, onTap: () -> Unit) {
    val c = Pk.colors
    val bw = (pageW * fw.box.w).coerceAtLeast(14.dp)
    val bh = (pageH * fw.box.h).coerceAtLeast(14.dp)
    val on = when (fw.kind) {
        FormWidget.Kind.CHECK -> value == "true"
        FormWidget.Kind.RADIO -> fw.onValue.isNotEmpty() && value == fw.onValue
        else -> false
    }
    val done = on || (value.isNotEmpty() && (fw.kind == FormWidget.Kind.TEXT || fw.kind == FormWidget.Kind.CHOICE))
    val bg by animateColorAsState(if (fw.readOnly) Color.Transparent else if (done) Color(0x2616A34A) else Color(0x242463EB), label = "fieldBg")
    val edge by animateColorAsState(if (done) Color(0x6616A34A) else Color(0x662463EB), label = "fieldEdge")
    val round = fw.kind == FormWidget.Kind.RADIO
    Box(
        Modifier.offset(pageW * fw.box.x, pageH * fw.box.y).size(bw, bh)
            .clip(if (round) CircleShape else RoundedCornerShape(3.dp)).background(bg)
            .border(1.dp, edge, if (round) CircleShape else RoundedCornerShape(3.dp))
            .clickable(enabled = !fw.readOnly, onClick = onTap),
        contentAlignment = if (fw.kind == FormWidget.Kind.CHECK || round) Alignment.Center else Alignment.CenterStart,
    ) {
        when (fw.kind) {
            FormWidget.Kind.TEXT, FormWidget.Kind.CHOICE -> if (value.isNotEmpty()) Text(
                value, color = Color(0xFF101217), fontSize = (bh.value * 0.58f).coerceIn(7f, 15f).sp,
                maxLines = if (fw.multiline) 8 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp),
            )
            FormWidget.Kind.CHECK -> AnimatedVisibility(on, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Icon(PkIcons.Check, null, tint = c.green, modifier = Modifier.size(bh * 0.8f))
            }
            FormWidget.Kind.RADIO -> AnimatedVisibility(on, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                Box(Modifier.size(bh * 0.5f).clip(CircleShape).background(c.green))
            }
        }
    }
}

@Composable
private fun FieldSheet(w: FormWidget, current: String, saved: Map<String, String>, onCommit: (String, Boolean) -> Unit, onDetails: () -> Unit) {
    val c = Pk.colors
    val title = w.label.ifBlank { "Field" }
    if (w.kind == FormWidget.Kind.CHOICE && w.options.isNotEmpty()) {
        PkSheet({ onCommit(current, false) }, title) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                w.options.forEach { o -> ChoiceOption(o, "", o == current) { onCommit(o, false) } }
            }
        }
        return
    }
    var text by remember(w.key) { mutableStateOf(current) }
    val guess = Autofill.guess(w.label + " " + w.field)
    val focus = remember(w.key) { FocusRequester() }
    LaunchedEffect(w.key) { runCatching { focus.requestFocus() } }
    fun fit(s: String) = if (w.maxLen > 0) s.take(w.maxLen) else s
    PkSheet({ onCommit(text, false) }, title) {
        OutlinedTextField(
            text, { text = fit(it) }, singleLine = !w.multiline, minLines = if (w.multiline) 3 else 1, maxLines = if (w.multiline) 6 else 1,
            placeholder = { Text("Type here", color = c.ink3) }, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = when (guess) { "Email" -> KeyboardType.Email; "Phone" -> KeyboardType.Phone; else -> KeyboardType.Text }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val keys = Autofill.KEYS.filter { saved[it] != null }.sortedBy { if (it == guess) 0 else 1 }
            keys.forEach { k ->
                val best = k == guess
                Text(
                    k, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (best) Color.White else c.blue,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (best) c.blue else c.blueTint)
                        .clickable { text = fit(saved[k].orEmpty()) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Text(
                if (keys.isEmpty()) "Save my details" else "Edit details", fontSize = 13.sp, color = c.ink2,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.surf).clickable(onClick = onDetails).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Next field", color = c.blue, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onCommit(text, true) }.padding(14.dp),
            )
            PrimaryButton("Done", Modifier.weight(1f)) { onCommit(text, false) }
        }
    }
}

/** Edit the details used to autofill forms. Stored only on this phone. */
@Composable
fun AutofillSheet(onDismiss: () -> Unit, onSaved: (Map<String, String>) -> Unit = {}) {
    val c = Pk.colors
    val scope = rememberCoroutineScope()
    val fields = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) { Graph.prefs.autofill().forEach { (k, v) -> fields[k] = v } }
    PkSheet(onDismiss, "Autofill details") {
        Text("Saved only on this phone. Used to fill forms in one tap.", fontSize = 13.5.sp, color = c.ink2, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Autofill.KEYS.forEach { k ->
                OutlinedTextField(
                    fields[k].orEmpty(), { fields[k] = it }, label = { Text(k) }, singleLine = k != "Address", shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = when (k) { "Email" -> KeyboardType.Email; "Phone" -> KeyboardType.Phone; else -> KeyboardType.Text }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue, focusedLabelColor = c.blue),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
        }
        PrimaryButton("Save", Modifier.padding(top = 4.dp)) {
            scope.launch {
                val clean = fields.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }
                Graph.prefs.setAutofill(clean)
                onSaved(clean); onDismiss(); Toaster.show("Details saved on this phone")
            }
        }
    }
}
