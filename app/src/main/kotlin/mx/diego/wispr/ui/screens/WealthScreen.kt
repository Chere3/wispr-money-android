package mx.diego.wispr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mx.diego.wispr.ui.components.AreaLine
import mx.diego.wispr.ui.components.Delta
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.Hero
import mx.diego.wispr.ui.components.IconBadge
import mx.diego.wispr.ui.components.LiveContent
import mx.diego.wispr.ui.components.MetricTile
import mx.diego.wispr.ui.components.SectionCard
import mx.diego.wispr.ui.components.SectionPage
import mx.diego.wispr.ui.components.Stamp
import mx.diego.wispr.ui.components.rememberLive
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.Account
import mx.diego.wisprkit.Money
import mx.diego.wisprkit.NetWorthResponse
import mx.diego.wisprkit.WisprDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs

data class WealthSnapshot(val worth: NetWorthResponse, val accounts: List<Account>)

/**
 * Totales por mes con el mismo criterio que la app de Mac: préstamos y tarjetas cuentan siempre
 * como deuda (Wispr manda los préstamos en positivo y las tarjetas en negativo).
 */
class WealthBreakdown(s: WealthSnapshot) {
    data class Total(val month: YearMonth, val net: Long, val assets: Long, val debts: Long)

    private val byId = s.accounts.associateBy { it.id }
    val currency = s.worth.current.currencyCode
    val balances: Map<String, Long> = s.worth.latestBalances.mapValues { (id, v) -> signed(id, v) }

    val totals: List<Total> = s.worth.evolution.data
        .mapNotNull { p -> Fmt.yearMonth(p.label)?.let { it to p.balances } }
        .sortedBy { it.first }
        .map { (month, values) ->
            val signed = values.map { (id, v) -> signed(id, v) }
            Total(month, signed.sum(), signed.filter { it > 0 }.sum(), signed.filter { it < 0 }.sum())
        }

    private fun signed(id: String, value: Long) = if (byId[id]?.isLiability == true) -abs(value) else value
}

@Composable
fun WealthScreen() {
    val state = rememberLive {
        coroutineScope {
            val today = LocalDate.now()
            val from = WisprDate.startOfMonth(today.minusMonths(11))
            val worth = async { netWorth(WisprDate.string(from), WisprDate.string(today)) }
            val accounts = async { accounts().accounts }
            WealthSnapshot(worth.await(), accounts.await())
        }
    }
    LiveContent(state) { snapshot, at -> WealthContent(snapshot, at) }
}

@Composable
fun WealthContent(snapshot: WealthSnapshot, at: LocalTime) {
    val b = WealthBreakdown(snapshot)
    val c = b.currency
    val current = snapshot.worth.current
    val last = b.totals.lastOrNull()
    SectionPage(Wispr.colors.wealth) {
        item {
            Hero("Patrimonio neto", Fmt.big(current.current, c)) {
                Delta(current.current, current.previous, higherIsBetter = true, against = "el mes anterior")
                // Wispr y la suma propia de cuentas no siempre coinciden (issue #2 de la app de Mac).
                if (last != null && last.net != current.current) {
                    Text("Suma de cuentas: ${Fmt.big(last.net, c)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (b.totals.size >= 2) {
            item {
                SectionCard(title = "Últimos 12 meses") {
                    AreaLine(
                        values = b.totals.map { Money.double(it.net, c) },
                        labels = listOf(b.totals.first(), b.totals.last()).map { Fmt.month(it.month, withYear = true) },
                        color = Wispr.colors.wealth,
                        description = "Evolución del patrimonio en ${b.totals.size} meses",
                    )
                }
            }
        }
        if (last != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("Activos", Fmt.big(last.assets, c), Icons.Rounded.AccountBalanceWallet,
                        Wispr.colors.good, Modifier.weight(1f))
                    MetricTile("Deudas", Fmt.big(-last.debts, c), Icons.Rounded.CreditCard,
                        Wispr.colors.bad, Modifier.weight(1f))
                }
            }
        }
        val groups = listOf(
            Triple("Débito y efectivo", null as String?, snapshot.accounts.filter { !it.isLiability }),
            Triple("Tarjetas de crédito", "Saldo = lo que debes en la tarjeta.", snapshot.accounts.filter { it.type == "credit_card" }),
            Triple("Préstamos", "Saldo = lo que falta por pagar.", snapshot.accounts.filter { it.type == "loan" }),
        )
        groups.filter { it.third.isNotEmpty() }.forEach { (title, note, accounts) ->
            item(key = title) {
                SectionCard(title = title) {
                    note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    accounts.sortedByDescending { abs(b.balances[it.id] ?: 0) }.forEachIndexed { i, account ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        AccountRow(account, b.balances[account.id])
                    }
                }
            }
        }
        item { Stamp(at) }
    }
}

@Composable
private fun AccountRow(account: Account, balance: Long?) {
    val (icon, tint) = when (account.type) {
        "credit_card" -> Icons.Rounded.CreditCard to Wispr.colors.bad
        "loan" -> Icons.Rounded.RequestQuote to Wispr.colors.warn
        else -> Icons.Rounded.AccountBalance to Wispr.colors.today
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(account.bank, if (account.isConnected) "Sincronizada" else "Manual").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(balance?.let { Fmt.money(if (account.isLiability) abs(it) else it, account.currency) } ?: "—", style = MaterialTheme.typography.titleMedium)
    }
}
