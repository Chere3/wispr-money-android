package mx.diego.wispr.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import mx.diego.wispr.MainActivity
import mx.diego.wispr.R
import mx.diego.wispr.data.Glance
import mx.diego.wisprkit.Money
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Notificación permanente con lo gastado en el mes. Se publica siempre con el mismo id; si el
 * usuario la desliza (Android 14+ lo permite), `NotificationDismissedReceiver` la vuelve a pedir
 * en vivo y la republica. Si la consulta falla dice "Sin conexión", nunca muestra cifras viejas.
 */
object SpendNotifier {
    private const val CHANNEL = "gasto-del-mes"
    private const val ID = 1
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Gasto del mes", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Resumen permanente de lo gastado y tus presupuestos"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, g: Glance) {
        val month = YearMonth.from(g.fetchedAt).month.getDisplayName(TextStyle.FULL_STANDALONE, Money.esMX)
        val s = g.summary
        val fraction = s.fraction
        val lead = buildString {
            if (fraction != null) {
                append("Presupuestos al ${(fraction * 100).roundToInt()} %")
                append(if (s.remaining >= 0) " · quedan ${big(s.remaining, g.currency)}" else " · excedido ${big(-s.remaining, g.currency)}")
                s.daysLeft?.let { append(" · $it ${if (it == 1) "día" else "días"}") }
            } else {
                append("Ingresos ${big(g.monthIncome, g.currency)}")
            }
        }
        val lines = g.budgets.mapNotNull { b ->
            val p = b.currentPeriod ?: return@mapNotNull null
            val pct = p.spentFraction?.let { "${(it * 100).roundToInt()} %" } ?: "—"
            val tail = if (p.isOver) "excedido ${big(-p.remainingAmount, g.currency)}" else "quedan ${big(p.remainingAmount, g.currency)}"
            "${b.name}: $pct · $tail"
        }
        val notification = base(context)
            .setContentTitle("Gastado en $month: ${big(g.monthExpense, g.currency)}")
            .setContentText(lead)
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(lead) + lines).joinToString("\n")))
            .setSubText("En vivo · ${g.fetchedAt.toLocalTime().format(hhmm)}")
            .apply { if (fraction != null) setProgress(100, (fraction * 100).roundToInt().coerceIn(0, 100), false) }
            .build()
        post(context, notification)
    }

    fun showOffline(context: Context, reason: String?) {
        val notification = base(context)
            .setContentTitle("Sin conexión con Wispr")
            .setContentText(reason ?: "No se pudo consultar el gasto del mes.")
            .setSubText("Intento a las ${LocalTime.now().format(hhmm)}")
            .build()
        post(context, notification)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun base(context: Context): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissed = PendingIntent.getBroadcast(
            context, 0, Intent(context, NotificationDismissedReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wispr)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
    }

    private fun post(context: Context, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    private fun big(minor: Long, currency: String) = Money.format(minor, currency, decimals = false)
}
