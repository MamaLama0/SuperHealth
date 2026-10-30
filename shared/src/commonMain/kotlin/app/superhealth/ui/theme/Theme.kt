package app.superhealth.ui.theme

import app.superhealth.platform.*

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// Couleurs des données : un jeu de teintes (réglable dans Apparence), décliné pour le clair et le sombre. Même teinte pour une
// même mesure partout dans l'app ; en sombre, des tons plus clairs mais jamais fluo.
object Base {
    val Grey get() = Hues.pack.grey
    val Green get() = Hues.pack.green
    val Yellow get() = Hues.pack.yellow
    val Orange get() = Hues.pack.orange
    val Blue get() = Hues.pack.blue
    val Purple get() = Hues.pack.purple
    val Red get() = Hues.pack.red
    val Pink get() = Hues.pack.pink
    val Indigo get() = Hues.pack.indigo
    val Teal get() = Hues.pack.teal
    val Mint get() = Hues.pack.mint
    val Cyan get() = Hues.pack.cyan
}

val CaloriesColor get() = Base.Blue
val ProteinColor get() = Base.Red
val FatColor get() = Base.Yellow
val CarbsColor get() = Base.Green

/** Couleur de l'entraînement (séries, muscles travaillés). */
val WorkoutBlue: Color get() = StyleLab.workoutColor
/** Corps (#DCDCDC) et muscles au repos (#EEEEEE) des silhouettes assets/body. */
val BodyGrey = Color(0xFFDCDCDC)
val MuscleIdle = Color(0xFFEEEEEE)

/** Couleurs des programmes. */
val ProgramColors = ProgramPalette

/** Familles de mesures santé, à la manière de Google Santé (une couleur par famille). */
object HealthHues {
    val Activity get() = Hues.pack.activity
    val Heart get() = Hues.pack.heart
    val Sleep get() = Hues.pack.sleep
    val Mind get() = Hues.pack.mind
    val Respiration get() = Base.Cyan
    val Fitness get() = Base.Orange
    val Body get() = Hues.pack.body
}

/** Les couleurs d'un jeu de teintes, pour un fond (clair ou sombre). */
@Immutable
class HuePack(
    val grey: Color, val green: Color, val yellow: Color, val orange: Color, val blue: Color, val purple: Color, val red: Color, val pink: Color,
    val indigo: Color, val teal: Color, val mint: Color, val cyan: Color, val activity: Color, val heart: Color, val sleep: Color, val mind: Color, val body: Color,
)

private fun pack(vararg c: Long) = HuePack(Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]), Color(c[5]), Color(c[6]), Color(c[7]),
    Color(c[8]), Color(c[9]), Color(c[10]), Color(c[11]), Color(c[12]), Color(c[13]), Color(c[14]), Color(c[15]), Color(c[16]))

/**
 * Jeux de teintes des données (Réglages > Apparence). Ordre des couleurs : gris, vert, jaune, orange, bleu, violet, rouge, rose,
 * indigo, sarcelle, menthe, cyan, puis activité, cœur, sommeil, esprit, corps.
 */
enum class HueSet(val label: String, val light: HuePack, val dark: HuePack) {
    VIVES("Vives",
        pack(0xFF7A7F88, 0xFF0F9F62, 0xFFCC9100, 0xFFE8601F, 0xFF1F68D6, 0xFF8B4FD8, 0xFFD93A3A, 0xFFD93465, 0xFF4B49D2, 0xFF0C968B, 0xFF0E9474, 0xFF0B8FC7,
            0xFF0E9474, 0xFFD62D52, 0xFF6448D6, 0xFFD18F00, 0xFF2873D0),
        pack(0xFF8E939B, 0xFF3FCF8E, 0xFFEDC055, 0xFFF2834B, 0xFF4A8EF0, 0xFFA87BEF, 0xFFEE5A5A, 0xFFE95A7E, 0xFF7470EA, 0xFF3EC1B4, 0xFF45C9A4, 0xFF35B4E0,
            0xFF45C9A4, 0xFFEE5A72, 0xFF8D7BEF, 0xFFF0BA45, 0xFF5BA0EC)),
    DOUCES("Douces",
        pack(0xFF8A8F98, 0xFF3FA97A, 0xFFC9A23C, 0xFFE08A5A, 0xFF5B8FD9, 0xFF9A7AD6, 0xFFD96B6B, 0xFFD9739A, 0xFF7471D6, 0xFF4AA9A0, 0xFF48A88E, 0xFF4BA3CC,
            0xFF48A88E, 0xFFD96B80, 0xFF8B79D6, 0xFFC9A23C, 0xFF5B8FD9),
        pack(0xFFA3A8B0, 0xFF8FDDB5, 0xFFF2D58A, 0xFFF5B48E, 0xFF9DBFF2, 0xFFC4AEF2, 0xFFF2A0A0, 0xFFF2A6C2, 0xFFA9A7F2, 0xFF93D9D2, 0xFF95DBC6, 0xFF96D2EC,
            0xFF95DBC6, 0xFFF2A3B2, 0xFFB9AAF2, 0xFFF2D58A, 0xFF9DBFF2)),
    PROFONDES("Profondes",
        pack(0xFF5B616B, 0xFF0B7A4B, 0xFFB07A00, 0xFFC2410C, 0xFF1D4ED8, 0xFF6D28D9, 0xFFB91C1C, 0xFFBE185D, 0xFF3730A3, 0xFF0F766E, 0xFF047857, 0xFF0369A1,
            0xFF047857, 0xFFBE123C, 0xFF5B21B6, 0xFFB07A00, 0xFF1D4ED8),
        // en sombre, seules les teintes froides s'assombrissent (saturées, type pierre) ; les chaudes restent claires
        pack(0xFF7D838C, 0xFF16A066, 0xFFE0A82E, 0xFFEA7A3C, 0xFF3366F2, 0xFF8550F2, 0xFFE23B3B, 0xFFDB3D7C, 0xFF5550EE, 0xFF0F9D91, 0xFF119B77, 0xFF1787D1,
            0xFF119B77, 0xFFE33D5F, 0xFF7A52F0, 0xFFE0A82E, 0xFF3366F2)),
    SOBRES("Sobres",
        pack(0xFF7A7F88, 0xFF4F8A6B, 0xFF9C8540, 0xFFB0704A, 0xFF4F6F9C, 0xFF7A6A9C, 0xFFA85454, 0xFFA85C78, 0xFF5F5F9C, 0xFF4B8A86, 0xFF4B8A78, 0xFF4B7F9C,
            0xFF4B8A78, 0xFFA8586A, 0xFF6F64A8, 0xFF9C8540, 0xFF4F6F9C),
        pack(0xFF8E939B, 0xFF7FB497, 0xFFC9B26E, 0xFFD29A7C, 0xFF85A3CC, 0xFFA898C9, 0xFFCC8585, 0xFFCC8CA4, 0xFF9393CC, 0xFF7FB5B1, 0xFF7FB5A5, 0xFF7FAECC,
            0xFF7FB5A5, 0xFFCC8A9A, 0xFF9E94D4, 0xFFC9B26E, 0xFF85A3CC)),
}

/**
 * Jeu de teintes en cours : état Compose lu par [Base] et [HealthHues], donc tout ce qui est dessiné suit le réglage et le
 * passage clair / sombre, même hors des fonctions Compose (graphiques, widgets).
 */
object Hues {
    var set by mutableStateOf(HueSet.VIVES)
    var dark by mutableStateOf(false)
    val pack: HuePack get() = if (dark) set.dark else set.light
}

/**
 * Fonds de l'app (Réglages > Apparence) : chaque fond a sa version claire et sa version sombre. Les fonds sombres restent
 * froids (un noir teinté de chaud vire au brun).
 */
enum class Surface(val label: String, val light: List<Long>, val dark: List<Long>) {
    // fond, carte, bloc posé sur une carte, remplissage neutre (pistes, champs)
    GRAPHITE("Graphite", listOf(0xFFF3F4F6, 0xFFFFFFFF, 0xFFF0F1F4, 0xFFE9EBEF), listOf(0xFF08090B, 0xFF131519, 0xFF1D2025, 0xFF262A30)),
    NUIT("Nuit", listOf(0xFFE6EEFA, 0xFFFFFFFF, 0xFFE6EDF8, 0xFFD9E3F3), listOf(0xFF081026, 0xFF12203D, 0xFF1B2B4D, 0xFF24365C)),
    ARDOISE("Ardoise", listOf(0xFFE1E6EC, 0xFFF8FAFC, 0xFFEBEFF3, 0xFFD8DFE7), listOf(0xFF12171D, 0xFF1D252E, 0xFF27313C, 0xFF313D4A)),
    FORET("Forêt", listOf(0xFFE3F0E8, 0xFFFFFFFF, 0xFFE3EFE7, 0xFFD3E6DA), listOf(0xFF05160F, 0xFF0E2A1E, 0xFF16382A, 0xFF1F4535)),
    PRUNE("Prune", listOf(0xFFEEE8F9, 0xFFFFFFFF, 0xFFEBE4F7, 0xFFDFD4F2), listOf(0xFF120B22, 0xFF21173A, 0xFF2C2049, 0xFF372A57)),
}

/** Fond légèrement teinté d'une couleur (pastilles, sélection), lisible en clair comme en sombre. */
@Composable
fun Color.container(strength: Float = 1f): Color =
    // en sombre, une teinte chaude mêlée au fond vire au brun : fond neutre à la place, la couleur reste au symbole
    if (Mf.colors.dark && isWarm()) androidx.compose.ui.graphics.lerp(Mf.colors.card, Color.White, 0.07f * strength)
    else androidx.compose.ui.graphics.lerp(Mf.colors.card, this, (if (Mf.colors.dark) 0.24f else 0.14f) * strength)

/** Teinte chaude (rouge, orange, jaune, rose) : celles qui tournent au brun quand on les assombrit. */
fun Color.isWarm(): Boolean {
    val mx = maxOf(red, green, blue); val mn = minOf(red, green, blue); val d = mx - mn
    if (d < 0.08f) return false
    val h = when (mx) {
        red -> 60f * (((green - blue) / d) % 6f)
        green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return h < 70f || h >= 320f
}

/** Texte coloré posé sur une carte : la couleur elle-même, assombrie en clair et éclaircie en sombre pour le contraste. */
@Composable
fun Color.onContainer(): Color {
    val boost = LocalInkBoost.current
    return if (Mf.colors.dark) androidx.compose.ui.graphics.lerp(this, Color.White, 0.12f + boost)
    else androidx.compose.ui.graphics.lerp(this, Color.Black, 0.18f + boost)
}

/** Rayon des cartes et des listes groupées. */
val CardRadius = 22.dp
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
    /** filet qui borde les cartes */
    val line: Color,
    /** remplissage neutre : champs, boutons secondaires, pistes */
    val chip: Color,
    val accent: Color,
    /** Action principale : la couleur d'accent, texte blanc (bouton plein de Material 3). */
    val strong: Color,
    val onStrong: Color,
)

private fun onAccent(accent: Color) = if (accent.luminance() > 0.62f) Color(0xFF1C1C1E) else Color.White

private fun lightMf(accent: Color, sf: Surface = Surface.GRAPHITE) = MfColors(
    dark = false, background = Color(sf.light[0]), card = Color(sf.light[1]), elevated = Color(sf.light[2]),
    text = Color(0xFF0F1114), textSecondary = Color(0xFF5F646D), textTertiary = Color(0xFF9EA3AC),
    divider = Color(0x14000000), line = Color(0x12000000), chip = Color(sf.light[3]), accent = accent, strong = accent, onStrong = onAccent(accent),
)

private fun darkMf(accent: Color, sf: Surface = Surface.GRAPHITE) = MfColors(
    dark = true, background = Color(sf.dark[0]), card = Color(sf.dark[1]), elevated = Color(sf.dark[2]),
    text = Color(0xFFF4F5F7), textSecondary = Color(0xFF8E939B), textTertiary = Color(0xFF5E636B),
    divider = Color(0x17FFFFFF), line = Color(0x14FFFFFF), chip = Color(sf.dark[3]), accent = accent, strong = accent, onStrong = onAccent(accent),
)

val LocalMf = staticCompositionLocalOf { lightMf(Base.Blue) }

object Mf {
    val colors: MfColors @Composable get() = LocalMf.current
}

/** Google Sans Flex (variable, réduite au latin) : graisse et taille optique. */
@OptIn(ExperimentalTextApi::class)
private val sansBytes by lazy { readAsset("fonts/sans_flex.ttf") ?: ByteArray(0) }

private fun sans(weight: Int, opsz: Float) = androidx.compose.ui.text.platform.Font(
    "sans_flex_${weight}_$opsz", { sansBytes }, FontWeight(weight), androidx.compose.ui.text.font.FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

private fun sansFamily(opsz: Float) = FontFamily(sans(400, opsz), sans(500, opsz), sans(600, opsz), sans(700, opsz), sans(800, opsz))

/** Texte courant (taille optique 14). */
val SansText = sansFamily(14f)
/** Titres et grands chiffres (taille optique 28). */
val SansDisplay = sansFamily(28f)

/** Inter (texte) et Inter Display (titres, chiffres) : la police du style Score. */
private fun inter(file: String, weight: FontWeight): androidx.compose.ui.text.font.Font {
    val bytes by lazy { readAsset("fonts/$file.ttf") ?: ByteArray(0) }
    return androidx.compose.ui.text.platform.Font(file, { bytes }, weight, androidx.compose.ui.text.font.FontStyle.Normal)
}
val InterText = FontFamily(inter("inter_regular", FontWeight.Normal), inter("inter_medium", FontWeight.Medium),
    inter("inter_semibold", FontWeight.SemiBold), inter("inter_semibold", FontWeight.Bold))
val InterDisplay = FontFamily(inter("inter_display_medium", FontWeight.Normal), inter("inter_display_medium", FontWeight.Medium),
    inter("inter_display_semibold", FontWeight.SemiBold), inter("inter_display_bold", FontWeight.Bold))

/** Police de l'app (Réglages > Apparence). */
enum class AppFont(val label: String) { INTER("Inter"), SANS("Google Sans") }

// échelle de Material 3 ; titres en graisse moyenne (pas de gras d'affiche), chiffres tabulaires pour les grandes valeurs
private fun typography(font: AppFont): Typography {
    val inter = font == AppFont.INTER
    val text = TextStyle(fontFamily = if (inter) InterText else SansText, letterSpacing = 0.sp)
    // Inter Display serré, chiffres en demi-gras : le style Score
    val display = TextStyle(fontFamily = if (inter) InterDisplay else SansDisplay, letterSpacing = 0.sp)
    val digits = display.copy(fontFeatureSettings = "tnum")
    return Typography(
        displayLarge = digits.copy(fontSize = 46.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 52.sp, letterSpacing = (-0.9).sp),
        displayMedium = digits.copy(fontSize = 38.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 44.sp, letterSpacing = (-0.7).sp),
        displaySmall = digits.copy(fontSize = 30.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineLarge = display.copy(fontSize = 30.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 38.sp, letterSpacing = if (inter) (-0.5).sp else 0.sp),
        headlineMedium = display.copy(fontSize = 25.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 32.sp, letterSpacing = if (inter) (-0.4).sp else 0.sp),
        headlineSmall = display.copy(fontSize = 21.sp, fontWeight = if (inter) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 28.sp, letterSpacing = if (inter) (-0.3).sp else 0.sp),
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
    val look = app.superhealth.SuperPrefs.settings.collectAsState().value
    val hueSet = HueSet.entries.firstOrNull { it.name == look.dataHues } ?: HueSet.VIVES
    // teintes des données posées avant de lire les couleurs (accent compris) : tout l'écran les suit
    if (Hues.set != hueSet) Hues.set = hueSet
    if (Hues.dark != dark) Hues.dark = dark
    val surface = Surface.entries.firstOrNull { it.name == look.surface } ?: Surface.GRAPHITE
    val font = AppFont.entries.firstOrNull { it.name == look.font } ?: AppFont.INTER
    val acc = accent.color()
    val mf = if (dark) darkMf(acc, surface) else lightMf(acc, surface)
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
        MaterialTheme(colorScheme = scheme, typography = remember(font) { typography(font) }, shapes = shapes) {
            CompositionLocalProvider(LocalContentColor provides mf.text, content = content)
        }
    }
}
