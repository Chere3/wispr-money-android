# CLAUDE.md — Wispr Money para Android

Repo público: github.com/Chere3/wispr-money-android (hermano de wispr-money-mac).

App nativa de Android (Kotlin + Jetpack Compose, Material 3 Expressive) con widget Glance y
notificación permanente para Wispr Money self-hosted. Hermana de `../wispr-mac` (misma conexión,
mismas reglas). Idioma: español (UI, comentarios, docs, commits).

## Reglas no negociables

- **Conexión directa, sin caché:** toda cifra viene de una llamada en vivo al MCP de Wispr
  (`wisprkit/MCPClient`). Nada de persistir montos (ni SharedPreferences, ni DataStore, ni estado
  de Glance, ni archivos). Si una consulta falla se muestra el error, nunca valores anteriores.
- Widget y notificación: se refrescan ~cada 15 min pidiendo en vivo, muestran la hora de consulta
  y "Sin conexión" si falla (acordado con Diego, igual que en Mac).
- Lo único persistido es endpoint + token, cifrados con AES-GCM y una clave del Android Keystore
  (`app/.../data/CredentialStore.kt`). EncryptedSharedPreferences está deprecado: no usarlo.
  El token nunca se imprime ni se loguea.
- Datos financieros reales: no van a commits, tests, fixtures, capturas en el repo ni reportes.
  Tests con fixtures sintéticos. Capturas de pantalla solo al scratchpad.
- Montos en unidades menores (`Long`, ver `Money`); la moneda sale de la respuesta.

## Estructura

- `wisprkit/` — Kotlin/JVM puro (sin Android): port 1:1 de `WisprKit` de la app de Mac. Cliente MCP
  Streamable HTTP (JSON-RPC, SSE, `Mcp-Session-Id`, renegocia en 404), modelos, `Money`, probe.
- `app/` — Compose. `ui/screens/` (Hoy, Movimientos + alta, Flujo, Patrimonio, Setup),
  `ui/components/` (anillos, tarjetas, gráficas Canvas, `rememberLive`/`LiveContent`),
  `ui/theme/` (esquema fijo + Google Sans Flex), `widget/` (Glance), `notify/` (WorkManager +
  notificación).

## Widget (`widget/WisprWidget.kt`)

Un solo widget redimensionable (`SizeMode.Exact`): `Layout()` elige entre 7 diseños según el
tamaño real (celda típica ~90 × 105 dp). La tabla de qué muestra cada tamaño está en el KDoc de
`WisprWidget`. Trampas de Glance: máximo **10 hijos por `Column`/`Row`** (los sobrantes se
descartan sin error; por eso cada lista va en su propia columna), no dibuja arcos (el anillo es un
bitmap) y la vista previa del selector usa `providePreview` con `WidgetData.sample()` (sintético).

## Compilar, instalar, probar

No hay `java` en el PATH: usa el JBR de Android Studio.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :wisprkit:test                     # tests del cliente (MockWebServer, sin red)
./gradlew :app:assembleDebug                 # APK debug
WISPR_ENDPOINT=https://<host>/mcp WISPR_TOKEN=<token> ./gradlew :wisprkit:probe   # conexión real, sin montos

~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &   # o: android emulator start
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Solo debug: conectar sin teclear el token
adb shell am start -n mx.diego.wispr/.MainActivity --es endpoint <url> --es token <token>
```

Sin servidor real: `python3 scripts/demo_server.py` (MCP con datos sintéticos) y en el emulador
`--es endpoint http://10.0.2.2:8765/mcp --es token demo`. Solo la variante debug permite HTTP
(`app/src/debug/res/xml/network_security_config.xml`); release es solo HTTPS. **Capturas para el
README o issues: siempre con el demo**, y se regeneran con `scripts/make-docs-images.py`.

El servidor está en Tailscale: el emulador lo alcanza a través de la Mac; un teléfono real
necesita la app de Tailscale conectada. Release: `./gradlew :app:assembleRelease` (firmado con la
clave debug; app personal sideloaded).

## Versiones (verificadas el 2026-10-05)

AGP 9.4.1 (Kotlin integrado: no se aplica `kotlin-android`) · Gradle 9.8.0 · Kotlin 2.4.20 ·
Compose BOM 2026.09.00 con **material3 1.5.0-alpha** forzado: el 1.4.0 estable quitó todo lo
Expressive (`MaterialExpressiveTheme`, `ShortNavigationBar` sí es estable). `LoadingIndicator` y
`MaterialShapes` siguen experimentales (opt-in global en `app/build.gradle.kts`). Glance 1.2.0 ·
WorkManager 2.12.0 · OkHttp 5.5.0. compileSdk/targetSdk 37, minSdk 31.

## Diseño: estética Google Health

- Esquema de color fijo (sin dynamic color, como Google Health): azul de marca en lo interactivo,
  fondo gris azulado, tarjetas blancas con esquinas de 28 dp.
- Un color por sección (velo arriba que se funde con el fondo): Hoy azul, Movimientos violeta,
  Flujo teal, Patrimonio morado. Semáforo de presupuestos: verde < 80 %, naranja 80–100 %, rojo.
- Números primero: cifra protagonista en `displayMedium`, contexto debajo (delta vs mes anterior).
- Anillos tipo Actividad; pasado el 100 % dan una segunda vuelta más oscura (app y widget).
- `ShortNavigationBar` de 4 pestañas, `MotionScheme.expressive()`, pull-to-refresh con
  `LoadingIndicator`, gráficas que entran de izquierda a derecha.
- Tipografía: Google Sans Flex variable (OFL, `res/font`, licencia en `licenses/`) con eje ROND.

## Notificación permanente

`notify/RefreshWorker` (WorkManager, 15 min) consulta en vivo y republica la notificación con id
fijo, canal silencioso. Desde Android 13/14 **ninguna** notificación (ni la de un servicio en
primer plano) es imposible de descartar con el teléfono desbloqueado; por eso, si Diego la
desliza, `NotificationDismissedReceiver` la vuelve a pedir y publicar. También se republica tras
reiniciar. Visibilidad PRIVATE: en la pantalla de bloqueo no se ven cifras. Pendiente posible:
`Notification.MetricStyle` (API 37) para mostrar métricas nativas.

## MCPs del proyecto (`.mcp.json`)

- `mobile-mcp` (mobile-next): manejar emulador/teléfono — taps, capturas, árbol de accesibilidad.
- `context7`: docs por versión de Compose, Glance, WorkManager, OkHttp.
- `android-skills` (skydoves): sirve las skills oficiales de `android/skills` por MCP.

Además, la **Android CLI** de Google (`android`, ya instalada): `android docs search <q>`,
`android emulator`, `android screen`, `android layout`, `android run`. Google no publica un MCP
oficial de Android; la CLI + skills es su vía para agentes.

## Skills (`.claude/skills/`)

Oficiales de Google (`android skills add <id> --project=. --agent=claude-code`; actualizar con
`android skills update --all`): `android-cli`, `edge-to-edge`, `styles`, `adaptive`,
`navigation-3`, `testing-setup`, `android-permissions-security`, `android-intent-security`,
`agp-9-upgrade`, `r8-analyzer`. Comunitaria: `material-3-expressive` (Albermonte, solo docs y
tokens de M3 Expressive; ver `ORIGEN.txt`). No hay skill de Glance con tracción: usar Context7.
