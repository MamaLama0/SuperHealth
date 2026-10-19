package app.localhealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nodomain.freeyourgadget.gadgetbridge.R

/** Google Health "ColorFamily": one hue with its container/data/on-* roles. */
@Immutable
data class ColorFamily(
    val color: Color,
    val container: Color,
    val containerVariant: Color,
    val data: Color,
    val dataBright: Color,
    val fixed: Color,
    val fixedDim: Color,
    val variant: Color,
    val onColor: Color,
    val onContainer: Color,
    val onFixed: Color,
    val onFixedVariant: Color,
    val onVariant: Color,
)

/** Google Health "ExtendedColorScheme": the metric hues used across charts and cards. */
@Immutable
data class ExtendedColorScheme(
    val blue: ColorFamily,
    val blueMarine: ColorFamily,
    val goldenrod: ColorFamily,
    val green: ColorFamily,
    val lilac: ColorFamily,
    val magenta: ColorFamily,
    val tangerine: ColorFamily,
    val teal: ColorFamily,
    val error: ColorFamily,
)

val LocalExtendedColors = staticCompositionLocalOf { GhLightExtendedColors }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.extendedColors: ExtendedColorScheme
    @Composable get() = LocalExtendedColors.current

/** The colour family for a metric hue; NEUTRAL is the grey card used for metrics without data. */
@Composable
fun familyFor(hue: app.localhealth.data.Hue): ColorFamily {
    val ext = LocalExtendedColors.current
    val cs = MaterialTheme.colorScheme
    return when (hue) {
        app.localhealth.data.Hue.BLUE -> ext.blue
        app.localhealth.data.Hue.BLUE_MARINE -> ext.blueMarine
        app.localhealth.data.Hue.GOLDENROD -> ext.goldenrod
        app.localhealth.data.Hue.GREEN -> ext.green
        app.localhealth.data.Hue.LILAC -> ext.lilac
        app.localhealth.data.Hue.MAGENTA -> ext.magenta
        app.localhealth.data.Hue.TANGERINE -> ext.tangerine
        app.localhealth.data.Hue.TEAL -> ext.teal
        app.localhealth.data.Hue.NEUTRAL -> neutralFamily(cs)
    }
}

/** Google Health's grey pill card: surfaceContainerHighest with a surfaceContainerLow icon pill. */
fun neutralFamily(cs: androidx.compose.material3.ColorScheme) = ColorFamily(
    color = cs.onSurfaceVariant,
    container = cs.surfaceContainerHighest,
    containerVariant = cs.surfaceContainerLow,
    data = cs.outline,
    dataBright = cs.onSurfaceVariant,
    fixed = cs.surfaceContainerHighest,
    fixedDim = cs.surfaceContainerHigh,
    variant = cs.onSurfaceVariant,
    onColor = cs.surface,
    onContainer = cs.onSurfaceVariant,
    onFixed = cs.onSurface,
    onFixedVariant = cs.onSurfaceVariant,
    onVariant = cs.onSurfaceVariant,
)

private fun googleSans(weight: FontWeight) = Font(
    R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val GoogleSansFlex = FontFamily(
    googleSans(FontWeight.Normal),
    googleSans(FontWeight.Medium),
    googleSans(FontWeight.SemiBold),
    googleSans(FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = GoogleSansFlex, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

// GM3 type scale (m3_sys_typescale_*), with Google Sans Flex for both brand and plain faces
private val GhTypography = Typography(
    displayLarge = style(57, 64),
    displayMedium = style(45, 52),
    displaySmall = style(36, 44),
    headlineLarge = style(32, 40),
    headlineMedium = style(28, 36),
    headlineSmall = style(24, 32),
    titleLarge = style(22, 28),
    titleMedium = style(16, 24, FontWeight.Medium, 0.15),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20, tracking = 0.25),
    bodySmall = style(12, 16, tracking = 0.4),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),
)

private val GhShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HealthTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) GhDarkExtendedColors else GhLightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) GhDarkColorScheme else GhLightColorScheme,
            typography = GhTypography,
            shapes = GhShapes,
            content = content,
        )
    }
}
