package mx.diego.wispr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.diego.wispr.ui.WisprRoot
import mx.diego.wispr.ui.theme.WisprTheme
import mx.diego.wisprkit.Credentials

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        debugConnect()
        setContent {
            val service by WisprApp.instance.service.collectAsStateWithLifecycle()
            WisprTheme { WisprRoot(service) }
        }
    }

    /**
     * Solo en debug: permite conectar desde adb sin teclear el token en el emulador.
     *   adb shell am start -n mx.diego.wispr/.MainActivity --es endpoint <url> --es token <token>
     */
    private fun debugConnect() {
        if (!BuildConfig.DEBUG) return
        val endpoint = intent.getStringExtra("endpoint") ?: return
        val token = intent.getStringExtra("token") ?: return
        WisprApp.instance.connect(Credentials(endpoint, token))
        intent.removeExtra("token")
    }
}
