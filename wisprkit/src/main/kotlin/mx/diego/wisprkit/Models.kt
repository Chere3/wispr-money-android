package mx.diego.wisprkit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// Modelos de las respuestas del MCP de Wispr Money. Los montos son enteros en unidades menores
// de su moneda (ver `Money`). Las claves llegan en snake_case (ver `WisprJson`).

// Presupuestos (list_budgets)

@Serializable
data class BudgetsResponse(val currency: String, val budgets: List<Budget>)

@Serializable
data class Budget(
    val id: String,
    val name: String,
    val periodType: String,
    val categories: List<NamedRef> = emptyList(),
    val labels: List<NamedRef> = emptyList(),
    val currentPeriod: BudgetPeriod? = null,
)

@Serializable
data class BudgetPeriod(
    val startDate: String,
    val endDate: String,
    val allocatedAmount: Long,
    val carriedOverAmount: Long = 0,
    val spentAmount: Long,
    val remainingAmount: Long,
    val processingHistorical: Boolean = false,
) {
    /** Lo disponible en el periodo: lo asignado más lo arrastrado del periodo anterior. */
    val available: Long get() = allocatedAmount + carriedOverAmount

    /** Fracción gastada (1.0 = 100 %). Puede pasar de 1 si se excedió. `null` si no hay nada asignado. */
    val spentFraction: Double? get() = if (available > 0) spentAmount.toDouble() / available else null

    val isOver: Boolean get() = remainingAmount < 0
}

@Serializable
data class NamedRef(val id: String, val name: String)

// Cuentas (list_accounts)

@Serializable
data class AccountsResponse(val spaceId: String? = null, val accounts: List<Account>)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val bank: String? = null,
    val isConnected: Boolean = false,
) {
    /** Préstamos y tarjetas: su saldo es deuda. */
    val isLiability: Boolean get() = type == "loan" || type == "credit_card"
}

// Movimientos (search_transactions / create_transaction)

@Serializable
data class TransactionsResponse(val count: Int, val transactions: List<Transaction>)

@Serializable
data class Transaction(
    val id: String,
    val date: String,
    val description: String,
    val amount: Long,
    val currency: String,
    val categoryId: String? = null,
    val category: String? = null,
    val accountId: String,
    val account: String,
    val labels: List<NamedRef> = emptyList(),
)

data class TransactionFilter(
    val query: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val limit: Int = 100,
)

data class NewTransaction(
    val accountId: String,
    val description: String,
    /** Con signo, en unidades menores: negativo = gasto, positivo = ingreso. */
    val amount: Long,
    val date: String,
    val categoryId: String? = null,
    val notes: String? = null,
)

// Categorías (list_categories, spending_by_category, get_cashflow)

@Serializable
data class CategoriesResponse(val categories: List<Category>)

@Serializable
data class Category(
    val id: String? = null,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val type: String? = null,
    val parentId: String? = null,
)

@Serializable
data class CategoryAmount(
    val categoryId: String? = null,
    val category: Category,
    val amount: Long,
    val hasChildren: Boolean = false,
) {
    val key: String get() = categoryId ?: "sin-categoria-${category.name}"
}

@Serializable
data class SpendingResponse(val categories: List<CategoryAmount>)

@Serializable
data class CashflowResponse(val summary: Summary, val sankey: Sankey, val trend: Trend) {
    @Serializable
    data class Summary(val current: Totals, val previous: Totals)

    @Serializable
    data class Totals(
        val income: Long,
        val expense: Long,
        val net: Long,
        val savingsRate: Double? = null,
        val savings: Long = 0,
        val investments: Long = 0,
    )

    @Serializable
    data class Sankey(
        val incomeCategories: List<CategoryAmount> = emptyList(),
        val expenseCategories: List<CategoryAmount> = emptyList(),
        val totalIncome: Long = 0,
        val totalExpense: Long = 0,
    )

    @Serializable
    data class Trend(val data: List<TrendPoint> = emptyList())

    @Serializable
    data class TrendPoint(val month: String, val income: Long, val expense: Long, val net: Long)
}

// Patrimonio (get_net_worth)

@Serializable
data class NetWorthResponse(val current: Current, val evolution: Evolution) {
    @Serializable
    data class Current(val current: Long, val previous: Long, val currencyCode: String)

    @Serializable
    data class Evolution(val data: List<NetWorthPoint> = emptyList())

    /** Último saldo conocido de cada cuenta dentro del rango consultado. */
    val latestBalances: Map<String, Long>
        get() = buildMap {
            evolution.data.sortedBy { it.timestamp }.forEach { putAll(it.balances) }
        }
}

/** Un punto de la evolución: `month`/`date`, `timestamp` y un saldo por id de cuenta (claves dinámicas). */
@Serializable(with = NetWorthPointSerializer::class)
data class NetWorthPoint(val label: String, val timestamp: Long, val balances: Map<String, Long>)

object NetWorthPointSerializer : KSerializer<NetWorthPoint> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): NetWorthPoint {
        val raw = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        fun str(key: String) = (raw[key] as? JsonPrimitive)?.contentOrNull
        val reserved = setOf("month", "date", "timestamp")
        return NetWorthPoint(
            label = str("month") ?: str("date") ?: "",
            timestamp = (raw["timestamp"] as? JsonPrimitive)?.longOrNull ?: 0,
            balances = raw.filterKeys { it !in reserved }
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }
                .toMap(),
        )
    }

    override fun serialize(encoder: Encoder, value: NetWorthPoint) =
        throw UnsupportedOperationException("solo lectura")
}
