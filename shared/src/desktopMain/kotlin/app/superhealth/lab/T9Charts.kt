package app.superhealth.lab

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Tour 9 : graphiques communs aux trois styles. Chaque fonction ne prend que des couleurs : le style décide de tout.
 */

/** Arc (anneau entier ou cadran 270°) ; dégradé qui suit l'arc si [b] diffère de [a]. */
fun DrawScope.arcT(c: Offset, r: Float, w: Float, f: Float, a: Color, b: Color, track: Color, from: Float = -90f, span: Float = 360f, cap: StrokeCap = StrokeCap.Round) {
    val tl = Offset(c.x - r, c.y - r); val sz = Size(2 * r, 2 * r)
    drawArc(track, from, span, false, tl, sz, style = Stroke(w, cap = cap))
    if (f <= 0f) return
    val sweep = span * f.coerceAtMost(1f)
    if (a == b) { drawArc(a, from, sweep, false, tl, sz, style = Stroke(w, cap = cap)); return }
    rotate(from, c) {
        val e = sweep / 360f
        drawArc(Brush.sweepGradient(0f to a, e to b, minOf(e + 0.05f, 0.98f) to b, 1f to a, center = c), 0f, sweep, false, tl, sz, style = Stroke(w, cap = cap))
    }
}

@Composable
fun Dial(f: Float, a: Color, b: Color, track: Color, modifier: Modifier, stroke: Dp = 10.dp, span: Float = 270f) =
    Canvas(modifier) { val w = stroke.toPx(); arcT(center, size.minDimension / 2 - w / 2, w, f, a, b, track, if (span == 360f) -90f else 90f + (360f - span) / 2, span) }

/** Hypnogramme en marches arrondies, couleur de chaque phase en dégradé vertical. */
@Composable
fun HypnoT(modifier: Modifier, awake: Color, rem: Color, light: Color, deep: Color, area: Color? = null, stroke: Dp = 2.5.dp) {
    Canvas(modifier) {
        val total = night4.sumOf { it.second }.toFloat(); val pad = stroke.toPx() * 1.5f; val r = 4.dp.toPx()
        fun y(st: Int) = pad + (size.height - 2 * pad) * (4 - st) / 3f
        val line = Path(); var x = 0f
        night4.forEachIndexed { i, (st, m) ->
            val w = size.width * m / total; val yy = y(st)
            if (i == 0) line.moveTo(0f, yy) else {
                val py = y(night4[i - 1].first); val d = if (yy > py) r else -r
                line.lineTo(x - r, py); line.quadraticTo(x, py, x, py + d); line.lineTo(x, yy - d); line.quadraticTo(x, yy, x + r, yy)
            }
            x += w
        }
        line.lineTo(size.width, y(night4.last().first))
        if (area != null) drawPath(Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Brush.verticalGradient(listOf(area, Color.Transparent)))
        drawPath(line, Brush.verticalGradient(listOf(awake, rem, light, deep), pad, size.height - pad), style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Phases en barre empilée (profond, léger, paradoxal, éveil). */
@Composable
fun PhaseBarT(modifier: Modifier, cols: List<Color>, gap: Dp = 2.dp) {
    Canvas(modifier) {
        val tot = listOf(1, 2, 3, 4).map { k -> night4.filter { it.first == k }.sumOf { it.second }.toFloat() }; val sum = tot.sum(); val g = gap.toPx()
        var x = 0f; val h = size.height
        tot.forEachIndexed { i, t -> val w = (size.width - 3 * g) * t / sum; drawRoundRect(cols[i], Offset(x, 0f), Size(w, h), CornerRadius(h / 2)); x += w + g }
    }
}

/** Pas par heure jusqu'à 16 h ; heures à venir en points. */
@Composable
fun StepBarsT(modifier: Modifier, a: Color, b: Color, future: Color, upTo: Int = 24) {
    Canvas(modifier) {
        val n = upTo; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = steps4.max().toFloat()
        steps4.take(n).forEachIndexed { i, v ->
            val x = i * (bw + gap)
            if (i > 16) drawCircle(future, bw / 2.6f, Offset(x + bw / 2, size.height - bw / 2))
            else { val h = maxOf(bw, size.height * v / max)
                drawRoundRect(if (a == b) SolidColor(a) else Brush.verticalGradient(listOf(b, a), size.height - h, size.height), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2), alpha = if (v == 0) 0.35f else 1f) }
        }
    }
}

/** Courbe lissée avec aire ; [dot] marque la dernière valeur. */
@Composable
fun LineT(values: List<Float>, modifier: Modifier, a: Color, b: Color, area: Color?, dotInner: Color? = null, stroke: Dp = 2.5.dp) {
    Canvas(modifier) {
        val lo = values.min(); val hi = values.max(); val span = maxOf(hi - lo, 1e-3f); val pad = 5.dp.toPx()
        val pts = values.mapIndexed { i, v -> Offset(pad + (size.width - 2 * pad) * i / (values.size - 1), pad + (size.height - 2 * pad) * (hi - v) / span) }
        val line = smooth(pts)
        if (area != null) drawPath(smooth(pts).apply { lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }, Brush.verticalGradient(listOf(area, Color.Transparent)))
        drawPath(line, if (a == b) SolidColor(a) else Brush.horizontalGradient(listOf(b, a)), style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        drawCircle(a, 4.5.dp.toPx(), pts.last()); if (dotInner != null) drawCircle(dotInner, 2.dp.toPx(), pts.last())
    }
}

/** Corps de l'app : silhouette [base], muscles [muscle], muscles du jour en dégradé [a]→[b]. */
@Composable
internal fun BodyT(d: Day, modifier: Modifier, base: Color, muscle: Color, a: Color, b: Color, bone: Color = base) {
    BodyCanvas(d.side, d.from, d.to, modifier) { art, _, y0, hh ->
        art.parts.forEach { (id, p) -> when { id == "body" -> drawPath(p, base); id == "bone" -> drawPath(p, bone); id !in d.target -> drawPath(p, muscle) } }
        art.parts.filter { it.first in d.target }.forEach { (_, p) -> drawPath(p, if (a == b) SolidColor(a) else Brush.verticalGradient(listOf(a, b), y0, y0 + hh)) }
    }
}

/** Tracé d'une sortie (forme fixe). */
@Composable
fun RouteT(modifier: Modifier, a: Color, b: Color, start: Color, stroke: Dp = 3.dp) {
    Canvas(modifier) {
        val pts = listOf(0.08f to 0.78f, 0.2f to 0.52f, 0.16f to 0.26f, 0.36f to 0.12f, 0.55f to 0.3f, 0.7f to 0.14f, 0.92f to 0.3f, 0.86f to 0.62f, 0.62f to 0.7f, 0.46f to 0.88f, 0.22f to 0.84f)
            .map { Offset(it.first * size.width, it.second * size.height) }
        drawPath(smooth(pts), if (a == b) SolidColor(a) else Brush.linearGradient(listOf(a, b)), style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
        drawCircle(start, 4.dp.toPx(), pts.first()); drawCircle(a, 4.dp.toPx(), pts.last(), style = Stroke(2.dp.toPx()))
    }
}

val week7 = listOf("L" to 48, "M" to 52, "M" to 0, "J" to 29, "V" to 0, "S" to -1, "D" to -1)
val recent3 = listOf(Triple("Course à pied", "Hier · 28:41 · 5:31 /km", "5,2 km"), Triple("Pull", "Mardi · 52 min · 18 séries", "12,3 t"), Triple("Vélo", "Lundi · 48 min · 26 km/h", "21,0 km"))
