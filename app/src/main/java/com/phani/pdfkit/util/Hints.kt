package com.phani.pdfkit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Settings
import kotlinx.coroutines.launch

/** One-time contextual hint. Returns (visible, dismiss). Hidden until settings load so it never flashes. */
@Composable
fun rememberHint(id: String): Pair<Boolean, () -> Unit> {
    val settings by Graph.prefs.settings.collectAsState(initial = null as Settings?)
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    val visible = settings != null && id !in settings!!.hintsSeen && !dismissed
    return visible to { if (!dismissed) { dismissed = true; scope.launch { Graph.prefs.markHint(id) } } }
}
