package app.superhealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.nutrition.domain.ThemeMode
import nodomain.freeyourgadget.gadgetbridge.R

// Palette décodée du pool d'objets Dart de MacroFactor 5.8.9 (themes/new_theme, light_theme, dark_theme) :
// voir MacroFactor-analyse/notes/12-design.md. Thème unique de SuperHealth : nutrition, entraînement et santé.
object Base {
    val Grey = Color(0xFF757575)
    val Green = Color(0xFF4CB179)
    val Yellow = Color(0xFFFFC447)
    val Orange = Color(0xFFFF9663)
    val Blue = Color(0xFF5A95F9)
    val Purple = Color(0xFF9082D4)
    val Red = Color(0xFFF46B5A)
    val Pink = Color(0xFFFF7FA2)
}

val CaloriesColor = Base.Blue
val ProteinColor = Base.Red
val FatColor = Base.Yellow
val CarbsColor = Base.Green

/** Bleu des muscles travaillés dans les SVG de MacroFactor Workouts (assets/muscle_groups, fill #276EF1). */
val WorkoutBlue = Color(0xFF276EF1)
/** Corps (#DCDCDC) et muscles au repos (#EEEEEE) des silhouettes assets/body. */
val BodyGrey = Color(0xFFDCDCDC)
val MuscleIdle = Color(0xFFEEEEEE)

/** Couleurs des programmes (WorkoutProgramColor) [INFÉRÉ : palette ThemeColor de MacroFactor]. */
val ProgramColors = listOf(WorkoutBlue, Base.Red, Base.Green, Base.Orange, Base.Purple, Base.Pink, Base.Yellow, Base.Grey)

/** Familles de mesures santé (montre) ramenées à la palette MacroFactor. */
object HealthHues {
    val Activity = Base.Green
    val Heart = Base.Red
    val Sleep = Base.Purple
    val Mind = Base.Yellow
    val Respiration = Base.Blue
    val Fitness = Base.Orange
    val Body = Base.Blue
}

fun AccentColor.color() = when (this) {
    AccentColor.BLUE -> Base.Blue
    AccentColor.PINK -> Base.Pink
    AccentColor.GREEN -> Base.Green
    AccentColor.YELLOW -> Base.Yellow
    AccentColor.PURPLE -> Base.Purple
    AccentColor.ORANGE -> Base.Orange
    AccentColor.GRAY -> Base.Grey
}

/** Couleurs propres à l'app, en plus du schéma Material. */
@Immutable
data class MfColors(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val elevated: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val chip: Color,
    val accent: Color,
    /** Bouton principal : noir en clair, blanc en sombre (comme le « + » et « Edit » de MacroFactor). */
    val strong: Color,
    val onStrong: Color,
)

private fun lightMf(accent: Color) = MfColors(
    dark = false, background = Color(0xFFF8F8F8), card = Color.White, elevated = Color(0xFFEEEEEE),
    text = Color(0xFF0D0D0D), textSecondary = Base.Grey, textTertiary = Color(0xFFABB0BA),
    divider = Color(0xFFE5E6E8), chip = Color(0xFFEEEEEE), accent = accent, strong = Color(0xFF0D0D0D), onStrong = Color.White,
)

private fun darkMf(accent: Color) = MfColors(
    dark = true, background = Color(0xFF121212), card = Color(0xFF1E1E1E), elevated = Color(0xFF292929),
    text = Color(0xFFE2E2E2), textSecondary = Color(0xFFB0B0B0), textTertiary = Color(0xFF6D6D6D),
    divider = Color(0xFF313133), chip = Color(0xFF292929), accent = accent, strong = Color.White, onStrong = Color(0xFF0D0D0D),
)

val LocalMf = staticCompositionLocalOf { lightMf(Base.Blue) }

object Mf {
    val colors: MfColors @Composable get() = LocalMf.current
}

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    R.font.dm_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 14f)),
)

val DmSans = FontFamily(dmSans(400), dmSans(500), dmSans(600), dmSans(700), dmSans(800))

private fun typography(): Typography {
    val base = TextStyle(fontFamily = DmSans, letterSpacing = 0.sp)
    return Typography(
        displayLarge = base.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
        displayMedium = base.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
        displaySmall = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
        headlineLarge = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
        headlineMedium = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
        headlineSmall = base.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
        titleLarge = base.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
        titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
        titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
        bodyLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
        bodyMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        bodySmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
        labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
        labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelSmall = base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
    )
}

fun ThemeMode.isDark(system: Boolean) = when (this) {
    ThemeMode.SYSTEM -> system
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SuperTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: AccentColor = AccentColor.BLUE, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val acc = accent.color()
    val mf = if (dark) darkMf(acc) else lightMf(acc)
    val scheme = if (dark) darkColorScheme(
        primary = mf.strong, onPrimary = mf.onStrong, secondary = acc, onSecondary = Color.White,
        background = mf.background, onBackground = mf.text, surface = mf.card, onSurface = mf.text,
        surfaceVariant = mf.elevated, onSurfaceVariant = mf.textSecondary, surfaceContainer = mf.card,
        surfaceContainerLow = mf.card, surfaceContainerHigh = mf.elevated, surfaceContainerHighest = mf.elevated,
        outline = Color(0xFF4B4B4D), outlineVariant = mf.divider, error = Base.Red, primaryContainer = mf.elevated,
        onPrimaryContainer = mf.text, secondaryContainer = mf.elevated, onSecondaryContainer = mf.text,
    ) else lightColorScheme(
        primary = mf.strong, onPrimary = mf.onStrong, secondary = acc, onSecondary = Color.White,
        background = mf.background, onBackground = mf.text, surface = mf.card, onSurface = mf.text,
        surfaceVariant = mf.elevated, onSurfaceVariant = mf.textSecondary, surfaceContainer = mf.card,
        surfaceContainerLow = mf.card, surfaceContainerHigh = Color(0xFFF3F3F3), surfaceContainerHighest = mf.elevated,
        outline = Color(0xFFD1D3D8), outlineVariant = mf.divider, error = Color(0xFFE11900), primaryContainer = mf.elevated,
        onPrimaryContainer = mf.text, secondaryContainer = mf.elevated, onSecondaryContainer = mf.text,
    )
    CompositionLocalProvider(LocalMf provides mf) {
        MaterialTheme(colorScheme = scheme, typography = typography()) {
            CompositionLocalProvider(LocalContentColor provides mf.text, content = content)
        }
    }
}
