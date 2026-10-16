package com.phani.pdfkit.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ui.components.PdfGlyph
import com.phani.pdfkit.ui.components.PrimaryButton
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import com.phani.pdfkit.ui.theme.tint
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val c = Pk.colors
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == 2

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfGlyph(width = 20.dp)
            Text("Pdf Kit", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.padding(start = 8.dp).weight(1f))
            val skipAlpha by animateFloatAsState(if (last) 0f else 1f, label = "skip")
            Text(
                "Skip", fontSize = 15.sp, color = c.ink2, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer { alpha = skipAlpha }.clip(RoundedCornerShape(8.dp)).clickable(enabled = !last, onClick = onFinish).padding(8.dp),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue
            val shown = (1f - offset).coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp).graphicsLayer { alpha = 0.3f + 0.7f * shown },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(320.dp).graphicsLayer { translationY = (1 - shown) * 40f }, contentAlignment = Alignment.Center) {
                    when (page) { 0 -> ToolsArt(); 1 -> PrivacyArt(); else -> SimpleArt() }
                }
                val (title, body) = when (page) {
                    0 -> "Every PDF tool, in your pocket" to "Convert, merge, compress, sign and scan. All in one clean app."
                    1 -> "Your files never leave your phone" to "Everything runs on your device. Nothing is uploaded, ever. Not even to us."
                    else -> "Simple by design" to "Pick your photos, tap once, and get a clean PDF. No menus to dig through."
                }
                Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center, lineHeight = 32.sp, modifier = Modifier.padding(top = 24.dp).graphicsLayer { translationY = (1 - shown) * 60f })
                Text(body, fontSize = 15.5.sp, color = c.ink2, textAlign = TextAlign.Center, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp).width(300.dp).graphicsLayer { translationY = (1 - shown) * 80f })
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 26.dp else 7.dp, tween(380, easing = FastOutSlowInEasing), label = "dot")
                Box(Modifier.padding(horizontal = 3.5.dp).height(7.dp).width(w).clip(RoundedCornerShape(9.dp)).background(if (pager.currentPage == i) c.blue else c.line))
            }
        }
        PrimaryButton(if (last) "Get started" else "Next", Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

@Composable
private fun Floaty(delay: Int, content: @Composable (Float) -> Unit) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(0f, -8f, infiniteRepeatable(tween(2000, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    content(y)
}

@Composable
private fun BoxScope.Orb(icon: ImageVector, fg: Long, bg: Long, x: Dp, y: Dp, delay: Int) {
    val c = Pk.colors
    val tn = tint(fg, bg, c.isDark)
    Floaty(delay) { dy ->
        Box(
            Modifier.offset(x, y).graphicsLayer { translationY = dy }.size(58.dp).shadow(10.dp, RoundedCornerShape(17.dp), spotColor = Color(0x44101217))
                .clip(RoundedCornerShape(17.dp)).background(if (c.isDark) c.card else Color(bg)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tn.fg, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun ToolsArt() {
    val c = Pk.colors
    Box(Modifier.size(320.dp)) {
        Box(Modifier.align(Alignment.Center).size(260.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.blueTint, Color.Transparent))))
        Column(
            Modifier.align(Alignment.Center).size(128.dp, 160.dp).shadow(20.dp, RoundedCornerShape(14.dp), spotColor = c.blue).clip(RoundedCornerShape(14.dp)).background(c.card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PdfGlyph(width = 30.dp)
            listOf(1f, 1f, 0.6f, 1f, 0.6f).forEach { f -> Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.line)) }
        }
        Orb(PkIcons.Image, 0xFF2463EB, 0xFFEEF3FF, 10.dp, 40.dp, 0)
        Orb(PkIcons.Layers, 0xFF16A34A, 0xFFECF8F1, 240.dp, 26.dp, 600)
        Orb(PkIcons.Compress, 0xFF2463EB, 0xFFEEF3FF, 258.dp, 150.dp, 1200)
        Orb(PkIcons.Signature, 0xFF7C3AED, 0xFFF4EFFF, 232.dp, 252.dp, 300)
        Orb(PkIcons.Scissors, 0xFFEA580C, 0xFFFFF3EB, 18.dp, 240.dp, 900)
        Orb(PkIcons.Scan, 0xFFE11D48, 0xFFFEEFF2, 0.dp, 140.dp, 1500)
    }
}

@Composable
private fun PrivacyArt() {
    val c = Pk.colors
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "p")
    Box(Modifier.size(320.dp)) {
        Canvas(Modifier.align(Alignment.Center).size(260.dp)) {
            listOf(p, (p + 0.5f) % 1f).forEach { v ->
                drawCircle(c.green.copy(alpha = (1f - v) * 0.35f), radius = size.minDimension / 2f * (0.55f + 0.45f * v), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(124.dp).shadow(20.dp, CircleShape, spotColor = c.green).clip(CircleShape).background(c.greenTint).padding(10.dp).clip(CircleShape).background(c.card),
            contentAlignment = Alignment.Center,
        ) { Icon(PkIcons.Shield, null, tint = c.green, modifier = Modifier.size(56.dp)) }
        Tag(PkIcons.CloudOff, "No uploads", Modifier.offset(0.dp, 46.dp), 0)
        Tag(PkIcons.UserX, "No sign-up", Modifier.align(Alignment.TopEnd).offset(0.dp, 120.dp), 500)
        Tag(PkIcons.WifiOff, "Works offline", Modifier.align(Alignment.BottomStart).offset(18.dp, (-44).dp), 1000)
    }
}

@Composable
private fun Tag(icon: ImageVector, text: String, modifier: Modifier, delay: Int) {
    val c = Pk.colors
    Floaty(delay) { dy ->
        Row(
            modifier.graphicsLayer { translationY = dy }.shadow(10.dp, RoundedCornerShape(99.dp), spotColor = Color(0x44101217)).clip(RoundedCornerShape(99.dp)).background(c.card)
                .padding(start = 10.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = c.green, modifier = Modifier.size(17.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(start = 7.dp))
        }
    }
}

@Composable
private fun SimpleArt() {
    val c = Pk.colors
    val photos = listOf(Color(0xFF8EC5FF) to Color(0xFF6E7F96), Color(0xFF2C5E39) to Color(0xFF4FA35F), Color(0xFFE5D6BE) to Color(0xFFB79066))
    Box(Modifier.size(320.dp)) {
        photos.forEachIndexed { i, (a, b) ->
            Box(
                Modifier.offset(8.dp + (i * 16).dp, 70.dp + (i * 36).dp).rotate(listOf(-10f, 4f, -3f)[i]).size(86.dp, 104.dp)
                    .shadow(12.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(3.dp)
                    .clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(a, b))),
            )
        }
        Box(
            Modifier.align(Alignment.Center).offset(x = 14.dp).size(44.dp).shadow(10.dp, CircleShape, spotColor = c.blue).clip(CircleShape).background(c.blue),
            contentAlignment = Alignment.Center,
        ) { Icon(PkIcons.ArrowRight, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
            Column(
                Modifier.size(104.dp, 132.dp).shadow(18.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(c.card),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                PdfGlyph(width = 38.dp)
                Text("3 pages", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp).size(30.dp).clip(CircleShape).background(c.card).padding(3.dp).clip(CircleShape).background(c.green), contentAlignment = Alignment.Center) {
                Icon(PkIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fewer taps", "Lightweight", "No clutter").forEach {
                Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.blue, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.blueTint).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}
