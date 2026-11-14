package app.superhealth.ui.league

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.superhealth.cards.Element
import app.superhealth.league.Achievements
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Emblème d'un badge, aux couleurs des biomes des cartes (voir [BadgeTheme.element]). Un hexagone au dégradé du biome et à ses reliefs, le symbole du type au centre, un liseré de métal qui
 * monte avec le palier (bronze, argent, or, platine, diamant, rubis, obsidienne, prisme) et autant de points que de rangs.
 * Les badges de série portent une petite flamme.
 */
object BadgeTheme {
    enum class Symbol { HALTERE, TROPHEE, BOUFFE, PIEDS, LOTUS, LUNE, COURSE, DRAPEAU, SOMMET, BOUSSOLE, ECLAIR }

    /**
     * Magma la force (musculation, records), Glacier le calme (esprit, sommeil), Tempête l'élan (course, quêtes),
     * Montagne les pieds sur terre (pas, distance, nutrition).
     */
    fun element(metric: String): Element = when (metric.removePrefix("streak:")) {
        "sessions", "STRENGTH", "records" -> Element.FEU
        "mindMinutes", "MIND", "SLEEP" -> Element.GLACE
        "RUN", "quests", "xp" -> Element.FOUDRE
        else -> Element.TERRE
    }

    fun symbol(metric: String): Symbol = when (metric.removePrefix("streak:")) {
        "sessions", "STRENGTH" -> Symbol.HALTERE
        "records" -> Symbol.TROPHEE
        "foodDays", "FOOD" -> Symbol.BOUFFE
        "STEPS" -> Symbol.PIEDS
        "mindMinutes", "MIND" -> Symbol.LOTUS
        "SLEEP" -> Symbol.LUNE
        "RUN" -> Symbol.COURSE
        "quests" -> Symbol.DRAPEAU
        "distanceKm" -> Symbol.BOUSSOLE
        "xp" -> Symbol.ECLAIR
        else -> Symbol.SOMMET
    }

    /** Ce que rapporte un type de badge, en une phrase. */
    fun explain(metric: String): String = when (metric) {
        "sessions" -> "Chaque séance de musculation enregistrée compte. Un palier atteint paie son or une fois."
        "records" -> "Chaque record battu sur un exercice compte. Un palier atteint paie son or une fois."
        "foodDays" -> "Un jour compte quand tes repas sont notés et proches de ta cible. Paiement unique par palier."
        "mindMinutes" -> "Minutes de méditation et d'étirements enregistrées. Paiement unique par palier."
        "quests" -> "Chaque quête du jour ou de la semaine terminée compte. Paiement unique par palier."
        "distanceKm" -> "Distance du téléphone ou de la montre, sinon tes pas × ta foulée, jour après jour. Paiement unique par palier."
        else -> "Une semaine compte dès 4 jours tenus sur 7. Chaque semaine d'affilée rapporte 10 or de plus, jusqu'à 100."
    }

    /** Métal du liseré, du premier au huitième palier. */
    val metals = listOf(
        listOf(0xFFF0B27A, 0xFF9A5A2C), listOf(0xFFF4F6FA, 0xFF8E99A8), listOf(0xFFFFE680, 0xFFC08A12), listOf(0xFFE6FFF8, 0xFF5FB8B0),
        listOf(0xFFE8F7FF, 0xFF62A8FF), listOf(0xFFFF9AA8, 0xFFB0102E), listOf(0xFF8E7BA8, 0xFF1C1428), listOf(0xFFFF7EDB, 0xFF56E1FF),
    ).map { (a, b) -> Color(a) to Color(b) }

    fun icon(symbol: Symbol): ImageVector? = when (symbol) {
        Symbol.HALTERE -> Icons.Rounded.FitnessCenter; Symbol.TROPHEE -> Icons.Rounded.EmojiEvents; Symbol.BOUFFE -> Icons.Rounded.Restaurant
        Symbol.LOTUS -> Icons.Rounded.SelfImprovement; Symbol.LUNE -> Icons.Rounded.Bedtime; Symbol.COURSE -> Icons.Rounded.DirectionsRun
        Symbol.DRAPEAU -> Icons.Rounded.Flag; Symbol.SOMMET -> Icons.Rounded.Landscape; Symbol.BOUSSOLE -> Icons.Rounded.Explore
        Symbol.ECLAIR -> Icons.Rounded.Bolt; Symbol.PIEDS -> null
    }
}

@Composable
fun BadgeEmblem(badge: Achievements.Badge, size: Dp, locked: Boolean = false, description: String? = null) =
    BadgeEmblem(badge.metric, Achievements.rank(badge), size, locked, description)

@Composable
fun BadgeEmblem(metric: String, rank: Int, size: Dp, locked: Boolean = false, description: String? = null) {
    val e = BadgeTheme.element(metric)
    val symbol = BadgeTheme.symbol(metric)
    val (shine, deep) = BadgeTheme.metals[rank.coerceIn(0, BadgeTheme.metals.lastIndex)]
    val streak = metric.startsWith("streak:")
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Box(Modifier.size(size).semantics { description?.let { contentDescription = it } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val hex = hexagon(this.size.minDimension / 2f, center)
            val inner = hexagon(this.size.minDimension / 2f * 0.84f, center)
            val core = Color(e.core); val mid = Color(e.mid); val outer = Color(e.outer)
            fun tone(c: Color) = if (locked) Color(0xFF6A707C).copy(alpha = 0.3f).compositeOver(c.copy(alpha = 0.8f)).compositeOver(Color(0xFF1A1D24)) else c
            // Liseré de métal, puis le fond du biome et ses reliefs.
            drawPath(hex, Brush.linearGradient(listOf(tone(shine), tone(deep), tone(shine)), Offset.Zero, Offset(this.size.width, this.size.height)))
            drawPath(inner, Brush.radialGradient(listOf(tone(core), tone(mid), tone(outer)), center.copy(y = center.y * 0.8f), this.size.minDimension * 0.55f))
            clipPath(inner) { relief(e, if (locked) 0.08f else 0.22f) }
            drawPath(inner, Color.White.copy(alpha = 0.25f), style = Stroke(this.size.minDimension * 0.015f))
            if (symbol == BadgeTheme.Symbol.PIEDS) feet(if (locked) Color.White.copy(alpha = 0.55f) else Color.White)
            // Rangs : un point par palier sous le symbole.
            val pips = (rank + 1).coerceAtMost(8)
            val r = this.size.minDimension * 0.028f
            val step = r * 3f
            repeat(pips) { i -> drawCircle(if (locked) Color.White.copy(alpha = 0.35f) else shine, r, Offset(center.x + (i - (pips - 1) / 2f) * step, this.size.height * 0.79f)) }
        }
        BadgeTheme.icon(symbol)?.let {
            Icon(it, null, Modifier.size(size * 0.42f), tint = if (locked) Color.White.copy(alpha = 0.55f) else Color.White)
        }
        if (streak) Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.align(Alignment.TopEnd).size(size * 0.3f),
            tint = if (locked) Color(0xFF9AA0A8) else Color(0xFFFF7A1A))
    }
}

private fun hexagon(r: Float, c: Offset) = Path().apply {
    for (i in 0 until 6) {
        val a = PI / 3 * i - PI / 2
        val x = c.x + r * cos(a).toFloat(); val y = c.y + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Reliefs du biome, en filigrane : coulées, cristaux, éclairs ou crêtes. */
private fun DrawScope.relief(e: Element, alpha: Float) {
    val w = size.width; val h = size.height
    val ink = Color.White.copy(alpha = alpha)
    when (e) {
        Element.FEU -> repeat(3) { i ->
            drawPath(Path().apply { moveTo(-0.1f * w, h * (0.7f + i * 0.12f)); cubicTo(w * 0.3f, h * (0.55f + i * 0.12f), w * 0.6f, h * (0.9f + i * 0.1f), w * 1.1f, h * (0.65f + i * 0.12f)) },
                ink, style = Stroke(w * 0.04f))
        }
        Element.GLACE -> listOf(0.2f to 0.75f, 0.75f to 0.3f, 0.82f to 0.78f).forEach { (x, y) ->
            repeat(3) { k -> rotate(k * 60f, Offset(w * x, h * y)) { drawLine(ink, Offset(w * x, h * (y - 0.09f)), Offset(w * x, h * (y + 0.09f)), w * 0.025f) } }
        }
        Element.FOUDRE -> drawPath(Path().apply { moveTo(w * 0.72f, h * 0.08f); lineTo(w * 0.58f, h * 0.42f); lineTo(w * 0.7f, h * 0.42f); lineTo(w * 0.52f, h * 0.9f) },
            ink, style = Stroke(w * 0.04f))
        Element.TERRE -> drawPath(Path().apply { moveTo(0f, h); lineTo(w * 0.28f, h * 0.6f); lineTo(w * 0.42f, h * 0.74f); lineTo(w * 0.66f, h * 0.48f); lineTo(w, h * 0.86f); lineTo(w, h); close() },
            ink)
    }
}

/** Deux empreintes de pied au centre, une gauche plus haute, une droite plus basse. */
private fun DrawScope.feet(color: Color) {
    val u = size.minDimension
    fun foot(cx: Float, cy: Float, mirror: Float) {
        drawOval(color, Offset(cx - u * 0.055f, cy - u * 0.02f), Size(u * 0.11f, u * 0.17f))
        drawOval(color, Offset(cx - u * 0.04f, cy + u * 0.16f), Size(u * 0.08f, u * 0.07f))
        listOf(-0.05f to 0.014f, -0.018f to 0.016f, 0.014f to 0.014f, 0.042f to 0.011f).forEachIndexed { i, (dx, r) ->
            drawCircle(color, u * r, Offset(cx + dx * u * mirror, cy - u * (0.045f + if (i == 1) 0.012f else if (i == 0) 0.004f else 0.006f * (3 - i))))
        }
    }
    foot(center.x - u * 0.09f, center.y - u * 0.15f, -1f)
    foot(center.x + u * 0.09f, center.y - u * 0.04f, 1f)
}
