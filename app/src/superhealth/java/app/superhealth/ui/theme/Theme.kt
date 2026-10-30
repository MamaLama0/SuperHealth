package app.superhealth.ui.theme

import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
val ProgramColors: List<Color> get() = ProgramPalette

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
    // les couleurs d'origine de l'app, les mêmes en clair et en sombre
    ORIGINE("Origine",
        pack(0xFF8E9199, 0xFF34A853, 0xFFF9B500, 0xFFF2803A, 0xFF4285F4, 0xFF9C5AE6, 0xFFEA4335, 0xFFE8558F, 0xFF6C6FE0, 0xFF12A4AF, 0xFF1EA88E, 0xFF2E97C7,
            0xFF1F9E85, 0xFFE5574F, 0xFF7B61D9, 0xFFD9931F, 0xFF3F7DE8),
        pack(0xFF8E9199, 0xFF34A853, 0xFFF9B500, 0xFFF2803A, 0xFF4285F4, 0xFF9C5AE6, 0xFFEA4335, 0xFFE8558F, 0xFF6C6FE0, 0xFF12A4AF, 0xFF1EA88E, 0xFF2E97C7,
            0xFF1F9E85, 0xFFE5574F, 0xFF7B61D9, 0xFFD9931F, 0xFF3F7DE8)),
}

/**
 * Jeu de teintes en cours : état Compose lu par [Base] et [HealthHues], donc tout ce qui est dessiné suit le réglage et le
 * passage clair / sombre, même hors des fonctions Compose (graphiques, widgets).
 */
object Hues {
    var set by mutableStateOf(HueSet.ORIGINE)
    var dark by mutableStateOf(false)
    /** Données en aplat : les dégradés restent réservés aux styles de cartes (Réglages > Style). */
    val gradient = false
    val pack: HuePack get() = if (dark) set.dark else set.light
}

/**
 * Fonds de l'app (Réglages > Apparence) : chaque fond a sa version claire et sa version sombre. Les fonds sombres restent
 * froids (un noir teinté de chaud vire au brun).
 */
enum class Surface(val label: String, val light: List<Long>, val dark: List<Long>) {
    // fond, carte, bloc posé sur une carte, remplissage neutre (pistes, champs)
    // le fond d'origine de l'app : cartes pleines et douces, sans filet
    ORIGINE("Origine", listOf(0xFFF1F3F8, 0xFFFFFFFF, 0xFFE9ECF3, 0xFFE7EAF1), listOf(0xFF111318, 0xFF1D2026, 0xFF282B32, 0xFF2A2D35)),
}

/** Fond légèrement teinté d'une couleur (pastilles, sélection), lisible en clair comme en sombre. */
@Composable
fun Color.container(strength: Float = 1f): Color =
    // en sombre, une teinte chaude mêlée au fond vire au brun : fond neutre à la place, la couleur reste au symbole
    if (Mf.colors.dark && isWarm()) androidx.compose.ui.graphics.lerp(Mf.colors.card, Color.White, 0.07f * strength)
    else androidx.compose.ui.graphics.lerp(Mf.colors.card, this, (if (Mf.colors.dark) 0.24f else 0.14f) * strength)

/**
 * Second ton du dégradé d'une donnée : teinte voisine (les froides glissent vers le bleu-violet, les chaudes vers le rose),
 * un peu plus claire, même saturation : un dégradé de deux couleurs proches, jamais fluo.
 */
fun Color.partner(): Color {
    val mx = maxOf(red, green, blue); val mn = minOf(red, green, blue); val d = mx - mn
    if (d < 0.08f) return this
    var h = when (mx) {
        red -> 60f * (((green - blue) / d) % 6f)
        green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    if (h < 0f) h += 360f
    val l = (mx + mn) / 2f
    val sat = if (l == 0f || l == 1f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
    val h2 = ((if (isWarm()) h - 28f else h + 34f) + 360f) % 360f
    return Color.hsl(h2, sat.coerceIn(0f, 1f), (l + 0.07f).coerceAtMost(0.82f), alpha)
}

/**
 * Couleur d'un voile ou d'une aire posés sur une carte : la teinte elle-même, sauf l'orange et le jaune en sombre qui
 * vireraient au brun et deviennent un rose (règle : jamais de teinte chaude assombrie).
 */
fun veilOf(c: Color): Color = if (!Hues.dark || !c.isWarm() || c.blue >= c.green) c else Color.hsl(338f, 0.75f, 0.6f)

/** Fond d'un bouton d'action : la couleur d'accent. */
@Composable
fun actionFill(@Suppress("UNUSED_PARAMETER") a: Color, @Suppress("UNUSED_PARAMETER") b: Color): Brush = SolidColor(Mf.colors.strong)

/** Symbole et texte posés sur un bouton d'action : un accent pâle prend une encre foncée (le blanc s'y perdrait). */
@Composable
fun actionInk(): Color = if (Mf.colors.strong.luminance() > 0.5f) Color(0xFF0F1114) else Mf.colors.onStrong

/** [partner] appelable sans import d'extension. */
fun partnerOf(c: Color): Color = c.partner()

/** Pinceau d'une donnée : aplat, ou dégradé de [partner] (départ, [from]) vers la couleur (arrivée, [to]) avec le réglage « Dégradé ». */
fun dataBrush(c: Color, from: Offset, to: Offset): Brush =
    if (Hues.gradient) Brush.linearGradient(listOf(c.partner(), c), from, to) else SolidColor(c)

/** Fond d'un onglet : le fond de l'app. */
@Composable
fun androidx.compose.ui.Modifier.tabBackground(@Suppress("UNUSED_PARAMETER") tint: Color?): androidx.compose.ui.Modifier = this.then(androidx.compose.ui.Modifier.background(Mf.colors.background))

/** Pinceau d'un arc tracé depuis 0° (après rotate(-90)) sur la fraction [f] du cercle : aplat, ou dégradé le long du trait. */
fun arcBrush(c: Color, f: Float): Brush =
    if (Hues.gradient) Brush.sweepGradient(0f to c.partner(), f.coerceIn(0.01f, 1f) to c, 1f to c) else SolidColor(c)

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
val CardRadius = 28.dp

/** Surtitre d'onglet (date, « Journal ») : gris, au-dessus du titre. */
@Composable
fun Overline(text: String) {
    androidx.compose.material3.Text(text, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, maxLines = 1)
}
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

private fun lightMf(accent: Color, sf: Surface = Surface.ORIGINE) = MfColors(
    dark = false, background = Color(sf.light[0]), card = Color(sf.light[1]), elevated = Color(sf.light[2]),
    text = if (sf == Surface.ORIGINE) Color(0xFF1B1C20) else Color(0xFF0F1114), textSecondary = if (sf == Surface.ORIGINE) Color(0xFF5E636D) else Color(0xFF5F646D),
    textTertiary = if (sf == Surface.ORIGINE) Color(0xFFA0A5AE) else Color(0xFF9EA3AC),
    divider = if (sf == Surface.ORIGINE) Color(0xFFE2E5EC) else Color(0x14000000), line = if (sf == Surface.ORIGINE) Color.Transparent else Color(0x12000000), chip = Color(sf.light[3]), accent = accent, strong = accent, onStrong = onAccent(accent),
)

private fun darkMf(accent: Color, sf: Surface = Surface.ORIGINE) = MfColors(
    dark = true, background = Color(sf.dark[0]), card = Color(sf.dark[1]), elevated = Color(sf.dark[2]),
    text = if (sf == Surface.ORIGINE) Color(0xFFE3E4E9) else Color(0xFFF4F5F7), textSecondary = if (sf == Surface.ORIGINE) Color(0xFFA9ADB7) else Color(0xFF8E939B),
    textTertiary = if (sf == Surface.ORIGINE) Color(0xFF6B707A) else Color(0xFF5E636B),
    divider = if (sf == Surface.ORIGINE) Color(0xFF30333A) else Color(0x17FFFFFF), line = if (sf == Surface.ORIGINE) Color.Transparent else Color(0x14FFFFFF), chip = Color(sf.dark[3]), accent = accent, strong = accent, onStrong = onAccent(accent),
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

/** Inter (texte) et Inter Display (titres, chiffres) : la police du style Score. */
val InterText = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_semibold, FontWeight.Bold))
val InterDisplay = FontFamily(Font(R.font.inter_display_medium, FontWeight.Normal), Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold), Font(R.font.inter_display_bold, FontWeight.Bold))

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
    // teintes des données posées avant de lire les couleurs (accent compris) : tout l'écran les suit
    if (Hues.dark != dark) Hues.dark = dark
    val surface = Surface.ORIGINE
    val font = AppFont.SANS
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
