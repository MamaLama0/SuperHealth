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
 * Carte « Constantes » : une tuile vivante par mesure. Chaque visuel suit la valeur mesurée (cœur qui bat à votre FC
 * de repos, cercles qui respirent à votre fréquence respiratoire, goutte remplie à votre SpO₂, compteur de VO₂ max…).
 * Les animations ne lisent leur état qu'au dessin : la carte ne se recompose pas à chaque image.
 */

/**
 * Grille centrée : 1 à 3 anneaux par ligne (4 → 2 × 2), la dernière ligne centrée, sans case vide.
 */
@Composable
internal fun VitalsGrid(metrics: List<Metric>, series: (Metric) -> List<Pair<LocalDate, Double?>>, female: Boolean, onOpen: (Metric) -> Unit) {
    if (metrics.isEmpty()) return
    val perRow = when (metrics.size) { 1 -> 1; 2, 4 -> 2; else -> 3 }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val w = (maxWidth - gap * (perRow - 1)) / perRow
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            metrics.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    row.forEach { m -> RingVital(m, series(m), female, big = perRow <= 2, modifier = Modifier.width(w).fillMaxHeight()) { onOpen(m) } }
                }
            }
        }
    }
}

/** Plage de l'anneau de chaque mesure (valeur → remplissage). */
private fun Metric.ringRange(): ClosedFloatingPointRange<Double> = when (this) {
    Metric.RESTING_HR -> 40.0..100.0
    Metric.HEART_RATE -> 40.0..180.0
    Metric.RESPIRATORY_RATE -> 8.0..25.0
    Metric.SPO2 -> 85.0..100.0
    Metric.HRV -> 20.0..120.0
    Metric.VO2MAX -> 25.0..65.0
    Metric.TEMPERATURE -> 30.0..38.0
    Metric.STEPS -> 0.0..10_000.0
    Metric.SLEEP -> 0.0..480.0
    else -> 0.0..100.0
}

/**
 * Tuile en anneau, à la Google Health : la valeur au centre d'un anneau qui se remplit à l'ouverture. L'anneau du cœur
 * pulse au rythme mesuré, celui de la respiration gonfle et se vide à votre fréquence ; le Body Battery passe du rouge au vert.
 */
@Composable
private fun RingVital(m: Metric, series: List<Pair<LocalDate, Double?>>, female: Boolean, big: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pts = series.mapNotNull { it.second }
    val latest = series.lastOrNull { it.second != null }
    val v = latest?.second
    val hue = m.color
    val status = status(m, v, pts, female)
    val range = m.ringRange()
    val target = v?.let { ((it - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0.02f, 1f) } ?: 0f
    val fill = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(target) { fill.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "ring")
    val pulse = when (m) {
        Metric.RESTING_HR, Metric.HEART_RATE -> {
            val period = (60_000 / (v ?: 60.0).coerceIn(35.0, 140.0)).toInt()
            t.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
                durationMillis = period
                0f at 0; 1f at (period * 0.12).toInt(); 0.25f at (period * 0.24).toInt(); 0.6f at (period * 0.34).toInt(); 0f at (period * 0.6).toInt()
            }), label = "beat")
        }
        Metric.RESPIRATORY_RATE -> t.animateFloat(0f, 1f, infiniteRepeatable(tween((30_000 / (v ?: 14.0).coerceIn(6.0, 40.0)).toInt(),
            easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        else -> null
    }
    val track = Mf.colors.card.copy(alpha = if (Mf.colors.dark) 0.5f else 0.85f)
    val ringSize = if (big) 112.dp else 88.dp
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(hue.container(1.5f), hue.container(0.7f))))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val p = pulse?.value ?: 0f
                val base = size.minDimension * 0.085f
                val stroke = if (m == Metric.RESPIRATORY_RATE) base * (0.8f + 0.5f * p) else base
                val inset = base * 0.9f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val tl = Offset(inset, inset)
                // Halo du battement autour de l'anneau.
                if (m == Metric.RESTING_HR || m == Metric.HEART_RATE) drawCircle(hue.copy(alpha = 0.22f * p), radius = size.minDimension / 2 * (0.9f + 0.1f * p))
                drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
                val brush = if (m == Metric.BODY_ENERGY) Brush.sweepGradient(listOf(Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFF2EA043), Color(0xFFE5484D)))
                else Brush.sweepGradient(listOf(hue.copy(alpha = 0.55f), hue, hue.copy(alpha = 0.55f)))
                rotate(-90f) { drawArc(brush, 0f, 360f * fill.value, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(m.icon, null, Modifier.size(if (big) 18.dp else 14.dp), tint = hue)
                Text(v?.let { formatValue(m, it) } ?: "—", style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1)
                unitOf(m)?.takeIf { v != null }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr(m.label), style = MaterialTheme.typography.labelMedium, color = hue.onContainer(), maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        status?.let { (label, strong) ->
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (strong) Color.White else hue.onContainer(),
                modifier = Modifier.clip(CircleShape).background(if (strong) hue else Mf.colors.card.copy(alpha = 0.75f)).padding(horizontal = 8.dp, vertical = 3.dp))
        } ?: if (latest != null && latest.first != LocalDate.now()) {
            Spacer(Modifier.height(6.dp))
            Text(relativeDay(latest.first), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, maxLines = 1)
        } else Unit
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

