package com.phani.pdfkit.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.phani.pdfkit.pdf.PdfTools
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Sends a PDF to the system print dialog (printers, "Save as PDF", page ranges). */
object PrintPdf {
    fun print(context: Context, file: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val pages = PdfTools.pageCount(file)
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?,
            ) {
                if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(if (pages > 0) pages else PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
                    true,
                )
            }

            override fun onWrite(
                ranges: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                try {
                    val all = ranges == null || ranges.any { it == PageRange.ALL_PAGES }
                    var source = file
                    var temp: File? = null
                    if (!all && pages > 0) {
                        val idx = ranges!!.flatMap { (it.start..it.end).toList() }.filter { it in 0 until pages }.distinct().sorted()
                        if (idx.isNotEmpty()) {
                            temp = File(context.cacheDir, "print_${System.nanoTime()}.pdf")
                            PdfTools.extract(file, idx, temp)
                            source = temp
                        }
                    }
                    FileInputStream(source).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    temp?.delete()
                    callback.onWriteFinished(if (all) arrayOf(PageRange.ALL_PAGES) else ranges!!.map { it }.toTypedArray())
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }
}
