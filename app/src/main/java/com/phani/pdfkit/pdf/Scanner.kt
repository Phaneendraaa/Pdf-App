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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ML Kit document scanner. Google's camera screen finds the page edges and lets the user crop,
 * rotate and reorder; our review screen then applies the filters and builds the PDF.
 */
object DocScanner {
    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(50)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
        .build()

    fun start(activity: Activity, launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>) {
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { Toaster.show("Scanner isn't available on this phone right now") }
    }

    /** Copies the scanned pages into our own cache so they stay while the user reviews them. */
    suspend fun keep(context: Context, uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "scan").apply { mkdirs() }
        uris.mapIndexedNotNull { i, u ->
            runCatching {
                val f = File(dir, "p${System.currentTimeMillis()}_$i.jpg")
                context.contentResolver.openInputStream(u)?.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                Uri.fromFile(f)
            }.getOrNull()
        }
    }
}

/** One scanned page waiting in the review screen. */
data class ScanPage(val uri: Uri, val filter: String, val turn: Int = 0, val id: Long = ScanDraft.nextId())

/** The scan being reviewed. */
object ScanDraft {
    val pages = mutableStateListOf<ScanPage>()
    /** Filter for new pages; follows the user's last choice. */
    var filter by mutableStateOf("Auto")
    private var counter = 0L
    fun nextId() = ++counter
    fun add(uris: List<Uri>) { pages.addAll(uris.map { ScanPage(it, filter) }) }
    fun clear(context: Context) {
        pages.clear()
        runCatching { File(context.cacheDir, "scan").deleteRecursively() }
    }
}

/** Returns a function that opens the scanner; [onPages] gets the scanned page images. */
@Composable
fun rememberScanner(onPages: (List<Uri>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
        val uris = res?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val kept = DocScanner.keep(ctx, uris)
            if (kept.isEmpty()) Toaster.show("Couldn't read the scan") else onPages(kept)
        }
    }
    return { (ctx as? Activity)?.let { DocScanner.start(it, launcher) } }
}

/** Builds the PDF from the reviewed pages. */
suspend fun saveScan(context: Context, pages: List<ScanPage>, quality: Quality, report: (Float) -> Unit): JobResult? {
    val lib = Graph.library
    val out = lib.newFile(lib.stampedName("Scan"))
    val n = ImagesToPdf.create(
        context, pages.map { it.uri }, out, PageOptions(size = "Fit to image"), quality, report,
        rotations = pages.associate { it.uri.toString() to it.turn },
        filters = pages.associate { it.uri.toString() to it.filter },
    )
    if (n == 0) { out.delete(); return null }
    if (Graph.prefs.settings.first().searchableScans) SearchablePdf.inBackground(out)
    return JobResult(out, n, "Scan saved!", again = "scan", againLabel = "Scan another document")
}

/** A recognized line of text and its box in the bitmap, in pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** On-device text recognition (Latin script). */
object Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = client.process(InputImage.fromBitmap(bitmap, 0)).await().text

    suspend fun lines(bitmap: Bitmap): List<OcrLine> =
        client.process(InputImage.fromBitmap(bitmap, 0)).await().textBlocks.flatMap { b ->
            b.lines.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.right, it.bottom) } }
        }

    suspend fun read(context: Context, uri: Uri): String = client.process(InputImage.fromFilePath(context, uri)).await().text
}
