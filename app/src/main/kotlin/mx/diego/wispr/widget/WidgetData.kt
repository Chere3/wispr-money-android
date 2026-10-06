package mx.diego.wispr.widget

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.diego.wispr.data.Glance
import mx.diego.wisprkit.Budget
import mx.diego.wisprkit.BudgetPeriod
import mx.diego.wisprkit.Transaction
import mx.diego.wisprkit.TransactionFilter
import mx.diego.wisprkit.WisprDate
import mx.diego.wisprkit.WisprService
import java.time.LocalDate
import java.time.LocalDateTime

/** Todo lo que el widget puede llegar a mostrar en su tamaño más grande. Se pide en vivo cada vez. */
data class WidgetData(
    val glance: Glance,
    val recent: List<Transaction>,
    val netWorth: Long,
    val netWorthCurrency: String,
) {
    companion object {
        suspend fun fetch(service: WisprService): WidgetData = coroutineScope {
            val today = WisprDate.string(LocalDate.now())
            val glance = async { Glance.fetch(service) }
            val recent = async { service.transactions(TransactionFilter(limit = 6)).transactions }
            val worth = async { service.netWorth(today, today).current }
            WidgetData(glance.await(), recent.await(), worth.await().current, worth.await().currencyCode)
        }

        /** Datos sintéticos para la vista previa del selector de widgets. Nunca datos reales. */
        fun sample(): WidgetData {
            val now = LocalDateTime.now()
            fun budget(name: String, spent: Long, allocated: Long) = Budget(
                id = name, name = name, periodType = "monthly",
                currentPeriod = BudgetPeriod("2026-10-01", "2026-10-31", allocated, 0, spent, allocated - spent),
            )
            fun tx(desc: String, cat: String, amount: Long) = Transaction(
                id = desc, date = WisprDate.string(now.toLocalDate()), description = desc, amount = amount,
                currency = "MXN", category = cat, accountId = "a", account = "Débito",
            )
            return WidgetData(
                glance = Glance(
                    currency = "MXN", monthExpense = 842_000, monthIncome = 1_250_000, previousExpense = 910_000,
                    budgets = listOf(
                        budget("Comida", 465_000, 400_000),
                        budget("Transporte", 98_000, 120_000),
                        budget("Suscripciones", 61_000, 150_000),
                        budget("Salidas", 40_000, 200_000),
                    ),
                    fetchedAt = now,
                ),
                recent = listOf(
                    tx("Supermercado", "Comida", -64_250),
                    tx("Metro", "Transporte", -1_000),
                    tx("Nómina", "Ingresos", 1_250_000),
                    tx("Cafetería", "Comida", -8_900),
                ),
                netWorth = 2_345_600,
                netWorthCurrency = "MXN",
            )
        }
    }
}
