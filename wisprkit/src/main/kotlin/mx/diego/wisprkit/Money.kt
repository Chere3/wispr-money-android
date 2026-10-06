package mx.diego.wisprkit

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Montos en unidades menores → texto. Cuántas unidades menores tiene la unidad mayor depende de la
 * moneda (no siempre 100), según las instrucciones del MCP de Wispr Money.
 */
object Money {
    private val exponents = mapOf(
        "COP" to 0, "CLP" to 0, "PYG" to 0, "JPY" to 0, "PKR" to 0,
        "KWD" to 3,
        "BTC" to 8,
    )

    val esMX: Locale = Locale.forLanguageTag("es-MX")

    fun exponent(currency: String): Int = exponents[currency.uppercase()] ?: 2

    fun major(minor: Long, currency: String): BigDecimal = BigDecimal.valueOf(minor, exponent(currency))

    /** Para gráficas; nunca para aritmética de dinero. */
    fun double(minor: Long, currency: String): Double = major(minor, currency).toDouble()

    fun minor(fromMajor: BigDecimal, currency: String): Long =
        fromMajor.movePointRight(exponent(currency)).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun format(minor: Long, currency: String, locale: Locale = esMX, decimals: Boolean = true): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currency.uppercase()) }
        val digits = if (decimals) exponent(currency) else 0
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(major(minor, currency))
    }
}
