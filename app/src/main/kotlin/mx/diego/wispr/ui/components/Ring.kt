package mx.diego.wispr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.diego.wispr.ui.theme.Wispr

/**
 * Anillo de presupuesto estilo Google Health. Pasado el 100 % da una segunda vuelta más oscura,
 * así el exceso se lee por la forma y no solo por el color.
 */
@Composable
fun BudgetRing(
    fraction: Double?,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    color: Color = Wispr.tone(fraction),
) {
    val target = (fraction ?: 0.0).coerceIn(0.0, 2.0).toFloat()
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) { progress.animateTo(target, spec) }
    val track = color.copy(alpha = 0.18f)
    val dark = lerp(color, Color.Black, 0.3f)
    Canvas(modifier) {
        val width = stroke.toPx()
        drawRing(track, 0f, 1f, width)
        val value = progress.value
        drawRing(color, 0f, value.coerceAtMost(1f), width)
        if (value > 1f) {
            drawRing(Color.Black.copy(alpha = 0.18f), 0f, (value - 1f).coerceAtMost(1f), width * 1.25f)
            drawRing(dark, 0f, (value - 1f).coerceAtMost(1f), width)
        }
    }
}

private fun DrawScope.drawRing(color: Color, from: Float, to: Float, width: Float) {
    if (to <= from) return
    val inset = width / 2
    drawArc(
        color = color,
        startAngle = -90f + 360f * from,
        sweepAngle = 360f * (to - from),
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        style = Stroke(width, cap = StrokeCap.Round),
    )
}

/** Anillo con el porcentaje al centro. */
@Composable
fun BudgetRingLabel(
    fraction: Double?,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Box(modifier.clearAndSetSemantics { contentDescription = "${Fmt.percent(fraction)} gastado" }, contentAlignment = Alignment.Center) {
        BudgetRing(fraction, Modifier.matchParentSize(), stroke)
        Text(Fmt.percent(fraction), style = style, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(stroke))
    }
}
