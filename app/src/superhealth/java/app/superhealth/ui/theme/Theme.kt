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
import androidx.compose.ui.unit.dp
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

/**
 * Familles de mesures santé : teintes un peu plus douces que la palette MacroFactor, à la manière de Google Health
 * (chaque famille a sa couleur et son fond pastel, voir [container]).
 */
object HealthHues {
    val Activity = Color(0xFF1F9E85)
    val Heart = Color(0xFFE5574F)
    val Sleep = Color(0xFF7B61D9)
    val Mind = Color(0xFFD9931F)
    val Respiration = Color(0xFF2E97C7)
    val Fitness = Color(0xFFE77A45)
    val Body = Color(0xFF3F7DE8)
}

/** Fond pastel d'une couleur de famille (cartes teintées, pastilles d'icônes), lisible en clair comme en sombre. */
@Composable
fun Color.container(): Color = androidx.compose.ui.graphics.lerp(Mf.colors.card, this, if (Mf.colors.dark) 0.20f else 0.14f)

/** Texte posé sur [container] : la couleur elle-même, assombrie en clair pour le contraste. */
@Composable
fun Color.onContainer(): Color = if (Mf.colors.dark) androidx.compose.ui.graphics.lerp(this, Color.White, 0.25f)
    else androidx.compose.ui.graphics.lerp(this, Color.Black, 0.30f)

/** Rayon des cartes (plus rond que MacroFactor, comme Google Health). */
val CardRadius = 24.dp

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
    dark = false, background = Color(0xFFF0F3F8), card = Color.White, elevated = Color(0xFFE6EBF2),
    text = Color(0xFF12161C), textSecondary = Color(0xFF5E6773), textTertiary = Color(0xFFA2AAB5),
    divider = Color(0xFFE3E8EF), chip = Color(0xFFE9EEF5), accent = accent, strong = Color(0xFF12161C), onStrong = Color.White,
)

private fun darkMf(accent: Color) = MfColors(
    dark = true, background = Color(0xFF0F1216), card = Color(0xFF1A1E24), elevated = Color(0xFF252A31),
    text = Color(0xFFE7EAEF), textSecondary = Color(0xFFA5ADB8), textTertiary = Color(0xFF6A727D),
    divider = Color(0xFF2A3038), chip = Color(0xFF252A31), accent = accent, strong = Color(0xFFE7EAEF), onStrong = Color(0xFF0F1216),
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
        surfaceContainerLow = mf.card, surfaceContainerHigh = Color(0xFFF3F6FA), surfaceContainerHighest = mf.elevated,
        outline = Color(0xFFD1D3D8), outlineVariant = mf.divider, error = Color(0xFFE11900), primaryContainer = mf.elevated,
        onPrimaryContainer = mf.text, secondaryContainer = mf.elevated, onSecondaryContainer = mf.text,
    )
    CompositionLocalProvider(LocalMf provides mf) {
        MaterialTheme(colorScheme = scheme, typography = typography()) {
            CompositionLocalProvider(LocalContentColor provides mf.text, content = content)
        }
    }
}
