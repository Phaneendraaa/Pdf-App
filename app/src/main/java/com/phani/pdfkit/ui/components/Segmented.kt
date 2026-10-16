package com.phani.pdfkit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phani.pdfkit.ui.theme.Pk

/** Segmented control with a sliding blue thumb. */
@Composable
fun Segmented(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val c = Pk.colors
    val idx = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(11.dp)).background(c.surf).padding(3.dp)) {
        val seg = maxWidth / options.size
        val x by animateDpAsState(seg * idx, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(seg).fillMaxHeight().shadow(4.dp, RoundedCornerShape(9.dp), spotColor = c.blue).clip(RoundedCornerShape(9.dp)).background(c.blue))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { o ->
                val on = o == selected
                val col by animateColorAsState(if (on) Color.White else c.ink, label = "segc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) { Text(o, color = col, fontSize = 14.5.sp, maxLines = 1) }
            }
        }
    }
}
