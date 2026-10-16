package com.phani.pdfkit

import android.app.Application
import com.phani.pdfkit.data.Library
import com.phani.pdfkit.data.Prefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PdfKitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        Graph.init(this)
        createSampleOnce()
    }

    /** First launch: drop a sample PDF in the library so every tool has something to try. */
    private fun createSampleOnce() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val s = Graph.prefs.settings.first()
            if ("sample" in s.hintsSeen) return@launch
            runCatching {
                val f = java.io.File(Graph.library.dir, "Welcome to Pdf Kit.pdf")
                if (!f.exists()) com.phani.pdfkit.pdf.SampleDoc.create(f)
                Graph.library.refreshBlocking()
            }
            Graph.prefs.markHint("sample")
        }
    }
}

/** Tiny service locator: one Library and one Prefs for the whole app. */
object Graph {
    lateinit var library: Library
        private set
    lateinit var prefs: Prefs
        private set

    fun init(app: Application) {
        library = Library(app)
        prefs = Prefs(app)
    }
}
