package com.phani.pdfkit.ui.edit

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.pdf.TextBlock
import com.phani.pdfkit.ui.components.PkSheet
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlin.math.roundToInt

/** A pending change to an existing line, shown live and written on save. */
data class LineEdit(val text: String, val sizePt: Float, val bold: Boolean, val color: Int, val background: Int)

/** Samples the page bitmap: background around the line and the darkest ink inside it. */
fun sampleColors(bmp: Bitmap?, b: TextBlock): Pair<Int, Int> {
    val white = 0xFFFFFFFF.toInt(); val black = 0xFF111111.toInt()
    if (bmp == null) return white to black
    fun px(fx: Float, fy: Float): Int {
        val x = (fx * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
        val y = (fy * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
        return bmp.getPixel(x, y)
    }
    val dx = 0.004f; val dy = 0.003f
    val around = listOf(
        px(b.x - dx, b.top - dy), px(b.x + b.w + dx, b.top - dy),
        px(b.x - dx, b.top + b.h + dy), px(b.x + b.w + dx, b.top + b.h + dy),
        px(b.x + b.w / 2, b.top - dy), px(b.x + b.w / 2, b.top + b.h + dy),
    )
    // Most common surrounding color (pages are usually white, but not always).
    val bg = around.groupingBy { it and 0xF0F0F0 }.eachCount().maxByOrNull { it.value }!!.key.let { key -> around.first { (it and 0xF0F0F0) == key } } or (0xFF shl 24)
    var darkest = bg; var best = luminance(bg)
    for (i in 0..14) for (j in 1..5) {
        val c = px(b.x + b.w * i / 14f, b.top + b.h * j / 6f)
        val l = luminance(c)
        if (l < best) { best = l; darkest = c }
    }
    val fg = if (kotlin.math.abs(best - luminance(bg)) < 0.15f) black else darkest or (0xFF shl 24)
    return bg to fg
}

private fun luminance(c: Int): Float = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

fun family(b: TextBlock): FontFamily = when (b.family) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }

/**
 * Overlay for "Edit text": outlines every line when [active], and draws edited lines
 * (a background patch plus the new text) at their real position and size.
 */
@Composable
fun TextEditLayer(
    blocks: List<TextBlock>,
    edits: Map<Int, LineEdit>,
    pageWidthPt: Float,
    wPx: Float,
    hPx: Float,
    active: Boolean,
    onTap: (TextBlock) -> Unit,
) {
    val c = Pk.colors
    val density = LocalDensity.current
    blocks.forEach { b ->
        val e = edits[b.id]
        val left = (b.x * wPx).roundToInt(); val top = (b.top * hPx).roundToInt()
        val bw = with(density) { (b.w * wPx).toDp() }; val bh = with(density) { (b.h * hPx).toDp() }
        if (e != null) {
            // Cover the original line, then draw the replacement on the same baseline.
            Box(Modifier.offset { IntOffset(left - 2, top - 2) }.size(bw + 2.dp, bh + 2.dp).background(Color(e.background)))
            if (e.text.isNotBlank()) {
                val fontPx = e.sizePt / pageWidthPt * wPx
                val textTop = (b.baseline * hPx - fontPx * 0.93f).roundToInt()
                Text(
                    e.text, color = Color(e.color), maxLines = 1, softWrap = false,
                    fontSize = with(density) { fontPx.toSp() }, lineHeight = with(density) { (fontPx * 1.05f).toSp() },
                    fontWeight = if (e.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (b.italic) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = family(b),
                    modifier = Modifier.offset { IntOffset(left, textTop) }.wrapContentSize(unbounded = true, align = Alignment.TopStart),
                )
            }
        }
        if (active) {
            Box(
                Modifier.offset { IntOffset(left - 3, top - 2) }.size(bw + 3.dp, bh + 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (e != null) c.blue.copy(alpha = 0.10f) else c.blue.copy(alpha = 0.05f))
                    .border(1.dp, c.blue.copy(alpha = if (e != null) 0.8f else 0.35f), RoundedCornerShape(3.dp))
                    .clickable { onTap(b) },
            )
        }
    }
}

/** Bottom sheet to change one line: text, size, bold, color, or delete. */
@Composable
fun EditLineSheet(
    block: TextBlock,
    current: LineEdit?,
    sampled: Pair<Int, Int>,
    onDismiss: () -> Unit,
    onApply: (LineEdit) -> Unit,
) {
    val c = Pk.colors
    var text by remember { mutableStateOf(current?.text ?: block.text) }
    var size by remember { mutableFloatStateOf(current?.sizePt ?: block.sizePt) }
    var bold by remember { mutableStateOf(current?.bold ?: block.bold) }
    var color by remember { mutableIntStateOf(current?.color ?: sampled.second) }
    val swatches = listOf(sampled.second, 0xFF101217.toInt(), 0xFF2463EB.toInt(), 0xFFE11D48.toInt(), 0xFF16A34A.toInt()).distinct()

    PkSheet(onDismiss, "Edit text") {
        Text(
            "Original: ${block.text}", fontSize = 12.5.sp, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        OutlinedTextField(
            text, { text = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = family(block), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (block.italic) FontStyle.Italic else FontStyle.Normal, fontSize = 17.sp, color = Color(color),
            ),
            shape = RoundedCornerShape(12.dp),
            // The field looks like the page itself (its own paper color), so the text color always reads
            // the way it will in the PDF, including in dark mode.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue,
                focusedContainerColor = Color(sampled.first), unfocusedContainerColor = Color(sampled.first),
            ),
        )
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size", fontSize = 14.sp, color = c.ink2, modifier = Modifier.width(44.dp))
            Step("−") { size = (size - 0.5f).coerceAtLeast(4f) }
            Text(String.format(java.util.Locale.US, "%.1f", size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Step("+") { size = (size + 0.5f).coerceAtMost(96f) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (bold) c.blue else c.surf).clickable { bold = !bold }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("B", fontWeight = FontWeight.Bold, color = if (bold) Color.White else c.ink, fontSize = 16.sp) }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Color", fontSize = 14.sp, color = c.ink2, modifier = Modifier.width(44.dp))
            swatches.forEach { s ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(s))
                        .border(3.dp, if (color == s) c.blueTint else Color.Transparent, CircleShape)
                        .border(1.dp, c.line, CircleShape).clickable { color = s },
                )
            }
        }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onApply(LineEdit("", size, bold, color, sampled.first)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Trash, null, tint = c.red, modifier = Modifier.size(18.dp))
                Text("Delete", color = c.red, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton("Apply", Modifier.weight(1f), enabled = text != block.text || size != block.sizePt || bold != block.bold || color != sampled.second || current != null) {
                onApply(LineEdit(text, size, bold, color, sampled.first))
            }
        }
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    val c = Pk.colors
    Box(Modifier.size(36.dp).clip(CircleShape).background(c.surf).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
    }
}
