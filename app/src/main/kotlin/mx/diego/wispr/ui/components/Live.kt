package mx.diego.wispr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CancellationException
import mx.diego.wisprkit.WisprService
import java.time.LocalTime

/** El servicio de Wispr de la sesión. Las pantallas lo leen de aquí; los previews no lo usan. */
val LocalWispr = staticCompositionLocalOf<WisprService> { error("Sin servicio de Wispr") }

sealed interface Live<out T> {
    data object Loading : Live<Nothing>
    data class Ready<T>(val value: T, val at: LocalTime) : Live<T>
    data class Failed(val message: String) : Live<Nothing>
}

@Stable
class LiveState<T> {
    var live: Live<T> by mutableStateOf(Live.Loading)
        internal set
    var refreshing by mutableStateOf(false)
        internal set
    internal var trigger by mutableIntStateOf(0)

    fun refresh() {
        trigger++
    }
}

/**
 * Pide los datos en vivo al entrar, al cambiar `key`, al volver a la app y al deslizar hacia
 * abajo. Si una consulta falla se muestra el error, nunca las cifras anteriores.
 */
@Composable
fun <T> rememberLive(key: Any? = Unit, load: suspend WisprService.() -> T): LiveState<T> {
    val service = LocalWispr.current
    val state = remember(service, key) { LiveState<T>() }
    LaunchedEffect(state, state.trigger) {
        state.refreshing = state.live is Live.Ready
        state.live = try {
            Live.Ready(service.load(), LocalTime.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Live.Failed(e.message ?: e.toString())
        }
        state.refreshing = false
    }
    LifecycleResumeEffect(state) {
        if (state.live !is Live.Loading) state.refresh()
        onPauseOrDispose { }
    }
    return state
}

@Composable
fun <T> LiveContent(
    state: LiveState<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T, LocalTime) -> Unit,
) {
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = state::refresh,
        state = pull,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pull,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when (val live = state.live) {
            Live.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator(Modifier.size(72.dp))
            }
            is Live.Failed -> Failure(live.message, state::refresh)
            is Live.Ready -> content(live.value, live.at)
        }
    }
}

@Composable
private fun Failure(message: String, retry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text("Sin conexión", style = MaterialTheme.typography.titleLarge)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "No se muestran cifras anteriores: todo se pide en vivo a Wispr.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = retry) { Text("Reintentar") }
    }
}

/** Indicador pequeño para cargas dentro de una tarjeta. */
@Composable
fun InlineLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}
