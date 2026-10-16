package com.phani.pdfkit.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ui.home.HomeAnchors
import com.phani.pdfkit.ui.theme.Pk

private data class Step(val anchor: String, val title: String, val body: String, val round: Boolean = false)

private val STEPS = listOf(
    Step("quick", "Your top tools, one tap away", "The tools people use most. Tap More to see all 17."),
    Step("search", "Find anything fast", "Search your files or jump straight to a tool."),
    Step("fab", "Start something new", "Scan a document, turn photos into a PDF, or import a file.", round = true),
    Step("recent", "Pick up where you left off", "Your latest PDFs live here. Tap ⋮ to share, star or delete."),
    Step("navfiles", "All your files", "Filter PDFs and images, star favourites, switch to grid view."),
)

/** Five-step spotlight tour over the Home screen. Anchors come from [HomeAnchors]. */
@Composable
fun QuickTour(onDone: () -> Unit) {
    val c = Pk.colors
    var i by remember { mutableIntStateOf(0) }
    val steps = STEPS.filter { HomeAnchors.bounds[it.anchor] != null }
    if (steps.isEmpty()) return
    val step = steps[i.coerceAtMost(steps.lastIndex)]
    val target = HomeAnchors.bounds[step.anchor] ?: Rect.Zero
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    val l by animateFloatAsState(target.left - pad, tween(450), label = "l")
    val t by animateFloatAsState(target.top - pad, tween(450), label = "t")
    val r by animateFloatAsState(target.right + pad, tween(450), label = "r")
    val b by animateFloatAsState(target.bottom + pad, tween(450), label = "b")
    val radius by animateFloatAsState(if (step.round) (target.width + 2 * pad) / 2f else with(LocalDensity.current) { 18.dp.toPx() }, tween(450), label = "rad")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "pv")
    var cardH by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0x94101217))
            drawRoundRect(Color.Transparent, Offset(l, t), Size(r - l, b - t), CornerRadius(radius), blendMode = BlendMode.Clear)
            val grow = pulse * 12f
            drawRoundRect(
                Color.White.copy(alpha = (1f - pulse) * 0.85f), Offset(l - grow, t - grow), Size(r - l + 2 * grow, b - t + 2 * grow),
                CornerRadius(radius + grow), style = Stroke(4f),
            )
        }
        val gap = with(LocalDensity.current) { 16.dp.toPx() }
        val below = (t + b) / 2f < hPx / 2f
        val y = if (below) b + gap else t - gap - cardH
        Column(
            Modifier.offset { IntOffset(0, y.toInt().coerceIn(40, (hPx - cardH - 40).toInt().coerceAtLeast(40))) }
                .padding(horizontal = 18.dp).fillMaxWidth()
                .onSizeChanged { cardH = it.height }
                .shadow(20.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(c.card).padding(16.dp),
        ) {
            Text(step.title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(step.body, fontSize = 14.sp, color = c.ink2, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1} of ${steps.size}", fontSize = 12.5.sp, color = c.ink3, modifier = Modifier.weight(1f))
                if (i < steps.lastIndex) Text(
                    "Skip", fontSize = 14.sp, color = c.ink2, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Box(
                    Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(c.blue)
                        .clickable { if (i < steps.lastIndex) i++ else onDone() }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (i < steps.lastIndex) "Next" else "Got it", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}
