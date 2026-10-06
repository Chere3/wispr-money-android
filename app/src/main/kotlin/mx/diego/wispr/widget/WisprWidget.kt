package mx.diego.wispr.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import mx.diego.wispr.MainActivity
import mx.diego.wispr.WisprApp
import mx.diego.wispr.ui.theme.Dark
import mx.diego.wispr.ui.theme.DarkExtra
import mx.diego.wispr.ui.theme.Light
import mx.diego.wispr.ui.theme.LightExtra
import mx.diego.wisprkit.Budget
import mx.diego.wisprkit.Money
import mx.diego.wisprkit.Transaction
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import java.time.format.TextStyle as JTextStyle

class WisprWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WisprWidget()
}

private sealed interface WidgetState {
    data class Ok(val data: WidgetData) : WidgetState
    data class Offline(val at: LocalTime) : WidgetState
    data object NotConfigured : WidgetState
}

/**
 * Widget redimensionable de 1×1 a pantalla completa: cuanto más espacio, más información.
 *
 * | Tamaño                | Muestra                                                        |
 * |-----------------------|----------------------------------------------------------------|
 * | diminuto (1×1)        | anillo del total con su %                                      |
 * | tira (2–3×1)          | anillo + gastado del mes                                       |
 * | tira ancha (4×1)      | + ingresos y neto                                              |
 * | cuadrado (2×2)        | anillo, gastado, hora y el presupuesto más apretado            |
 * | alto (1–2×3+)         | + lista de presupuestos (tantos como quepan)                   |
 * | ancho (3–4×2)         | gastado, variación vs mes anterior, anillo y 2–3 presupuestos  |
 * | grande (4×3+)         | + ingresos, neto, patrimonio, presupuestos con anillo y últimos movimientos |
 *
 * Cada vez que se dibuja pide los datos en vivo (nada se guarda en el estado de Glance); muestra
 * la hora de consulta y "Sin conexión" si falla, nunca cifras viejas.
 */
class WisprWidget : GlanceAppWidget() {
    // Exact: el diseño se elige con el tamaño real, así cada redimensión cambia lo que se ve.
    override val sizeMode = SizeMode.Exact

    // El selector de widgets dibuja la vista previa en el tamaño por defecto (2×2).
    override val previewSizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 210.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val service = WisprApp.freshService()
        val state = when (service) {
            null -> WidgetState.NotConfigured
            else -> try {
                WidgetState.Ok(WidgetData.fetch(service))
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                WidgetState.Offline(LocalTime.now())
            }
        }
        provideContent { Themed { Content(state) } }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { Themed { Content(WidgetState.Ok(WidgetData.sample())) } }
    }
}

@Composable
private fun Themed(content: @Composable () -> Unit) =
    GlanceTheme(colors = ColorProviders(light = Light, dark = Dark), content = content)

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun Content(state: WidgetState) {
    val size = LocalSize.current
    val compact = size.height < 130.dp || size.width < 130.dp
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            WidgetState.NotConfigured -> Note("Abre Wispr para conectar")
            is WidgetState.Offline -> Note("Sin conexión · ${state.at.format(hhmm)}")
            is WidgetState.Ok -> Layout(state.data)
        }
    }
}

/**
 * Elige el diseño según el tamaño real. En un teléfono típico una celda mide ~90 × 105 dp:
 * 1 fila < 130 dp, 2 filas < 260 dp; 1 columna < 130 dp, 2 columnas < 230 dp.
 */
@Composable
private fun Layout(d: WidgetData) {
    val w = LocalSize.current.width
    val h = LocalSize.current.height
    when {
        h < 130.dp && w < 130.dp -> Tiny(d)
        h < 130.dp && w < 300.dp -> Strip(d)
        h < 130.dp -> WideStrip(d)
        w < 230.dp && h < 260.dp -> Square(d)
        w < 230.dp -> Tall(d)
        h < 260.dp -> Wide(d)
        else -> Large(d, roomy = h >= 400.dp)
    }
}

// Diseños

@Composable
private fun Tiny(d: WidgetData) {
    val side = minOf(LocalSize.current.width, LocalSize.current.height) - 16.dp
    Ring(d.glance.summary.fraction, side, label = true)
}

@Composable
private fun Strip(d: WidgetData) {
    val h = LocalSize.current.height - 16.dp
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(d.glance.summary.fraction, h.coerceAtMost(56.dp), label = true)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Caption("Gastado en ${month(d)}")
            Amount(d.glance.monthExpense, d.glance.currency, 20.sp)
        }
    }
}

@Composable
private fun WideStrip(d: WidgetData) {
    val g = d.glance
    val h = LocalSize.current.height - 16.dp
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(g.summary.fraction, h.coerceAtMost(56.dp), label = true)
        Spacer(GlanceModifier.width(10.dp))
        Metric("Gastado", g.monthExpense, g.currency, GlanceModifier.defaultWeight())
        Metric("Ingresos", g.monthIncome, g.currency, GlanceModifier.defaultWeight())
        Metric("Neto", g.monthIncome - g.monthExpense, g.currency, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Square(d: WidgetData) {
    val size = LocalSize.current
    val withBudget = size.height >= 190.dp && d.glance.budgets.isNotEmpty()
    val ring = (minOf(size.width - 28.dp, size.height - if (withBudget) 110.dp else 80.dp)).coerceIn(40.dp, 88.dp)
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically) {
        Ring(d.glance.summary.fraction, ring, label = true)
        Spacer(GlanceModifier.height(6.dp))
        Amount(d.glance.monthExpense, d.glance.currency, 20.sp)
        Stamp(d)
        // El presupuesto más apretado: lo primero que conviene vigilar.
        if (withBudget) BudgetLine(d.glance.budgets.first(), d.glance.currency, detail = false)
    }
}

@Composable
private fun Tall(d: WidgetData) {
    val g = d.glance
    val size = LocalSize.current
    val fit = ((size.height - 190.dp).value / 26).toInt().coerceIn(1, 8)
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(g.summary.fraction, (size.width - 28.dp).coerceIn(48.dp, 88.dp), label = true)
        Spacer(GlanceModifier.height(6.dp))
        Amount(g.monthExpense, g.currency, 18.sp)
        Stamp(d)
        Spacer(GlanceModifier.height(10.dp))
        // Glance admite máximo 10 hijos por Column: las listas van en su propia columna.
        Column(GlanceModifier.fillMaxWidth()) {
            g.budgets.take(fit).forEach { BudgetLine(it, g.currency, detail = false) }
        }
    }
}

@Composable
private fun Wide(d: WidgetData) {
    val g = d.glance
    val fit = ((LocalSize.current.height - 120.dp).value / 24).toInt().coerceIn(1, 3)
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Header(d)
            Spacer(GlanceModifier.height(8.dp))
            g.budgets.take(fit).forEach { BudgetLine(it, g.currency, detail = LocalSize.current.width >= 300.dp) }
        }
        Spacer(GlanceModifier.width(10.dp))
        Ring(g.summary.fraction, 80.dp, label = true)
    }
}

@Composable
private fun Large(d: WidgetData, roomy: Boolean) {
    val g = d.glance
    val h = LocalSize.current.height
    val budgets = minOf(g.budgets.size, if (roomy) 6 else 3)
    // Lo que sobra tras cabecera, métricas y presupuestos se llena con los últimos movimientos.
    val txFit = ((h - 230.dp - (budgets * 30).dp).value / 40).toInt().coerceIn(0, 6)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) { Header(d) }
            Ring(g.summary.fraction, 72.dp, label = true)
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Tile("Ingresos", g.monthIncome, g.currency, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Tile("Neto", g.monthIncome - g.monthExpense, g.currency, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Tile("Patrimonio", d.netWorth, d.netWorthCurrency, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(10.dp))
        // Glance admite máximo 10 hijos por Column: cada sección va en su propia columna.
        Column(GlanceModifier.fillMaxWidth()) {
            Section("Presupuestos")
            g.budgets.take(budgets).forEach { BudgetLine(it, g.currency, detail = true, ring = true) }
        }
        if (txFit > 0 && d.recent.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Column(GlanceModifier.fillMaxWidth()) {
                Section("Últimos movimientos")
                d.recent.take(txFit).forEach { TxLine(it) }
            }
        }
    }
}

// Piezas

@Composable
private fun Header(d: WidgetData) {
    val g = d.glance
    Caption("Gastado en ${month(d)}")
    Amount(g.monthExpense, g.currency, 26.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (g.previousExpense > 0) {
            val change = (g.monthExpense - g.previousExpense).toDouble() / g.previousExpense
            val down = change < 0
            Text(
                "${if (down) "▼" else "▲"} ${(abs(change) * 100).roundToInt()} % · ",
                style = TextStyle(color = ColorProvider(if (down) palette().good else palette().bad),
                    fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
        }
        Stamp(d)
    }
}

@Composable
private fun Metric(label: String, minor: Long, currency: String, modifier: GlanceModifier) {
    Column(modifier) {
        Caption(label)
        Amount(minor, currency, 16.sp)
    }
}

@Composable
private fun Tile(label: String, minor: Long, currency: String, modifier: GlanceModifier) {
    Column(modifier.cornerRadius(16.dp).background(GlanceTheme.colors.surfaceVariant).padding(10.dp)) {
        Caption(label)
        Amount(minor, currency, 15.sp)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(bottom = 2.dp))
}

@Composable
private fun BudgetLine(b: Budget, currency: String, detail: Boolean, ring: Boolean = false) {
    val p = b.currentPeriod ?: return
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ring) {
            Ring(p.spentFraction, 22.dp, label = false)
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(b.name, GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
        val pct = p.spentFraction?.let { "${(it * 100).roundToInt()} %" } ?: "—"
        val text = if (detail) "$pct · ${if (p.isOver) "−" else ""}${big(abs(p.remainingAmount), currency)}" else pct
        Text(text, style = TextStyle(color = ColorProvider(tone(p.spentFraction)), fontSize = 13.sp,
            fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun TxLine(tx: Transaction) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(tx.description, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
            Text(tx.category ?: "Sin categoría",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
        }
        val income = tx.amount > 0
        Text((if (income) "+" else "") + Money.format(tx.amount, tx.currency),
            style = TextStyle(color = if (income) ColorProvider(palette().income) else GlanceTheme.colors.onSurface,
                fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun Caption(text: String) =
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)

@Composable
private fun Amount(minor: Long, currency: String, size: TextUnit) =
    Text(big(minor, currency), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = size,
        fontWeight = FontWeight.Bold), maxLines = 1)

@Composable
private fun Stamp(d: WidgetData) =
    Text("En vivo · ${d.glance.fetchedAt.toLocalTime().format(hhmm)}",
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)

@Composable
private fun Note(text: String) =
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))

/** Glance no dibuja arcos: el anillo se pinta en un bitmap, igual que el de la app. */
@Composable
private fun Ring(fraction: Double?, size: Dp, label: Boolean) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(8)
    val color = tone(fraction)
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(ringBitmap(px, fraction, color, stroke = px * 0.13f)), contentDescription = null,
            modifier = GlanceModifier.size(size))
        if (label) {
            Text(fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = (size.value / 4.5f).sp,
                    fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun palette() =
    if ((LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES) DarkExtra else LightExtra

@Composable
private fun tone(fraction: Double?): Color {
    val c = palette()
    return when {
        fraction == null -> Color.Gray
        fraction > 1 -> c.bad
        fraction >= 0.8 -> c.warn
        else -> c.good
    }
}

private fun ringBitmap(size: Int, fraction: Double?, color: Color, stroke: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = color.copy(alpha = 0.2f).toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)
    val value = (fraction ?: 0.0).coerceIn(0.0, 2.0).toFloat()
    if (value > 0f) {
        paint.color = color.toArgb()
        canvas.drawArc(rect, -90f, 360f * value.coerceAtMost(1f), false, paint)
    }
    if (value > 1f) {
        // Segunda vuelta más oscura: el exceso se lee por la forma, no solo por el color.
        paint.color = lerp(color, Color.Black, 0.3f).toArgb()
        canvas.drawArc(rect, -90f, 360f * (value - 1f), false, paint)
    }
    return bitmap
}

private fun month(d: WidgetData) =
    YearMonth.from(d.glance.fetchedAt).month.getDisplayName(JTextStyle.FULL_STANDALONE, Money.esMX)

private fun big(minor: Long, currency: String) = Money.format(minor, currency, decimals = false)
