package app.superhealth.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Anneaux du jour, à la manière d'Apple Santé : de l'extérieur vers l'intérieur, les mesures choisies (pas, Body Battery et
 * calories mangées par défaut). Un anneau plein = objectif atteint ; au-delà, il repart pour un second tour.
 */
object Rings {
    enum class Kind(val label: String, val color: Color, val icon: ImageVector) {
        STEPS("Pas", Color(0xFFFA114F), Icons.AutoMirrored.Rounded.DirectionsWalk),
        ENERGY("Body Battery", Color(0xFF9BE22A), Icons.Rounded.Bolt),
        KCAL("Calories mangées", Color(0xFF14D8F0), Icons.Rounded.Restaurant),
        PROTEIN("Protéines", Color(0xFFFF9F0A), Icons.Rounded.EggAlt),
        WATER("Hydratation", Color(0xFF0A84FF), Icons.Rounded.WaterDrop),
        SLEEP("Sommeil", Color(0xFFBF5AF2), Icons.Rounded.Bedtime),
        CARDIO("Minutes cardio", Color(0xFFFFD60A), Icons.Rounded.Favorite),
    }

    const val MAX = 4
    val defaults = listOf(Kind.STEPS, Kind.ENERGY, Kind.KCAL)

    /** Mesures choisies (noms), dans l'ordre, sans doublon ; les valeurs par défaut si rien de valide. */
    fun kinds(names: List<String>): List<Kind> =
        names.mapNotNull { n -> Kind.entries.firstOrNull { it.name == n } }.distinct().take(MAX).ifEmpty { defaults }

    /** Valeur du jour et objectif ; null : pas de donnée (anneau vide, « — »). */
    data class Ring(val kind: Kind, val value: Double?, val goal: Double) {
        /** Remplissage : 1 = objectif atteint, plafonné à 2 tours. */
        val progress: Float get() = fraction(value, goal)
    }

    fun fraction(value: Double?, goal: Double): Float =
        if (value == null || goal <= 0 || value.isNaN()) 0f else (value / goal).toFloat().coerceIn(0f, 2f)

    fun build(kinds: List<Kind>, value: (Kind) -> Double?, goal: (Kind) -> Double): List<Ring> = kinds.map { Ring(it, value(it), goal(it)) }

    /** Les trois anneaux par défaut ; le Body Battery se lit sur 100. */
    fun of(steps: Double?, stepGoal: Int, energy: Double?, eatenKcal: Double?, targetKcal: Double): List<Ring> = listOf(
        Ring(Kind.STEPS, steps, stepGoal.toDouble()),
        Ring(Kind.ENERGY, energy, 100.0),
        Ring(Kind.KCAL, eatenKcal, targetKcal),
    )

    /** Teinte sombre du départ du dégradé (comme les anneaux d'Apple). */
    fun shade(c: Color, k: Float = 0.72f) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)
}

/** Les anneaux emboîtés, remplis une fois à l'ouverture (pas d'animation en boucle). */
@Composable
fun ActivityRings(rings: List<Rings.Ring>, size: Dp, modifier: Modifier = Modifier, track: Color, icons: Boolean = true) {
    val n = rings.size.coerceAtLeast(1)
    val stroke = size * (0.30f / n)
    val gap = stroke * 0.12f
    val anims = rings.map { r ->
        val a = remember(r.kind) { Animatable(0f) }
        LaunchedEffect(r.progress) { a.animateTo(r.progress, tween(1100, easing = FastOutSlowInEasing)) }
        a
    }
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val g = gap.toPx()
            rings.forEachIndexed { i, r ->
                val inset = s / 2 + i * (s + g)
                val tl = Offset(inset, inset)
                val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val c = r.kind.color
                drawArc(c.copy(alpha = 0.2f).compositeOver(track), 0f, 360f, false, tl, box, style = Stroke(s))
                val p = anims[i].value
                if (p <= 0.001f) return@forEachIndexed
                rotate(-90f) {
                    val first = (p.coerceAtMost(1f)) * 360f
                    drawArc(Brush.sweepGradient(listOf(Rings.shade(c), c, c)), 0f, first, false, tl, box, style = Stroke(s, cap = StrokeCap.Round))
                    if (p > 1f) {
                        // second tour : la pointe porte une ombre pour se détacher du premier
                        val end = (p - 1f) * 360f
                        val rad = Math.toRadians(end.toDouble())
                        val rr = box.width / 2
                        val cx = this.size.width / 2 + rr * cos(rad).toFloat(); val cy = this.size.height / 2 + rr * sin(rad).toFloat()
                        drawCircle(Color.Black.copy(alpha = 0.35f), s / 2 * 1.08f, Offset(cx + s * 0.06f, cy + s * 0.1f))
                        drawArc(c, 0f, end, false, tl, box, style = Stroke(s, cap = StrokeCap.Round))
                    }
                }
            }
        }
        // icône au départ de chaque anneau, comme les flèches d'Apple
        if (icons) rings.forEachIndexed { i, r ->
            val y = stroke * 0.5f + (stroke + gap) * i - stroke * 0.36f
            Icon(r.kind.icon, null, Modifier.offset(y = y).size(stroke * 0.72f), tint = Color.Black.copy(alpha = 0.78f))
        }
    }
}
