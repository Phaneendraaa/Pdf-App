package com.phani.pdfkit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ui.components.EmptyState
import com.phani.pdfkit.ui.components.Pill
import com.phani.pdfkit.ui.components.TintTile
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint

data class Tool(
    val key: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val fg: Long,
    val bg: Long,
    val section: String,
    val isNew: Boolean = false,
)

object ToolCatalog {
    private const val BLUE = 0xFF2463EB; private const val BLUE_T = 0xFFEEF3FF
    private const val RED = 0xFFEF3B3B; private const val RED_T = 0xFFFFEEEE
    private const val MAG = 0xFFC026D3; private const val MAG_T = 0xFFFBEEFE
    private const val GREEN = 0xFF16A34A; private const val GREEN_T = 0xFFECF8F1
    private const val ORANGE = 0xFFEA580C; private const val ORANGE_T = 0xFFFFF3EB
    private const val TEAL = 0xFF0D9488; private const val TEAL_T = 0xFFE9F8F6
    private const val VIOLET = 0xFF7C3AED; private const val VIOLET_T = 0xFFF4EFFF
    private const val SKY = 0xFF0284C7; private const val SKY_T = 0xFFEAF6FD
    private const val ROSE = 0xFFE11D48; private const val ROSE_T = 0xFFFEEFF2

    val all = listOf(
        Tool("jpg2pdf", "JPG to PDF", "Convert images to PDF", PkIcons.Image, BLUE, BLUE_T, "Convert"),
        Tool("pdf2jpg", "PDF to JPG", "Convert PDF pages to images", PkIcons.ImageOut, RED, RED_T, "Convert"),
        Tool("word2pdf", "Word to PDF", "Convert DOC/DOCX to PDF", PkIcons.DocPdf, BLUE, BLUE_T, "Convert"),
        Tool("pdf2word", "PDF to Word", "Convert PDF to DOCX", PkIcons.Word, MAG, MAG_T, "Convert"),
        Tool("merge", "Merge PDF", "Combine multiple PDFs", PkIcons.Merge, GREEN, GREEN_T, "Organize"),
        Tool("split", "Split PDF", "Split PDF into multiple files", PkIcons.Scissors, ORANGE, ORANGE_T, "Organize"),
        Tool("compress", "Compress PDF", "Reduce file size", PkIcons.Compress, BLUE, BLUE_T, "Organize"),
        Tool("rotate", "Rotate PDF", "Rotate pages left or right", PkIcons.Rotate, BLUE, BLUE_T, "Organize"),
        Tool("reorder", "Organize Pages", "Reorder, rotate, duplicate or delete pages", PkIcons.Reorder, TEAL, TEAL_T, "Organize"),
        Tool("blank", "Add Blank Page", "Insert empty pages anywhere", PkIcons.Plus, TEAL, TEAL_T, "Organize", isNew = true),
        Tool("delpages", "Delete Pages", "Remove pages you don't need", PkIcons.Trash, TEAL, TEAL_T, "Organize"),
        Tool("edittext", "Edit PDF Text", "Change existing text in a PDF", PkIcons.Edit, BLUE, BLUE_T, "Edit", isNew = true),
        Tool("fillform", "Fill Form", "Fill in a fillable PDF form", PkIcons.Pencil, GREEN, GREEN_T, "Edit", isNew = true),
        Tool("margins", "Add Margins", "Add white space or padding around pages", PkIcons.Pages, TEAL, TEAL_T, "Edit", isNew = true),
        Tool("crop", "Crop Pages", "Trim the edges of your pages", PkIcons.Crop, TEAL, TEAL_T, "Edit", isNew = true),
        Tool("text", "Add Text", "Add new text anywhere on a page", PkIcons.TextT, BLUE, BLUE_T, "Edit"),
        Tool("sign", "Sign PDF", "Draw or place your signature", PkIcons.Signature, VIOLET, VIOLET_T, "Edit"),
        Tool("watermark", "Watermark", "Stamp text on every page", PkIcons.Droplet, SKY, SKY_T, "Edit"),
        Tool("pagenum", "Page Numbers", "Number all pages in one go", PkIcons.Hash, ORANGE, ORANGE_T, "Edit"),
        Tool("protect", "Protect PDF", "Add a password", PkIcons.Lock, VIOLET, VIOLET_T, "Secure"),
        Tool("unlock", "Unlock PDF", "Remove a password", PkIcons.Unlock, VIOLET, VIOLET_T, "Secure"),
        Tool("scan", "Scan Document", "Use your camera to create a PDF", PkIcons.Scan, ROSE, ROSE_T, "Scan & OCR"),
        Tool("searchable", "Make Searchable", "Add hidden text to scanned PDFs", PkIcons.Search, ROSE, ROSE_T, "Scan & OCR", isNew = true),
        Tool("ocr", "Text Recognition", "Copy text from scans and photos", PkIcons.Ocr, ROSE, ROSE_T, "Scan & OCR", isNew = true),
    )
    val sections = listOf("Convert", "Organize", "Edit", "Secure", "Scan & OCR")

    /** Home grid, ordered by search demand. "more" is appended by the screen. */
    val quick = listOf("jpg2pdf", "edittext", "pdf2word", "pdf2jpg", "merge", "compress", "unlock")
    val quickLabels = mapOf("edittext" to "Edit PDF", "pdf2word" to "PDF to Word", "word2pdf" to "Word to PDF", "merge" to "Merge", "compress" to "Compress", "unlock" to "Unlock")

    fun byKey(key: String) = all.firstOrNull { it.key == key }
}

@Composable
fun ToolsScreen(focusSearch: Boolean, onBack: () -> Unit, onTool: (String) -> Unit) {
    val c = Pk.colors
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }
    val filtered = ToolCatalog.all.filter {
        q.isBlank() || it.name.contains(q, true) || it.desc.contains(q, true)
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("All Tools", onBack = onBack)
        Row(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp).fillMaxWidth().height(46.dp)
                .clip(RoundedCornerShape(12.dp)).background(c.surf).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PkIcons.Search, null, tint = c.ink3, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Search tools", color = c.ink3, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.blue),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(PkIcons.Search, "No tools found", "Try another word, like \"merge\" or \"sign\".")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
            ToolCatalog.sections.forEach { sec ->
                val items = filtered.filter { it.section == sec }
                if (items.isNotEmpty()) {
                    item(key = "h_$sec") {
                        Text(sec, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 10.dp))
                    }
                    items(items, key = { it.key }) { t -> androidx.compose.foundation.layout.Box(Modifier.animateItem()) { ToolRow(t) { onTool(t.key) } } }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun ToolRow(t: Tool, onClick: () -> Unit) {
    val c = Pk.colors
    val tn = tint(t.fg, t.bg, c.isDark)
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()
            .pressable(scaleTo = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(start = 11.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintTile(t.icon, tn.fg, tn.bg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t.isNew) Pill("New", c.blue, c.blueTint)
            }
            Text(t.desc, style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(PkIcons.ChevronRight, null, tint = c.ink2, modifier = Modifier.size(18.dp))
    }
}
