package app.superhealth.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.health.data.Metric
import app.superhealth.ui.formatValue
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.unitOf
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Carte « Constantes » : une bulle par mesure, son anneau rempli selon la valeur (la respiration gonfle à votre fréquence).
 * Les animations ne lisent leur état qu'au dessin : la carte ne se recompose pas à chaque image.
 */

/**
 * Bulles plutôt que tuiles : la première mesure choisie est le primaire (grande bulle), les deux suivantes des bulles
 * plus petites nichées à sa droite, décalées comme une grappe ; les autres, s'il y en a, en petites bulles dessous.
 * Chaque bulle respire doucement à son propre rythme.
 */
@Composable
internal fun VitalsGrid(metrics: List<Metric>, series: (Metric) -> List<Pair<LocalDate, Double?>>, female: Boolean, onOpen: (Metric) -> Unit) {
    if (metrics.isEmpty()) return
    val side = metrics.drop(1).take(2)
    val rest = metrics.drop(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            val d1 = if (side.isEmpty()) minOf(w * 0.7f, 230.dp) else minOf(w * 0.58f, 230.dp)
            val d2 = w * 0.36f
            val d3 = w * 0.30f
            val h = when (side.size) { 0 -> d1; 1 -> maxOf(d1, d2); else -> maxOf(d1, d2 + d3 + 2.dp) }
            Box(Modifier.fillMaxWidth().height(h)) {
                val x1 = if (side.isEmpty()) (w - d1) / 2 else 0.dp
                Bubble(metrics[0], series(metrics[0]), female, d1, true, 0, Modifier.offset(x1, (h - d1) / 2)) { onOpen(metrics[0]) }
                side.getOrNull(0)?.let { m ->
                    val y = if (side.size == 1) (h - d2) / 2 else 0.dp
                    Bubble(m, series(m), female, d2, false, 1, Modifier.offset(w - d2, y)) { onOpen(m) }
                }
                side.getOrNull(1)?.let { m ->
                    // nichée entre le primaire et le secondaire, un peu vers la gauche
                    val x = minOf(d1 + 4.dp - d3 * 0.05f, w - d3)
                    Bubble(m, series(m), female, d3, false, 2, Modifier.offset(x, h - d3)) { onOpen(m) }
                }
            }
        }
        rest.chunked(4).forEach { row ->
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val d = minOf(maxWidth / 4 - 6.dp, 84.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEachIndexed { i, m -> Bubble(m, series(m), female, d, false, 3 + i, Modifier) { onOpen(m) } }
                }
            }
        }
    }
}

/** Plage de l'anneau de chaque mesure (valeur → remplissage). */
internal fun Metric.ringRange(): ClosedFloatingPointRange<Double> = when (this) {
    Metric.RESTING_HR -> 40.0..100.0
    Metric.HEART_RATE -> 40.0..180.0
    Metric.RESPIRATORY_RATE -> 8.0..25.0
    Metric.SPO2 -> 85.0..100.0
    Metric.HRV -> 20.0..120.0
    Metric.VO2MAX -> 25.0..65.0
    Metric.TEMPERATURE -> 30.0..38.0
    Metric.STEPS -> 0.0..10_000.0
    Metric.SLEEP -> 0.0..480.0
    Metric.FITNESS_AGE -> 20.0..80.0
    else -> 0.0..100.0
}

/**
 * Tuile en anneau, à la Google Health : la valeur au centre d'un anneau qui se remplit à l'ouverture. L'anneau du cœur
 * pulse au rythme mesuré, celui de la respiration gonfle et se vide à votre fréquence ; le Body Battery passe du rouge au vert.
 */
/** Une bulle : fond en dégradé radial, anneau de remplissage sur le bord, valeur au centre. */
@Composable
private fun Bubble(m: Metric, series: List<Pair<LocalDate, Double?>>, female: Boolean, d: androidx.compose.ui.unit.Dp, primary: Boolean, seed: Int,
                   modifier: Modifier, onClick: () -> Unit) {
    val pts = series.mapNotNull { it.second }
    val latest = series.lastOrNull { it.second != null }
    val v = latest?.second
    val hue = m.color
    val status = status(m, v, pts, female)
    // respiration lente, décalée d'une bulle à l'autre : la grappe ne bouge jamais d'un bloc
    val breath = rememberInfiniteTransition(label = "bubble").animateFloat(0f, 1f,
        infiniteRepeatable(tween(3600 + seed * 700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val small = d < 100.dp
    Box(modifier.size(d).graphicsLayer { val k = 0.985f + 0.03f * breath.value; scaleX = k; scaleY = k }
        .clip(CircleShape).background(Brush.radialGradient(listOf(hue.container(1.8f), hue.container(0.9f))))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        VitalRing(m, v, d) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = d * 0.14f)) {
                Icon(m.icon, null, Modifier.size(if (primary) 22.dp else if (small) 13.dp else 16.dp), tint = hue)
                Text(v?.let { formatValue(m, it) } ?: "—", style = when {
                    primary -> MaterialTheme.typography.displaySmall; small -> MaterialTheme.typography.titleSmall; else -> MaterialTheme.typography.titleLarge
                }, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1)
                unitOf(m)?.takeIf { v != null && !small }?.let {
                    Text(it, style = if (primary) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
                }
                Text(tr(m.label), style = if (primary) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal, color = hue.onContainer(), maxLines = if (primary) 2 else 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
                if (primary) StatusLine(status, latest, hue, Modifier.padding(top = 6.dp))
                else if (status?.second == true && !small) Box(Modifier.padding(top = 3.dp).size(6.dp).clip(CircleShape).background(hue))
            }
        }
    }
}

/** Statut de la mesure (pastille) ou, à défaut, l'ancienneté de la dernière valeur. */
@Composable
private fun StatusLine(status: Pair<String, Boolean>?, latest: Pair<LocalDate, Double?>?, hue: Color, modifier: Modifier) {
    if (status != null) {
        val (label, strong) = status
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (strong) Color.White else hue.onContainer(),
            modifier = modifier.clip(CircleShape).background(if (strong) hue else Mf.colors.card.copy(alpha = 0.75f)).padding(horizontal = 8.dp, vertical = 3.dp))
    } else if (latest != null && latest.first != LocalDate.now())
        Text(relativeDay(latest.first), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, maxLines = 1, modifier = modifier)
}

/**
 * Anneau qui se remplit à l'ouverture ; celui de la respiration gonfle doucement à votre fréquence, le Body Battery
 * passe du rouge au vert. Pas de battement de cœur : le clignotement distrayait.
 */
@Composable
private fun VitalRing(m: Metric, v: Double?, size: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val hue = m.color
    val range = m.ringRange()
    val target = v?.let { ((it - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0.02f, 1f) } ?: 0f
    val fill = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target) { fill.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "ring")
    val pulse = when (m) {
        Metric.RESPIRATORY_RATE -> t.animateFloat(0f, 1f, infiniteRepeatable(tween((30_000 / (v ?: 14.0).coerceIn(6.0, 40.0)).toInt(),
            easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        else -> null
    }
    val track = Mf.colors.card.copy(alpha = if (Mf.colors.dark) 0.5f else 0.85f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = pulse?.value ?: 0f
            val base = this.size.minDimension * (if (size >= 150.dp) 0.045f else 0.06f)
            val stroke = if (m == Metric.RESPIRATORY_RATE) base * (0.8f + 0.5f * p) else base
            val inset = base * 0.9f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
            val brush = if (m == Metric.BODY_ENERGY) Brush.sweepGradient(listOf(Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFF2EA043), Color(0xFFE5484D)))
            else Brush.sweepGradient(listOf(hue.copy(alpha = 0.55f), hue, hue.copy(alpha = 0.55f)))
            rotate(-90f) { drawArc(brush, 0f, 360f * fill.value, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round)) }
        }
        content()
    }
}

/** Statut : catégorie de VO₂ max, sinon écart à votre moyenne des 14 jours. Second : mis en avant. */
private fun status(m: Metric, v: Double?, pts: List<Double>, female: Boolean): Pair<String, Boolean>? {
    v ?: return null
    if (m == Metric.VO2MAX) {
        // Repères 20–39 ans (tables de Cooper), décalés pour les femmes.
        val o = if (female) 6.0 else 0.0
        return when {
            v >= 57 - o -> tr("Excellent") to true
            v >= 50 - o -> tr("Très bon") to true
            v >= 42 - o -> tr("Bon") to false
            v >= 35 - o -> tr("Moyen") to false
            else -> tr("À travailler") to false
        }
    }
    if (m == Metric.BODY_ENERGY) return when {
        v >= 75 -> tr("Plein d'énergie") to true
        v >= 50 -> tr("Bonne forme") to false
        v >= 25 -> tr("Moyen") to false
        else -> tr("À recharger") to true
    }
    if (m == Metric.SPO2) return if (v >= 95) tr("Normal") to false else tr("Bas") to true
    if (m == Metric.FITNESS_AGE) {
        val age = java.time.LocalDate.now().year - app.superhealth.nutrition.NutriApp.repository.prefs.profile.value.birthYear
        val d = kotlin.math.round(age - v).toInt()
        return when {
            d >= 1 -> app.superhealth.ui.trf("%1\$s ans de moins", d) to true
            d <= -1 -> app.superhealth.ui.trf("%1\$s ans de plus", -d) to false
            else -> tr("Votre âge") to false
        }
    }
    if (pts.size < 5) return null
    val mean = pts.average()
    val sd = sqrt(pts.sumOf { (it - mean) * (it - mean) } / pts.size).coerceAtLeast(mean * 0.02)
    val z = (v - mean) / sd
    return when {
        z > 1.5 -> tr("Élevé") to true
        z < -1.5 -> tr("Bas") to true
        else -> tr("Habituel") to false
    }
}

