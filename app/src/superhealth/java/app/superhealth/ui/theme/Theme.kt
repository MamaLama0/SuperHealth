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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.domain.AccentColor
import app.superhealth.nutrition.domain.TextSize
import app.superhealth.nutrition.domain.ThemeMode
import nodomain.freeyourgadget.gadgetbridge.R

// Palette inspirée des couleurs Google (Material 3), tons intermédiaires entre clair et sombre :
// même teinte partout dans l'app, lisible sur les deux fonds. Thème unique : nutrition, entraînement et santé.
object Base {
    val Grey = Color(0xFF8E9199)
    val Green = Color(0xFF34A853)
    val Yellow = Color(0xFFF9B500)
    val Orange = Color(0xFFF2803A)
    val Blue = Color(0xFF4285F4)
    val Purple = Color(0xFF9C5AE6)
    val Red = Color(0xFFEA4335)
    val Pink = Color(0xFFE8558F)
    val Indigo = Color(0xFF6C6FE0)
    val Teal = Color(0xFF12A4AF)
    val Mint = Color(0xFF1EA88E)
    val Cyan = Color(0xFF2E97C7)
}

val CaloriesColor = Base.Blue
val ProteinColor = Base.Red
val FatColor = Base.Yellow
val CarbsColor = Base.Green

/** Couleur de l'entraînement (séries, muscles travaillés). */
val WorkoutBlue: Color get() = StyleLab.workoutColor
/** Corps (#DCDCDC) et muscles au repos (#EEEEEE) des silhouettes assets/body. */
val BodyGrey = Color(0xFFDCDCDC)
val MuscleIdle = Color(0xFFEEEEEE)

/** Couleurs des programmes. */
val ProgramColors = ProgramPalette

/** Familles de mesures santé, à la manière de Google Santé (une couleur par famille). */
object HealthHues {
    val Activity = Color(0xFF1F9E85)
    val Heart = Color(0xFFE5574F)
    val Sleep = Color(0xFF7B61D9)
    val Mind = Color(0xFFD9931F)
    val Respiration = Base.Cyan
    val Fitness = Base.Orange
    val Body = Color(0xFF3F7DE8)
}

/** Fond légèrement teinté d'une couleur (pastilles, sélection), lisible en clair comme en sombre. */
@Composable
fun Color.container(strength: Float = 1f): Color =
    androidx.compose.ui.graphics.lerp(Mf.colors.card, this, (if (Mf.colors.dark) 0.24f else 0.14f) * strength)

/** Texte coloré posé sur une carte : la couleur elle-même, assombrie en clair et éclaircie en sombre pour le contraste. */
@Composable
fun Color.onContainer(): Color {
    val boost = LocalInkBoost.current
    return if (Mf.colors.dark) androidx.compose.ui.graphics.lerp(this, Color.White, 0.12f + boost)
    else androidx.compose.ui.graphics.lerp(this, Color.Black, 0.18f + boost)
}

/** Rayon des cartes et des listes groupées (Material 3 : grand). */
val CardRadius = 24.dp
/** Rayon des champs, vignettes et petits blocs posés dans une carte. */
val FieldRadius = 12.dp

fun AccentColor.color() = when (this) {
    AccentColor.BLUE -> Base.Blue
    AccentColor.PINK -> Base.Pink
    AccentColor.GREEN -> Base.Green
    AccentColor.YELLOW -> Base.Yellow
    AccentColor.PURPLE -> Base.Purple
    AccentColor.ORANGE -> Base.Orange
    AccentColor.GRAY -> Base.Grey
}

/** Couleurs propres à l'app, en plus du schéma Material (rôles des surfaces de Material 3). */
@Immutable
data class MfColors(
    val dark: Boolean,
    /** surface du fond */
    val background: Color,
    /** surfaceContainerLowest / surfaceContainer : cartes et listes */
    val card: Color,
    /** surfaceContainerHigh : blocs posés sur une carte, menus */
    val elevated: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** séparateur fin des listes */
    val divider: Color,
    /** remplissage neutre : champs, boutons secondaires, pistes */
    val chip: Color,
    val accent: Color,
    /** Action principale : la couleur d'accent, texte blanc (bouton plein de Material 3). */
    val strong: Color,
    val onStrong: Color,
)

private fun onAccent(accent: Color) = if (accent.luminance() > 0.62f) Color(0xFF1C1C1E) else Color.White

// neutres légèrement bleutés, comme les surfaces tonales de Material 3
private fun lightMf(accent: Color) = MfColors(
    dark = false, background = Color(0xFFF1F3F8), card = Color.White, elevated = Color(0xFFE9ECF3),
    text = Color(0xFF1B1C20), textSecondary = Color(0xFF5E636D), textTertiary = Color(0xFFA0A5AE),
    divider = Color(0xFFE2E5EC), chip = Color(0xFFE7EAF1), accent = accent, strong = accent, onStrong = onAccent(accent),
)

private fun darkMf(accent: Color) = MfColors(
    dark = true, background = Color(0xFF111318), card = Color(0xFF1D2026), elevated = Color(0xFF282B32),
    text = Color(0xFFE3E4E9), textSecondary = Color(0xFFA9ADB7), textTertiary = Color(0xFF6B707A),
    divider = Color(0xFF30333A), chip = Color(0xFF2A2D35), accent = accent, strong = accent, onStrong = onAccent(accent),
)

val LocalMf = staticCompositionLocalOf { lightMf(Base.Blue) }

object Mf {
    val colors: MfColors @Composable get() = LocalMf.current
}

/** Google Sans Flex (variable, réduite au latin) : graisse et taille optique. */
@OptIn(ExperimentalTextApi::class)
private fun sans(weight: Int, opsz: Float) = Font(
    R.font.sans_flex, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

private fun sansFamily(opsz: Float) = FontFamily(sans(400, opsz), sans(500, opsz), sans(600, opsz), sans(700, opsz), sans(800, opsz))

/** Texte courant (taille optique 14). */
val SansText = sansFamily(14f)
/** Titres et grands chiffres (taille optique 28). */
val SansDisplay = sansFamily(28f)

// échelle de Material 3 ; titres en graisse moyenne (pas de gras d'affiche), chiffres tabulaires pour les grandes valeurs
private fun typography(): Typography {
    val text = TextStyle(fontFamily = SansText, letterSpacing = 0.sp)
    val display = TextStyle(fontFamily = SansDisplay, letterSpacing = 0.sp)
    val digits = display.copy(fontFeatureSettings = "tnum")
    return Typography(
        displayLarge = digits.copy(fontSize = 46.sp, fontWeight = FontWeight.Medium, lineHeight = 52.sp, letterSpacing = (-0.6).sp),
        displayMedium = digits.copy(fontSize = 38.sp, fontWeight = FontWeight.Medium, lineHeight = 44.sp, letterSpacing = (-0.4).sp),
        displaySmall = digits.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp, letterSpacing = (-0.2).sp),
        headlineLarge = display.copy(fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 38.sp),
        headlineMedium = display.copy(fontSize = 25.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
        headlineSmall = display.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
        titleLarge = text.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        titleMedium = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        titleSmall = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = text.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp, letterSpacing = 0.1.sp),
        bodyMedium = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.15.sp),
        bodySmall = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.2.sp),
        labelLarge = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = text.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        labelSmall = text.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    )
}

fun ThemeMode.isDark(system: Boolean) = when (this) {
    ThemeMode.SYSTEM -> system
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private val shapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(FieldRadius),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun SuperTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: AccentColor = AccentColor.BLUE, textSize: TextSize = TextSize.NORMAL,
               content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val acc = accent.color()
    val mf = if (dark) darkMf(acc) else lightMf(acc)
    // labo de style : couleur de l'entraînement (bleu, accent ou programme), suivie par WorkoutBlue
    val labWorkout = app.superhealth.SuperPrefs.settings.collectAsState().value.styleWorkoutColor
    androidx.compose.runtime.SideEffect { if (labWorkout.isNotEmpty()) StyleLab.workoutColor = StyleLab.resolveWorkout(acc) }
    val scheme = if (dark) darkColorScheme(
        primary = mf.strong, onPrimary = mf.onStrong, secondary = acc, onSecondary = Color.White,
        background = mf.background, onBackground = mf.text, surface = mf.card, onSurface = mf.text,
        surfaceVariant = mf.elevated, onSurfaceVariant = mf.textSecondary, surfaceContainer = mf.card,
        surfaceContainerLow = mf.card, surfaceContainerHigh = mf.elevated, surfaceContainerHighest = mf.elevated,
        outline = Color(0xFF8E9199), outlineVariant = mf.divider, error = Base.Red, primaryContainer = mf.elevated,
        onPrimaryContainer = mf.text, secondaryContainer = mf.elevated, onSecondaryContainer = mf.text,
    ) else lightColorScheme(
        primary = mf.strong, onPrimary = mf.onStrong, secondary = acc, onSecondary = Color.White,
        background = mf.background, onBackground = mf.text, surface = mf.card, onSurface = mf.text,
        surfaceVariant = mf.elevated, onSurfaceVariant = mf.textSecondary, surfaceContainer = mf.card,
        surfaceContainerLow = mf.card, surfaceContainerHigh = mf.card, surfaceContainerHighest = mf.elevated,
        outline = Color(0xFF747880), outlineVariant = mf.divider, error = Base.Red, primaryContainer = mf.elevated,
        onPrimaryContainer = mf.text, secondaryContainer = mf.elevated, onSecondaryContainer = mf.text,
    )
    // taille du texte : multiplie l'échelle du système, donc aussi les tailles fixées à la main hors typographie
    val density = LocalDensity.current
    val scaled = if (textSize == TextSize.NORMAL) density else Density(density.density, density.fontScale * textSize.scale)
    CompositionLocalProvider(LocalMf provides mf, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides mf.text, content = content)
        }
    }
}
