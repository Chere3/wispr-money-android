package mx.diego.wispr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Animación de entrada de izquierda a derecha, como las gráficas de Google Health. */
@Composable
private fun rememberReveal(key: Any?): Float {
    val reveal = remember(key) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(key) { reveal.animateTo(1f, spec) }
    return reveal.value
}

data class BarGroup(val label: String, val values: List<Double>)

/** Barras agrupadas (p. ej. ingresos vs gastos por mes). Valores en unidades mayores. */
@Composable
fun GroupedBars(
    groups: List<BarGroup>,
    colors: List<Color>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberReveal(groups)
    val max = groups.flatMap { it.values }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / groups.size.coerceAtLeast(1)
            val barCount = colors.size
            val gap = 4.dp.toPx()
            val bar = ((slot * 0.6f) - gap * (barCount - 1)) / barCount
            for (i in 0..3) {
                val y = size.height * i / 3
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            groups.forEachIndexed { g, group ->
                val visible = (reveal * groups.size - g).coerceIn(0f, 1f)
                var x = slot * g + slot * 0.2f
                group.values.forEachIndexed { b, value ->
                    val h = (value / max).toFloat().coerceAtLeast(0f) * size.height * visible
                    drawRoundRect(colors[b % colors.size], Offset(x, size.height - h), Size(bar, h),
                        CornerRadius(bar / 2, bar / 2))
                    x += bar + gap
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach {
                Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Línea con área suave (patrimonio). Valores en unidades mayores; marca el cero si cruza. */
@Composable
fun AreaLine(
    values: List<Double>,
    labels: List<String>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberReveal(values)
    val zero = MaterialTheme.colorScheme.outline
    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (values.size < 2) return@Canvas
            val min = minOf(values.min(), 0.0)
            val max = maxOf(values.max(), 0.0)
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
            val step = size.width / (values.size - 1)
            val points = values.mapIndexed { i, v -> Offset(step * i, y(v)) }
            val line = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p = points[i - 1]
                    val c = points[i]
                    val mid = (p.x + c.x) / 2
                    cubicTo(mid, p.y, mid, c.y, c.x, c.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            clipRect(right = size.width * reveal) {
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f))))
                drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            if (min < 0) {
                drawLine(zero, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            drawCircle(color, 5.dp.toPx(), points.last())
            drawCircle(Color.White, 2.5.dp.toPx(), points.last())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
