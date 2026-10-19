package app.superhealth.ui.health

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlin.math.roundToInt

/**
 * Courbe de fréquence cardiaque (comme Garmin Connect ou Google Health) : ligne lissée, dégradé dessous, coupée quand
 * la montre n'a rien mesuré pendant plus de [gapMs], point pulsant sur la dernière mesure quand [live].
 * [points] : (horodatage ms, bpm) triés. [from]/[to] fixent l'axe des temps (sinon, celui des données).
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
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(tr(if (live) "En attente de la montre…" else "Pas encore assez de données"), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, textAlign = TextAlign.Center)
        }
        return
    }
    val x0 = (from ?: points.first().first).toDouble()
    val x1 = maxOf((to ?: points.last().first).toDouble(), x0 + 1)
    val vs = points.map { it.second } + listOfNotNull(reference)
    val lo = (vs.min() - 6).coerceAtLeast(30.0)
    val hi = vs.max() + 6
    val grid = Mf.colors.divider
    val axis = Mf.colors.textSecondary
    val pulse = if (live) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "pulse").value
    } else 0f
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Canvas(Modifier.weight(1f).height(height)) {
                fun x(t: Long) = ((t - x0) / (x1 - x0) * size.width).toFloat()
                fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
                listOf(0f, 0.5f, 1f).forEach { f -> drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1.dp.toPx()) }
                reference?.let { r ->
                    drawLine(axis.copy(alpha = 0.6f), Offset(0f, y(r)), Offset(size.width, y(r)), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                // Segments continus (une mesure manquante trop longue coupe la courbe).
                val segments = mutableListOf(mutableListOf(points.first()))
                points.zipWithNext().forEach { (a, b) -> if (b.first - a.first > gapMs) segments += mutableListOf(b) else segments.last() += b }
                val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.0f)))
                segments.filter { it.size >= 2 }.forEach { seg ->
                    val line = Path()
                    seg.forEachIndexed { i, (t, v) ->
                        if (i == 0) line.moveTo(x(t), y(v))
                        else {
                            val (pt, pv) = seg[i - 1]
                            val mx = (x(pt) + x(t)) / 2
                            line.cubicTo(mx, y(pv), mx, y(v), x(t), y(v))
                        }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(x(seg.last().first), size.height); lineTo(x(seg.first().first), size.height); close()
                    }
                    drawPath(area, fill)
                    drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                segments.filter { it.size == 1 }.forEach { (p) -> drawCircle(color, 2.5.dp.toPx(), Offset(x(p.first), y(p.second))) }
                if (live) {
                    val (t, v) = points.last()
                    val c = Offset(x(t), y(v))
                    drawCircle(color.copy(alpha = 0.35f * (1 - pulse)), radius = (5 + 10 * pulse).dp.toPx(), center = c)
                    drawCircle(Color.White, radius = 5.dp.toPx(), center = c)
                    drawCircle(color, radius = 3.5.dp.toPx(), center = c)
                }
            }
            if (showAxis) Column(Modifier.height(height).padding(start = 6.dp).width(26.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(hi, (hi + lo) / 2, lo).forEach { Text("${it.roundToInt()}", fontSize = 10.sp, color = axis, maxLines = 1) }
            }
        }
        if (xLabels.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp, end = if (showAxis) 32.dp else 0.dp).height(14.dp)) {
            xLabels.forEach { (t, label) ->
                val f = ((t - x0) / (x1 - x0)).toFloat().coerceIn(0f, 1f)
                // Étiquette centrée sur son heure, sans déborder des bords.
                Text(label, fontSize = 10.sp, color = axis, maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp).offset(x = (maxWidth * f - 18.dp).coerceIn(0.dp, maxWidth - 36.dp)))
            }
        }
    }
}
