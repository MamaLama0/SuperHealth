package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightVitals
import app.superhealth.ui.formatValue
import app.superhealth.ui.go
import app.superhealth.ui.health.VitalTrend
import app.superhealth.ui.health.color
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.unitOf
import kotlin.math.roundToInt

/*
 * 2.2 : deux cartes qui ne se ressemblent plus.
 * « Constantes » = l'instant, sans chiffres autour : une grappe de bulles (valeur et symbole), dans toutes les tailles.
 * « Tendances » = les données : chaque mesure avec sa courbe de 30 jours posée sur sa bande habituelle, et son statut.
 * Pas de BoxWithConstraints : les rangées de la grille se mesurent en hauteur intrinsèque (Layout simple à la place).
 */

/** Place d'une bulle dans la grappe, en fraction du côté : coin haut-gauche, diamètre, et rang (taille du texte). */
private class Slot(val x: Float, val y: Float, val d: Float, val rank: Int)

/** Moyen : quatre bulles qui se nichent sans se toucher (vérifié : distances des centres > somme des rayons). */
private val SLOTS_M = listOf(Slot(0f, .02f, .58f, 0), Slot(.60f, 0f, .40f, 2), Slot(.50f, .50f, .48f, 1), Slot(.08f, .62f, .36f, 2))
/** Petit : trois bulles. */
private val SLOTS_S = listOf(Slot(0f, .05f, .62f, 1), Slot(.62f, .02f, .38f, 3), Slot(.55f, .58f, .42f, 3))

/** Constantes en petit, moyen ou bandeau ; le grand garde la grappe complète (VitalsGrid). */
@Composable
internal fun VitalsCompact(c: DashCard, size: CardSize, metrics: List<Metric>, latest: (Metric) -> Double?, modifier: Modifier, nav: NavHostController) {
    val open: (Metric) -> Unit = { m -> nav.go("h/metric/${m.name}") }
    val shown = metrics.ifEmpty { listOf(Metric.RESTING_HR, Metric.HRV, Metric.RESPIRATORY_RATE, Metric.SPO2) }
    when (size) {
        CardSize.S -> Box(modifier.square().clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).padding(8.dp)) {
            Cluster(SLOTS_S, shown, Modifier.fillMaxSize()) { m, rank -> VitalDot(m, latest(m), rank) { open(m) } }
        }
        CardSize.W -> Row(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).cardClick { nav.go("h/vitals") }
            .padding(horizontal = 8.dp, vertical = 8.dp).height(70.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            // une vague : tailles et hauteurs alternées, comme des bulles qui montent
            val sizes = listOf(66, 54, 62, 52, 58)
            shown.take(5).forEachIndexed { i, m ->
                Box(Modifier.size(sizes[i].dp).align(if (i % 2 == 0) Alignment.Top else Alignment.Bottom)) {
                    VitalDot(m, latest(m), if (i == 0) 1 else 2) { open(m) }
                }
            }
        }
        else -> Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).padding(10.dp)) {
            Cluster(SLOTS_M, shown, Modifier.fillMaxSize()) { m, rank -> VitalDot(m, latest(m), rank) { open(m) } }
        }
    }
}

/** Grappe : chaque bulle mesurée et posée à sa place, à l'échelle du plus petit côté. */
@Composable
private fun Cluster(slots: List<Slot>, items: List<Metric>, modifier: Modifier, bubble: @Composable (Metric, Int) -> Unit) {
    val n = minOf(slots.size, items.size)
    Layout(content = { (0 until n).forEach { bubble(items[it], slots[it].rank) } }, modifier = modifier) { ms, c ->
        val w = c.maxWidth
        val h = if (c.hasBoundedHeight) c.maxHeight else w
        val side = minOf(w, h)
        val ox = (w - side) / 2
        val oy = (h - side) / 2
        val ps = ms.mapIndexed { i, m -> val px = (slots[i].d * side).roundToInt(); m.measure(Constraints.fixed(px, px)) }
        layout(w, h) { ps.forEachIndexed { i, p -> p.place(ox + (slots[i].x * side).roundToInt(), oy + (slots[i].y * side).roundToInt()) } }
    }
}

/** Une bulle : dégradé de la mesure, anneau rempli selon la valeur, symbole et valeur au centre. */
@Composable
private fun VitalDot(m: Metric, v: Double?, rank: Int, onClick: () -> Unit) {
    val hue = m.color
    val range = m.ringRange()
    val fill = v?.let { ((it - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0.03f, 1f) } ?: 0f
    val track = Mf.colors.card.copy(alpha = if (Mf.colors.dark) 0.5f else 0.85f)
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.radialGradient(listOf(hue.container(1.8f), hue.container(0.9f))))
        .drawBehind {
            val stroke = size.minDimension * (if (rank == 0) 0.055f else 0.07f)
            val inset = stroke * 0.9f
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            if (fill > 0f) rotate(-90f) {
                drawArc(Brush.sweepGradient(listOf(hue.copy(alpha = 0.55f), hue, hue.copy(alpha = 0.55f))), 0f, 360f * fill, false,
                    Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(m.icon, null, Modifier.size(when (rank) { 0 -> 18.dp; 1 -> 14.dp; 2 -> 12.dp; else -> 10.dp }), tint = hue)
            Text(v?.let { formatValue(m, it) } ?: "—", style = when (rank) {
                0 -> MaterialTheme.typography.titleLarge; 1 -> MaterialTheme.typography.titleMedium
                2 -> MaterialTheme.typography.titleSmall; else -> MaterialTheme.typography.labelMedium
            }, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1)
            if (rank == 0) unitOf(m)?.takeIf { v != null }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1) }
        }
    }
}

/** Tendances en petit, moyen ou bandeau : chaque mesure avec sa courbe sur la bande habituelle. */
@Composable
internal fun TrendsCompact(c: DashCard, size: CardSize, trends: List<VitalTrend>, modifier: Modifier, nav: NavHostController) {
    val rows = trends.filter { it.last != null }
    val open: () -> Unit = { nav.go("h/vitals") }
    when (size) {
        CardSize.S -> Column(modifier.square().clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).cardClick(open).padding(10.dp),
            verticalArrangement = Arrangement.SpaceEvenly) {
            if (rows.isEmpty()) EmptyTrends()
            rows.take(3).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(t.metric.icon, null, Modifier.size(13.dp), tint = t.metric.color)
                    Spacer(Modifier.width(5.dp))
                    TrendValue(t, MaterialTheme.typography.labelLarge, Modifier.weight(1f))
                    StatusDot(t)
                }
            }
        }
        CardSize.W -> Row(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).cardClick(open).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rows.isEmpty()) EmptyTrends()
            rows.take(3).forEach { t ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(t.metric.icon, null, Modifier.size(14.dp), tint = t.metric.color)
                        Spacer(Modifier.width(5.dp))
                        TrendValue(t, MaterialTheme.typography.titleSmall, Modifier.weight(1f))
                        StatusDot(t)
                    }
                    TrendSpark(t, Modifier.padding(top = 6.dp).fillMaxWidth().height(26.dp))
                }
            }
        }
        else -> Column(modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).dashCardBackground(c.ink()).cardClick(open).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) EmptyTrends()
            rows.take(4).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(t.metric.icon, null, Modifier.size(14.dp), tint = t.metric.color)
                    Spacer(Modifier.width(6.dp))
                    TrendValue(t, MaterialTheme.typography.titleSmall, Modifier.width(52.dp))
                    TrendSpark(t, Modifier.weight(1f).height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyTrends() {
    Text(tr("Après une nuit avec la montre."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
}

/** Valeur colorée par son statut : dans la plage, la couleur de la mesure ; hors plage, celle de l'alerte. */
@Composable
private fun TrendValue(t: VitalTrend, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    val st = t.status
    Text(formatValue(t.metric, t.last!!.second), style = style, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip,
        color = if (st == NightVitals.Status.UNKNOWN) t.metric.color.onContainer() else st.color().onContainer(), modifier = modifier)
}

@Composable
private fun StatusDot(t: VitalTrend) {
    val st = t.status
    if (st == NightVitals.Status.UNKNOWN) return
    Box(Modifier.size(7.dp).clip(CircleShape).background(st.color()))
}

/**
 * Courbe de 30 jours posée sur la bande habituelle de la mesure (28 jours précédents) : on voit d'un coup d'œil si
 * les dernières nuits sortent de votre normale. Le dernier point est marqué dans la couleur du statut.
 */
@Composable
internal fun TrendSpark(t: VitalTrend, modifier: Modifier) {
    val hue = t.metric.color
    val band = hue.copy(alpha = if (Mf.colors.dark) 0.20f else 0.16f)
    val dot = if (t.status == NightVitals.Status.UNKNOWN) hue else t.status.color()
    val halo = Color.White.copy(alpha = if (Mf.colors.dark) 0f else 0.9f)
    val vals = t.days.map { it.second }.dropWhile { it == null }
    Canvas(modifier) {
        val present = vals.filterNotNull()
        if (present.isEmpty()) return@Canvas
        val lo = minOf(present.min(), t.range?.first ?: present.min())
        val hi = maxOf(present.max(), t.range?.second ?: present.max())
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val pad = 3.dp.toPx()
        fun y(v: Double) = (pad + (1 - (v - lo) / span) * (size.height - pad * 2)).toFloat()
        t.range?.let { (a, b) -> drawRoundRect(band, Offset(0f, y(b)), Size(size.width, (y(a) - y(b)).coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())) }
        val n = vals.size
        val step = if (n > 1) size.width / (n - 1) else 0f
        val path = Path()
        var started = false
        var last: Offset? = null
        vals.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val p = Offset(i * step, y(v))
            if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            last = p
        }
        drawPath(path, hue.copy(alpha = 0.9f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        last?.let { drawCircle(halo, 3.6.dp.toPx(), it); drawCircle(dot, 2.6.dp.toPx(), it) }
    }
}
