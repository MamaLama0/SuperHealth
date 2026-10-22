package app.superhealth.ui.achievements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import app.superhealth.achievements.Achievements
import app.superhealth.ui.theme.Mf
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Couleurs du voyage, communes à la scène et aux médailles. */
internal object Space {
    val Gold = Color(0xFFF2A93B)
    val Night = Color(0xFF1B2140)
    val Ocean = Color(0xFF3D8BF2)
    val OceanDeep = Color(0xFF2559C7)
    val Land = Color(0xFF45C27A)
    val Skin = Color(0xFFF5C49B)
    val Suit = Color(0xFFE5574F)
    val Moon = Color(0xFFC9CDD6)
    val Mars = Color(0xFFE0703F)
    val Jupiter = Color(0xFFD9A066)
    val Saturn = Color(0xFFE8C77A)
    val Star = Color(0xFFFFE8A3)
}

/**
 * Scène du voyage, dessinée (aucune image) : sur la première étape, le petit bonhomme fait le tour de la Terre en laissant
 * une trace dorée ; ensuite une fusée file d'une planète à la suivante. La progression se remplit une fois à l'ouverture,
 * sans animation en boucle.
 */
@Composable
internal fun JourneyScene(j: Achievements.Journey, modifier: Modifier, night: Boolean = Mf.colors.dark) {
    val anim = remember(j.leg) { Animatable(0f) }
    LaunchedEffect(j.leg, j.fraction) { anim.animateTo(j.fraction, tween(1400, easing = FastOutSlowInEasing)) }
    Canvas(modifier) { drawJourney(j.leg, anim.value, night) }
}

/** Ciel, étoiles et scène de l'étape [leg] à [f] (0..1) ; partagé avec le widget, qui la dessine en image. */
internal fun DrawScope.drawJourney(leg: Achievements.Leg, f: Float, night: Boolean) {
    val sky = if (night) Space.Night else Color(0xFFE6EEFF)
    drawRoundRect(Brush.verticalGradient(listOf(sky, sky.copy(alpha = 0.6f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f))
    stars(if (night) Space.Star else Color.White, leg.ordinal)
    if (!leg.rocket) earthWalk(f) else flight(leg, f)
}

private fun DrawScope.stars(c: Color, seed: Int) {
    val r = Random(42 + seed)
    repeat(28) {
        val p = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
        drawCircle(c.copy(alpha = 0.35f + r.nextFloat() * 0.5f), size.minDimension * (0.004f + r.nextFloat() * 0.006f), p)
    }
}

/** La Terre : océan, continents en formes douces, atmosphère ; le bonhomme marche sur le bord, sa trace derrière lui. */
private fun DrawScope.earthWalk(f: Float) {
    val r = size.minDimension * 0.34f
    val c = Offset(size.width / 2, size.height * 0.58f)
    drawCircle(Space.Ocean.copy(alpha = 0.18f), r * 1.12f, c)
    drawCircle(Brush.radialGradient(listOf(Space.Ocean, Space.OceanDeep), c.copy(x = c.x - r * 0.3f, y = c.y - r * 0.3f), r * 1.6f), r, c)
    // continents : trois taches arrondies
    listOf(Triple(-0.35f, -0.25f, 0.42f), Triple(0.38f, 0.05f, 0.34f), Triple(-0.05f, 0.45f, 0.28f)).forEach { (dx, dy, s) ->
        val p = Path()
        val cx = c.x + dx * r; val cy = c.y + dy * r; val rr = s * r
        p.moveTo(cx - rr, cy)
        p.cubicTo(cx - rr, cy - rr * 0.9f, cx + rr * 0.2f, cy - rr * 1.1f, cx + rr, cy - rr * 0.2f)
        p.cubicTo(cx + rr * 1.2f, cy + rr * 0.5f, cx + rr * 0.1f, cy + rr * 0.9f, cx - rr * 0.4f, cy + rr * 0.7f)
        p.cubicTo(cx - rr * 0.9f, cy + rr * 0.5f, cx - rr, cy + rr * 0.3f, cx - rr, cy)
        p.close()
        drawPath(p, Space.Land)
    }
    // trace dorée depuis le départ (en haut), dans le sens des aiguilles d'une montre
    val sweep = 360f * f
    if (sweep > 0.5f) drawArc(Space.Gold, -90f, sweep, false, Offset(c.x - r * 1.05f, c.y - r * 1.05f), Size(r * 2.1f, r * 2.1f),
        style = Stroke(r * 0.04f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.06f, r * 0.06f))))
    // drapeau de départ
    drawLine(Color.White, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 1.28f), r * 0.025f)
    drawRect(Space.Gold, Offset(c.x, c.y - r * 1.28f), Size(r * 0.16f, r * 0.1f))
    // le bonhomme, debout sur le globe
    val a = Math.toRadians((-90.0 + sweep))
    val foot = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
    rotate(sweep, pivot = foot) { walker(foot, r * 0.62f) }
}

/** Petit bonhomme en marche : tête ronde, combinaison rouge, sac à dos doré ; les pieds sur [foot], debout vers le haut. */
private fun DrawScope.walker(foot: Offset, h: Float) {
    val w = h * 0.09f
    val hip = Offset(foot.x, foot.y - h * 0.45f)
    val neck = Offset(foot.x, foot.y - h * 0.82f)
    // jambes et bras écartés (pose de marche)
    drawLine(Space.Suit, hip, Offset(foot.x - h * 0.16f, foot.y), w, cap = StrokeCap.Round)
    drawLine(Space.Suit, hip, Offset(foot.x + h * 0.16f, foot.y), w, cap = StrokeCap.Round)
    drawRoundRect(Space.Gold, Offset(neck.x - h * 0.2f, neck.y + h * 0.02f), Size(h * 0.13f, h * 0.26f), androidx.compose.ui.geometry.CornerRadius(h * 0.05f))
    drawLine(Space.Suit, hip, neck, w * 1.3f, cap = StrokeCap.Round)
    drawLine(Space.Suit, Offset(neck.x, neck.y + h * 0.06f), Offset(neck.x + h * 0.2f, hip.y - h * 0.05f), w * 0.8f, cap = StrokeCap.Round)
    drawLine(Space.Suit, Offset(neck.x, neck.y + h * 0.06f), Offset(neck.x - h * 0.17f, hip.y - h * 0.02f), w * 0.8f, cap = StrokeCap.Round)
    drawCircle(Space.Skin, h * 0.13f, Offset(neck.x, neck.y - h * 0.1f))
    drawCircle(Color(0xFF2B2B2B), h * 0.018f, Offset(neck.x + h * 0.05f, neck.y - h * 0.12f))
}

/** Vol en fusée : planète de départ à gauche, d'arrivée à droite, trajectoire en arc, fusée et sa flamme. */
private fun DrawScope.flight(leg: Achievements.Leg, f: Float) {
    val from = Offset(size.width * 0.16f, size.height * 0.68f)
    val to = Offset(size.width * 0.84f, size.height * 0.36f)
    val r = size.minDimension * 0.14f
    planet(Achievements.Leg.entries.getOrNull(leg.ordinal - 1) ?: Achievements.Leg.EARTH, from, r * 1.1f)
    planet(leg, to, r)
    val ctrl = Offset(size.width * 0.5f, size.height * 0.08f)
    fun at(t: Float): Offset {
        val u = 1 - t
        return Offset(u * u * from.x + 2 * u * t * ctrl.x + t * t * to.x, u * u * from.y + 2 * u * t * ctrl.y + t * t * to.y)
    }
    val path = Path().apply { moveTo(from.x, from.y); quadraticTo(ctrl.x, ctrl.y, to.x, to.y) }
    drawPath(path, Color.White.copy(alpha = 0.45f), style = Stroke(r * 0.05f, cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.08f, r * 0.1f))))
    val p = at(f.coerceIn(0.02f, 0.98f)); val q = at((f + 0.01f).coerceIn(0.03f, 0.99f))
    val angle = Math.toDegrees(kotlin.math.atan2((q.y - p.y).toDouble(), (q.x - p.x).toDouble())).toFloat()
    translate(p.x, p.y) { rotate(angle + 90f, pivot = Offset.Zero) { rocket(r * 0.9f) } }
}

/** Fusée pointée vers le haut, centrée sur l'origine. */
private fun DrawScope.rocket(h: Float) {
    val w = h * 0.36f
    val body = Path().apply {
        moveTo(0f, -h * 0.55f)
        cubicTo(w * 0.7f, -h * 0.35f, w * 0.55f, h * 0.2f, w * 0.45f, h * 0.3f)
        lineTo(-w * 0.45f, h * 0.3f)
        cubicTo(-w * 0.55f, h * 0.2f, -w * 0.7f, -h * 0.35f, 0f, -h * 0.55f)
        close()
    }
    // flamme
    drawPath(Path().apply { moveTo(-w * 0.3f, h * 0.3f); quadraticTo(0f, h * 0.85f, w * 0.3f, h * 0.3f); close() }, Space.Gold)
    drawPath(Path().apply { moveTo(-w * 0.15f, h * 0.3f); quadraticTo(0f, h * 0.6f, w * 0.15f, h * 0.3f); close() }, Color(0xFFFFF1C2))
    // ailerons
    drawPath(Path().apply { moveTo(-w * 0.45f, h * 0.05f); lineTo(-w * 0.85f, h * 0.38f); lineTo(-w * 0.4f, h * 0.3f); close() }, Space.Suit)
    drawPath(Path().apply { moveTo(w * 0.45f, h * 0.05f); lineTo(w * 0.85f, h * 0.38f); lineTo(w * 0.4f, h * 0.3f); close() }, Space.Suit)
    drawPath(body, Color(0xFFF4F6FA))
    drawCircle(Space.Ocean, w * 0.22f, Offset(0f, -h * 0.15f))
    drawCircle(Color.White, w * 0.08f, Offset(-w * 0.06f, -h * 0.18f))
}

private fun DrawScope.planet(leg: Achievements.Leg, c: Offset, r: Float) {
    when (leg) {
        Achievements.Leg.EARTH -> {
            drawCircle(Brush.radialGradient(listOf(Space.Ocean, Space.OceanDeep), c, r * 1.4f), r, c)
            drawCircle(Space.Land, r * 0.35f, Offset(c.x - r * 0.3f, c.y - r * 0.2f))
            drawCircle(Space.Land, r * 0.25f, Offset(c.x + r * 0.35f, c.y + r * 0.25f))
        }
        Achievements.Leg.MOON -> {
            drawCircle(Space.Moon, r, c)
            listOf(Triple(-0.3f, -0.2f, 0.22f), Triple(0.35f, 0.1f, 0.16f), Triple(-0.05f, 0.42f, 0.12f)).forEach { (x, y, s) ->
                drawCircle(Color(0xFFA9AEB9), r * s, Offset(c.x + x * r, c.y + y * r))
            }
        }
        Achievements.Leg.MARS -> { drawCircle(Space.Mars, r, c); drawCircle(Color(0xFFC2552C), r * 0.25f, Offset(c.x + r * 0.3f, c.y - r * 0.2f)) }
        Achievements.Leg.JUPITER -> {
            drawCircle(Space.Jupiter, r, c)
            listOf(-0.45f, -0.1f, 0.3f).forEach { y -> drawLine(Color(0xFFB8763F), Offset(c.x - r * 0.9f, c.y + y * r), Offset(c.x + r * 0.9f, c.y + y * r), r * 0.12f, cap = StrokeCap.Round) }
        }
        Achievements.Leg.SATURN -> {
            drawCircle(Space.Saturn, r * 0.8f, c)
            drawOval(Color(0xFFCFAE5E), Offset(c.x - r * 1.4f, c.y - r * 0.3f), Size(r * 2.8f, r * 0.6f), style = Stroke(r * 0.12f))
        }
        Achievements.Leg.BEYOND -> {
            val p = Path()
            for (k in 0 until 10) {
                val rr = if (k % 2 == 0) r else r * 0.45f
                val a = Math.toRadians(-90.0 + k * 36)
                val pt = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
                if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            }
            p.close(); drawPath(p, Space.Star)
        }
    }
}

/** Médaille d'un palier (bronze, argent, or) ou disque vide s'il n'est pas obtenu. */
internal fun tierColor(t: Achievements.Tier?): Color = when (t) {
    Achievements.Tier.GOLD -> Color(0xFFF2B33D)
    Achievements.Tier.SILVER -> Color(0xFFB9C2CF)
    Achievements.Tier.BRONZE -> Color(0xFFD08A57)
    null -> Color(0x00000000)
}
