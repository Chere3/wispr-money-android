package mx.diego.wispr.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import mx.diego.wispr.WisprApp
import mx.diego.wispr.data.Glance
import mx.diego.wispr.widget.WisprWidget
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cada ~15 min (el mínimo de WorkManager, lo mismo que el widget de Mac) pide en vivo el gasto
 * del mes, republica la notificación y redibuja el widget (que hace su propia consulta en vivo).
 * Sin restricción de red a propósito: si no hay conexión, ambos deben decir "Sin conexión".
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val service = WisprApp.freshService() ?: return Result.success()
        try {
            SpendNotifier.show(applicationContext, Glance.fetch(service))
        } catch (e: CancellationException) {
            throw e // WorkManager canceló o reemplazó el trabajo: no es falta de conexión.
        } catch (e: Exception) {
            SpendNotifier.showOffline(applicationContext, e.message)
        }
        WisprWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "refresco-periodico"
        private const val NOW = "refresco-inmediato"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

/** El usuario deslizó la notificación: se vuelve a consultar y se republica. */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WisprApp.freshService() != null) RefreshWorker.refreshNow(context)
    }
}

/** Tras reiniciar o actualizar la app, la notificación vuelve sin esperar al siguiente ciclo. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WisprApp.freshService() == null) return
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
    }
}
