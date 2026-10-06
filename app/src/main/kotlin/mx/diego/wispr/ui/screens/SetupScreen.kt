package mx.diego.wispr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.diego.wispr.WisprApp
import mx.diego.wispr.ui.components.BudgetRing
import mx.diego.wispr.ui.theme.Wispr
import mx.diego.wisprkit.Credentials
import mx.diego.wisprkit.WisprService

/** Primer arranque: URL del endpoint MCP y token. Se valida con una consulta real antes de guardar. */
@Composable
fun SetupScreen() {
    var endpoint by remember { mutableStateOf("https://") }
    val token = rememberTextFieldState()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Wispr.colors.today.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)))
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(top = 48.dp).size(120.dp)) {
            BudgetRing(0.72, Modifier.padding(28.dp), color = MaterialTheme.colorScheme.primary)
        }
        Text("Wispr Money", style = MaterialTheme.typography.displaySmall)
        Text(
            "Conecta tu servidor. Las cifras se piden en vivo cada vez; el teléfono solo guarda el token, cifrado.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it.trim() },
            label = { Text("Endpoint MCP") },
            placeholder = { Text("https://servidor/mcp") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedSecureTextField(state = token, label = { Text("Token") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val credentials = Credentials(endpoint, token.text.toString().trim().removePrefix("Bearer "))
                checking = true
                error = null
                scope.launch {
                    try {
                        WisprService(credentials).budgets()
                        WisprApp.instance.connect(credentials)
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        checking = false
                    }
                }
            },
            enabled = !checking && endpoint.startsWith("https://") && token.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            if (checking) LoadingIndicator(Modifier.size(28.dp)) else Text("Conectar")
        }
        Text(
            "Si el servidor está en Tailscale, el teléfono necesita la app de Tailscale conectada.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
