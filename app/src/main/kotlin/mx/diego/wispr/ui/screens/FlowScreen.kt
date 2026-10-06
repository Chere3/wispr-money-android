package mx.diego.wispr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.diego.wispr.ui.components.BarGroup
import mx.diego.wispr.ui.components.CategoryDot
import mx.diego.wispr.ui.components.Delta
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.GroupedBars
import mx.diego.wispr.ui.components.Hero
import mx.diego.wispr.ui.components.LiveContent
import mx.diego.wispr.ui.components.MetricTile
import mx.diego.wispr.ui.components.MonthStepper
import mx.diego.wispr.ui.components.SectionCard
import mx.diego.wispr.ui.components.SectionPage
import mx.diego.wispr.ui.components.ShareBar
import mx.diego.wispr.ui.components.Stamp
import mx.diego.wispr.ui.components.categoryColor
import mx.diego.wispr.ui.components.rememberLive
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.CashflowResponse
import mx.diego.wisprkit.CategoryAmount
import mx.diego.wisprkit.Money
import mx.diego.wisprkit.WisprDate
import java.time.LocalTime
import java.time.YearMonth

data class FlowSnapshot(
    val month: CashflowResponse,
    val trend: List<CashflowResponse.TrendPoint>,
    /** Moneda base de la cuenta (el cashflow no la trae; `list_budgets` sí). */
    val currency: String,
)

@Composable
fun FlowScreen() {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val state = rememberLive(month) {
        coroutineScope {
            val from = WisprDate.string(month.atDay(1))
            val to = WisprDate.string(month.atEndOfMonth())
            val current = async { cashflow(from, to) }
            val history = async { cashflow(WisprDate.string(month.minusMonths(5).atDay(1)), to) }
            val base = async { budgets().currency }
            FlowSnapshot(current.await(), history.await().trend.data, base.await())
        }
    }
    LiveContent(state) { snapshot, at -> FlowContent(snapshot, month, at, onMonth = { month = it }) }
}

@Composable
fun FlowContent(snapshot: FlowSnapshot, month: YearMonth, at: LocalTime, onMonth: (YearMonth) -> Unit) {
    val s = snapshot.month.summary
    val c = snapshot.currency
    val prev = Fmt.month(month.minusMonths(1), withYear = false).lowercase()
    var open by remember { mutableStateOf<CategoryAmount?>(null) }
    SectionPage(Wispr.colors.flow) {
        item { MonthStepper("Flujo", month, onMonth) }
        item {
            Hero("Neto del mes", Fmt.big(s.current.net, c)) {
                Delta(s.current.net, s.previous.net, higherIsBetter = true, against = prev)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Ingresos", Fmt.big(s.current.income, c), Icons.Rounded.SouthWest, Wispr.colors.income,
                    Modifier.weight(1f)) { Delta(s.current.income, s.previous.income, true, prev) }
                MetricTile("Gastos", Fmt.big(s.current.expense, c), Icons.Rounded.NorthEast, Wispr.colors.bad,
                    Modifier.weight(1f)) { Delta(s.current.expense, s.previous.expense, false, prev) }
            }
        }
        item {
            MetricTile("Tasa de ahorro", Fmt.percent(s.current.savingsRate?.let { it / 100 }), Icons.Rounded.Savings,
                Wispr.colors.flow, Modifier.fillMaxWidth()) {
                val p = s.previous.savingsRate
                Text(p?.let { "En $prev: ${Fmt.percent(it / 100)}" } ?: "Sin datos de $prev",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Expenses(snapshot.month.sankey.expenseCategories, c) { open = it } }
        item { Trend(snapshot.trend, c) }
        item { Stamp(at) }
    }
    open?.let { SubcategorySheet(it, month, c) { open = null } }
}

@Composable
private fun Expenses(categories: List<CategoryAmount>, currency: String, onOpen: (CategoryAmount) -> Unit) {
    val sorted = categories.sortedByDescending { it.amount }
    val total = sorted.sumOf { it.amount }.coerceAtLeast(1)
    SectionCard(title = "Gastos por categoría") {
        if (sorted.isEmpty()) Text("Sin gastos este mes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        sorted.forEach { item ->
            val color = categoryColor(item.category.name, item.category.color)
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable(enabled = item.hasChildren && item.categoryId != null) { onOpen(item) }
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(item.category.name, item.category.color, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Text(item.category.name + if (item.hasChildren) "  ›" else "", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.big(item.amount, currency), style = MaterialTheme.typography.titleSmall)
                }
                ShareBar(item.amount.toFloat() / total, color)
            }
        }
    }
}

@Composable
private fun Trend(points: List<CashflowResponse.TrendPoint>, currency: String) {
    val income = Wispr.colors.income
    val expense = Wispr.colors.bad
    SectionCard(title = "Últimos 6 meses") {
        GroupedBars(
            groups = points.map { p ->
                BarGroup(Fmt.yearMonth(p.month)?.let(Fmt::shortMonth) ?: p.month,
                    listOf(Money.double(p.income, currency), Money.double(p.expense, currency)))
            },
            colors = listOf(income, expense),
            description = "Ingresos y gastos de los últimos ${points.size} meses",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Ingresos", income)
            Legend("Gastos", expense)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SubcategorySheet(parent: CategoryAmount, month: YearMonth, currency: String, onDismiss: () -> Unit) {
    val state = rememberLive(parent.categoryId) {
        spending(WisprDate.string(month.atDay(1)), WisprDate.string(month.atEndOfMonth()), parent.categoryId)
            .categories.sortedByDescending { it.amount }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(parent.category.name, style = MaterialTheme.typography.headlineSmall)
            Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 320.dp)) {
                LiveContent(state) { list, _ ->
                    Column {
                        list.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider()
                            Row(Modifier.padding(vertical = 12.dp)) {
                                Text(item.category.name, Modifier.weight(1f))
                                Text(Fmt.money(item.amount, currency), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
