package com.phani.pdfkit.pdf

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Result shown on the success screen. */
data class JobResult(
    val file: File,
    val pages: Int,
    val title: String = "PDF created successfully!",
    val note: String? = null,          // e.g. "12.4 MB → 3.1 MB · 75% smaller"
    val again: String? = null,         // route for "Create another"
    val againLabel: String = "Create another PDF",
    val detail: String? = null,        // replaces "size · N pages" when set (e.g. "8 images")
)

/**
 * The one long-running task the Processing screen runs. Tools fill this in and
 * navigate to Processing; Processing runs [work] and hands the result to Success.
 */
object ActiveJob {
    var title: String = "Creating PDF"
    var heading: String = "Creating your PDF..."
    var steps: List<String> = listOf("Preparing", "Working", "Finalizing")
    var work: (suspend (report: (Float) -> Unit) -> JobResult?)? = null
    var result: JobResult? = null

    fun set(title: String, heading: String, steps: List<String>, work: suspend (report: (Float) -> Unit) -> JobResult?) {
        this.title = title; this.heading = heading; this.steps = steps; this.work = work; this.result = null
    }
}

/** Draft for the JPG to PDF flow, kept while the app is open so it can be resumed. */
object JpgDraft {
    val images = mutableStateListOf<Uri>()
    var options by mutableStateOf(PageOptions())
    fun clear() { images.clear(); options = PageOptions() }
}
