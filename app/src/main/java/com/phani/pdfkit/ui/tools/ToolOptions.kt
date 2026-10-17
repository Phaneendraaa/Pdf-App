package com.phani.pdfkit.ui.tools

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.pdf.PdfTools
import com.phani.pdfkit.ui.components.Segmented
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.util.PdfThumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- Compress ----------------

class CompressState {
    var mode by mutableStateOf("To a size")
    var level by mutableStateOf("Balanced")
    var preset by mutableStateOf("200 KB")
    var custom by mutableStateOf("")
    var unit by mutableStateOf("KB")
    var gray by mutableStateOf(false)

    /** The size limit in bytes when compressing to a size, else null. */
    fun targetBytes(): Long? {
        if (mode != "To a size") return null
        if (preset == "Custom") return custom.toLongOrNull()?.takeIf { it > 0 }?.let { if (unit == "MB") it * 1024 * 1024 else it * 1024 }
        val n = preset.substringBefore(' ').toLongOrNull() ?: return null
        return if (preset.endsWith("MB")) n * 1024 * 1024 else n * 1024
    }

    val ready: Boolean get() = mode != "To a size" || (targetBytes() ?: 0L) > 0L

    companion object { val PRESETS = listOf("100 KB", "200 KB", "300 KB", "500 KB", "1 MB", "2 MB", "Custom") }
}

@Composable
fun CompressOptions(st: CompressState, file: File?) {
    val c = Pk.colors
    OptLabel("How do you want to compress?")
    Segmented(listOf("To a size", "By quality"), st.mode) { st.mode = it }
    if (st.mode == "To a size") {
        OptLabel("Make it smaller than")
        ChipRow(CompressState.PRESETS, st.preset) { st.preset = it }
        AnimatedVisibility(st.preset == "Custom") {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    st.custom, { st.custom = it.filter(Char::isDigit).take(5) }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. 150", color = c.ink3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
                )
                Spacer(Modifier.width(10.dp))
                Segmented(listOf("KB", "MB"), st.unit, Modifier.width(120.dp)) { st.unit = it }
            }
        }
        OptHint("Keeps the best quality that fits. Many job and government forms ask for under 200 KB or 500 KB." + (file?.let { " This file is ${Fmt.size(it.length())}." } ?: ""))
    } else {
        OptLabel("Compression level")
        Segmented(listOf("Strong", "Balanced", "Light"), st.level) { st.level = it }
        OptHint(
            when (st.level) {
                "Strong" -> "Smallest file. Images get softer."
                "Light" -> "Keeps images sharp. Smaller savings."
                else -> "Good balance of size and quality."
            } + (file?.let { " This file is ${Fmt.size(it.length())}." } ?: ""),
        )
    }
    SwitchRow("Black and white", "Smaller files, good for printing and forms", st.gray) { st.gray = it }
}

// ---------------- Page numbers ----------------

class NumberState {
    var h by mutableStateOf("Center")
    var v by mutableStateOf("Bottom")
    var format by mutableStateOf("1")
    var start by mutableStateOf("1")
    var from by mutableStateOf("1")
    var margin by mutableStateOf("Medium")
    var size by mutableStateOf("Medium")
    var color by mutableStateOf("Gray")
    var bold by mutableStateOf(false)

    fun style() = PdfTools.NumberStyle(
        h = h, v = v, format = format,
        start = start.toIntOrNull() ?: 1,
        fromPage = (from.toIntOrNull() ?: 1).coerceAtLeast(1),
        margin = when (margin) { "Small" -> 16f; "Large" -> 44f; else -> 28f },
        size = when (size) { "Small" -> 8.5f; "Large" -> 14f; else -> 11f },
        color = when (color) { "Black" -> 0xFF101217.toInt(); "Blue" -> 0xFF2463EB.toInt(); else -> 0xFF5A5E6B.toInt() },
        bold = bold,
    )
}

@Composable
fun NumberOptions(st: NumberState, file: File?, pages: Int) {
    if (file != null) NumberPreview(file, st.style())
    OptLabel("Position")
    Segmented(listOf("Left", "Center", "Right"), st.h) { st.h = it }
    Spacer(Modifier.height(8.dp))
    Segmented(listOf("Top", "Bottom"), st.v) { st.v = it }
    OptLabel("Distance from the edge")
    Segmented(listOf("Small", "Medium", "Large"), st.margin) { st.margin = it }
    OptLabel("Style")
    ChipRow(listOf("1", "Page 1", "1 of N", "- 1 -"), st.format) { st.format = it }
    OptLabel("Text size")
    Segmented(listOf("Small", "Medium", "Large"), st.size) { st.size = it }
    OptLabel("Color")
    Segmented(listOf("Gray", "Black", "Blue"), st.color) { st.color = it }
    SwitchRow("Bold", null, st.bold) { st.bold = it }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            OptLabel("Start numbering on page")
            NumberField(st.from) { st.from = it }
        }
        Column(Modifier.weight(1f)) {
            OptLabel("First number")
            NumberField(st.start) { st.start = it }
        }
    }
    OptHint("Every page from page ${st.from.ifBlank { "1" }} gets its number in one go${if (pages > 0) " ($pages pages)" else ""}. Set page 2 to skip a cover.")
}

/** First page with the number drawn where it will go; it glides when you change the position. */
@Composable
private fun NumberPreview(file: File, st: PdfTools.NumberStyle) {
    val c = Pk.colors
    val page by produceState<Pair<Bitmap?, Float>?>(null, file) {
        value = withContext(Dispatchers.IO) { PdfThumbs.render(file, 0, 520) to PdfThumbs.pageWidthPt(file) }
    }
    val bmp = page?.first
    val widthPt = (page?.second ?: 595f).coerceAtLeast(100f)
    val bx by animateFloatAsState(when (st.h) { "Left" -> -1f; "Right" -> 1f; else -> 0f }, label = "nx")
    val biasY by animateFloatAsState(if (st.v == "Top") -1f else 1f, label = "ny")
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.width(170.dp).aspectRatio(bmp?.let { it.width.toFloat() / it.height } ?: 0.707f)
                .shadow(8.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, c.line, RoundedCornerShape(4.dp)),
        ) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            val dpPerPt = maxWidth / widthPt
            Text(
                st.label(st.start, st.start + 9), color = Color(st.color), fontWeight = if (st.bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = (dpPerPt.value * st.size).coerceAtLeast(5f).sp,
                modifier = Modifier.align(BiasAlignment(bx, biasY)).padding(horizontal = dpPerPt * st.margin, vertical = dpPerPt * (st.margin - 3f).coerceAtLeast(2f)),
            )
        }
    }
}

// ---------------- Small shared bits ----------------

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val c = Pk.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) c.blue else c.surf, label = "chip")
            val fg by animateColorAsState(if (on) Color.White else c.ink, label = "chipText")
            Text(
                o, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(bg).clickable { onSelect(o) }.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Pk.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
            if (sub != null) Text(sub, fontSize = 12.5.sp, color = c.ink2)
        }
        Switch(
            on, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.blue, checkedThumbColor = Color.White, uncheckedTrackColor = c.line, uncheckedBorderColor = c.line, uncheckedThumbColor = Color.White),
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit) {
    val c = Pk.colors
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(4)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
    )
}

@Composable
private fun OptLabel(text: String) {
    Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Pk.colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun OptHint(text: String) {
    Text(text, fontSize = 13.sp, color = Pk.colors.ink2, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
}
