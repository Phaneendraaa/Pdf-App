package com.phani.pdfkit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PkSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Pk.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 14.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        ) {
            if (title != null) Text(
                title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp), color = c.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
fun SheetOption(
    icon: ImageVector,
    fg: Color,
    bg: Color,
    title: String,
    sub: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Pk.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintTile(icon, fg, bg, size = 40.dp, iconSize = 21.dp, radius = 11.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = if (danger) c.red else c.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
    }
}

@Composable
fun ChoiceOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = Pk.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
            Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        if (selected) Icon(PkIcons.Check, null, tint = c.blue, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val c = Pk.colors
    val base = current.substringBeforeLast('.')
    var text by remember { mutableStateOf(base) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Rename file", color = c.ink, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                suffix = { Text("." + current.substringAfterLast('.', "pdf"), color = c.ink3) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue, unfocusedBorderColor = c.line, cursorColor = c.blue),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onRename(text.trim()) }) { Text("Rename", color = c.blue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) } },
    )
}

/** The "Update this PDF or keep a copy" question shown after any edit. */
@Composable
fun SaveChoiceSheet(
    title: String, onDismiss: () -> Unit, onUpdate: () -> Unit, onCopy: () -> Unit,
    copyHint: String = "Keeps the original untouched", extra: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Pk.colors
    val blue = com.phani.pdfkit.ui.theme.tint(0xFF2463EB, 0xFFEEF3FF, c.isDark)
    val green = com.phani.pdfkit.ui.theme.tint(0xFF16A34A, 0xFFECF8F1, c.isDark)
    PkSheet(onDismiss, title) {
        SheetOption(PkIcons.Check, blue.fg, blue.bg, "Update this PDF", "Replaces the current file, no extra copy") { onDismiss(); onUpdate() }
        SheetOption(PkIcons.Copy, green.fg, green.bg, "Save as a new copy", copyHint) { onDismiss(); onCopy() }
        extra()
    }
}
