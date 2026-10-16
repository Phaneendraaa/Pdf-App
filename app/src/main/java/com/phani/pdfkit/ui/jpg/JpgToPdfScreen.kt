package com.phani.pdfkit.ui.jpg

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.pdf.JpgDraft
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.components.Segmented
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.components.pressable
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun JpgToPdfScreen(settings: Settings, onBack: () -> Unit, onCreate: () -> Unit) {
    val c = Pk.colors
    val images = JpgDraft.images
    val opts = JpgDraft.options
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { uris ->
        val fresh = uris.filter { it !in images }
        images.addAll(fresh)
        if (images.isEmpty()) onBack()
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(Unit) { if (images.isEmpty()) pick() }

    val hintSeen = "jpg_drag" in settings.hintsSeen
    var hintVisible by remember { mutableStateOf(!hintSeen) }
    val dismissHint = { if (hintVisible) { hintVisible = false; scope.launch { Graph.prefs.markHint("jpg_drag") } } }

    var optionsOpen by remember { mutableStateOf(true) }
    val grid = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(
            "JPG to PDF",
            subtitle = "${images.size} image${if (images.size == 1) "" else "s"} selected",
            back = PkIcons.ArrowLeft, onBack = onBack,
        )
        AnimatedVisibility(hintVisible && images.size > 1, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.blueTint)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PkIcons.Grip, null, tint = c.blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Hold the dots and drag to reorder. Tap × to remove.", fontSize = 13.5.sp, color = c.ink, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { dismissHint() }, contentAlignment = Alignment.Center) {
                    Icon(PkIcons.Close, "Dismiss", tint = c.ink2, modifier = Modifier.size(15.dp))
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(images, key = { _, u -> u.toString() }) { i, u ->
                val key = u.toString()
                val dragging = dragKey == key
                val lift by animateFloatAsState(if (dragging) 1.06f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
                Box(
                    Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .zIndex(if (dragging) 2f else 0f)
                        .graphicsLayer {
                            scaleX = lift; scaleY = lift
                            if (dragging) { translationX = dragOffset.x; translationY = dragOffset.y }
                        }
                        .aspectRatio(1f / 1.08f)
                        .shadow(if (dragging) 16.dp else 0.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surf),
                ) {
                    AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.padding(7.dp).size(24.dp).clip(CircleShape).background(if (dragging) c.blue else Color(0x9E101217)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                            .background(Color.White).clickable { images.remove(u) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(PkIcons.Close, "Remove", tint = Color(0xFF101217), modifier = Modifier.size(14.dp)) }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(40.dp)
                            .pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = key; dragOffset = Offset.Zero
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dismissHint()
                                    },
                                    onDragEnd = { dragKey = null; dragOffset = Offset.Zero },
                                    onDragCancel = { dragKey = null; dragOffset = Offset.Zero },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val info = grid.layoutInfo.visibleItemsInfo
                                    val cur = info.firstOrNull { it.key == key } ?: return@detectDragGestures
                                    val center = cur.offset.toOffset() + dragOffset + Offset(cur.size.width / 2f, cur.size.height / 2f)
                                    val target = info.firstOrNull {
                                        it.key != key && it.key != "add" &&
                                            center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                                            center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
                                    }
                                    if (target != null && target.index < images.size && cur.index < images.size) {
                                        images.add(target.index, images.removeAt(cur.index))
                                        dragOffset += (cur.offset - target.offset).toOffset()
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(PkIcons.Grip, "Drag to reorder", tint = Color.White, modifier = Modifier.size(18.dp).shadow(2.dp, CircleShape, clip = false))
                    }
                }
            }
            item(key = "add") {
                Column(
                    Modifier.animateItem().aspectRatio(1f / 1.08f).clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, c.line, RoundedCornerShape(12.dp)).background(c.card).clickable { pick() },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(PkIcons.Plus, null, tint = c.ink2, modifier = Modifier.size(28.dp))
                    Text("Add more", fontSize = 13.5.sp, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Options sheet
        Column(
            Modifier.fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = Color(0x22101217), spotColor = Color(0x33101217))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.card)
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { optionsOpen = !optionsOpen }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(4.5.dp).clip(RoundedCornerShape(9.dp)).background(c.line))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("PDF Options", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                if (images.isNotEmpty()) {
                    val mb = images.size * when (settings.compression) { "Low" -> 0.18; "High" -> 0.6; else -> 0.33 }
                    Text(
                        String.format(Locale.US, "≈ %.1f MB · %d page%s", mb, images.size, if (images.size == 1) "" else "s"),
                        fontSize = 12.5.sp, color = c.ink2,
                    )
                }
            }
            AnimatedVisibility(optionsOpen) {
                Column {
                    Label("Page size")
                    Segmented(listOf("A4", "Letter", "Fit to image"), opts.size) { JpgDraft.options = opts.copy(size = it) }
                    Label("Margins")
                    Segmented(listOf("None", "Small", "Large"), opts.margin) { JpgDraft.options = opts.copy(margin = it) }
                    Label("Orientation")
                    Segmented(listOf("Portrait", "Landscape"), opts.orientation) { JpgDraft.options = opts.copy(orientation = it) }
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (images.isEmpty()) "Add images to continue" else "Create PDF (${images.size} image${if (images.size == 1) "" else "s"})",
                enabled = images.isNotEmpty(), onClick = onCreate,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = Pk.colors.ink, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Suppress("unused")
private fun Uri.short() = lastPathSegment ?: toString()
