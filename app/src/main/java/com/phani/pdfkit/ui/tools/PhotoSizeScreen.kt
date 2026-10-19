package com.phani.pdfkit.ui.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.pdf.Bitmaps
import com.phani.pdfkit.pdf.Filters
import com.phani.pdfkit.pdf.JobResult
import com.phani.pdfkit.pdf.PhotoSize
import com.phani.pdfkit.pdf.rememberScanner
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/** Presets for common Indian exam and job forms. Always check the form's own notice. */
private data class PhotoType(val name: String, val w: Int, val h: Int, val minKb: Int, val maxKb: Int, val clean: Boolean)

private val TYPES = listOf(
    PhotoType("Photo", 350, 450, 20, 50, false),
    PhotoType("Signature", 350, 150, 10, 20, true),
    PhotoType("Thumb", 300, 300, 20, 50, true),
    PhotoType("Custom", 600, 800, 50, 200, false),
)

/** Photo, signature or thumb impression at the exact size and KB range a form asks for. */
@Composable
fun PhotoSizeScreen(onBack: () -> Unit, onStart: () -> Unit) {
    val c = Pk.colors
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var type by remember { mutableStateOf(TYPES[0]) }
    var w by remember { mutableStateOf("350") }
    var h by remember { mutableStateOf("450") }
    var minKb by remember { mutableStateOf("20") }
    var maxKb by remember { mutableStateOf("50") }
    var clean by remember { mutableStateOf(false) }
    // Crop frame as fractions of the picture: left, top and width (height follows the ratio).
    var fl by remember { mutableFloatStateOf(0f) }
    var ft by remember { mutableFloatStateOf(0f) }
    var fw by remember { mutableFloatStateOf(1f) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) uri = u }
    val scan = rememberScanner { uris -> uris.firstOrNull()?.let { uri = it } }
    val preview by androidx.compose.runtime.produceState<Bitmap?>(null, uri) {
        value = uri?.let { u -> withContext(Dispatchers.IO) { Bitmaps.decode(ctx, u, 1400) } }
    }

    val outW = w.toIntOrNull()?.takeIf { it in 40..4000 }
    val outH = h.toIntOrNull()?.takeIf { it in 40..4000 }
    val ratio = if (outW != null && outH != null) outW.toFloat() / outH else 0.78f
    val bmp = preview
    // Frame height as a fraction of the picture's height.
    fun fh(): Float = if (bmp == null) 1f else fw * bmp.width / ratio / bmp.height

    fun fitFrame() {
        val b = bmp ?: return
        val imgRatio = b.width.toFloat() / b.height
        fw = if (ratio > imgRatio) 0.9f else 0.9f * ratio / imgRatio
        fl = (1f - fw) / 2f; ft = (1f - fh()) / 2f
    }
    LaunchedEffect(bmp, ratio) { fitFrame() }

    fun applyType(t: PhotoType) {
        type = t; w = t.w.toString(); h = t.h.toString(); minKb = t.minKb.toString(); maxKb = t.maxKb.toString(); clean = t.clean
    }

    val ready = uri != null && outW != null && outH != null && (minKb.toIntOrNull() ?: -1) >= 0 && (maxKb.toIntOrNull() ?: 0) > (minKb.toIntOrNull() ?: 0)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Photo & Signature", subtitle = "Exact size for exam and job forms", back = PkIcons.ArrowLeft, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            if (bmp == null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.surf).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Pick a photo, or scan your signature on white paper.", color = c.ink2, fontSize = 14.5.sp)
                    PrimaryButton("Pick from gallery") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Text(
                        "Scan with camera", color = c.blue, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { scan() }.padding(12.dp),
                    )
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp).padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    val boxRatio = maxWidth / maxHeight
                    val imgRatio = bmp.width.toFloat() / bmp.height
                    val dw = if (imgRatio > boxRatio) maxWidth else maxHeight * imgRatio
                    val dh = if (imgRatio > boxRatio) maxWidth / imgRatio else maxHeight
                    val density = LocalDensity.current
                    val wPx = with(density) { dw.toPx() }; val hPx = with(density) { dh.toPx() }
                    val grab = with(density) { 36.dp.toPx() }
                    Box(
                        Modifier.size(dw, dh).pointerInput(ratio) {
                            var resize = false
                            detectDragGestures(
                                onDragStart = { o ->
                                    val cx = (fl + fw) * wPx; val cy = (ft + fh()) * hPx
                                    resize = abs(o.x - cx) < grab && abs(o.y - cy) < grab
                                },
                                onDrag = { ch, d ->
                                    ch.consume()
                                    if (resize) {
                                        val maxW = min(1f - fl, (1f - ft) * bmp.height * ratio / bmp.width)
                                        fw = (fw + d.x / wPx).coerceIn(0.15f, maxW)
                                    } else {
                                        fl = (fl + d.x / wPx).coerceIn(0f, 1f - fw)
                                        ft = (ft + d.y / hPx).coerceIn(0f, 1f - fh())
                                    }
                                },
                            )
                        },
                    ) {
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) {
                            val x0 = fl * size.width; val y0 = ft * size.height
                            val x1 = (fl + fw) * size.width; val y1 = (ft + fh()) * size.height
                            val dim = Color(0x99101217)
                            drawRect(dim, Offset.Zero, Size(size.width, y0))
                            drawRect(dim, Offset(0f, y1), Size(size.width, size.height - y1))
                            drawRect(dim, Offset(0f, y0), Size(x0, y1 - y0))
                            drawRect(dim, Offset(x1, y0), Size(size.width - x1, y1 - y0))
                            drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2.dp.toPx()))
                            drawCircle(Color.White, 9.dp.toPx(), Offset(x1, y1))
                            drawCircle(c.blue, 9.dp.toPx(), Offset(x1, y1), style = Stroke(2.5.dp.toPx()))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Drag to move · corner to resize", fontSize = 12.5.sp, color = c.ink3)
                    Text("Change", fontSize = 13.sp, color = c.blue, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { uri = null })
                }
            }

            OptLabel("What is it?")
            ChipRow(TYPES.map { it.name }, type.name) { n -> applyType(TYPES.first { it.name == n }) }
            OptLabel("Size in pixels (width × height)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NumberField(w) { w = it } }
                Text("×", color = c.ink2)
                Box(Modifier.weight(1f)) { NumberField(h) { h = it } }
            }
            OptLabel("File size in KB (min – max)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NumberField(minKb) { minKb = it } }
                Text("–", color = c.ink2)
                Box(Modifier.weight(1f)) { NumberField(maxKb) { maxKb = it } }
            }
            SwitchRow("Clean white background", "Removes shadows and paper color, good for signatures", clean) { clean = it }
            OptHint("Common limits: photo 20–50 KB, signature 10–20 KB. Always check your form's own instructions.")
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
            PrimaryButton("Make JPG", enabled = ready) {
                val u = uri ?: return@PrimaryButton
                val crop = floatArrayOf(fl, ft, fw, fh())
                val ow = outW ?: return@PrimaryButton; val oh = outH ?: return@PrimaryButton
                val lo = minKb.toIntOrNull() ?: 0; val hi = maxKb.toIntOrNull() ?: 50
                val name = type.name.let { if (it == "Custom") "Photo" else it }
                val cleanNow = clean
                ActiveJob.set("Making $name", "Sizing your ${name.lowercase()}...", listOf("Cropping", "Finding the best quality", "Saving JPG")) { r ->
                    withContext(Dispatchers.Default) {
                        val full = Bitmaps.decode(ctx, u, 3000) ?: return@withContext null
                        r(0.3f)
                        val x = (crop[0] * full.width).toInt().coerceIn(0, full.width - 1)
                        val y = (crop[1] * full.height).toInt().coerceIn(0, full.height - 1)
                        val cw = (crop[2] * full.width).toInt().coerceIn(1, full.width - x)
                        val chh = (crop[3] * full.height).toInt().coerceIn(1, full.height - y)
                        val cut = Bitmap.createBitmap(full, x, y, cw, chh)
                        val look = if (cleanNow) Filters.apply(cut, "Auto") else cut
                        val lib = Graph.library
                        val out = lib.newFile(lib.stampedName(name), "jpg")
                        val res = PhotoSize.make(look, ow, oh, lo, hi, out)
                        r(1f); lib.refreshBlocking()
                        JobResult(
                            res.file, 1, "$name ready!",
                            detail = "${Fmt.size(res.bytes.toLong())} · ${res.width} × ${res.height} px · JPG",
                            note = if (res.resized) "Pixel size changed to fit $lo–$hi KB" else null,
                            again = "photosize", againLabel = "Make another",
                        )
                    }
                }
                onStart()
            }
        }
    }
}
