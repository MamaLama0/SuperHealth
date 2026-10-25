package app.superhealth.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Charte commune de tous les graphiques de l'app : mêmes traits, grilles, aires, points, barres et axes, pour que
 * nutrition, musculation, santé et tableau de bord se lisent de la même façon.
 */
object ChartStyle {
    /** Trait d'une courbe principale ; les courbes secondaires passent leur propre épaisseur. */
    val line = 2.5.dp
    /** Lignes de repère (objectif, moyenne, FC au repos), toujours en pointillés. */
    val reference = 1.5.dp
    val grid = 1.dp
    val dashOn = 5.dp
    val dashOff = 4.dp
    /** Aire sous une courbe seule : dégradé de cette opacité vers transparent. */
    const val AREA_ALPHA = 0.28f
    /** Points d'une série en nuage (pesées). */
    val dot = 2.5.dp
    /** Point qui marque la dernière valeur. */
    val endDot = 3.5.dp
    /** Barres : 56 % de leur place, jamais plus larges que [barMax] (des barres, pas des pastilles). */
    const val BAR_FILL = 0.56f
    val barMax = 12.dp
    /** Atténuation d'une barre sous son objectif (cartes du tableau de bord). */
    const val BELOW_GOAL_ALPHA = 0.6f
    val label = 10.sp
    val axisGap = 6.dp
    /** Hauteur à partir de laquelle un graphique est « détaillé » : axe gradué et grille. */
    val detailed = 100.dp

    fun dash(scope: DrawScope) = with(scope) { PathEffect.dashPathEffect(floatArrayOf(dashOn.toPx(), dashOff.toPx())) }
}

/** Bornes et graduations rondes d'un axe ; [step] sert aussi à choisir le nombre de décimales. */
data class Scale(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double> get() = generateSequence(min) { it + step }.takeWhile { it <= max + step * 1e-6 }.toList()
    fun frac(v: Double) = if (max > min) ((v - min) / (max - min)).toFloat() else 0.5f
    /** Libellé d'une graduation, avec juste les décimales qu'impose le pas. */
    fun label(v: Double): String = formatNumber(v, (0..2).firstOrNull { d -> 10.0.pow(d).let { abs(step * it - Math.round(step * it)) < 1e-6 } } ?: 2)
}

object Scales {
    /**
     * Échelle à graduations « rondes » (1, 2, 2,5 ou 5 × 10ⁿ ; [time] : pas en secondes 5 s… 1 h) couvrant [lo]…[hi],
     * en [count] intervalles environ. Une série plate reçoit une marge, pour ne pas coller la courbe à un bord.
     */
    fun nice(lo: Double, hi: Double, count: Int = 4, zero: Boolean = false, time: Boolean = false): Scale {
        var a = if (zero) min(lo, 0.0) else lo
        var b = if (zero) max(hi, 0.0) else hi
        if (b - a < 1e-9) { val d = max(abs(b) * 0.05, if (time) 30.0 else 1.0); if (!(zero && a == 0.0)) a -= d; b += d }
        // parmi les pas ronds proches, celui dont l'échelle serre le mieux la donnée (3 à 6 graduations)
        return (count - 1..count + 1).map { k ->
            val raw = (b - a) / k
            val step = if (time) TIME_STEPS.firstOrNull { it >= raw } ?: (ceil(raw / 3600) * 3600) else niceStep(raw)
            Scale(floor(a / step + 1e-9) * step, ceil(b / step - 1e-9) * step, step)
        }.filter { it.ticks.size in 3..6 }
            .sortedWith(compareByDescending<Scale> { Math.round((b - a) / (it.max - it.min) * 100) }.thenBy { abs(it.ticks.size - 5) }).firstOrNull()
            ?: niceStep((b - a) / count).let { Scale(floor(a / it + 1e-9) * it, ceil(b / it - 1e-9) * it, it) }
    }

    /** Échelle sans graduations des petits graphiques : la donnée remplit la hauteur. */
    fun fit(lo: Double, hi: Double): Scale = if (hi - lo < 1e-9) Scale(lo - 1, hi + 1, 1.0) else Scale(lo, hi, hi - lo)

    private val TIME_STEPS = listOf(5.0, 10.0, 15.0, 20.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1800.0, 3600.0)

    fun niceStep(raw: Double): Double {
        if (raw <= 0 || raw.isNaN()) return 1.0
        val p = 10.0.pow(floor(log10(raw)))
        val f = raw / p
        return p * when { f <= 1.0 -> 1.0; f <= 2.0 -> 2.0; f <= 2.5 -> 2.5; f <= 5.0 -> 5.0; else -> 10.0 }
    }
}

/**
 * Tracé lisse qui passe exactement par chaque point sans jamais inventer de creux ni de pic (interpolation monotone
 * de Fritsch–Carlson) : la courbe reste fidèle aux valeurs. [move] puis [cubic] par segment.
 */
inline fun monotone(xs: FloatArray, ys: FloatArray, move: (Float, Float) -> Unit, line: (Float, Float) -> Unit,
                    cubic: (Float, Float, Float, Float, Float, Float) -> Unit) {
    val n = xs.size
    if (n == 0) return
    move(xs[0], ys[0])
    if (n == 1) return
    if (n == 2) { line(xs[1], ys[1]); return }
    val d = FloatArray(n - 1) { i -> val dx = xs[i + 1] - xs[i]; if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx }
    val t = FloatArray(n)
    t[0] = d[0]; t[n - 1] = d[n - 2]
    for (i in 1 until n - 1) {
        t[i] = if (d[i - 1] * d[i] <= 0f) 0f else {
            val h0 = xs[i] - xs[i - 1]; val h1 = xs[i + 1] - xs[i]
            3 * (h0 + h1) / ((2 * h1 + h0) / d[i - 1] + (h1 + 2 * h0) / d[i])
        }
    }
    for (i in 0 until n - 1) {
        val h = (xs[i + 1] - xs[i]) / 3
        cubic(xs[i] + h, ys[i] + t[i] * h, xs[i + 1] - h, ys[i + 1] - t[i + 1] * h, xs[i + 1], ys[i + 1])
    }
}

/** Ajoute à [path] la courbe lisse des [pts] ([start] : commence un nouveau tracé, sinon le prolonge). */
fun Path.smooth(pts: List<Offset>, start: Boolean = true) {
    if (pts.isEmpty()) return
    val xs = FloatArray(pts.size) { pts[it].x }; val ys = FloatArray(pts.size) { pts[it].y }
    monotone(xs, ys, { x, y -> if (start) moveTo(x, y) else lineTo(x, y) }, { x, y -> lineTo(x, y) },
        { a, b, c, d, e, f -> cubicTo(a, b, c, d, e, f) })
}

/**
 * Série d'une courbe. [points] : (x, y) triés par x (jours, minutes ou millisecondes) ; [dots] : nuage de points
 * (pesées) ; [markers] : courbe avec un point par valeur ; [gap] : écart en x au-delà duquel la courbe s'interrompt ;
 * [area] : dégradé dessous (par défaut quand la courbe est seule).
 */
data class Series(
    val points: List<Pair<Number, Double>>, val color: Color, val dots: Boolean = false, val dashed: Boolean = false,
    /** Bande (x, bas, haut) remplie autour de la courbe. */
    val band: List<Triple<Number, Double, Double>>? = null, val width: Float = 2.5f,
    val markers: Boolean = false, val gap: Double? = null, val area: Boolean? = null,
)

@Composable
fun ChartEmpty(modifier: Modifier, height: Dp, text: String = "Pas encore assez de données") {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(tr(text), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

private class AxisText(val measurer: TextMeasurer, val style: TextStyle)

@Composable
private fun axisText() = AxisText(rememberTextMeasurer(), TextStyle(fontSize = ChartStyle.label, color = Mf.colors.textSecondary))

/** Libellés de l'axe vertical à droite, centrés sur leur graduation ; renvoie la largeur occupée. */
private fun DrawScope.yAxis(t: AxisText, scale: Scale, labels: List<String>, top: Float, plotH: Float, invert: Boolean, draw: Boolean): Float {
    val sizes = labels.map { t.measurer.measure(it, t.style).size }
    val w = (sizes.maxOfOrNull { it.width } ?: 0) + ChartStyle.axisGap.toPx()
    if (draw) scale.ticks.forEachIndexed { i, v ->
        val f = scale.frac(v).let { if (invert) it else 1 - it }
        val y = top + f * plotH - sizes[i].height / 2f
        drawText(t.measurer, labels[i], Offset(size.width - sizes[i].width, y.coerceIn(0f, size.height - sizes[i].height)), t.style)
    }
    return w
}

/** Libellés de l'axe horizontal, centrés sous leur position sans sortir du graphique ni se chevaucher. */
private fun DrawScope.xAxis(t: AxisText, labels: List<Pair<Float, String>>, plotW: Float, y: Float) {
    var lastRight = Float.NEGATIVE_INFINITY
    labels.forEach { (x, s) ->
        if (s.isEmpty()) return@forEach
        val sz = t.measurer.measure(s, t.style).size
        val left = (x - sz.width / 2f).coerceIn(0f, max(0f, plotW - sz.width))
        if (left < lastRight + 4.dp.toPx()) return@forEach
        drawText(t.measurer, s, Offset(left, y), t.style)
        lastRight = left + sz.width
    }
}

private fun DrawScope.gridLines(scale: Scale, top: Float, plotW: Float, plotH: Float, color: Color) {
    scale.ticks.forEach { v -> val y = top + (1 - scale.frac(v)) * plotH; drawLine(color, Offset(0f, y), Offset(plotW, y), ChartStyle.grid.toPx()) }
}

/**
 * Courbe(s). Avec [showAxis] : axe gradué à valeurs rondes à droite et grille alignée ; sans : la courbe remplit la
 * hauteur (cartes). [xFrom]/[xTo] fixent l'axe des x, [reference] trace une ligne de repère, [invertY] met les petites
 * valeurs en haut (allure), [xLabels] : (x, libellé) sous le graphique, [highlightEnd] : point sur la dernière valeur.
 */
@Composable
fun LineChart(
    series: List<Series>, modifier: Modifier = Modifier, height: Dp = 160.dp, showAxis: Boolean = true,
    format: ((Double) -> String)? = null, xFrom: Double? = null, xTo: Double? = null, reference: Double? = null,
    invertY: Boolean = false, xLabels: List<Pair<Number, String>> = emptyList(), timeAxis: Boolean = false,
    highlightEnd: Boolean = true, ringedEnd: Boolean = false, emptyText: String = "Pas encore assez de données",
) {
    val ys = series.flatMap { s -> s.points.map { it.second } + s.band.orEmpty().flatMap { listOf(it.second, it.third) } } + listOfNotNull(reference)
    val xs = series.flatMap { s -> s.points.map { it.first.toDouble() } + s.band.orEmpty().map { it.first.toDouble() } }
    if (series.sumOf { it.points.size } < 2 || xs.isEmpty()) { ChartEmpty(modifier, height, emptyText); return }
    val x0 = xFrom ?: xs.min(); val x1 = max(xTo ?: xs.max(), x0 + 1e-6)
    val scale = if (showAxis) Scales.nice(ys.min(), ys.max(), time = timeAxis) else Scales.fit(ys.min(), ys.max())
    val labels = scale.ticks.map { format?.invoke(it) ?: scale.label(it) }
    val grid = Mf.colors.divider
    val refColor = Mf.colors.textSecondary.copy(alpha = 0.7f)
    val card = Mf.colors.card
    val t = axisText()
    val xRow = if (xLabels.isNotEmpty()) 18.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + xRow)) {
        val labelH = t.measurer.measure("0", t.style).size.height.toFloat()
        // marge haute et basse : la moitié d'un libellé avec axe, sinon l'épaisseur du trait et du point final
        val inset = if (showAxis) labelH / 2 else (ChartStyle.endDot.toPx() + 1.dp.toPx())
        val plotH = height.toPx() - 2 * inset
        val gutter = if (showAxis) yAxis(t, scale, labels, inset, plotH, invertY, draw = true) else 0f
        val plotW = size.width - gutter
        fun x(v: Double) = ((v - x0) / (x1 - x0) * plotW).toFloat()
        fun y(v: Double) = inset + (if (invertY) scale.frac(v) else 1 - scale.frac(v)) * plotH
        if (showAxis) gridLines(scale, inset, plotW, plotH, grid)
        reference?.let { r -> drawLine(refColor, Offset(0f, y(r)), Offset(plotW, y(r)), ChartStyle.reference.toPx(), pathEffect = ChartStyle.dash(this)) }
        val solo = series.count { !it.dots } == 1
        series.forEach { s ->
            s.band?.takeIf { it.size >= 2 }?.let { b ->
                val p = Path()
                p.smooth(b.map { Offset(x(it.first.toDouble()), y(it.second)) })
                p.smooth(b.reversed().map { Offset(x(it.first.toDouble()), y(it.third)) }, start = false)
                p.close()
                drawPath(p, s.color.copy(alpha = 0.16f))
            }
            if (s.dots) {
                s.points.forEach { (px, py) -> drawCircle(s.color.copy(alpha = 0.55f), ChartStyle.dot.toPx(), Offset(x(px.toDouble()), y(py))) }
                return@forEach
            }
            // segments continus : un trou plus long que [gap] interrompt la courbe
            val segs = mutableListOf<MutableList<Offset>>()
            var prev: Double? = null
            s.points.forEach { (px, py) ->
                val xv = px.toDouble()
                if (prev == null || (s.gap != null && xv - prev!! > s.gap)) segs += mutableListOf<Offset>()
                segs.last() += Offset(x(xv), y(py)); prev = xv
            }
            val area = s.area ?: (solo && !s.dashed && s.band == null)
            val base = if (invertY) inset else inset + plotH
            segs.forEach { seg ->
                if (seg.size == 1) { drawCircle(s.color, ChartStyle.dot.toPx(), seg[0]); return@forEach }
                val line = Path().apply { smooth(seg) }
                if (area) drawPath(Path().apply { addPath(line); lineTo(seg.last().x, base); lineTo(seg.first().x, base); close() },
                    Brush.verticalGradient(listOf(s.color.copy(alpha = ChartStyle.AREA_ALPHA), s.color.copy(alpha = 0f)).let { if (invertY) it.reversed() else it },
                        startY = inset, endY = inset + plotH))
                drawPath(line, s.color, style = Stroke(s.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (s.dashed) ChartStyle.dash(this) else null))
            }
            if (s.markers) segs.flatten().forEach { drawCircle(s.color, ChartStyle.dot.toPx(), it) }
        }
        // dernière valeur de la courbe principale (la dernière série pleine)
        if (highlightEnd) series.lastOrNull { !it.dots && !it.dashed && it.points.isNotEmpty() }?.let { s ->
            val (px, py) = s.points.last()
            val c = Offset(x(px.toDouble()), y(py))
            if (ringedEnd) drawCircle(card, ChartStyle.endDot.toPx() + 1.5.dp.toPx(), c)
            drawCircle(s.color, ChartStyle.endDot.toPx(), c)
        }
        if (xLabels.isNotEmpty()) xAxis(t, xLabels.map { x(it.first.toDouble()) to it.second }, plotW, height.toPx() + 4.dp.toPx())
    }
}

/**
 * Barres par jour (ou semaine). Détaillé ([showAxis], par défaut à partir de [ChartStyle.detailed]) : grille et axe
 * gradué ; compact : fond de barre [track]. [target] : objectif de chaque barre en pointillés (une ligne continue s'il
 * est le même partout) ; [dimBelowGoal] : barres sous l'objectif atténuées.
 */
@Composable
fun BarChart(
    values: List<Double?>, target: List<Double?>, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp,
    labels: List<String> = emptyList(), track: Color = Mf.colors.chip, showAxis: Boolean = height >= ChartStyle.detailed,
    format: ((Double) -> String)? = null, dimBelowGoal: Boolean = false,
) {
    val top = (values.filterNotNull() + target.filterNotNull()).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val scale = if (showAxis) Scales.nice(0.0, top, zero = true) else Scale(0.0, top, top)
    val axisLabels = scale.ticks.map { format?.invoke(it) ?: scale.label(it) }
    val grid = Mf.colors.divider
    val line = Mf.colors.textSecondary
    val t = axisText()
    val xRow = if (labels.any { it.isNotEmpty() }) 18.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + xRow)) {
        val labelH = t.measurer.measure("0", t.style).size.height.toFloat()
        val inset = if (showAxis) labelH / 2 else 0f
        val plotH = height.toPx() - 2 * inset
        val gutter = if (showAxis) yAxis(t, scale, axisLabels, inset, plotH, false, draw = true) else 0f
        val plotW = size.width - gutter
        if (showAxis) gridLines(scale, inset, plotW, plotH, grid)
        val n = values.size.coerceAtLeast(1)
        val slot = plotW / n
        val bw = min(slot * ChartStyle.BAR_FILL, ChartStyle.barMax.toPx())
        fun y(v: Double) = inset + (1 - scale.frac(v)) * plotH
        values.forEachIndexed { i, v ->
            val left = i * slot + (slot - bw) / 2
            if (!showAxis) drawRoundRect(track, Offset(left, inset), Size(bw, plotH), CornerRadius(bw / 2))
            if (v != null && v > 0) {
                val h = (plotH * scale.frac(v)).coerceAtLeast(bw)
                val goal = target.getOrNull(i)
                val c = if (dimBelowGoal && goal != null && v < goal) color.copy(alpha = ChartStyle.BELOW_GOAL_ALPHA) else color
                drawRoundRect(c, Offset(left, inset + plotH - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        val goals = target.filterNotNull()
        if (goals.isNotEmpty()) {
            val stroke = ChartStyle.reference.toPx()
            if (goals.distinct().size == 1 && target.size == values.size && target.all { it != null })
                drawLine(line, Offset(0f, y(goals[0])), Offset(plotW, y(goals[0])), stroke, pathEffect = ChartStyle.dash(this))
            else target.forEachIndexed { i, g ->
                g ?: return@forEachIndexed
                drawLine(line, Offset(i * slot + 2, y(g)), Offset((i + 1) * slot - 2, y(g)), stroke, pathEffect = ChartStyle.dash(this))
            }
        }
        if (xRow > 0.dp) xAxis(t, labels.mapIndexed { i, s -> (i + 0.5f) * slot to s }, plotW, height.toPx() + 4.dp.toPx())
    }
}

/** Barres signées autour de zéro (balance énergétique : surplus au-dessus, déficit dessous), échelle symétrique. */
@Composable
fun SignedBarChart(
    values: List<Double?>, posColor: Color, negColor: Color, modifier: Modifier = Modifier, height: Dp = 140.dp,
    labels: List<String> = emptyList(), showAxis: Boolean = height >= ChartStyle.detailed, format: ((Double) -> String)? = null,
) {
    val maxAbs = values.filterNotNull().maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
    val step = if (showAxis) Scales.niceStep(maxAbs) else maxAbs
    val scale = Scale(-ceil(maxAbs / step - 1e-9) * step, ceil(maxAbs / step - 1e-9) * step, ceil(maxAbs / step - 1e-9) * step)
    val axisLabels = scale.ticks.map { v -> (format?.invoke(v) ?: scale.label(abs(v))).let { if (v > 0) "+$it" else if (v < 0) "−$it" else it } }
    val zero = Mf.colors.textTertiary
    val grid = Mf.colors.divider
    val t = axisText()
    val xRow = if (labels.any { it.isNotEmpty() }) 18.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + xRow)) {
        val labelH = t.measurer.measure("0", t.style).size.height.toFloat()
        val inset = if (showAxis) labelH / 2 else 0f
        val plotH = height.toPx() - 2 * inset
        val gutter = if (showAxis) yAxis(t, scale, axisLabels, inset, plotH, false, draw = true) else 0f
        val plotW = size.width - gutter
        if (showAxis) gridLines(scale, inset, plotW, plotH, grid)
        val mid = inset + plotH / 2
        drawLine(zero, Offset(0f, mid), Offset(plotW, mid), ChartStyle.grid.toPx())
        val n = values.size.coerceAtLeast(1)
        val slot = plotW / n
        val bw = min(slot * ChartStyle.BAR_FILL, ChartStyle.barMax.toPx())
        values.forEachIndexed { i, v ->
            if (v == null || v == 0.0) return@forEachIndexed
            val h = (abs(v) / scale.max * plotH / 2).toFloat().coerceAtLeast(min(bw, 2.dp.toPx()))
            val left = i * slot + (slot - bw) / 2
            drawRoundRect(if (v > 0) posColor else negColor, Offset(left, if (v > 0) mid - h else mid), Size(bw, h), CornerRadius(bw / 2))
        }
        if (xRow > 0.dp) xAxis(t, labels.mapIndexed { i, s -> (i + 0.5f) * slot to s }, plotW, height.toPx() + 4.dp.toPx())
    }
}

/** Cascade (progression d'objectif) : barre initiale, variations vers l'objectif ou à contresens, reste à faire. */
@Composable
fun WaterfallChart(start: Double, steps: List<Double>, remaining: Double, toward: (Double) -> Boolean, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val good = Mf.colors.accent
    val bad = Mf.colors.textTertiary
    val base = Mf.colors.text
    Canvas(modifier.fillMaxWidth().height(height)) {
        val total = max(abs(start), 0.1)
        val n = steps.size + 2
        val slot = size.width / n
        val bw = min(slot * 0.62f, 28.dp.toPx())
        val r = CornerRadius(min(bw / 4, 6.dp.toPx()))
        fun yOf(v: Double) = (size.height * (1 - v / total)).toFloat().coerceIn(0f, size.height)
        fun left(i: Int) = i * slot + (slot - bw) / 2
        drawRoundRect(base, Offset(left(0), yOf(abs(start))), Size(bw, size.height - yOf(abs(start))), r)
        var level = abs(start)
        steps.forEachIndexed { i, d ->
            val next = (level - (if (toward(d)) abs(d) else -abs(d))).coerceIn(0.0, total)
            val top = yOf(max(level, next)); val bot = yOf(min(level, next))
            drawRoundRect(if (toward(d)) good else bad, Offset(left(i + 1), top), Size(bw, (bot - top).coerceAtLeast(2.dp.toPx())), r)
            level = next
        }
        val rest = abs(remaining).coerceAtMost(total)
        drawRoundRect(base.copy(alpha = 0.35f), Offset(left(n - 1), yOf(rest)), Size(bw, size.height - yOf(rest)), r)
    }
}
