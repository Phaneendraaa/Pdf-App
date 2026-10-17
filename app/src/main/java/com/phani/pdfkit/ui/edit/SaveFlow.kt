package com.phani.pdfkit.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Library
import com.phani.pdfkit.ui.components.SaveChoiceSheet
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.tools.SwitchRow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Returns the action for a Save button. It follows the user's choice in Settings
 * ("update" by default, so no question is asked), asks only in "ask" mode, and
 * always makes a copy for files opened from outside Pdf Kit.
 */
@Composable
fun rememberSave(inLibrary: Boolean, save: (replace: Boolean) -> Unit): () -> Unit {
    val settings by Graph.prefs.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var always by remember { mutableStateOf(false) }
    if (ask) SaveChoiceSheet(
        "Save your changes", { ask = false },
        onUpdate = { if (always) scope.launch { Graph.prefs.setSaveMode("update") }; save(true) },
        onCopy = { if (always) scope.launch { Graph.prefs.setSaveMode("copy") }; save(false) },
        extra = { SwitchRow("Always do this", "Change it later in Settings", always) { always = it } },
    )
    return {
        when {
            !inLibrary -> save(false)
            settings?.saveMode == "copy" -> save(false)
            settings?.saveMode == "ask" -> ask = true
            else -> save(true)
        }
    }
}

/** Tells the user where the edit went. When the PDF was updated in place, offers Undo. */
fun announceSaved(original: File, saved: Library.Saved, reopen: (File) -> Unit) {
    val backup = saved.backup
    if (backup == null) { Toaster.show("Saved as ${saved.file.name}"); return }
    Toaster.show("Saved to ${original.name}", "Undo") {
        Graph.library.appScope.launch {
            val ok = Graph.library.undoSave(original, backup)
            Toaster.show(if (ok) "Changes undone" else "Couldn't undo")
            if (ok) reopen(original)
        }
    }
}
