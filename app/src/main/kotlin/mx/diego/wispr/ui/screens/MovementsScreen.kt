package mx.diego.wispr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.LiveContent
import mx.diego.wispr.ui.components.LiveState
import mx.diego.wispr.ui.components.MetricTile
import mx.diego.wispr.ui.components.MonthStepper
import mx.diego.wispr.ui.components.SectionCard
import mx.diego.wispr.ui.components.SectionPage
import mx.diego.wispr.ui.components.Stamp
import mx.diego.wispr.ui.components.TransactionRow
import mx.diego.wispr.ui.components.rememberLive
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.Account
import mx.diego.wisprkit.Transaction
import mx.diego.wisprkit.TransactionFilter
import mx.diego.wisprkit.WisprDate
import java.time.LocalTime
import java.time.YearMonth

/** `refreshKey` cambia cuando se crea un movimiento, para volver a pedir la lista. */
@Composable
fun MovementsScreen(refreshKey: Int) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        delay(400)
        query = text.trim()
    }

    val accounts = rememberLive { accounts().accounts }
    val state = rememberLive(listOf(month, query, accountId, refreshKey)) {
        transactions(
            TransactionFilter(
                query = query,
                accountId = accountId,
                from = WisprDate.string(month.atDay(1)),
                to = WisprDate.string(month.atEndOfMonth()),
                limit = 200, // tope del servidor
            )
        ).transactions
    }

    val accent = Wispr.colors.movements
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to accent.copy(alpha = 0.22f), 1f to MaterialTheme.colorScheme.surface)
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonthStepper("Movimientos", month, { month = it })
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Buscar descripción") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Borrar búsqueda") }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            AccountChips(accounts, accountId) { accountId = it }
        }
        Box(Modifier.weight(1f)) {
            LiveContent(state) { list, at -> MovementsContent(list, at) }
        }
    }
}

@Composable
private fun AccountChips(state: LiveState<List<Account>>, selected: String?, onSelect: (String?) -> Unit) {
    val accounts = (state.live as? mx.diego.wispr.ui.components.Live.Ready)?.value.orEmpty()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        item { FilterChip(selected == null, { onSelect(null) }, { Text("Todas") }, shape = CircleShape) }
        items(accounts, key = { it.id }) { account ->
            FilterChip(selected == account.id, { onSelect(if (selected == account.id) null else account.id) },
                { Text(account.name) }, shape = CircleShape)
        }
    }
}

@Composable
fun MovementsContent(list: List<Transaction>, at: LocalTime) {
    val currency = list.firstOrNull()?.currency ?: "MXN"
    val out = list.filter { it.amount < 0 }.sumOf { -it.amount }
    val inc = list.filter { it.amount > 0 }.sumOf { it.amount }
    SectionPage(accent = null) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile("Salidas", Fmt.big(out, currency), Icons.Rounded.NorthEast, Wispr.colors.bad, Modifier.weight(1f))
                MetricTile("Entradas", Fmt.big(inc, currency), Icons.Rounded.SouthWest, Wispr.colors.income, Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) {
            item { SectionCard { Text("Sin movimientos con estos filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        val byDay = list.groupBy { it.date }.toSortedMap(compareByDescending { it })
        byDay.forEach { (date, txs) ->
            item(key = date) {
                SectionCard(
                    title = WisprDate.parse(date)?.let(Fmt::longDay) ?: date,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    txs.forEachIndexed { i, tx ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        TransactionRow(tx)
                    }
                }
            }
        }
        item { Stamp(at) }
    }
}
