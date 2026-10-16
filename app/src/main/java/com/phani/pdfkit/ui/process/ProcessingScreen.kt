package com.phani.pdfkit.ui.process

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.Graph
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.components.TopBar
import com.phani.pdfkit.ui.icons.PkIcons
import com.phani.pdfkit.ui.theme.Pk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProcessingScreen(onCancel: () -> Unit, onDone: () -> Unit) {
    val c = Pk.colors
    var target by remember { mutableFloatStateOf(0f) }
    val p by animateFloatAsState(target, tween(450, easing = FastOutSlowInEasing), label = "p")

    BackHandler { Toaster.show("Cancelled"); onCancel() }

    LaunchedEffect(Unit) {
        val work = ActiveJob.work ?: run { onCancel(); return@LaunchedEffect }
        target = 0.04f
        try {
            val res = withContext(Dispatchers.IO) { work { f -> target = 0.06f + f.coerceIn(0f, 1f) * 0.86f } }
            if (res == null) {
                Toaster.show("Something went wrong. Please try again.")
                onCancel(); return@LaunchedEffect
            }
            target = 1f
            ActiveJob.result = res
            Graph.library.refresh()
            Graph.prefs.bumpSuccess()
            delay(650)
            onDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Toaster.show(e.message?.take(80) ?: "Something went wrong")
            onCancel()
        }
    }

    val steps = ActiveJob.steps
    val active = when {
        p >= 0.999f -> steps.size
        else -> (p * steps.size).toInt().coerceIn(0, steps.size - 1)
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(ActiveJob.title, back = PkIcons.ArrowLeft, onBack = { Toaster.show("Cancelled"); onCancel() })
        Column(Modifier.fillMaxSize().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                    drawArc(c.blueTint, 0f, 360f, false, style = Stroke(20f))
                    drawArc(c.blue, -90f, 360f * p, false, style = Stroke(20f, cap = StrokeCap.Round))
                }
                Text("${(p * 100).toInt()}%", fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            }
            Text(ActiveJob.heading, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.padding(top = 34.dp))
            Text("Please don't close the app", fontSize = 15.sp, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
            Column(Modifier.padding(top = 40.dp).width(240.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            when {
                                i < active -> Box(Modifier.size(24.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) {
                                    Icon(PkIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                i == active -> CircularProgressIndicator(Modifier.size(20.dp), color = c.blue, trackColor = c.blueTint, strokeWidth = 2.5.dp)
                                else -> Box(Modifier.size(12.dp).clip(CircleShape).background(c.line))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(s, fontSize = 15.5.sp, color = if (i <= active) c.ink else c.ink3)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
