package mx.diego.wisprkit

import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.system.exitProcess

// Prueba la conexión real con Wispr Money sin el emulador. Imprime solo conteos y nombres, nunca montos.
//   WISPR_ENDPOINT=https://<host>/mcp WISPR_TOKEN=<token> ./gradlew :wisprkit:probe

fun main() = runBlocking {
    val endpoint = System.getenv("WISPR_ENDPOINT").orEmpty()
    val token = System.getenv("WISPR_TOKEN").orEmpty().removePrefix("Bearer ").removePrefix("bearer ")
    if (endpoint.isEmpty() || token.isEmpty()) {
        System.err.println("Faltan WISPR_ENDPOINT y/o WISPR_TOKEN.")
        exitProcess(2)
    }
    val service = WisprService(Credentials(endpoint, token))
    try {
        val budgets = service.budgets()
        println("OK list_budgets: ${budgets.budgets.size} presupuestos (${budgets.budgets.joinToString { it.name }})")
        println("OK list_accounts: ${service.accounts().accounts.size} cuentas")
        val today = WisprDate.string(LocalDate.now())
        println("OK get_net_worth: ${service.netWorth(today, today).latestBalances.size} cuentas con saldo")
        val month = LocalDate.now()
        val cash = service.cashflow(WisprDate.string(WisprDate.startOfMonth(month)), today)
        println("OK get_cashflow: ${cash.trend.data.size} meses de tendencia")
        fun sign(v: Long) = if (v < 0) "negativo" else if (v > 0) "positivo" else "cero"
        println("   signos: gasto ${sign(cash.summary.current.expense)}, categorías de gasto ${cash.sankey.expenseCategories.map { sign(it.amount) }.distinct()}")
        println("   presupuestos gastado: ${budgets.budgets.mapNotNull { it.currentPeriod }.map { sign(it.spentAmount) }.distinct()}")
        val tx = service.transactions(TransactionFilter(limit = 20))
        println("OK search_transactions: ${tx.transactions.size} (signos ${tx.transactions.map { sign(it.amount) }.distinct()})")
        val cats = service.categories().categories
        println("OK list_categories: ${cats.size}; iconos ej. ${cats.mapNotNull { it.icon }.distinct().take(12)}; colores ${cats.mapNotNull { it.color }.distinct()}")
        println("Conexión directa verificada.")
    } catch (e: Exception) {
        println("FALLÓ: ${e.message}")
        exitProcess(1)
    }
}
