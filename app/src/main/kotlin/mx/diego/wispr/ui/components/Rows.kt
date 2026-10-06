package mx.diego.wispr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.Transaction
import mx.diego.wisprkit.WisprDate
import kotlin.math.abs

/** Color estable por categoría (Wispr no siempre manda uno): el mismo nombre, el mismo color. */
@Composable
fun categoryColor(name: String?, wisprColor: String? = null): Color {
    namedColor(wisprColor)?.let { return it }
    val palette = Wispr.colors.chart
    return if (name == null) MaterialTheme.colorScheme.outline else palette[abs(name.hashCode()) % palette.size]
}

/** Colores con nombre de Tailwind que usa Wispr para sus categorías. */
@Composable
private fun namedColor(name: String?): Color? {
    val c = Wispr.colors
    return when (name) {
        "red", "rose" -> c.bad
        "orange", "amber", "yellow" -> c.warn
        "lime", "green", "emerald" -> c.good
        "teal", "cyan" -> c.flow
        "sky", "blue" -> c.today
        "indigo", "violet" -> c.movements
        "purple", "fuchsia", "pink" -> c.wealth
        else -> null
    }
}

@Composable
fun CategoryDot(name: String?, wisprColor: String? = null, size: Int = 40) {
    val color = categoryColor(name, wisprColor)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name?.firstOrNull()?.uppercase() ?: "·", style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
fun TransactionRow(tx: Transaction, modifier: Modifier = Modifier, showDate: Boolean = false) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryDot(tx.category)
        Column(Modifier.weight(1f)) {
            Text(tx.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                tx.category ?: "Sin categoría",
                tx.account,
                if (showDate) WisprDate.parse(tx.date)?.let(Fmt::dayMonth) else null,
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val income = tx.amount > 0
        Text(
            (if (income) "+" else "") + Fmt.money(tx.amount, tx.currency),
            style = MaterialTheme.typography.titleMedium,
            color = if (income) Wispr.colors.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}
