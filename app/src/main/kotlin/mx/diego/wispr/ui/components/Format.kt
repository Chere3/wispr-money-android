package mx.diego.wispr.ui.components

import mx.diego.wisprkit.Money
import mx.diego.wisprkit.WisprDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

object Fmt {
    private val es = Money.esMX
    private val time = DateTimeFormatter.ofPattern("HH:mm", es)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", es)
    private val longDay = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)

    fun money(minor: Long, currency: String, decimals: Boolean = true) = Money.format(minor, currency, decimals = decimals)

    /** Cifra grande sin centavos: lo que se lee de un vistazo. */
    fun big(minor: Long, currency: String) = Money.format(minor, currency, decimals = false)

    fun percent(fraction: Double?) = fraction?.let { "${(it * 100).roundToInt()} %" } ?: "—"

    fun time(t: LocalTime): String = t.format(time)

    fun dayMonth(d: LocalDate): String = d.format(dayMonth).replace(".", "")

    fun longDay(d: LocalDate): String = d.format(longDay).replaceFirstChar { it.uppercase() }

    fun month(m: YearMonth, withYear: Boolean = m.year != YearMonth.now().year): String {
        val name = m.month.getDisplayName(TextStyle.FULL_STANDALONE, es).replaceFirstChar { it.uppercase() }
        return if (withYear) "$name ${m.year}" else name
    }

    fun shortMonth(m: YearMonth): String =
        m.month.getDisplayName(TextStyle.SHORT_STANDALONE, es).replace(".", "").replaceFirstChar { it.uppercase() }

    /** "1 – 31 oct", o las fechas crudas si no se pueden leer. */
    fun range(start: String, end: String): String {
        val s = WisprDate.parse(start) ?: return "$start → $end"
        val e = WisprDate.parse(end) ?: return "$start → $end"
        return if (s.month == e.month) "${s.dayOfMonth} – ${dayMonth(e)}" else "${dayMonth(s)} – ${dayMonth(e)}"
    }

    /** "2026-03" o "2026-03-01" → YearMonth. */
    fun yearMonth(label: String): YearMonth? = WisprDate.parse(label)?.let(YearMonth::from)
}
