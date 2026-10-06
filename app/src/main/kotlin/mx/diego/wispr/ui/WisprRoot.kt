package mx.diego.wispr.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import mx.diego.wispr.ui.theme.Wispr
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.diego.wispr.WisprApp
import mx.diego.wispr.notify.RefreshWorker
import mx.diego.wispr.ui.components.Fmt
import mx.diego.wispr.ui.components.LocalWispr
import mx.diego.wispr.ui.screens.FlowScreen
import mx.diego.wispr.ui.screens.MovementsScreen
import mx.diego.wispr.ui.screens.NewTransactionSheet
import mx.diego.wispr.ui.screens.SetupScreen
import mx.diego.wispr.ui.screens.TodayScreen
import mx.diego.wispr.ui.screens.WealthScreen
import mx.diego.wispr.widget.WisprWidgetReceiver
import mx.diego.wisprkit.WisprService
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Hoy", Icons.Rounded.WbSunny),
    Movements("Movimientos", Icons.Rounded.ReceiptLong),
    Flow("Flujo", Icons.Rounded.Insights),
    Wealth("Patrimonio", Icons.Rounded.AccountBalance),
}

@Composable
private fun Tab.accent(): Color = when (this) {
    Tab.Today -> Wispr.colors.today
    Tab.Movements -> Wispr.colors.movements
    Tab.Flow -> Wispr.colors.flow
    Tab.Wealth -> Wispr.colors.wealth
}

@Composable
fun WisprRoot(service: WisprService?) {
    if (service == null) {
        SetupScreen()
        return
    }
    CompositionLocalProvider(LocalWispr provides service) { Home() }
}

@Composable
private fun Home() {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var adding by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var created by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    NotificationPermission()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                // Movimientos y Flujo ya titulan con su selector de mes.
                title = {
                    when (tab) {
                        Tab.Today -> Text(Fmt.longDay(LocalDate.now()))
                        Tab.Wealth -> Text(tab.label)
                        else -> {}
                    }
                },
                actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, "Ajustes") } },
                // Mismo tono con el que arranca el velo de la sección: la barra se funde con él.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tab.accent().copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.surface),
                ),
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach {
                    ShortNavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Movements || tab == Tab.Today) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Nuevo") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pestaña") { current ->
                when (current) {
                    Tab.Today -> TodayScreen(onSeeMovements = { tab = Tab.Movements })
                    Tab.Movements -> MovementsScreen(created)
                    Tab.Flow -> FlowScreen()
                    Tab.Wealth -> WealthScreen()
                }
            }
        }
    }

    if (adding) {
        val context = LocalContext.current
        NewTransactionSheet(
            onDismiss = { adding = false },
            onCreated = {
                adding = false
                created++
                RefreshWorker.refreshNow(context)
                scope.launch { snackbar.showSnackbar("Movimiento guardado en Wispr") }
            },
        )
    }
    if (settings) SettingsSheet { settings = false }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RefreshWorker.refreshNow(context)
    }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = WisprApp.instance
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Conectado a ${app.credentials.load()?.endpoint?.substringAfter("://")?.substringBefore("/") ?: "—"}. " +
                    "Todo se consulta en vivo; el teléfono solo guarda el token, cifrado con el Keystore.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { RefreshWorker.refreshNow(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Actualizar widget y notificación")
            }
            val widgets = AppWidgetManager.getInstance(context)
            if (widgets.isRequestPinAppWidgetSupported) {
                FilledTonalButton(
                    onClick = { widgets.requestPinAppWidget(ComponentName(context, WisprWidgetReceiver::class.java), null, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Añadir widget a la pantalla de inicio") }
            }
            OutlinedButton(onClick = { app.disconnect(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text("Desconectar")
            }
        }
    }
}
