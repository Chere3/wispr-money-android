package mx.diego.wispr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.diego.wispr.ui.theme.Wispr
import kotlin.math.abs

/**
 * Página estilo Google Health: un velo del color de la sección arriba, que se funde con el fondo,
 * y debajo tarjetas blancas muy redondeadas.
 */
@Composable
fun SectionPage(
    /** `null`: sin velo (cuando el velo ya lo pinta un contenedor de arriba). */
    accent: Color?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxSize()
            .then(
                if (accent == null) Modifier
                else Modifier.background(Brush.verticalGradient(0f to accent.copy(alpha = 0.22f), 0.45f to surface, 1f to surface))
            ),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Cabecera de una sección: etiqueta pequeña, la cifra protagonista y una línea de contexto. */
@Composable
fun Hero(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displayMedium, maxLines = 1)
            below()
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    title?.let { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)) }
                    action?.invoke(this)
                }
            }
            content()
        }
    }
}

/** Tile pequeño de métrica, como los de "Hoy" en Google Health. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent, size = 32)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            footer?.invoke()
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, accent: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = accent, modifier = Modifier.size((size * 0.55f).dp)) }
}

/** Variación contra el periodo anterior. `higherIsBetter` decide si subir es verde o rojo. */
@Composable
fun Delta(current: Long, previous: Long, higherIsBetter: Boolean, against: String, modifier: Modifier = Modifier) {
    if (previous == 0L) {
        Text("Sin datos de $against", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val change = (current - previous).toDouble() / abs(previous)
    val up = change >= 0
    val good = up == higherIsBetter
    val color = if (good) Wispr.colors.good else Wispr.colors.bad
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null,
                tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("${Fmt.percent(abs(change))} vs $against", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/** Barra horizontal de proporción (desglose por categoría). */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(color.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Stamp(at: java.time.LocalTime, modifier: Modifier = Modifier) {
    Text("En vivo · consultado a las ${Fmt.time(at)}", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(horizontal = 8.dp))
}
