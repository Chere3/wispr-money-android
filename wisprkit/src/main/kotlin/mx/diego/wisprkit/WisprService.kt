package mx.diego.wisprkit

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth

/** Operaciones de Wispr Money. Cada método es una llamada en vivo al servidor; nada se guarda. */
class WisprService(private val client: MCPClient) {

    constructor(credentials: Credentials) : this(MCPClient(credentials.endpoint, credentials.token))

    suspend fun budgets(): BudgetsResponse = call("list_budgets")

    suspend fun accounts(): AccountsResponse = call("list_accounts")

    suspend fun categories(): CategoriesResponse = call("list_categories")

    suspend fun transactions(filter: TransactionFilter): TransactionsResponse =
        call("search_transactions", buildJsonObject {
            put("limit", filter.limit)
            if (filter.query.isNotEmpty()) put("query", filter.query)
            filter.accountId?.let { put("account_id", it) }
            filter.categoryId?.let { put("category_id", it) }
            filter.from?.let { put("from", it) }
            filter.to?.let { put("to", it) }
        })

    /** Crea un movimiento manual. Wispr no deduplica: solo para lo que el banco no va a sincronizar. */
    suspend fun createTransaction(new: NewTransaction) {
        client.callTool("create_transaction", buildJsonObject {
            put("account_id", new.accountId)
            put("description", new.description)
            put("amount", new.amount)
            put("transaction_date", new.date)
            new.categoryId?.let { put("category_id", it) }
            new.notes?.takeIf { it.isNotEmpty() }?.let { put("notes", it) }
        })
    }

    suspend fun cashflow(from: String, to: String): CashflowResponse =
        call("get_cashflow", buildJsonObject { put("from", from); put("to", to) })

    suspend fun spending(from: String, to: String, parentCategoryId: String? = null): SpendingResponse =
        call("spending_by_category", buildJsonObject {
            put("from", from)
            put("to", to)
            parentCategoryId?.let { put("parent_category_id", it) }
        })

    suspend fun netWorth(from: String, to: String, daily: Boolean = false): NetWorthResponse =
        call("get_net_worth", buildJsonObject {
            put("from", from)
            put("to", to)
            put("granularity", if (daily) "daily" else "monthly")
        })

    private suspend inline fun <reified T> call(tool: String, arguments: JsonObject = JsonObject(emptyMap())): T {
        val text = client.callTool(tool, arguments)
        return try {
            WisprJson.decodeFromString<T>(text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw WisprException.BadResponse("$tool: ${e.message}")
        }
    }
}

data class Credentials(val endpoint: String, val token: String)

/** Fechas `YYYY-MM-DD` en el calendario local, como las espera el MCP. */
object WisprDate {
    fun string(date: LocalDate): String = date.toString()

    fun startOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun endOfMonth(date: LocalDate): LocalDate = YearMonth.from(date).atEndOfMonth()

    /** Lee `YYYY-MM-DD` o `YYYY-MM` (etiquetas mensuales de `get_net_worth`). `null` si no encaja. */
    fun parse(text: String): LocalDate? {
        val parts = text.split("-").map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> runCatching { LocalDate.of(parts[0], parts[1], 1) }.getOrNull()
            3 -> runCatching { LocalDate.of(parts[0], parts[1], parts[2]) }.getOrNull()
            else -> null
        }
    }
}
