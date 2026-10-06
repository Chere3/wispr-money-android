package mx.diego.wispr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.diego.wispr.R

// Estética Google Health: azul de marca constante en lo interactivo, fondo gris azulado con
// tarjetas blancas muy redondeadas, un color temático por sección y cifras grandes primero.
// Sin color dinámico, igual que Google Health: la marca y los semáforos deben verse siempre igual.

internal val Light = lightColorScheme(
    primary = Color(0xFF1F5FC4), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF565E71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF006A64), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9CF2E9), onTertiaryContainer = Color(0xFF00201E),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF1F3F9), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF1F3F9), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC), onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF757780), outlineVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FD),
    surfaceContainer = Color(0xFFEDEFF5), surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceDim = Color(0xFFD9D9E0), surfaceBright = Color(0xFFF9F9FF),
    inverseSurface = Color(0xFF2F3036), inverseOnSurface = Color(0xFFF0F0F7), inversePrimary = Color(0xFFADC6FF),
)

internal val Dark = darkColorScheme(
    primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494), onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF80D5CD), onTertiary = Color(0xFF003734),
    tertiaryContainer = Color(0xFF00504B), onTertiaryContainer = Color(0xFF9CF2E9),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C0E13), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF0C0E13), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F), onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099), outlineVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF1A1C21), surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1E2025), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    surfaceDim = Color(0xFF111318), surfaceBright = Color(0xFF37393E),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2F3036), inversePrimary = Color(0xFF1F5FC4),
)

/** Colores fuera del esquema M3: semáforo de presupuestos y acento por sección. */
@Immutable
data class WisprColors(
    val good: Color,
    val warn: Color,
    val bad: Color,
    val income: Color,
    val today: Color,
    val movements: Color,
    val flow: Color,
    val wealth: Color,
    val chart: List<Color>,
)

internal val LightExtra = WisprColors(
    good = Color(0xFF1E8E3E), warn = Color(0xFFE37400), bad = Color(0xFFD93025), income = Color(0xFF137333),
    today = Color(0xFF1F5FC4), movements = Color(0xFF7C4DFF), flow = Color(0xFF00897B), wealth = Color(0xFF8E24AA),
    chart = listOf(
        Color(0xFF1F5FC4), Color(0xFF00897B), Color(0xFFE37400), Color(0xFF8E24AA), Color(0xFFD93025),
        Color(0xFF0097A7), Color(0xFF7CB342), Color(0xFFC2185B), Color(0xFF5C6BC0), Color(0xFF8D6E63),
    ),
)

internal val DarkExtra = WisprColors(
    good = Color(0xFF81C995), warn = Color(0xFFFCAD70), bad = Color(0xFFF28B82), income = Color(0xFF81C995),
    today = Color(0xFFADC6FF), movements = Color(0xFFC6B6FF), flow = Color(0xFF80D5CD), wealth = Color(0xFFE1A6F0),
    chart = listOf(
        Color(0xFFADC6FF), Color(0xFF80D5CD), Color(0xFFFCAD70), Color(0xFFE1A6F0), Color(0xFFF28B82),
        Color(0xFF7FD8E6), Color(0xFFB5DD8B), Color(0xFFF48FB1), Color(0xFF9FA8DA), Color(0xFFBCAAA4),
    ),
)

val LocalWisprColors = staticCompositionLocalOf { LightExtra }

@OptIn(ExperimentalTextApi::class)
private fun sans(weight: Int) = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // ROND redondea las terminaciones: el toque amable de Google Health.
        FontVariation.Setting("ROND", 100f),
    ),
)

val GoogleSansFlex = FontFamily(sans(400), sans(500), sans(600), sans(700), sans(800))

private val base = Typography()

private fun TextStyle.flex() = copy(fontFamily = GoogleSansFlex)

private val WisprTypography = Typography(
    displayLarge = base.displayLarge.flex().copy(fontWeight = FontWeight(700), letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.flex().copy(fontWeight = FontWeight(700), letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.flex().copy(fontWeight = FontWeight(700)),
    headlineLarge = base.headlineLarge.flex().copy(fontWeight = FontWeight(600)),
    headlineMedium = base.headlineMedium.flex().copy(fontWeight = FontWeight(600)),
    headlineSmall = base.headlineSmall.flex().copy(fontWeight = FontWeight(600)),
    titleLarge = base.titleLarge.flex().copy(fontWeight = FontWeight(600)),
    titleMedium = base.titleMedium.flex().copy(fontWeight = FontWeight(600)),
    titleSmall = base.titleSmall.flex().copy(fontWeight = FontWeight(600)),
    bodyLarge = base.bodyLarge.flex(),
    bodyMedium = base.bodyMedium.flex(),
    bodySmall = base.bodySmall.flex(),
    labelLarge = base.labelLarge.flex().copy(fontWeight = FontWeight(600)),
    labelMedium = base.labelMedium.flex().copy(fontWeight = FontWeight(600)),
    labelSmall = base.labelSmall.flex().copy(fontWeight = FontWeight(600)),
)

private val WisprShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun WisprTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWisprColors provides if (dark) DarkExtra else LightExtra) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) Dark else Light,
            motionScheme = MotionScheme.expressive(),
            typography = WisprTypography,
            shapes = WisprShapes,
            content = content,
        )
    }
}

object Wispr {
    val colors: WisprColors @Composable get() = LocalWisprColors.current

    /** Verde < 80 %, naranja 80–100 %, rojo si se excedió. */
    @Composable
    fun tone(fraction: Double?): Color = when {
        fraction == null -> MaterialTheme.colorScheme.outline
        fraction > 1 -> colors.bad
        fraction >= 0.8 -> colors.warn
        else -> colors.good
    }
}
