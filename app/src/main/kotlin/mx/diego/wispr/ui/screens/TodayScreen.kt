package mx.diego.wispr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.diego.wispr.data.Glance
import mx.diego.wispr.ui.components.BudgetRingLabel
import mx.diego.wispr.ui.components.Delta
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.Hero
import mx.diego.wispr.ui.components.LiveContent
import mx.diego.wispr.ui.components.MetricTile
import mx.diego.wispr.ui.components.SectionCard
import mx.diego.wispr.ui.components.SectionPage
import mx.diego.wispr.ui.components.Stamp
import mx.diego.wispr.ui.components.TransactionRow
import mx.diego.wispr.ui.components.rememberLive
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.Budget
import mx.diego.wisprkit.Transaction
import mx.diego.wisprkit.TransactionFilter
import java.time.LocalTime
import java.time.YearMonth

data class TodaySnapshot(val glance: Glance, val recent: List<Transaction>)

@Composable
fun TodayScreen(onSeeMovements: () -> Unit) {
    val state = rememberLive {
        coroutineScope {
            val glance = async { Glance.fetch(this@rememberLive) }
            val recent = async { transactions(TransactionFilter(limit = 6)).transactions }
            TodaySnapshot(glance.await(), recent.await())
        }
    }
    LiveContent(state) { snapshot, at -> TodayContent(snapshot, at, onSeeMovements) }
}

@Composable
fun TodayContent(snapshot: TodaySnapshot, at: LocalTime, onSeeMovements: () -> Unit) {
    val g = snapshot.glance
    val summary = g.summary
    val month = YearMonth.from(g.fetchedAt)
    var open by remember { mutableStateOf<Budget?>(null) }

    SectionPage(Wispr.colors.today) {
        item {
            Hero(
                label = "Gastado en ${Fmt.month(month, withYear = false).lowercase()}",
                value = Fmt.big(g.monthExpense, g.currency),
                trailing = {
                    BudgetRingLabel(summary.fraction, Modifier.size(96.dp), stroke = 12.dp,
                        style = MaterialTheme.typography.titleLarge)
                },
            ) {
                Delta(g.monthExpense, g.previousExpense, higherIsBetter = false,
                    against = Fmt.month(month.minusMonths(1), withYear = false).lowercase())
            }
        }
        item { BudgetsCard(g, onOpen = { open = it }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Ingresos", Fmt.big(g.monthIncome, g.currency), Icons.Rounded.SouthWest,
                    Wispr.colors.income, Modifier.weight(1f))
                val net = g.monthIncome - g.monthExpense
                MetricTile("Neto del mes", Fmt.big(net, g.currency), Icons.Rounded.Savings,
                    if (net < 0) Wispr.colors.bad else Wispr.colors.flow, Modifier.weight(1f))
            }
        }
        item {
            SectionCard(title = "Últimos movimientos", action = { TextButton(onClick = onSeeMovements) { Text("Ver todo") } }) {
                if (snapshot.recent.isEmpty()) {
                    Text("Sin movimientos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                snapshot.recent.forEachIndexed { i, tx ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    TransactionRow(tx, showDate = true)
                }
            }
        }
        item { Stamp(at) }
    }

    open?.let { budget -> BudgetSheet(budget, g.currency) { open = null } }
}

@Composable
private fun BudgetsCard(g: Glance, onOpen: (Budget) -> Unit) {
    val s = g.summary
    SectionCard(title = "Presupuestos") {
        if (s.activeCount == 0) {
            Text("No hay presupuestos con periodo activo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val left = if (s.remaining >= 0) "Quedan ${Fmt.big(s.remaining, g.currency)}"
        else "Excedido por ${Fmt.big(-s.remaining, g.currency)}"
        val days = s.daysLeft?.let { " · $it ${if (it == 1) "día" else "días"}" } ?: ""
        Text("$left de ${Fmt.big(s.available, g.currency)}$days", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        g.budgets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { BudgetTile(it, g.currency, Modifier.weight(1f)) { onOpen(it) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BudgetTile(budget: Budget, currency: String, modifier: Modifier, onClick: () -> Unit) {
    val p = budget.currentPeriod ?: return
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetRingLabel(p.spentFraction, Modifier.size(64.dp), stroke = 8.dp, style = MaterialTheme.typography.labelLarge)
            Text(budget.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (p.isOver) "Excedido ${Fmt.big(-p.remainingAmount, currency)}" else "Quedan ${Fmt.big(p.remainingAmount, currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (p.isOver) Wispr.colors.bad else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetSheet(budget: Budget, currency: String, onDismiss: () -> Unit) {
    val p = budget.currentPeriod ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(budget.name, style = MaterialTheme.typography.headlineSmall)
            Text(Fmt.range(p.startDate, p.endDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
            BudgetRingLabel(p.spentFraction, Modifier.size(160.dp), stroke = 18.dp, style = MaterialTheme.typography.displaySmall)
            if (p.processingHistorical) {
                Text("Wispr aún está asociando movimientos: el gasto es provisional.",
                    style = MaterialTheme.typography.bodySmall, color = Wispr.colors.warn)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Line("Gastado", Fmt.money(p.spentAmount, currency))
                Line("Asignado", Fmt.money(p.allocatedAmount, currency))
                if (p.carriedOverAmount != 0L) Line("Arrastrado", Fmt.money(p.carriedOverAmount, currency))
                HorizontalDivider()
                Line(if (p.isOver) "Excedido" else "Restante", Fmt.money(kotlin.math.abs(p.remainingAmount), currency))
            }
            val tracked = (budget.categories + budget.labels).joinToString { it.name }
            if (tracked.isNotEmpty()) {
                Text("Cuenta: $tracked", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
