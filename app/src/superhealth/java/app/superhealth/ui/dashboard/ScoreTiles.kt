package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.DashCard
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightSummary
import app.superhealth.ui.charts.smooth
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.arcBrush
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.dataBrush
import app.superhealth.ui.theme.veilOf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Tuiles du tableau de bord (2.4, style Score, lignée WHOOP) : en-tête symbole + nom (détail coloré à droite), la valeur
 * en haut, le graphique de la mesure en bas sur toute la largeur. Petit = l'état d'un coup d'œil ; moyen = la même chose
 * avec le graphique qui raconte la journée ou la semaine (hypnogramme, pas heure par heure, courbe du cœur, plage d'effort).
 * Dégradés seulement sur les données, entre deux teintes voisines ; aucun halo.
 */

/** Ce que certaines tuiles dessinent en plus de [CompactData] : pas par heure, dernière nuit, Repos, plage d'effort. */
internal class TileExtra(
    val hourly: List<Double?>? = null,
    val night: NightSummary? = null,
    val rest: Int? = null,
    val effortRange: Pair<Double, Double>? = null,
    /** Cœur : FC au repos du jour (la tuile parle du repos, la courbe aussi). */
    val resting: Double? = null,
)

/** Données en plus d'une tuile, lues dans les mesures déjà chargées. */
@Composable
internal fun tileExtra(c: DashCard, h: HealthSnap): TileExtra {
    val w = h.watch
    return when (c) {
        DashCard.STEPS -> TileExtra(hourly = w.hourly.takeIf { it.isNotEmpty() }?.let(::hourlySteps))
        DashCard.SLEEP -> TileExtra(night = w.lastNight, rest = w.sleepScore?.roundToInt())
        DashCard.HEART -> TileExtra(resting = h.latest(Metric.RESTING_HR)?.second)
        DashCard.EFFORT -> {
            val form = app.superhealth.ui.wellbeing.rememberForm() as? app.superhealth.wellbeing.Form.Advice
            TileExtra(effortRange = form?.let { app.superhealth.wellbeing.Form.effortRange(it.verdict, w.efforts.dropLast(1)) })
        }
        else -> TileExtra()
    }
}

/** En-tête : pastille teintée, nom, détail à droite dans la couleur. */
@Composable
internal fun TileLabel(icon: ImageVector, title: String, hue: Color, trailing: String? = null) = CardHead(icon, title, hue, trailing)

private fun CompactData.hasChart() = history.count { it != null } >= 2

/** Valeur et unité de la tuile (le Cœur montre la FC au repos quand elle existe). */
private fun shown(c: DashCard, d: CompactData, x: TileExtra): Pair<String, String?> =
    if (c == DashCard.HEART && x.resting != null) formatNumber(x.resting) to tr("bpm")
    else if (c == DashCard.EFFORT && x.effortRange != null) d.value to "/ ${formatNumber(x.effortRange.first)}–${formatNumber(x.effortRange.second)}"
    else d.value to d.unit

private fun titleOf(c: DashCard, x: TileExtra) = when {
    c == DashCard.HEART && x.resting != null -> tr("Cœur au repos")
    c == DashCard.EFFORT -> tr("Effort du jour")
    else -> c.short()
}

/**
 * Petit (un tiers, carré), une seule logique pour toutes les cartes : avec un objectif, l'anneau et la valeur au centre ;
 * sans objectif, la valeur et sa courbe ; sans historique, le symbole et la valeur.
 */
@Composable
internal fun CompactBubble(c: DashCard, d: CompactData, modifier: Modifier, x: TileExtra = TileExtra()) {
    val hue = c.ink()
    val (value, unit) = shown(c, d, x)
    when {
        d.progress != null -> RingTile(modifier, d.onClick, hue, d.progress) { RingCenter(c.icon(), hue, value, unit) }
        d.hasChart() -> CurveTile(modifier, d.onClick, c.icon(), c.tiny(), hue, value, unit) {
            if (d.bars) DayBars(d.history, d.goal, hue, it) else TileLine(d.history, hue, it)
        }
        else -> RingTile(modifier, d.onClick, hue, null, ring = false) { BigChip(c.icon(), hue); Spacer(Modifier.height(6.dp)); RingCenter(null, hue, value, unit) }
    }
}

/** Moyen générique : l'anatomie commune (voir MediumShell) ; la courbe ou les barres de la carte, sinon sa progression, sinon son symbole. */
@Composable
internal fun CompactMedium(c: DashCard, d: CompactData, modifier: Modifier, x: TileExtra = TileExtra()) {
    val hue = c.ink()
    val (v, u) = shown(c, d, x)
    val delta = d.delta(c)?.let { (n, t) -> (if (n < 0) "−" else "+") + t }
    val note = d.label.takeIf { !it.equals(c.short(), ignoreCase = true) && !it.equals(tr(c.label), ignoreCase = true) }
    val detail = d.progress?.takeIf { d.unit != "%" && !d.value.endsWith("%") }?.let { "${(it * 100).roundToInt()} %" }
    MediumShell(modifier, d.onClick, c.icon(), titleOf(c, x), hue, detail, value = v, unit = u,
        caption = if (v != "—") delta ?: note else if (c == DashCard.LOAD) tr("Pas assez de données") else tr("Pas de données")) {
        when {
            d.week != null -> WeekDots(d.week, hue, Modifier.padding(bottom = 4.dp))
            d.hasChart() -> if (d.bars) DayBars(d.history, d.goal, hue, Modifier.fillMaxSize()) else TileLine(d.history, hue, Modifier.fillMaxSize())
            d.progress != null -> ProgressLine(d.progress, hue, Modifier.fillMaxWidth().padding(bottom = 4.dp))
            d.bars && d.history.isNotEmpty() -> DayBars(d.history, d.goal, hue, Modifier.fillMaxSize())
            v == "—" -> EmptyDots()
            else -> BigChip(c.icon(), hue, Modifier.align(Alignment.BottomEnd))
        }
    }
}

// ---- Graphiques ----------------------------------------------------------------------------------------------------

/** Cadran : anneau entier ([span] 360) ou jauge ouverte en bas (270°), dégradé le long du trait ; sans [stroke], trait de 9,5 % du diamètre. */
@Composable
internal fun Dial(f: Float, hue: Color, modifier: Modifier, stroke: Dp?, span: Float = 270f) {
    val track = softTrack()
    Canvas(modifier) {
        val w = stroke?.toPx() ?: (size.minDimension * 0.095f); val r = size.minDimension / 2 - w / 2
        val tl = Offset(center.x - r, center.y - r); val box = Size(2 * r, 2 * r)
        val from = if (span == 360f) -90f else 90f + (360f - span) / 2
        drawArc(track, from, span, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
        val sweep = span * f.coerceIn(0f, 1f)
        if (sweep > 0f) rotate(from) { drawArc(arcBrush(hue, sweep / 360f), 0f, sweep, false, tl, box, style = Stroke(w, cap = StrokeCap.Round)) }
    }
}

/** Couleur des points « vides » (heures sans pas, heures à venir) : la piste, jamais une teinte chaude assombrie. */
@Composable
private fun emptyDot(): Color = Mf.colors.text.copy(alpha = 0.16f)

/** Pas heure par heure : barres en dégradé jusqu'à l'heure en cours, points pour les heures vides et à venir. */
@Composable
internal fun HourBars(values: List<Double?>, hue: Color, now: Int, modifier: Modifier) {
    val dot = emptyDot()
    Canvas(modifier) {
        val n = values.size; val gap = 2.dp.toPx(); val bw = ((size.width - gap * (n - 1)) / n).coerceAtMost(8.dp.toPx())
        val slot = size.width / n
        val max = values.maxOf { it ?: 0.0 }.coerceAtLeast(1.0)
        values.forEachIndexed { i, v ->
            val cx = i * slot + slot / 2
            if (i > now || v == null || v <= 0) drawCircle(dot, bw / 2.4f, Offset(cx, size.height - bw / 2))
            else {
                val h = maxOf(bw, (size.height * v / max).toFloat())
                drawRoundRect(dataBrush(hue, Offset(0f, size.height), Offset(0f, size.height - h)), Offset(cx - bw / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
    }
}

/** Barres des derniers jours : objectif atteint en dégradé, sinon atténué (gris neutre en sombre). */
@Composable
internal fun DayBars(values: List<Double?>, goal: Double?, hue: Color, modifier: Modifier) {
    val dark = Mf.colors.dark
    val below = if (dark) Mf.colors.textTertiary else hue.copy(alpha = 0.4f)
    val dot = emptyDot()
    Canvas(modifier) {
        val n = values.size; val slot = size.width / n; val bw = (slot * 0.56f).coerceAtMost(10.dp.toPx())
        val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.coerceAtLeast(1e-9) ?: 1.0
        values.forEachIndexed { i, v ->
            val cx = i * slot + slot / 2
            if (v == null || v <= 0) drawCircle(dot, bw / 2.4f, Offset(cx, size.height - bw / 2))
            else {
                val h = maxOf(bw, (size.height * v / max).toFloat())
                val brush = if (goal == null || v >= goal) dataBrush(hue, Offset(0f, size.height), Offset(0f, size.height - h)) else SolidColor(below)
                drawRoundRect(brush, Offset(cx - bw / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
    }
}

/** Courbe lissée, aire douce dessous (neutre en sombre pour une teinte chaude), point cerclé sur la dernière valeur. */
@Composable
internal fun TileLine(values: List<Double?>, hue: Color, modifier: Modifier) {
    // courbe fidèle aux valeurs ; lissée seulement quand les points sont très nombreux
    val vals = values.dropWhile { it == null }
    val inner = Mf.colors.card
    val fill = veilOf(hue).copy(alpha = if (Mf.colors.dark) 0.3f else 0.2f)
    Canvas(modifier) {
        val present = vals.filterNotNull(); if (present.size < 2) return@Canvas
        val lo = present.min(); val hi = present.max(); val pad = 5.dp.toPx()
        fun xOf(i: Int) = pad + (size.width - 2 * pad) * i / (vals.size - 1).coerceAtLeast(1)
        fun yOf(v: Double) = pad + (size.height - 2 * pad) * (if (hi > lo) ((hi - v) / (hi - lo)).toFloat() else 0.5f)
        val pts = vals.withIndex().mapNotNull { (i, v) -> v?.let { Offset(xOf(i), yOf(it)) } }
        val line = Path().apply { if (pts.size > 40) smooth(pts) else { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } } }
        drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() },
            Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0f))))
        drawPath(line, dataBrush(hue, Offset(0f, 0f), Offset(size.width, 0f)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(hue, 4.5.dp.toPx(), pts.last()); drawCircle(inner, 2.dp.toPx(), pts.last())
    }
}

/** Barre d'objectif en dégradé. */
@Composable
internal fun ProgressLine(f: Float, hue: Color, modifier: Modifier) {
    val track = emptyDot()
    Canvas(modifier.height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * f.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(dataBrush(hue, Offset.Zero, Offset(w, 0f)), size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}

/** Effort du jour : barre remplie jusqu'à l'effort (sur 100), plage conseillée cerclée. */
@Composable
internal fun RangeBar(f: Float, range: Pair<Double, Double>, hue: Color, modifier: Modifier) {
    val track = softTrack()
    Canvas(modifier.height(10.dp)) {
        val h = 8.dp.toPx(); val y = (size.height - h) / 2; val r = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
        val w = size.width * f.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(dataBrush(hue, Offset.Zero, Offset(w, 0f)), Offset(0f, y), Size(maxOf(w, h), h), r)
        val a = (size.width * range.first / 100).toFloat(); val b = (size.width * range.second / 100).toFloat().coerceAtLeast(a + h)
        drawRoundRect(hue, Offset(a, 1.dp.toPx() / 2), Size(b - a, size.height - 1.dp.toPx()), CornerRadius(size.height / 2), style = Stroke(1.5.dp.toPx()))
    }
}

/** Niveau d'une phase sur l'hypnogramme : éveil en haut, profond en bas. */
private fun level(k: ActivityKind) = when (k) { ActivityKind.AWAKE_SLEEP -> 0; ActivityKind.REM_SLEEP -> 1; ActivityKind.DEEP_SLEEP -> 3; else -> 2 }

/** Hypnogramme en marches arrondies, couleur de chaque phase en dégradé vertical, aire douce dessous. */
@Composable
internal fun Hypnogram(n: NightSummary, modifier: Modifier) {
    val cols = listOf(Base.Pink, Base.Cyan, Base.Purple, Base.Indigo)
    val fill = HealthHues.Sleep.copy(alpha = if (Mf.colors.dark) 0.22f else 0.14f)
    // phases voisines de même niveau fusionnées
    val runs = androidx.compose.runtime.remember(n) {
        n.phases.sortedBy { it.start }.fold(mutableListOf<Triple<Int, Long, Long>>()) { acc, p ->
            val l = level(p.kind)
            val last = acc.lastOrNull()
            if (last != null && last.first == l) acc[acc.size - 1] = Triple(l, last.second, maxOf(last.third, p.end)) else acc += Triple(l, p.start, p.end)
            acc
        }
    }
    Canvas(modifier) {
        if (runs.isEmpty()) return@Canvas
        val stroke = 2.5.dp.toPx(); val pad = stroke * 1.5f; val span = (n.wakeMs - n.bedMs).coerceAtLeast(1).toFloat()
        fun x(t: Long) = size.width * (t - n.bedMs) / span
        fun y(l: Int) = pad + (size.height - 2 * pad) * l / 3f
        val line = Path()
        runs.forEachIndexed { i, (l, s, e) ->
            val yy = y(l); val xs = x(s)
            if (i == 0) line.moveTo(xs, yy) else {
                val py = y(runs[i - 1].first)
                val r = minOf(4.dp.toPx(), (x(e) - xs) / 2, (xs - x(runs[i - 1].second)) / 2, abs(yy - py) / 2).coerceAtLeast(0f)
                val d = if (yy > py) r else -r
                line.lineTo(xs - r, py); line.quadraticTo(xs, py, xs, py + d); line.lineTo(xs, yy - d); line.quadraticTo(xs, yy, xs + r, yy)
            }
            if (i == runs.lastIndex) line.lineTo(x(e), yy)
        }
        drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0f))))
        drawPath(line, Brush.verticalGradient(cols, pad, size.height - pad), style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

