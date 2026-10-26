package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series

/**
 * Courbe de fréquence cardiaque (comme Garmin Connect ou Google Health), dans la charte commune : ligne lissée,
 * dégradé dessous, coupée quand la montre n'a rien mesuré pendant plus de [gapMs], point cerclé sur la dernière mesure
 * quand [live]. [points] : (horodatage ms, bpm) triés. [from]/[to] fixent l'axe des temps (sinon, celui des données).
 */
@Composable
fun HeartChart(
    points: List<Pair<Long, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    from: Long? = null,
    to: Long? = null,
    gapMs: Long = 25 * 60_000L,
    live: Boolean = false,
    xLabels: List<Pair<Long, String>> = emptyList(),
    showAxis: Boolean = true,
    /** Ligne horizontale de référence (FC au repos), en pointillés. */
    reference: Double? = null,
) {
    LineChart(
        listOf(Series(points, color, gap = gapMs.toDouble())), modifier, height, showAxis = showAxis,
        xFrom = from?.toDouble(), xTo = to?.toDouble(), reference = reference, xLabels = xLabels,
        highlightEnd = live, ringedEnd = true,
        emptyText = if (live) "En attente de la montre…" else "Pas encore assez de données",
    )
}
