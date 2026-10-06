package mx.diego.wispr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.Live
import mx.diego.wispr.ui.components.LocalWispr
import mx.diego.wispr.ui.components.rememberLive
import mx.diego.wisprkit.Account
import mx.diego.wisprkit.Category
import mx.diego.wisprkit.Money
import mx.diego.wisprkit.NewTransaction
import mx.diego.wisprkit.WisprDate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private data class FormData(val accounts: List<Account>, val categories: List<Category>)

/**
 * Alta manual. Wispr no deduplica: solo para lo que el banco no va a sincronizar
 * (efectivo, provisionales). Se guarda directo en el servidor; nada queda en el teléfono.
 */
@Composable
fun NewTransactionSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val service = LocalWispr.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val data = rememberLive {
        coroutineScope {
            val a = async { accounts().accounts }
            val c = async { categories().categories }
            FormData(a.await(), c.await().filter { it.id != null }.sortedBy { it.name })
        }
    }

    var expense by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<Account?>(null) }
    var category by remember { mutableStateOf<Category?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsed = amount.replace(",", "").toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val valid = parsed != null && description.isNotBlank() && account != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Nuevo movimiento", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Wispr no deduplica: úsalo para efectivo o lo que el banco no va a sincronizar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val form = (data.live as? Live.Ready)?.value
            if (form == null) {
                when (val live = data.live) {
                    is Live.Failed -> Text(live.message, color = MaterialTheme.colorScheme.error)
                    else -> LoadingIndicator(Modifier.heightIn(min = 48.dp))
                }
                return@Column
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(expense, { expense = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Gasto") }
                SegmentedButton(!expense, { expense = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Ingreso") }
            }
            val currency = account?.currency ?: form.accounts.firstOrNull()?.currency ?: "MXN"
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Monto ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Picker("Cuenta", account?.name, form.accounts, { it.name }) { account = it }
            Picker("Categoría (opcional)", category?.name, form.categories, { it.name }) { category = it }
            OutlinedTextField(
                value = Fmt.longDay(date),
                onValueChange = {},
                readOnly = true,
                label = { Text("Fecha") },
                trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Rounded.CalendarMonth, "Elegir fecha") } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val acc = account ?: return@Button
                    val major = parsed ?: return@Button
                    val minor = Money.minor(major, acc.currency)
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            service.createTransaction(
                                NewTransaction(
                                    accountId = acc.id,
                                    description = description.trim(),
                                    amount = if (expense) -minor else minor,
                                    date = WisprDate.string(date),
                                    categoryId = category?.id,
                                    notes = notes.trim(),
                                )
                            )
                            sheet.hide()
                            onCreated()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (saving) LoadingIndicator(Modifier.heightIn(max = 24.dp)) else Text("Guardar en Wispr")
            }
        }
    }

    if (picking) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun <T> Picker(label: String, value: String?, options: List<T>, name: (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(name(option)) }, onClick = {
                    onPick(option)
                    expanded = false
                })
            }
        }
    }
}
