package com.phani.pdfkit.pdf

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.phani.pdfkit.Graph
import com.phani.pdfkit.ui.components.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** ML Kit document scanner. Runs through Google Play services, so it adds almost nothing to the APK. */
object DocScanner {
    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(40)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    fun start(activity: Activity, launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>) {
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { Toaster.show("Scanner isn't available on this phone right now") }
    }

    /** Copies the scanned PDF into the library. */
    suspend fun save(context: Context, uri: Uri, pages: Int): JobResult? = withContext(Dispatchers.IO) {
        runCatching {
            val lib = Graph.library
            val out = lib.newFile(lib.stampedName("Scan"))
            context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
            lib.refreshBlocking()
            JobResult(out, pages, "Scan saved!", again = "scan", againLabel = "Scan another document")
        }.getOrNull()
    }
}

/** Returns a function that opens the scanner; [onSaved] gets the result after the PDF is in the library. */
@Composable
fun rememberScanner(onSaved: (JobResult) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
        val pdf = res?.pdf ?: return@rememberLauncherForActivityResult
        scope.launch {
            val job = DocScanner.save(ctx, pdf.uri, pdf.pageCount)
            if (job != null) { Graph.prefs.bumpSuccess(); onSaved(job) } else Toaster.show("Couldn't save the scan")
        }
    }
    return { (ctx as? Activity)?.let { DocScanner.start(it, launcher) } }
}

/** On-device text recognition (Latin script). */
object Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = client.process(InputImage.fromBitmap(bitmap, 0)).await().text

    suspend fun read(context: Context, uri: Uri): String = client.process(InputImage.fromFilePath(context, uri)).await().text
}
