package app.superhealth.ui.theme

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.superhealth.SuperPrefs

/**
 * Labo de style (version test) : variantes à comparer sur le téléphone avant d'en garder une.
 * Cartes du tableau de bord, du plus neutre au plus vibrant (réglées à part en clair et en sombre), et séance de musculation.
 */
object StyleLab {
    /** Du plus neutre au plus coloré. */
    enum class Cards(val label: String) {
        NEUTRAL("Neutre"), VIVID("Pastille pleine"), OUTLINE("Liseré"), SOFT("Teinte douce"), GRADIENT("Dégradé"), STRONG("Vif"), FULL("Plein")
    }
    enum class WorkoutColor(val label: String) { BLUE("Bleu"), ACCENT("Accent"), PROGRAM("Programme") }
    enum class Hero(val label: String) { LARGE("Grande"), COMPACT("Compacte"), NONE("Sans image") }
    enum class Sets(val label: String) { CARDS("Cartes"), ROWS("Lignes"), BOLD("Contrastées") }

    /** Couleur de l'entraînement en cours (lue par [WorkoutBlue]) : état Compose, tout l'écran suit le changement. */
    var workoutColor by mutableStateOf(Base.Blue)

    private inline fun <reified E : Enum<E>> pick(name: String, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

    @Composable fun cards(): Cards {
        // dans une carte : la variante de son ton (une carte « Plein » passe en couleurs sombres, sans changer de variante)
        LocalCardStyle.current?.let { return it }
        val s by SuperPrefs.settings.collectAsState()
        return pick(if (Mf.colors.dark) s.styleCardsDark else s.styleCardsLight, Cards.NEUTRAL)
    }
    @Composable fun hero(): Hero = pick(SuperPrefs.settings.collectAsState().value.styleHero, Hero.LARGE)
    @Composable fun sets(): Sets = pick(SuperPrefs.settings.collectAsState().value.styleSets, Sets.CARDS)
    fun workoutChoice(): WorkoutColor = pick(SuperPrefs.settings.value.styleWorkoutColor, WorkoutColor.BLUE)

    /** Couleur choisie pour l'entraînement : bleu, accent de l'app ou couleur du programme actif. */
    fun resolveWorkout(accent: Color): Color = when (workoutChoice()) {
        WorkoutColor.BLUE -> Base.Blue
        WorkoutColor.ACCENT -> accent
        WorkoutColor.PROGRAM -> runCatching {
            val st = app.superhealth.training.WorkoutApp.store.state.value
            st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.let { ProgramPalette[it.color % ProgramPalette.size] }
        }.getOrNull() ?: Base.Blue
    }
}

/** Couleurs des programmes, fixes (sans dépendre de la couleur d'entraînement choisie). */
internal val ProgramPalette get() = listOf(Base.Blue, Base.Red, Base.Green, Base.Orange, Base.Purple, Base.Pink, Base.Yellow, Base.Grey)

/** Fond d'une carte posé par [CardTone] (dégradé éventuel, liseré). */
class CardSurface(val brush: Brush, val border: Color?)

val LocalCardSurface = compositionLocalOf<CardSurface?> { null }
/** Variante de la carte en cours (posée par [CardTone]). */
val LocalCardStyle = compositionLocalOf<StyleLab.Cards?> { null }
/** Couleur des tracés et des valeurs dans une carte « Plein » (blanc) ; null : la couleur de la carte. */
val LocalCardInk = compositionLocalOf<Color?> { null }
/** Contraste ajouté au texte coloré ([onContainer]) sur les fonds teintés. */
val LocalInkBoost = compositionLocalOf { 0f }

/**
 * Ton d'une carte du tableau de bord : fond selon la variante choisie, et couleurs de l'app (surfaces, pistes, texte)
 * recalculées pour ce fond. Tout ce qui est dessiné dans la carte suit donc le style, même les cartes à mise en page propre.
 */
@Composable
fun CardTone(hue: Color, rich: Boolean = false, night: Boolean = false, content: @Composable () -> Unit) {
    val style = StyleLab.cards()
    val base = Mf.colors
    val d = base.dark
    fun mix(f: Float) = lerp(base.card, hue, f)
    // anneaux : sur les styles colorés, fond presque noir à peine teinté (comme Apple Fitness) pour que chaque anneau ressorte
    // Théorie des couleurs : une teinte chaude (rouge, orange, jaune) assombrie vers le noir tourne au brun ; une teinte froide
    // (bleu, vert, violet) donne un ton profond (marine, forêt, indigo). Les cartes multicolores chaudes passent donc sur un
    // noir neutre légèrement froid, comme les anneaux, où leurs couleurs vives ressortent par contraste.
    val warm = hueDegrees(hue)
        .let { it < 70f || it >= 320f }
    // les anneaux sur noir seulement en « Plein » : sur les autres styles ils suivent la carte (pistes posées sur son fond)
    val onNight = style == StyleLab.Cards.FULL && (night || (rich && warm))
    val (top, bottom) = if (onNight) Color(0xFF1A1D24) to Color(0xFF101217) else when (style) {
        StyleLab.Cards.NEUTRAL, StyleLab.Cards.VIVID, StyleLab.Cards.OUTLINE -> base.card to base.card
        StyleLab.Cards.SOFT -> mix(if (d) 0.20f else 0.13f).let { it to it }
        StyleLab.Cards.GRADIENT -> mix(if (d) 0.46f else 0.36f) to mix(if (d) 0.16f else 0.10f)
        StyleLab.Cards.STRONG -> mix(if (d) 0.44f else 0.34f).let { it to it }
        // carte à plusieurs couleurs (anneaux, muscles, phases…) : version profonde de sa couleur, ses couleurs restent lisibles
        StyleLab.Cards.FULL -> if (rich) lerp(hue, Color.Black, if (d) 0.55f else 0.42f) to lerp(hue, Color.Black, if (d) 0.68f else 0.56f)
            else (if (d) lerp(hue, Color.Black, 0.22f) else hue) to (if (d) lerp(hue, Color.Black, 0.42f) else lerp(hue, Color.Black, 0.14f))
    }
    val mid = lerp(top, bottom, 0.5f)
    val border = if (style == StyleLab.Cards.OUTLINE) hue.copy(alpha = if (d) 0.7f else 0.55f) else null
    val full = style == StyleLab.Cards.FULL || onNight
    val mf = when {
        full -> base.copy(dark = true, card = mid, background = lerp(mid, Color.White, 0.16f), elevated = lerp(mid, Color.White, 0.12f),
            chip = lerp(mid, Color.White, 0.2f), divider = Color.White.copy(alpha = 0.22f),
            text = Color.White, textSecondary = Color.White.copy(alpha = 0.8f), textTertiary = Color.White.copy(alpha = 0.6f),
            // boutons et liens posés sur la couleur : blanc translucide, texte blanc
            accent = Color.White, strong = lerp(mid, Color.White, 0.24f), onStrong = Color.White)
        top == base.card -> base
        // fond teinté : pistes et blocs posés dans la carte en un peu plus clair (clair) ou plus sombre (sombre) que le fond
        else -> base.copy(card = mid, background = lerp(mid, if (d) Color.Black else Color.White, 0.35f), elevated = lerp(mid, if (d) Color.White else Color.Black, 0.06f),
            chip = lerp(mid, if (d) Color.Black else Color.White, 0.45f), divider = lerp(mid, base.text, 0.12f))
    }
    // « Plein » : les textes colorés (valeurs, macros) deviennent presque blancs, teintés de leur couleur
    val boost = when (style) { StyleLab.Cards.GRADIENT, StyleLab.Cards.STRONG -> 0.18f; StyleLab.Cards.FULL -> if (rich || onNight) 0.25f else 0.78f; else -> if (onNight) 0.25f else 0f }
    CompositionLocalProvider(
        LocalMf provides mf, LocalContentColor provides mf.text, LocalInkBoost provides boost,
        LocalCardInk provides (if (full && !rich && !onNight) Color.White else null), LocalCardStyle provides style,
        LocalCardSurface provides CardSurface(if (top == bottom) androidx.compose.ui.graphics.SolidColor(top) else Brush.verticalGradient(listOf(top, bottom)), border),
        content = content,
    )
}

/** Fond d'une carte du tableau de bord : celui de son [CardTone], sinon la surface neutre. */
@Composable
fun Modifier.dashCardBackground(@Suppress("UNUSED_PARAMETER") hue: Color = Color.Unspecified): Modifier {
    // style Score : surface de carte bordée d'un filet
    val s = LocalCardSurface.current ?: return background(Mf.colors.card).border(1.dp, Mf.colors.line, RoundedCornerShape(CardRadius))
    val b = background(s.brush)
    return b.border(if (s.border != null) 2.dp else 1.dp, s.border ?: Mf.colors.line, RoundedCornerShape(CardRadius))
}

/** Pastille de symbole d'une carte : fond et couleur du symbole. */
@Composable
fun dashChipColors(hue: Color, style: StyleLab.Cards = StyleLab.cards()): Pair<Color, Color> = when (style) {
    StyleLab.Cards.VIVID -> hue to Color.White
    StyleLab.Cards.FULL -> lerp(Mf.colors.card, Color.White, 0.22f) to Color.White
    StyleLab.Cards.SOFT, StyleLab.Cards.GRADIENT, StyleLab.Cards.STRONG -> Mf.colors.chip to hue.onContainer()
    else -> hue.container(1.2f) to hue.onContainer()
}
