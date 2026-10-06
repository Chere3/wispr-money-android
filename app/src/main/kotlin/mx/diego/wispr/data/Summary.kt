package mx.diego.wispr.data

import mx.diego.wisprkit.Budget
import mx.diego.wisprkit.BudgetsResponse
import mx.diego.wisprkit.CashflowResponse
import mx.diego.wisprkit.WisprDate
import mx.diego.wisprkit.WisprService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Suma de los presupuestos con periodo activo (misma moneda: la de `list_budgets`). */
class BudgetSummary(budgets: List<Budget>) {
    private val periods = budgets.mapNotNull { it.currentPeriod }
    val spent: Long = periods.sumOf { it.spentAmount }
    val available: Long = periods.sumOf { it.available }
    val overCount: Int = periods.count { it.isOver }
    val activeCount: Int = periods.size
    private val endsOn: LocalDate? = periods.mapNotNull { WisprDate.parse(it.endDate) }.minOrNull()

    val fraction: Double? get() = if (available > 0) spent.toDouble() / available else null
    val remaining: Long get() = available - spent

    /** Días que faltan, contando hoy. */
    val daysLeft: Int? get() = endsOn?.let { maxOf(ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() + 1, 0) }
}

/** Los más excedidos primero. */
fun List<Budget>.byUrgency(): List<Budget> =
    filter { it.currentPeriod != null }.sortedByDescending { it.currentPeriod?.spentFraction ?: 0.0 }

/** Lo que muestran el widget y la notificación: gasto del mes y presupuestos, pedidos en vivo. */
data class Glance(
    val currency: String,
    val monthExpense: Long,
    val monthIncome: Long,
    val previousExpense: Long,
    val budgets: List<Budget>,
    val fetchedAt: LocalDateTime,
) {
    val summary get() = BudgetSummary(budgets)

    companion object {
        suspend fun fetch(service: WisprService, today: LocalDate = LocalDate.now()): Glance {
            val budgets: BudgetsResponse = service.budgets()
            val cash: CashflowResponse = service.cashflow(
                WisprDate.string(WisprDate.startOfMonth(today)), WisprDate.string(today),
            )
            return Glance(
                currency = budgets.currency,
                monthExpense = cash.summary.current.expense,
                monthIncome = cash.summary.current.income,
                previousExpense = cash.summary.previous.expense,
                budgets = budgets.budgets.byUrgency(),
                fetchedAt = LocalDateTime.now(),
            )
        }
    }
}
