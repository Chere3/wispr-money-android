package mx.diego.wispr

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mx.diego.wispr.data.CredentialStore
import mx.diego.wispr.notify.RefreshWorker
import mx.diego.wispr.notify.SpendNotifier
import mx.diego.wispr.widget.WisprWidgetReceiver
import mx.diego.wisprkit.Credentials
import mx.diego.wisprkit.WisprService

class WisprApp : Application() {
    lateinit var credentials: CredentialStore
        private set

    private val _service = MutableStateFlow<WisprService?>(null)

    /** Servicio con las credenciales actuales; `null` si no hay conexión configurada. */
    val service: StateFlow<WisprService?> get() = _service

    override fun onCreate() {
        super.onCreate()
        instance = this
        credentials = CredentialStore(this)
        _service.value = credentials.load()?.let(::WisprService)
        SpendNotifier.createChannel(this)
        publishWidgetPreview()
        if (_service.value != null) RefreshWorker.schedule(this)
    }

    /** Vista previa generada (Android 15+) para el selector de widgets, con datos sintéticos. */
    private fun publishWidgetPreview() {
        if (Build.VERSION.SDK_INT < 35) return
        MainScope().launch {
            runCatching { GlanceAppWidgetManager(this@WisprApp).setWidgetPreviews(WisprWidgetReceiver::class) }
        }
    }

    fun connect(new: Credentials) {
        credentials.save(new)
        _service.value = WisprService(new)
        RefreshWorker.schedule(this)
        RefreshWorker.refreshNow(this)
    }

    fun disconnect() {
        credentials.delete()
        _service.value = null
        RefreshWorker.cancel(this)
        SpendNotifier.cancel(this)
    }

    companion object {
        lateinit var instance: WisprApp
            private set

        /** Para el worker y el widget, que viven fuera de la UI. */
        fun freshService(): WisprService? = instance.credentials.load()?.let(::WisprService)
    }
}
