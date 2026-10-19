package com.phani.pdfkit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Fmt
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Scales an element down slightly while pressed and runs [onClick]. */
fun Modifier.pressable(
    scaleTo: Float = 0.96f,
    haptic: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    val h = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled) {
            if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** The red PDF document glyph used across the app. */
@Composable
fun PdfGlyph(modifier: Modifier = Modifier, width: Dp = 38.dp) = DocGlyph(modifier, width, Pk.colors.red, Color(0xFFFF9A9A), "PDF")

/** Blue Word document glyph. */
@Composable
fun WordGlyph(modifier: Modifier = Modifier, width: Dp = 38.dp) = DocGlyph(modifier, width, Color(0xFF2B5CD6), Color(0xFF9DB8F5), "DOC")

@Composable
private fun DocGlyph(modifier: Modifier, width: Dp, body: Color, foldColor: Color, label: String) {
    val red = body
    Box(modifier.size(width, width * 1.2f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val r = w * 0.18f; val fold = w * 0.35f
            val body = Path().apply {
                moveTo(r, 0f); lineTo(w - fold, 0f); lineTo(w, fold); lineTo(w, h - r)
                quadraticBezierTo(w, h, w - r, h); lineTo(r, h); quadraticBezierTo(0f, h, 0f, h - r)
                lineTo(0f, r); quadraticBezierTo(0f, 0f, r, 0f); close()
            }
            drawPath(body, red)
            val f = Path().apply { moveTo(w - fold, 0f); lineTo(w - fold, fold * 0.75f); quadraticBezierTo(w - fold, fold, w - fold * 0.75f, fold); lineTo(w, fold); close() }
            drawPath(f, foldColor)
        }
        Text(
            label, color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = (width.value * 0.27f).sp, modifier = Modifier.padding(top = width * 0.28f),
        )
    }
}

/** File thumbnail: the PDF's first page (glyph while it loads), Word glyph, or image preview. */
@Composable
fun FileThumb(doc: DocFile, size: Dp = 40.dp) {
    if (doc.isPdf) {
        val page = com.phani.pdfkit.util.rememberPdfThumb(doc)
        if (page == null) PdfGlyph(width = size * 0.95f)
        else androidx.compose.foundation.Image(
            page, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier.size(size * 0.9f, size * 1.15f).clip(RoundedCornerShape(5.dp))
                .border(1.dp, Pk.colors.line, RoundedCornerShape(5.dp)).background(Color.White),
        )
    }
    else if (doc.isDoc) WordGlyph(width = size * 0.95f)
    else AsyncImage(
        model = File(doc.path), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size + 2.dp).clip(RoundedCornerShape(9.dp)).background(Pk.colors.surf),
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun FileRow(
    doc: DocFile,
    starred: Boolean? = null,
    onStar: (() -> Unit)? = null,
    onMore: () -> Unit,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Pk.colors
    val bg by androidx.compose.animation.animateColorAsState(if (selected == true) c.blueTint else Color.Transparent, label = "rowSel")
    Row(
        Modifier.fillMaxWidth().background(bg).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            FileThumb(doc)
            androidx.compose.animation.AnimatedVisibility(
                selected == true, enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) {
                    Icon(PkIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Fmt.size(doc.bytes)} · ${Fmt.date(doc.modified)}", style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        if (selected == null) {
            if (starred != null && onStar != null) {
                IconBtn(if (starred) PkIcons.StarFilled else PkIcons.Star, tint = if (starred) c.amber else c.ink3, size = 21.dp, onClick = onStar)
            }
            IconBtn(PkIcons.DotsV, tint = c.ink2, size = 20.dp, onClick = onMore)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, tint: Color = Pk.colors.ink, size: Dp = 22.dp, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(size)) }
}

/** Top bar used by pushed screens. */
@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    back: ImageVector = PkIcons.ChevronLeft,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Pk.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(back, size = 26.dp, label = "Back", onClick = onBack) else Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun TintTile(icon: ImageVector, fg: Color, bg: Color, size: Dp = 46.dp, iconSize: Dp = 24.dp, radius: Dp = 12.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Pk.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(13.dp))
            .background(c.blue)
            .pressable(scaleTo = 0.98f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Pk.colors.ink, modifier = modifier)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val c = Pk.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TintTile(icon, c.blue, c.blueTint, size = 64.dp, iconSize = 30.dp, radius = 20.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.padding(top = 16.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = c.ink2, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** App-wide short messages (the prototype's dark toast), with an optional action like Undo. */
data class ToastMsg(val text: String, val id: Long, val action: String? = null, val onAction: (() -> Unit)? = null)

object Toaster {
    val message = MutableStateFlow<ToastMsg?>(null)
    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        message.value = ToastMsg(text, System.nanoTime(), action, onAction)
    }
}

@Composable
fun BoxScope.ToastHost() {
    val msg by Toaster.message.collectAsState()
    LaunchedEffect(msg?.id) {
        val m = msg ?: return@LaunchedEffect
        delay(if (m.action != null) 6000 else 2000)
        if (Toaster.message.value?.id == m.id) Toaster.message.value = null
    }
    var last by remember { mutableStateOf<ToastMsg?>(null) }
    if (msg != null) last = msg
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
    ) {
        val m = last
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xEB101217)).padding(start = 18.dp, end = if (m?.action != null) 6.dp else 18.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m?.text ?: "", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 2, modifier = Modifier.weight(1f, fill = false))
            if (m?.action != null) {
                Text(
                    m.action, color = Color(0xFF8FB1FF), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        m.onAction?.invoke(); Toaster.message.value = null
                    }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
