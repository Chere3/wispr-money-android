# Contribuir

¡Gracias por querer mejorar Wispr Money para Android! Issues y PRs en español o inglés.

## Reglas no negociables

- **Nada de datos financieros reales** en commits, tests, fixtures, capturas ni issues. Para
  probar la UI usa `scripts/demo_server.py`; para tests, fixtures sintéticos.
- **Sin caché de montos:** toda cifra viene de una llamada en vivo al MCP. Nada de persistir montos
  (SharedPreferences, DataStore, estado de Glance, archivos). Si una consulta falla se muestra el
  error, nunca valores anteriores.
- Montos en unidades menores (`Money`); la moneda sale de la respuesta, no se hardcodea.
- El token vive cifrado con el Keystore y nunca se imprime ni se registra.

## Flujo de trabajo

Requiere JDK 17+ (el JBR de Android Studio sirve) y el Android SDK con la plataforma 37.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
./gradlew :wisprkit:test :app:assembleDebug

python3 scripts/demo_server.py &              # datos sintéticos
./gradlew :app:installDebug
adb shell am start -n mx.diego.wispr/.MainActivity --es endpoint http://10.0.2.2:8765/mcp --es token demo
```

- Cada pantalla es `XScreen` (carga en vivo con `rememberLive`) + `XContent` puro que recibe los
  datos. La UI nueva va en esa separación.
- Widget (`app/.../widget/WisprWidget.kt`): `Layout()` elige el diseño por tamaño. Glance admite
  **máximo 10 hijos por `Column`/`Row`** y descarta el resto sin avisar: agrupa las listas.
- Cambios de UI: adjunta capturas de antes y después **con el servidor de demostración**.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`,
  `docs:`…).
- Las imágenes del README se regeneran con `scripts/make-docs-images.py` (ver su docstring).
