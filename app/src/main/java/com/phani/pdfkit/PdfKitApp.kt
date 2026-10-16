package com.phani.pdfkit

import android.app.Application
import com.phani.pdfkit.data.Library
import com.phani.pdfkit.data.Prefs

class PdfKitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        Graph.init(this)
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
