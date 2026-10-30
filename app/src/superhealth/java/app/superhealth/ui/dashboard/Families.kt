package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightSummary
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.nutrition.ui.components.FoodIcon
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.macros
import app.superhealth.nutrition.ui.minuteOf
import app.superhealth.nutrition.ui.screens.MealSlot
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.training.ui.components.XRayBody
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.charts.smooth
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.health.RouteTrace
import app.superhealth.ui.health.clock
import app.superhealth.ui.health.pace
import app.superhealth.ui.health.routeOf
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Hues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.arcBrush
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.theme.partnerOf
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.wellbeing.color
import app.superhealth.wellbeing.Form
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Widgets du tableau de bord (planches Score) : chaque carte principale existe dans les quatre tailles, avec la même anatomie.
 *   S (carré)    : nom, LA valeur, un seul repère visuel.
 *   M (moyen)    : valeur et une ligne d'explication, le graphique à droite (ou dessous à demi-largeur).
 *   W (bandeau)  : une ligne, pastille, nom et valeur, repère compact à droite.
 *   L (grand)    : le détail, valeur, chiffres annexes, graphique complet, légende.
 * Cartes neutres, couleur seulement sur les données, dégradés entre deux teintes voisines.
 */

/** Deux tons d'une donnée (départ et arrivée du dégradé) ; les mêmes en aplat. */
internal class Tone(val a: Color, val b: Color)

/** Couleurs des données : le jeu choisi (Réglages > Apparence), un seul ton en aplat, la teinte et sa voisine en dégradé. */
internal object Sc {
    private fun tone(c: Color) = if (Hues.gradient) Tone(c, partnerOf(c)) else Tone(c, c)
    private val b get() = app.superhealth.ui.theme.Base
    private val h get() = app.superhealth.ui.theme.HealthHues
    val forme get() = tone(b.Green)
    val repos get() = tone(h.Sleep)
    val effort get() = tone(b.Blue)
    val kcal get() = tone(app.superhealth.ui.theme.CaloriesColor)
    val sleep get() = tone(h.Sleep)
    val heart get() = tone(h.Heart)
    val steps get() = tone(h.Activity)
    val weight get() = tone(b.Pink)
    val prot get() = app.superhealth.ui.theme.ProteinColor
    val fat get() = app.superhealth.ui.theme.FatColor
    val carb get() = app.superhealth.ui.theme.CarbsColor
    val good get() = b.Green
    /** Charge par zones, comme une jauge de récupération : basse en rouge, moyenne en jaune, haute en vert. */
    fun charge(v: Int?) = tone(when { v == null -> b.Green; v < 34 -> b.Red; v < 67 -> b.Yellow; else -> b.Green })
}

/** Couleur des valeurs de la tuile en cours. */
private val LocalInk = staticCompositionLocalOf<Color?> { null }

/** Carte moyenne à demi-largeur (deux moyennes côte à côte) : le graphique passe sous la valeur. */
internal val LocalCardHalf = staticCompositionLocalOf { false }

private val sub @Composable get() = Mf.colors.textSecondary
private val faint @Composable get() = Mf.colors.chip

// ---- Anatomie ------------------------------------------------------------------------------------------------------

/** Texte d'une ligne ; [num] : chiffres en Inter Display. */
@Composable
private fun Tq(text: String, size: TextUnit, color: Color? = null, weight: FontWeight = FontWeight.Normal, num: Boolean = false, modifier: Modifier = Modifier, brush: Brush? = null) =
    Text(text, modifier, style = (if (num) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium).let { if (brush != null) it.copy(brush = brush) else it },
        color = if (brush != null) Color.Unspecified else color ?: Mf.colors.text, fontSize = size, lineHeight = size * 1.2f, fontWeight = weight, maxLines = 1,
        overflow = TextOverflow.Ellipsis, letterSpacing = if (num) (-0.02 * size.value).sp else 0.sp)

/** Valeur : chiffre en grand, unité plus petite en gris sur la même ligne de base ; [v2] pour « 7 h 32 ». */
@Composable
private fun V(v: String, unit: String? = null, size: TextUnit = 24.sp, v2: String? = null, unit2: String? = null, color: Color? = null) {
    val us = maxOf(12f, size.value * 0.5f).sp
    val ink = color ?: LocalInk.current
    Row(verticalAlignment = Alignment.Bottom) {
        Tq(v, size, ink, FontWeight.SemiBold, true, Modifier.alignByBaseline())
        if (unit != null) Tq(" $unit" + if (v2 != null) " " else "", us, sub, FontWeight.Medium, modifier = Modifier.alignByBaseline())
        if (v2 != null) Tq(v2, size, ink, FontWeight.SemiBold, true, Modifier.alignByBaseline())
        if (unit2 != null) Tq(" $unit2", us, sub, FontWeight.Medium, modifier = Modifier.alignByBaseline())
    }
}

/** Durée en heures et minutes : « 7 h 32 ». */
@Composable
private fun Hm(min: Double?, size: TextUnit, unit2: String? = null) {
    if (min == null) { V("—", null, size); return }
    val t = min.roundToInt()
    V("${t / 60}", "h", size, "%02d".format(t % 60), unit2)
}

@Composable
private fun Head(icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c) = CardHead(icon, label, c, trailing, tc)

@Composable
private fun Tile(c: Color, modifier: Modifier, onClick: () -> Unit, hPad: Dp, vPad: Dp, center: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalInk provides c.onContainer()) {
        Column(modifier.clip(RoundedCornerShape(CardRadius)).dashCardBackground(c).cardClick(onClick).padding(horizontal = hPad, vertical = vPad),
            verticalArrangement = if (center) Arrangement.Center else Arrangement.Top, content = content)
    }

/** Petit à objectif : l'anneau remplit la tuile, le contenu au centre ; [ring] faux : le contenu seul. */
@Composable
internal fun RingTile(m: Modifier, onClick: () -> Unit, hue: Color, f: Float?, ring: Boolean = true, inner0: @Composable ColumnScope.() -> Unit) {
    val track = Mf.colors.chip
    Column(m.square().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).cardClick(onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (ring) Canvas(Modifier.fillMaxSize()) {
                // cercle sur le plus petit côté, centré : le bloc peut être plus haut que large
                val d = size.minDimension; val stroke = d * 0.085f
                val box = Size(d - stroke, d - stroke); val tl = Offset((size.width - d) / 2 + stroke / 2, (size.height - d) / 2 + stroke / 2)
                drawArc(track, 0f, 360f, false, tl, box, style = Stroke(stroke))
                f?.takeIf { it > 0f }?.let { p ->
                    val v = p.coerceAtMost(1f)
                    rotate(-90f) { drawArc(arcBrush(hue, v), 0f, 360f * v, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round)) }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = inner0)
        }
    }
}

/** Centre d'un anneau : symbole, valeur en couleur, unité. */
@Composable
internal fun RingCenter(icon: ImageVector?, hue: Color, value: String, unit: String?) {
    if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = hue)
    Text(value, style = MaterialTheme.typography.titleMedium, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    unit?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp)) }
}

/** Pastille teintée, grande : le symbole d'un petit sans anneau ni courbe. */
@Composable
internal fun BigChip(icon: ImageVector, hue: Color) {
    val (bg, tint) = app.superhealth.ui.theme.dashChipColors(hue)
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = tint) }
}

/** Petit sans objectif : pastille et nom, valeur en couleur, sa courbe en bas. */
@Composable
internal fun CurveTile(m: Modifier, onClick: () -> Unit, icon: ImageVector, label: String, hue: Color, value: String, unit: String?, chart: @Composable (Modifier) -> Unit) {
    val (bg, tint) = app.superhealth.ui.theme.dashChipColors(hue)
    Column(m.square().clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).cardClick(onClick).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = tint) }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = hue.onContainer(), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).alignByBaseline())
            if (!unit.isNullOrBlank()) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(4.dp))
        chart(Modifier.fillMaxWidth().weight(1f))
    }
}

/** Moyen : valeur à gauche, graphique à droite ; à demi-largeur, le graphique sous la valeur. [chartW] nul : tout à gauche. */
@Composable
private fun Medium(m: Modifier, onClick: () -> Unit, icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c, chartW: Dp = 108.dp, chartH: Dp = 58.dp,
                   left: @Composable ColumnScope.() -> Unit, right: @Composable BoxScope.() -> Unit = {}) {
    val half = LocalCardHalf.current
    Tile(c, m.fillMaxWidth().heightIn(min = if (half) 160.dp else 132.dp), onClick, 16.dp, 14.dp) {
        Head(icon, label, c, trailing, tc)
        if (half || chartW == 0.dp) {
            Spacer(Modifier.height(4.dp))
            Column(if (chartW == 0.dp) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(), verticalArrangement = if (chartW == 0.dp) Arrangement.Bottom else Arrangement.Top, content = left)
            if (chartW != 0.dp) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(chartH), contentAlignment = Alignment.BottomCenter, content = right)
            }
        } else Row(Modifier.weight(1f).padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, content = left)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(chartW).fillMaxHeight(), contentAlignment = Alignment.BottomCenter, content = right)
        }
    }
}

/** Bandeau : pastille teintée, valeur en couleur et nom dessous, repère à droite. */
@Composable
private fun Wide(m: Modifier, onClick: () -> Unit, icon: ImageVector, c: Color, label: String, value: @Composable () -> Unit, rightW: Dp = 130.dp, right: @Composable BoxScope.() -> Unit) {
    val (chipBg, chipTint) = app.superhealth.ui.theme.dashChipColors(c)
    Tile(c, m.fillMaxWidth().heightIn(min = 72.dp), onClick, 12.dp, 10.dp, center = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(chipBg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = chipTint) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { value(); Tq(label, 12.sp, sub, modifier = Modifier.padding(top = 1.dp)) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(rightW), contentAlignment = Alignment.CenterEnd, content = right)
        }
    }
}

@Composable
private fun Large(m: Modifier, onClick: () -> Unit, icon: ImageVector, label: String, c: Color, trailing: String? = null, tc: Color = c, content: @Composable ColumnScope.() -> Unit) =
    Tile(c, m.fillMaxWidth(), onClick, 16.dp, 16.dp) { Head(icon, label, c, trailing, tc); Spacer(Modifier.height(12.dp)); content() }

/** Colonne « libellé / valeur » des grandes cartes. */
@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, c: Color? = null) =
    Column(modifier) { Tq(label, 12.sp, sub); Tq(value, 16.sp, c, FontWeight.SemiBold, true) }

@Composable
private fun Bar(f: Float, t: Tone, modifier: Modifier, h: Dp = 10.dp) {
    val track = faint
    Canvas(modifier.height(h)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (f > 0f) {
            val w = maxOf(size.height, size.width * f.coerceAtMost(1f))
            drawRoundRect(Brush.horizontalGradient(listOf(t.a, t.b), 0f, w), size = Size(w, size.height), cornerRadius = r)
        }
    }
}

/** Écart : flèche dans une pastille et texte ; [good] null : neutre (monter ou baisser n'est ni bon ni mauvais en soi). */
@Composable
private fun Delta(text: String, up: Boolean, good: Boolean? = null) {
    val c = when (good) { true -> Sc.good; false -> Sc.heart.a; null -> sub }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(c.copy(alpha = if (Mf.colors.dark) 0.16f else 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(12.dp), tint = c)
        }
        Tq(" $text", 12.sp, c, FontWeight.SemiBold)
    }
}

// ---- Graphiques ----------------------------------------------------------------------------------------------------

/** Cadran (jauge ouverte 270° ou anneau 360°), dégradé des deux tons le long du trait. */
@Composable
internal fun DialT(f: Float, t: Tone, modifier: Modifier, stroke: Dp, span: Float = 270f) {
    val track = faint
    Canvas(modifier) {
        val w = stroke.toPx(); val r = size.minDimension / 2 - w / 2
        val tl = Offset(center.x - r, center.y - r); val box = Size(2 * r, 2 * r)
        val from = if (span == 360f) -90f else 90f + (360f - span) / 2
        drawArc(track, from, span, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
        val sweep = span * f.coerceIn(0f, 1f)
        if (sweep > 0f) rotate(from) {
            val e = (sweep / 360f).coerceAtMost(0.98f)
            drawArc(Brush.sweepGradient(0f to t.a, e to t.b, minOf(e + 0.01f, 1f) to t.a), 0f, sweep, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** Phases de la nuit en barre empilée (profond, léger, paradoxal, éveil). */
@Composable
private fun PhaseBar(n: NightSummary, cols: List<Color>, modifier: Modifier) = Canvas(modifier.height(8.dp)) {
    val parts = listOf(n.deepMin, n.lightMin, n.remMin, n.awakeMin); val sum = parts.sum().coerceAtLeast(1.0); val g = 2.dp.toPx()
    val avail = size.width - g * (parts.count { it > 0 } - 1)
    var x = 0f
    parts.forEachIndexed { i, m ->
        if (m > 0) { val w = (avail * m / sum).toFloat(); drawRoundRect(cols[i], Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2)); x += w + g }
    }
}

/** Pas par heure : barres jusqu'à l'heure en cours, points pour les heures vides et à venir. */
@Composable
private fun StepBars(values: List<Double?>, t: Tone, now: Int, modifier: Modifier) {
    val dot = faint
    Canvas(modifier) {
        val n = values.size; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val max = values.maxOf { it ?: 0.0 }.coerceAtLeast(1.0)
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            if (i > now || v == null || v <= 0) drawCircle(dot, bw / 2.4f, Offset(x + bw / 2, size.height - bw / 2))
            else {
                val h = maxOf(bw, (size.height * v / max).toFloat())
                drawRoundRect(Brush.verticalGradient(listOf(t.b, t.a), size.height - h, size.height), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
    }
}

/** Jours en barres face à l'objectif (pointillés) : objectif atteint en couleur, sinon gris ; vides en points. */
@Composable
private fun GoalBars(values: List<Double?>, goal: Double?, t: Tone, modifier: Modifier) {
    val below = if (Mf.colors.dark) Color(0xFF3A3F47) else Color(0xFFD7DAE0)
    val dash = sub; val dot = faint
    Canvas(modifier) {
        val max = (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.times(1.08)?.coerceAtLeast(1e-6) ?: 1.0
        val n = values.size; val slot = size.width / n; val bw = minOf(slot * 0.56f, 10.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - bw) / 2
            if (v == null || v <= 0) drawCircle(dot, bw / 2.4f, Offset(x + bw / 2, size.height - bw / 2))
            else {
                val h = maxOf(bw, (size.height * v / max).toFloat())
                drawRoundRect(if (goal == null || v >= goal) Brush.verticalGradient(listOf(t.b, t.a), size.height - h, size.height) else SolidColor(below),
                    Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        if (goal != null) {
            val gy = (size.height * (1 - goal / max)).toFloat()
            drawLine(dash, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        }
    }
}

/** Courbe lissée, aire [area] dessous, point cerclé sur la dernière valeur. */
@Composable
private fun LineT(values: List<Double?>, t: Tone, modifier: Modifier, area: Boolean = false, stroke: Dp = 2.dp) {
    val inner = Mf.colors.card
    val fill = app.superhealth.ui.theme.veilOf(t.a).copy(alpha = 0.2f)
    val vals = values.dropWhile { it == null }
    Canvas(modifier) {
        val present = vals.filterNotNull(); if (present.size < 2) return@Canvas
        val lo = present.min(); val hi = present.max(); val pad = 5.dp.toPx()
        val pts = vals.withIndex().mapNotNull { (i, v) -> v?.let {
            Offset(pad + (size.width - 2 * pad) * i / (vals.size - 1).coerceAtLeast(1), pad + (size.height - 2 * pad) * (if (hi > lo) ((hi - it) / (hi - lo)).toFloat() else 0.5f)) } }
        // fidèle aux valeurs ; lissée seulement quand les points sont très nombreux
        val line = Path().apply { if (pts.size > 60) smooth(pts) else { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } } }
        if (area) drawPath(Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() },
            Brush.verticalGradient(listOf(fill, Color.Transparent)))
        drawPath(line, Brush.horizontalGradient(listOf(t.b, t.a)), style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(t.a, 4.5.dp.toPx(), pts.last()); drawCircle(inner, 2.dp.toPx(), pts.last())
    }
}

/** Où tombe la valeur dans sa plage habituelle : piste, plage en relief, point. */
@Composable
private fun RangeDot(v: Double, lo: Double, hi: Double, min: Double, max: Double, c: Color, modifier: Modifier) {
    val track = faint; val inner = Mf.colors.card; val dark = Mf.colors.dark
    Canvas(modifier.height(12.dp)) {
        fun x(t: Double) = (size.width * ((t - min) / (max - min)).coerceIn(0.0, 1.0)).toFloat()
        val y = size.height / 2
        drawRoundRect(track, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(if (dark) Color(0xFF4A5059) else c.copy(alpha = 0.35f), Offset(x(lo), y - 3.dp.toPx()), Size(x(hi) - x(lo), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawCircle(inner, 6.dp.toPx(), Offset(x(v), y)); drawCircle(c, 4.5.dp.toPx(), Offset(x(v), y))
    }
}

/** Courbe posée sur sa plage habituelle (bande) ; [dots] : un point par valeur. */
@Composable
private fun BandLine(values: List<Double>, lo: Double, hi: Double, t: Tone, modifier: Modifier, dots: Boolean) {
    val band = app.superhealth.ui.theme.veilOf(t.a).copy(alpha = if (Mf.colors.dark) 0.14f else 0.08f); val inner = Mf.colors.card
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val m = (hi - lo).coerceAtLeast(1.0) * 0.4
        val ylo = minOf(values.min(), lo) - m; val yhi = maxOf(values.max(), hi) + m
        fun y(x: Double) = (size.height * (yhi - x) / (yhi - ylo)).toFloat()
        drawRoundRect(band, Offset(0f, y(hi)), Size(size.width, y(lo) - y(hi)), CornerRadius(6.dp.toPx()))
        val pts = values.mapIndexed { i, x -> Offset(size.width * (i + 0.5f) / values.size, y(x)) }
        drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, Brush.horizontalGradient(listOf(t.b, t.a)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (dots) pts.forEach { drawCircle(inner, 3.5.dp.toPx(), it); drawCircle(t.a, 2.5.dp.toPx(), it) }
        else drawCircle(t.a, 4.dp.toPx(), pts.last())
    }
}

/** Sept jours en barres (minutes d'activité) ; points pour les jours vides. */
@Composable
private fun WeekBars(minutes: List<Long>, todayIdx: Int, start: LocalDate, t: Tone, modifier: Modifier, h: Dp, letters: Boolean = true, barW: Dp = 7.dp) {
    val dot = faint; val max = minutes.max().coerceAtLeast(1L)
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        minutes.forEachIndexed { i, m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(barW).height(h)) {
                    val w = size.width
                    if (m > 0) { val bh = maxOf(w, size.height * m / max); drawRoundRect(Brush.verticalGradient(listOf(t.b, t.a), size.height - bh, size.height), Offset(0f, size.height - bh), Size(w, bh), CornerRadius(w / 2)) }
                    else drawCircle(dot, w / 2, Offset(w / 2, size.height - w / 2))
                }
                if (letters) Tq(dayLetter(start.plusDays(i.toLong())), 12.sp, if (i == todayIdx) null else sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Effort : cadran avec la zone conseillée en relief. */
@Composable
private fun EffortDial(f: Float, range: Pair<Double, Double>?, size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    val t = Sc.effort
    Box(modifier.size(size)) {
        DialT(f, t, Modifier.fillMaxSize(), stroke)
        if (range != null) Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx(); val r = this.size.minDimension / 2 - w / 2 + w * 0.9f
            drawArc(t.b, 135f + 270f * (range.first / 100).toFloat(), 270f * ((range.second - range.first) / 100).toFloat().coerceAtLeast(0.02f), false,
                Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun EffortBar(f: Float, range: Pair<Double, Double>?, modifier: Modifier) {
    val t = Sc.effort; val track = faint
    Canvas(modifier.height(10.dp)) {
        val h = 8.dp.toPx(); val y = (size.height - h) / 2; val r = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
        val w = size.width * f.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(Brush.horizontalGradient(listOf(t.a, t.b), 0f, maxOf(w, h)), Offset(0f, y), Size(maxOf(w, h), h), r)
        if (range != null) {
            val a = (size.width * range.first / 100).toFloat(); val b = (size.width * range.second / 100).toFloat().coerceAtLeast(a + h)
            drawRoundRect(t.b, Offset(a, 1.dp.toPx() / 2), Size(b - a, size.height - 1.dp.toPx()), CornerRadius(size.height / 2), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun DayAxis(modifier: Modifier = Modifier) =
    Row(modifier.padding(top = 4.dp)) { listOf("0 h", "6 h", "12 h", "18 h").forEach { Tq(it, 12.sp, sub, modifier = Modifier.weight(1f)) } }

// ---- Aiguillage ----------------------------------------------------------------------------------------------------

/** Dessine la carte si elle a sa famille de widgets ; false pour les autres (tailles génériques). */
@Composable
internal fun FamilyCard(c: DashCard, size: CardSize, vm: SuperViewModel, nav: NavHostController, h: HealthSnap, an: Analytics, s: NutritionSettings, m: Modifier, tab: (String) -> Unit): Boolean {
    val i = c.icon()
    val large = size == CardSize.L
    when {
        c == DashCard.SCORES -> WForme(size, i, h.watch, m) { nav.go("form") }
        c == DashCard.SLEEP -> WSommeil(size, i, h, m) { nav.go("h/metric/SLEEP") }
        // Calories, macros et pas en grand : la version détaillée d'origine
        c == DashCard.CALORIES && !large -> WCalories(size, i, vm, an, s, m) { vm.nutrition.selectedDay.value = an.today; tab("log") }
        c == DashCard.MACROS && !large -> WMacros(size, i, vm, an, m) { nav.go("macros") }
        c == DashCard.MEALS -> WRepas(size, i, vm, s, m) { vm.nutrition.selectedDay.value = an.today; tab("log") }
        c == DashCard.STEPS && !large -> WPas(size, i, h, s, m) { nav.go("h/metric/STEPS") }
        c == DashCard.HEART -> WCoeur(size, i, h, m) { nav.go("h/metric/HEART_RATE") }
        c == DashCard.HRV_STATUS -> WVfc(size, i, h.watch, m) { nav.go("h/metric/HRV") }
        // Poids : la courbe en petit ; moyen, bandeau et grand gardent la mise en page générique et d'origine
        c == DashCard.WEIGHT && size == CardSize.S -> WPoids(i, an, s, m) { nav.go("weight") }
        c == DashCard.EFFORT -> WEffort(size, i, h.watch, m) { nav.go("h/metric/EFFORT") }
        c == DashCard.NEXT_WORKOUT -> WSeance(size, i, vm, m, nav, tab)
        c == DashCard.TRAINING_WEEK -> WSemaine(size, i, vm, h, m) { nav.go("t/insights") }
        c == DashCard.RUNNING -> WCourse(size, i, h, m) { nav.go("h/activities") }
        else -> return false
    }
    return true
}

// ---- Forme ---------------------------------------------------------------------------------------------------------

@Composable
private fun WForme(sz: CardSize, icon: ImageVector, w: WatchSnap, m: Modifier, onClick: () -> Unit) {
    val form = app.superhealth.ui.wellbeing.rememberForm()
    val a = form as? Form.Advice
    val score = a?.score
    val tone = a?.verdict?.color()?.let { if (Hues.gradient) Tone(it, partnerOf(it)) else Tone(it, it) } ?: Sc.forme
    val word = a?.let { tr(it.verdict.label) } ?: if (form is Form.Missing) tr("À compléter") else null
    val charge = w.charge?.score; val repos = w.sleepScore?.roundToInt(); val chargeTone = Sc.charge(charge)
    fun text(v: Int?) = v?.toString() ?: "—"
    when (sz) {
        CardSize.S -> RingTile(m, onClick, tone.a, (score ?: 0) / 100f) { RingCenter(icon, tone.a, text(score), a?.let { tr(it.verdict.short) } ?: tr("Forme")) }
        CardSize.M -> Medium(m, onClick, icon, tr("Forme"), tone.a, if (LocalCardHalf.current) a?.let { tr(it.verdict.short) } ?: word else word, chartW = 118.dp, chartH = 62.dp, left = {
            Box(contentAlignment = Alignment.Center) { DialT((score ?: 0) / 100f, tone, Modifier.size(78.dp), 8.dp); Tq(text(score), 26.sp, weight = FontWeight.SemiBold, num = true) }
        }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(tr("Charge"), charge, chargeTone), Triple(tr("Repos"), repos, Sc.repos)).forEach { (l, v, t) ->
                    Column {
                        Row { Tq(l, 12.sp, sub, modifier = Modifier.weight(1f)); Tq(text(v), 13.sp, weight = FontWeight.SemiBold) }
                        Bar((v ?: 0) / 100f, t, Modifier.fillMaxWidth().padding(top = 3.dp), 6.dp)
                    }
                }
            }
        }
        CardSize.W -> Wide(m, onClick, icon, tone.a, tr("Forme"), {
            Row(verticalAlignment = Alignment.Bottom) {
                Tq(text(score), 20.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.alignByBaseline())
                if (word != null) Tq("  $word", 14.sp, tone.a, FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
            }
        }, 96.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(charge to chargeTone, repos to Sc.repos).forEach { (v, t) ->
                    Box(contentAlignment = Alignment.Center) { DialT((v ?: 0) / 100f, t, Modifier.size(42.dp), 4.dp); Tq(text(v), 13.sp, weight = FontWeight.SemiBold, num = true) }
                }
            }
        }
        else -> Large(m, onClick, icon, tr("Forme du jour"), tone.a) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                @Composable fun D(v: Int?, label: String, t: Tone, size: Dp, stroke: Dp, big: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        DialT((v ?: 0) / 100f, t, Modifier.size(size), stroke)
                        Tq(text(v), if (big) 38.sp else 22.sp, weight = FontWeight.SemiBold, num = true)
                    }
                    Tq(label, 13.sp, if (big) null else sub, if (big) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.offset(y = (-6).dp))
                }
                D(charge, tr("Charge"), chargeTone, 80.dp, 7.dp, false); D(score, tr("Forme"), tone, 124.dp, 11.dp, true); D(repos, tr("Repos"), Sc.repos, 80.dp, 7.dp, false)
            }
            val range = a?.let { Form.effortRange(it.verdict, w.efforts.dropLast(1)) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(18.dp)).background(Mf.colors.chip).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Tq(word ?: "—", 15.sp, weight = FontWeight.SemiBold, brush = Brush.horizontalGradient(listOf(tone.a, tone.b)), modifier = Modifier.weight(1f))
                if (range != null) {
                    Tq(tr("Effort conseillé") + " ", 13.sp, sub)
                    Tq("${formatNumber(range.first)}–${formatNumber(range.second)}", 14.sp, Sc.effort.a, FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---- Sommeil -------------------------------------------------------------------------------------------------------

@Composable
private fun WSommeil(sz: CardSize, icon: ImageVector, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val w = h.watch; val n = w.lastNight
    val min = n?.asleepMin ?: h.latest(Metric.SLEEP)?.second
    val rest = w.sleepScore?.roundToInt()
    val t = Sc.sleep; val c = t.b
    val cols = app.superhealth.ui.theme.Base.let { listOf(it.Indigo, it.Purple, it.Cyan, it.Pink) }
    val span = n?.let { "${timeOf(it.bedMs)} → ${timeOf(it.wakeMs)}" }
    val staged = n?.staged == true
    @Composable fun Hyp(mod: Modifier) { if (n != null && staged) Hypnogram(n, mod) }
    val score = rest?.let { trf("Score %1\$s", it) }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, min?.let { (it / (w.needMin ?: 480.0)).toFloat() }) {
            RingCenter(icon, c, min?.let { formatDuration(it) } ?: "—", rest?.let { trf("score %1\$s", it) })
        }
        CardSize.M -> Medium(m, onClick, icon, tr("Sommeil"), c, score, left = {
            Hm(min, 26.sp); if (span != null) Tq(span, 12.sp, sub)
        }) { Hyp(Modifier.fillMaxWidth().height(58.dp)) }
        CardSize.W -> Wide(m, onClick, icon, c, if (rest != null) "${tr("Sommeil")} · ${trf("score %1\$s", rest)}" else tr("Sommeil"), { Hm(min, 20.sp, "min") }) {
            Column(horizontalAlignment = Alignment.End) {
                if (n != null && staged) PhaseBar(n, cols, Modifier.width(124.dp))
                if (span != null) { Spacer(Modifier.height(5.dp)); Tq(span, 12.sp, sub) }
            }
        }
        else -> Large(m, onClick, icon, tr("Sommeil"), c, score) {
            // régularité : écart-type de l'heure de coucher sur les nuits lues
            val beds = w.nights.mapNotNull { it.second?.bedClock }
            val spread = beds.takeIf { it.size >= 3 }?.let { b -> val avg = b.average(); sqrt(b.sumOf { (it - avg) * (it - avg) } / b.size) }
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { Hm(min, 30.sp, "min") }
                w.debt?.let { Stat(tr("Dette"), (if (it > 0) "−" else "") + formatDuration(abs(it)), Modifier.padding(start = 14.dp)) }
                spread?.let { Stat(tr("Régularité"), "± ${it.roundToInt()} min", Modifier.padding(start = 14.dp)) }
            }
            if (n != null && staged) {
                Spacer(Modifier.height(10.dp))
                Hypnogram(n, Modifier.fillMaxWidth().height(64.dp))
                Row(Modifier.padding(top = 2.dp)) { Tq(timeOf(n.bedMs), 12.sp, sub); Spacer(Modifier.weight(1f)); Tq(timeOf(n.wakeMs), 12.sp, sub) }
                Spacer(Modifier.height(8.dp))
                PhaseBar(n, cols, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val tot = (n.deepMin + n.lightMin + n.remMin + n.awakeMin).coerceAtLeast(1.0)
                fun pct(v: Double) = "${(v / tot * 100).roundToInt()} %"
                listOf(listOf(tr("Profond") to n.deepMin, tr("Léger") to n.lightMin), listOf(tr("Paradoxal") to n.remMin, tr("Éveil") to n.awakeMin)).forEachIndexed { r, row ->
                    Row(Modifier.padding(top = if (r == 0) 0.dp else 4.dp)) {
                        row.forEachIndexed { k, (l, v) ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(cols[r * 2 + k]))
                                Tq("  $l", 13.sp, sub, modifier = Modifier.weight(1f)); Tq(pct(v), 13.sp, weight = FontWeight.SemiBold)
                                if (k == 0) Spacer(Modifier.width(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Calories, macros, repas ---------------------------------------------------------------------------------------

/** Repas du jour : (créneau, aliments), dans l'ordre de la journée. */
private fun mealsOf(eaten: List<FoodEntryEntity>) = eaten.sortedBy { it.minuteOf() }.groupBy { MealSlot.of(it.minuteOf()) }.toSortedMap().toList()

@Composable
private fun MealBar(eaten: List<FoodEntryEntity>, goal: Double, modifier: Modifier) {
    val track = faint
    val parts = mealsOf(eaten).map { (slot, l) -> slot.color() to l.sumOf { it.kcal } }
    Canvas(modifier.height(10.dp).clip(CircleShape)) {
        drawRect(track)
        val total = maxOf(goal, parts.sumOf { it.second }).coerceAtLeast(1.0); val g = 2.dp.toPx(); var x = 0f
        parts.forEach { (c, k) -> val w = (size.width * k / total).toFloat(); drawRect(c, Offset(x, 0f), Size((w - g).coerceAtLeast(1f), size.height)); x += w }
    }
}

@Composable
private fun MacroRings(eaten: Macros, target: Macros) {
    Row {
        listOf(Triple(tr("Protéines"), eaten.protein to target.protein, Sc.prot), Triple(tr("Lipides"), eaten.fat to target.fat, Sc.fat), Triple(tr("Glucides"), eaten.carbs to target.carbs, Sc.carb)).forEach { (l, v, c) ->
            val f = if (v.second > 0) (v.first / v.second).toFloat() else 0f
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    DialT(f, Tone(c, c), Modifier.size(40.dp), 5.dp, 360f)
                    Tq("${(f * 100).roundToInt()}", 12.sp, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Column { Tq(l, 12.sp, sub); Row(verticalAlignment = Alignment.Bottom) { Tq("${v.first.roundToInt()}", 15.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.alignByBaseline()); Tq(" g", 12.sp, sub, modifier = Modifier.alignByBaseline()) } }
            }
        }
    }
}

@Composable
private fun WCalories(sz: CardSize, icon: ImageVector, vm: SuperViewModel, an: Analytics, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val eatenList by vm.nutrition.todayEntries.collectAsState()
    val eaten = remember(eatenList) { eatenList.fold(Macros.ZERO) { acc, e -> acc + e.macros() } }
    val target = an.targetFor(an.today).macros
    val left = target.kcal - eaten.kcal
    val leftTxt = formatNumber(abs(s.energyValue(left))); val word = if (left < 0) tr("en trop") else tr("restantes")
    val t = Sc.kcal; val f = if (target.kcal > 0) (eaten.kcal / target.kcal).toFloat() else 0f
    val pair = "${formatNumber(s.energyValue(eaten.kcal))} / ${formatNumber(s.energyValue(target.kcal))}"
    val unit = tr(s.energyUnit.label)
    when (sz) {
        CardSize.S -> RingTile(m, onClick, t.a, f) { RingCenter(icon, t.a, leftTxt, word) }
        CardSize.M -> Medium(m, onClick, icon, tr("Calories"), t.a, pair, sub, chartH = 62.dp, left = { V(leftTxt, null, 26.sp, color = t.a); Tq(word, 12.sp, sub) }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(Triple("P", eaten.protein to target.protein, Sc.prot), Triple("L", eaten.fat to target.fat, Sc.fat), Triple("G", eaten.carbs to target.carbs, Sc.carb)).forEach { (n, v, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tq(n, 12.sp, c, FontWeight.SemiBold, modifier = Modifier.width(14.dp))
                        Bar(if (v.second > 0) (v.first / v.second).toFloat() else 0f, Tone(c, c), Modifier.weight(1f), 6.dp)
                        Tq("${v.first.roundToInt()}", 12.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(32.dp).padding(start = 6.dp))
                    }
                }
            }
        }
        CardSize.W -> Wide(m, onClick, icon, t.a, if (left < 0) tr("Calories en trop") else tr("Calories restantes"), { V(leftTxt, "/ ${formatNumber(s.energyValue(target.kcal))} $unit", 20.sp) }, 112.dp) {
            Column(horizontalAlignment = Alignment.End) {
                MealBar(eatenList, target.kcal, Modifier.width(112.dp)); Spacer(Modifier.height(5.dp))
                Tq(trf("%1\$s mangées", formatNumber(s.energyValue(eaten.kcal))), 12.sp, sub)
            }
        }
        else -> Large(m, onClick, icon, tr("Calories"), t.a, "$leftTxt $word") {
            V(formatNumber(s.energyValue(eaten.kcal)), "/ ${formatNumber(s.energyValue(target.kcal))} $unit", 30.sp)
            Spacer(Modifier.height(10.dp)); MealBar(eatenList, target.kcal, Modifier.fillMaxWidth())
            val meals = mealsOf(eatenList)
            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row {
                    meals.take(4).forEach { (slot, l) ->
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(slot.color())); Tq("  " + tr(slot.short), 12.sp, sub) }
                            Tq(formatNumber(s.energyValue(l.sumOf { it.kcal })), 16.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Mf.colors.line))
            MacroRings(eaten, target)
        }
    }
}

@Composable
private fun WMacros(sz: CardSize, icon: ImageVector, vm: SuperViewModel, an: Analytics, m: Modifier, onClick: () -> Unit) {
    val eatenList by vm.nutrition.todayEntries.collectAsState()
    val eaten = remember(eatenList) { eatenList.fold(Macros.ZERO) { acc, e -> acc + e.macros() } }
    val target = an.targetFor(an.today).macros
    val c = Sc.prot
    val p = eaten.protein.roundToInt(); val pt = target.protein.roundToInt()
    val pf = if (target.protein > 0) (eaten.protein / target.protein).toFloat() else 0f
    val all = listOf(Triple("P", eaten.protein to target.protein, Sc.prot), Triple("L", eaten.fat to target.fat, Sc.fat), Triple("G", eaten.carbs to target.carbs, Sc.carb))
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, pf) { RingCenter(icon, c, "$p", "/ $pt g") }
        CardSize.M -> Medium(m, onClick, icon, tr("Macros"), c, tr("aujourd'hui"), sub, chartW = 0.dp, left = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                all.forEach { (n, v, col) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) { DialT(if (v.second > 0) (v.first / v.second).toFloat() else 0f, Tone(col, col), Modifier.size(50.dp), 6.dp, 360f); Tq(n, 13.sp, col, FontWeight.Bold) }
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) { Tq("${v.first.roundToInt()}", 15.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.alignByBaseline()); Tq(" g", 12.sp, sub, modifier = Modifier.alignByBaseline()) }
                    }
                }
            }
        })
        CardSize.W -> Wide(m, onClick, icon, c, tr("Macros"), {
            Row(verticalAlignment = Alignment.Bottom) { Tq("$p", 20.sp, weight = FontWeight.SemiBold, num = true, modifier = Modifier.alignByBaseline()); Tq(" " + trf("/ %1\$s g de protéines", pt), 12.sp, sub, modifier = Modifier.alignByBaseline()) }
        }, 104.dp) {
            Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { all.forEach { (_, v, col) -> Bar(if (v.second > 0) (v.first / v.second).toFloat() else 0f, Tone(col, col), Modifier.fillMaxWidth(), 5.dp) } }
        }
        else -> Large(m, onClick, icon, tr("Macros de la semaine"), c) {
            MacroRings(eaten, target)
            Spacer(Modifier.height(12.dp))
            val monday = an.today - LocalDate.ofEpochDay(an.today).dayOfWeek.value + 1
            val week by remember(monday) { vm.nutrition.repo.entries.rangeFlow(monday, monday + 6) }.collectAsState(initial = emptyList())
            val byDay = remember(week) { week.groupBy { it.epochDay }.mapValues { (_, l) -> Triple(l.sumOf { it.protein } * 4, l.sumOf { it.fat } * 9, l.sumOf { it.carbs } * 4) } }
            val max = (byDay.values.maxOfOrNull { it.first + it.second + it.third } ?: 0.0).coerceAtLeast(1.0)
            val track = faint; val (pc, fc, cc) = Triple(Sc.prot, Sc.fat, Sc.carb)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0L..6L).forEach { i ->
                    val d = monday + i; val v = byDay[d]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(66.dp)) {
                            val r = CornerRadius(6.dp.toPx()); val tot = v?.let { it.first + it.second + it.third } ?: 0.0
                            if (tot <= 0) drawRoundRect(track, Offset(0f, size.height - 6.dp.toPx()), Size(size.width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                            else {
                                val h = (size.height * tot / max).toFloat(); val g = 1.5.dp.toPx(); var y = size.height - h
                                val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, y, size.width, size.height, r)) }
                                clipPath(path) {
                                    listOf(v!!.third to cc, v.second to fc, v.first to pc).forEach { (k, col) ->
                                        val ph = (h * k / tot).toFloat(); drawRect(col, Offset(0f, y), Size(size.width, (ph - g).coerceAtLeast(1f))); y += ph
                                    }
                                }
                            }
                        }
                        Tq(dayLetter(LocalDate.ofEpochDay(d)), 12.sp, if (d == an.today) null else sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WRepas(sz: CardSize, icon: ImageVector, vm: SuperViewModel, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val eaten by vm.nutrition.todayEntries.collectAsState()
    val ends = app.superhealth.SuperPrefs.settings.collectAsState().value.mealEnds
    val meals = remember(eaten, ends) { mealsOf(eaten) }
    val total = formatNumber(s.energyValue(eaten.sumOf { it.kcal })); val unit = tr(s.energyUnit.label)
    val c = Sc.kcal.a
    @Composable fun hour(l: List<FoodEntryEntity>) = l.minOf { it.minuteOf() }.let { "%d:%02d".format(it / 60, it % 60) }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, null, ring = false) {
            if (meals.isEmpty()) Icon(icon, null, Modifier.size(26.dp), tint = c)
            else Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) { meals.take(3).forEach { (_, l) -> FoodIcon(l.first().name, 34.dp, Modifier.border(2.dp, Mf.colors.card, CircleShape)) } }
            Spacer(Modifier.height(6.dp))
            Text(total, style = MaterialTheme.typography.titleMedium, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onContainer(), maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1)
        }
        CardSize.M -> Medium(m, onClick, icon, tr("Repas du jour"), c, "$total $unit", sub, chartW = 0.dp, left = {
            if (meals.isEmpty()) Tq(tr("Rien de noté"), 13.sp, sub)
            meals.take(4).forEach { (slot, l) ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!LocalCardHalf.current) Tq(hour(l), 12.sp, sub, modifier = Modifier.width(38.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(slot.color()))
                    Tq("  " + tr(if (LocalCardHalf.current) slot.short else slot.label), 14.sp, modifier = Modifier.weight(1f))
                    Tq(formatNumber(s.energyValue(l.sumOf { it.kcal })), 13.sp, weight = FontWeight.SemiBold)
                }
            }
        })
        CardSize.W -> Wide(m, onClick, icon, c, if (meals.size == 1) tr("1 repas") else trf("%1\$s repas", meals.size), { V(total, unit, 20.sp) }, 150.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { eaten.sortedBy { it.minuteOf() }.take(6).forEach { FoodIcon(it.name, 23.dp) } }
        }
        else -> Large(m, onClick, icon, tr("Repas du jour"), c, "$total $unit", sub) {
            // le fil de la journée : l'heure, un point sur la ligne, le repas et ce qu'il contient
            val track = faint
            meals.forEach { (slot, l) ->
                Row(Modifier.height(54.dp)) {
                    Column(Modifier.width(44.dp)) { Tq(hour(l), 13.sp, weight = FontWeight.SemiBold, num = true); Tq(formatNumber(s.energyValue(l.sumOf { it.kcal })), 12.sp, sub) }
                    Box(Modifier.width(14.dp).fillMaxHeight()) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).width(2.dp).fillMaxHeight().background(track))
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(10.dp).clip(CircleShape).background(slot.color()))
                    }
                    Column(Modifier.padding(start = 8.dp)) {
                        Tq(tr(slot.label), 15.sp, weight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            l.take(3).forEach { f -> Row(Modifier.clip(CircleShape).background(Mf.colors.chip).padding(start = 3.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                FoodIcon(f.name, 18.dp); Tq(" " + f.name.substringBefore(' '), 12.sp) } }
                            if (l.size > 3) Tq("+${l.size - 3}", 12.sp, sub, FontWeight.SemiBold)
                        }
                    }
                }
            }
            // le prochain repas à noter
            val next = MealSlot.entries.firstOrNull { sl -> meals.isEmpty() || sl.ordinal > meals.last().first.ordinal }
            if (next != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(44.dp))
                Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, sub, CircleShape)) }
                Row(Modifier.padding(start = 8.dp).clip(CircleShape).border(1.dp, track, CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(14.dp), tint = sub); Tq(" " + tr(next.label), 13.sp, sub, FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Pas -----------------------------------------------------------------------------------------------------------

@Composable
private fun WPas(sz: CardSize, icon: ImageVector, h: HealthSnap, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val v = h.today(Metric.STEPS) ?: 0.0
    val goal = s.stepGoalFor(LocalDate.now()).toDouble()
    val f = if (goal > 0) (v / goal).toFloat() else 0f
    val pct = "${(f * 100).roundToInt()} %"; val txt = formatNumber(v); val t = Sc.steps; val c = t.a
    val hourly = remember(h.watch.hourly) { hourlySteps(h.watch.hourly) }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, f) { RingCenter(icon, c, txt, pct) }
        CardSize.M -> Medium(m, onClick, icon, tr("Pas"), c, pct, left = { V(txt, null, 26.sp); Tq(trf("sur %1\$s", formatNumber(goal)), 12.sp, sub) }) {
            GoalBars(h.series[Metric.STEPS].orEmpty().map { it.second }, goal, t, Modifier.fillMaxWidth().height(56.dp))
        }
        CardSize.W -> Wide(m, onClick, icon, c, tr("Pas"), { V(txt, "/ ${formatNumber(goal)}", 20.sp) }, 112.dp) {
            Column(horizontalAlignment = Alignment.End) { Bar(f, t, Modifier.width(112.dp)); Spacer(Modifier.height(5.dp)); Tq(pct, 12.sp, c, FontWeight.SemiBold) }
        }
        else -> Large(m, onClick, icon, tr("Pas"), c, trf("%1\$s de %2\$s", pct, formatNumber(goal))) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(txt, tr("pas"), 30.sp) }
                h.today(Metric.DISTANCE)?.takeIf { it > 0 }?.let { Stat(tr("Distance"), formatNumber(it / 1000, 1) + " km", Modifier.padding(start = 14.dp)) }
                h.today(Metric.CALORIES)?.takeIf { it > 0 }?.let { Stat(tr("Actives"), formatNumber(it) + " kcal", Modifier.padding(start = 14.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            StepBars(hourly, t, LocalTime.now().hour, Modifier.fillMaxWidth().height(76.dp))
            DayAxis()
        }
    }
}

// ---- Cœur et VFC ---------------------------------------------------------------------------------------------------

/** Plage habituelle d'une série : moyenne ± un écart-type, au moins 5 valeurs. */
private fun usual(v: List<Double>): Pair<Double, Double>? {
    if (v.size < 5) return null
    val avg = v.average(); val sd = sqrt(v.sumOf { (it - avg) * (it - avg) } / v.size).coerceAtLeast(0.5)
    return avg - sd to avg + sd
}

@Composable
private fun WCoeur(sz: CardSize, icon: ImageVector, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val hist = h.series[Metric.RESTING_HR].orEmpty().mapNotNull { it.second }
    val rest = hist.lastOrNull()
    val v = rest ?: h.heartToday.lastOrNull()?.value
    val range = usual(hist.dropLast(1)); val avg = hist.dropLast(1).takeIf { it.size >= 3 }?.average()
    val diff = if (rest != null && avg != null) (rest - avg).roundToInt() else null
    val t = Sc.heart; val c = t.a; val txt = v?.let { formatNumber(it) } ?: "—"; val bpm = tr("bpm")
    @Composable fun D() { if (diff != null && diff != 0) Delta(trf("%1\$s vs moy.", abs(diff)), up = diff > 0, good = diff < 0) }
    @Composable fun Dot(mod: Modifier) { if (rest != null && range != null) RangeDot(rest, range.first, range.second, minOf(range.first, rest) - 4, maxOf(range.second, rest) + 4, c, mod) }
    when (sz) {
        CardSize.S -> CurveTile(m, onClick, icon, tr("Repos"), c, txt, bpm) { LineT(hist, t, it, area = true) }
        CardSize.M -> Medium(m, onClick, icon, tr("Cœur au repos"), c, left = { V(txt, bpm, 26.sp); D() }) {
            if (range != null) BandLine(hist, range.first, range.second, t, Modifier.fillMaxWidth().height(58.dp), false) else LineT(hist, t, Modifier.fillMaxWidth().height(58.dp), area = true)
        }
        CardSize.W -> Wide(m, onClick, icon, c, tr("Cœur au repos"), { V(txt, bpm, 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) {
                Dot(Modifier.width(112.dp)); Spacer(Modifier.height(4.dp))
                if (range != null) Tq(trf("habituel %1\$s–%2\$s", range.first.roundToInt(), range.second.roundToInt()), 12.sp, sub)
            }
        }
        else -> Large(m, onClick, icon, tr("Rythme cardiaque"), c, tr("aujourd'hui"), sub) {
            val pts = h.heartToday
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(txt, tr("bpm au repos"), 30.sp) }
                pts.minOfOrNull { it.value }?.let { Stat(tr("Min"), formatNumber(it), Modifier.padding(start = 14.dp)) }
                pts.maxOfOrNull { it.value }?.let { Stat(tr("Max"), formatNumber(it), Modifier.padding(start = 14.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            if (pts.size >= 2) {
                // pouls de la journée par quart d'heure ; pointillés pour les heures à venir
                val start = HealthRepository.startMs(LocalDate.now())
                val slots = remember(pts) { pts.groupBy { ((it.timestampMs - start) / 900_000).toInt() }.mapValues { e -> e.value.map { it.value }.average() } }
                val last = (slots.keys.maxOrNull() ?: 0).coerceAtLeast(1)
                val done = ((last + 1) / 96f).coerceIn(0.1f, 1f); val track = faint
                Box(Modifier.fillMaxWidth().height(86.dp)) {
                    LineT((0..last).map { slots[it] }, t, Modifier.fillMaxWidth(done).fillMaxHeight(), area = true)
                    Canvas(Modifier.fillMaxSize()) {
                        if (done < 1f) drawLine(track, Offset(size.width * done, size.height - 1), Offset(size.width, size.height - 1), 1.5.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(4f, 8f)))
                    }
                }
                DayAxis()
            } else if (range != null) BandLine(hist, range.first, range.second, t, Modifier.fillMaxWidth().height(86.dp), true)
        }
    }
}

@Composable
private fun WVfc(sz: CardSize, icon: ImageVector, w: WatchSnap, m: Modifier, onClick: () -> Unit) {
    val all = w.hrv.mapNotNull { it.second }
    val v = all.lastOrNull(); val past = all.dropLast(1)
    val range = usual(past); val avg = past.takeIf { it.isNotEmpty() }?.average()
    val t = Sc.heart; val c = t.a; val txt = v?.let { formatNumber(it) } ?: "—"
    val diff = if (v != null && avg != null) (v - avg).roundToInt() else null
    val status = if (v != null && range != null) tr(when { v < range.first -> "Basse"; v > range.second -> "Haute"; else -> "Normale" }) else null
    val good = Sc.good
    @Composable fun D() { if (diff != null && diff != 0) Delta(trf("%1\$s vs moy.", abs(diff)), up = diff > 0, good = diff > 0) }
    @Composable fun Dot(mod: Modifier) { if (v != null && range != null) RangeDot(v, range.first, range.second, minOf(range.first, v) * 0.85, maxOf(range.second, v) * 1.15, c, mod) }
    when (sz) {
        CardSize.S -> CurveTile(m, onClick, icon, tr("VFC"), c, txt, "ms") { LineT(all.takeLast(14), t, it, area = true) }
        CardSize.M -> Medium(m, onClick, icon, tr("VFC nocturne"), c, status, good, left = { V(txt, "ms", 26.sp); D() }) {
            if (range != null) BandLine(all.takeLast(7), range.first, range.second, t, Modifier.fillMaxWidth().height(58.dp), true) else LineT(all.takeLast(7), t, Modifier.fillMaxWidth().height(58.dp))
        }
        CardSize.W -> Wide(m, onClick, icon, c, tr("VFC nocturne"), { V(txt, "ms", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) {
                Dot(Modifier.width(112.dp)); Spacer(Modifier.height(4.dp))
                if (status != null && range != null) Tq("$status · ${range.first.roundToInt()}–${range.second.roundToInt()}", 12.sp, sub)
            }
        }
        else -> Large(m, onClick, icon, tr("Variabilité cardiaque"), c, status, good) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(txt, tr("ms cette nuit"), 30.sp) }
                avg?.let { Stat(tr("Moyenne"), formatNumber(it) + " ms", Modifier.padding(start = 14.dp)) }
                range?.let { Stat(tr("Plage"), "${it.first.roundToInt()}–${it.second.roundToInt()}", Modifier.padding(start = 14.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            if (range != null) BandLine(all.takeLast(14), range.first, range.second, t, Modifier.fillMaxWidth().height(82.dp), true) else LineT(all.takeLast(14), t, Modifier.fillMaxWidth().height(82.dp))
            Row(Modifier.padding(top = 4.dp)) { Tq(trf("il y a %1\$s nuits", all.takeLast(14).size), 12.sp, sub, modifier = Modifier.weight(1f)); Tq(tr("cette nuit"), 12.sp, sub) }
        }
    }
}

// ---- Poids ---------------------------------------------------------------------------------------------------------

@Composable
private fun WPoids(icon: ImageVector, an: Analytics, s: NutritionSettings, m: Modifier, onClick: () -> Unit) {
    val trend = an.trend.takeLast(28).map { s.weightValue(it.trend) }
    val c = DashCard.WEIGHT.hue()
    CurveTile(m, onClick, icon, tr("Poids"), c, an.currentTrendKg?.let { formatNumber(s.weightValue(it), 1) } ?: "—", tr(s.weightUnit.label)) { LineT(trend, Tone(c, c), it, area = true) }
}

// ---- Effort --------------------------------------------------------------------------------------------------------

@Composable
private fun WEffort(sz: CardSize, icon: ImageVector, w: WatchSnap, m: Modifier, onClick: () -> Unit) {
    val form = app.superhealth.ui.wellbeing.rememberForm() as? Form.Advice
    val range = form?.let { Form.effortRange(it.verdict, w.efforts.dropLast(1)) }
    val v = w.effort; val f = ((v ?: 0.0) / 100).toFloat(); val t = Sc.effort; val c = t.a
    val txt = v?.let { formatNumber(it) } ?: "—"
    val zone = range?.let { trf("zone %1\$s–%2\$s", formatNumber(it.first), formatNumber(it.second)) }
    val rest = if (range != null && v != null && v < range.first) formatNumber(range.first - v) else null
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, f) { RingCenter(icon, c, txt, zone ?: "/ 100") }
        CardSize.M -> Medium(m, onClick, icon, tr(if (LocalCardHalf.current) "Effort" else "Effort du jour"), c, zone, t.b, chartW = 82.dp, chartH = 62.dp, left = { V(txt, "/ 100", 26.sp); if (rest != null) Tq(trf("encore %1\$s", rest), 12.sp, sub) }) {
            if (LocalCardHalf.current) EffortBar(f, range, Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 4.dp))
            else Box(contentAlignment = Alignment.Center) { EffortDial(f, range, 78.dp, 8.dp); Icon(icon, null, Modifier.size(24.dp), tint = c) }
        }
        CardSize.W -> Wide(m, onClick, icon, c, tr("Effort du jour"), { V(txt, "/ 100", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) { EffortBar(f, range, Modifier.width(112.dp)); if (zone != null) { Spacer(Modifier.height(5.dp)); Tq(zone, 12.sp, t.b, FontWeight.SemiBold) } }
        }
        else -> Large(m, onClick, icon, tr("Effort du jour"), c, zone, t.b) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    EffortDial(f, range, 128.dp, 11.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Tq(txt, 32.sp, weight = FontWeight.SemiBold, num = true); Tq(tr("sur 100"), 12.sp, sub) }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    // les sept derniers jours
                    val start = LocalDate.now().minusDays(6)
                    Tq(tr("7 derniers jours"), 13.sp, sub)
                    Spacer(Modifier.height(6.dp))
                    GoalBars(w.efforts.takeLast(7), range?.first, t, Modifier.fillMaxWidth().height(62.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp)) { (0L..6L).forEach { Tq(dayLetter(start.plusDays(it)), 12.sp, if (it == 6L) null else sub, FontWeight.SemiBold, modifier = Modifier.weight(1f)) } }
                    if (rest != null) Tq(trf("Encore %1\$s pour la zone", rest), 13.sp, t.b, FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ---- Séance --------------------------------------------------------------------------------------------------------

@Composable
private fun WSeance(sz: CardSize, icon: ImageVector, vm: SuperViewModel, m: Modifier, nav: NavHostController, tab: (String) -> Unit) {
    val st by vm.training.state.collectAsState()
    val db by vm.training.db.collectAsState()
    val p = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
    val next = p?.days?.getOrNull(p.dayIndex)?.takeIf { !it.isRest }
    val t = Sc.effort
    val c = p?.let { app.superhealth.ui.theme.ProgramColors[it.color % app.superhealth.ui.theme.ProgramColors.size] } ?: t.a
    val resume = st.session != null
    val open: () -> Unit = { if (resume) nav.go("session") else tab("activities") }
    val start: () -> Unit = {
        if (resume) nav.go("session") else if (p != null && next != null) { vm.training.startProgramDay(p, p.dayIndex, p.cycleIndex); nav.go("session") } else tab("activities")
    }
    @Composable fun muscles(d: app.superhealth.training.domain.ProgramDay) = remember(d, db) { d.exercises.flatMap { db?.exercise(it.exerciseId)?.muscleGroups.orEmpty() }.toSet() }
    val train = p?.days?.withIndex()?.filter { !it.value.isRest }.orEmpty()
    val rank = train.indexOfFirst { it.index == p?.dayIndex } + 1
    val minutes = next?.let { app.superhealth.training.domain.Generator.estimateMinutes(it, p!!.cycleIndex) }
    val brush = app.superhealth.ui.theme.actionFill(t.a, t.b)
    val ink = app.superhealth.ui.theme.actionInk()
    @Composable fun Play(s: Dp) = Box(Modifier.size(s).clip(CircleShape).background(brush).clickable(onClick = start), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, tr("Commencer"), Modifier.size(s * 0.6f), tint = ink)
    }
    val name = next?.name ?: tr("Aucune séance")
    when (sz) {
        CardSize.S -> RingTile(m, open, c, if (train.isNotEmpty()) train.count { it.value.id in p!!.doneDayIds }.toFloat() / train.size else null) {
            if (next != null) XRayBody(muscles(next), st.settings.sex, c, Modifier.size(34.dp, 44.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.onContainer(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
        }
        // moyen et grand : les séances sont là, on choisit laquelle lancer ou modifier
        CardSize.M -> Tile(c, m.fillMaxWidth(), open, 16.dp, 14.dp) {
            Head(icon, p?.name ?: tr("Prochaine séance"), c)
            Spacer(Modifier.height(10.dp))
            if (p != null) app.superhealth.training.ui.screens.ProgramPicker(p, db, st.settings.sex, resume, 58.dp, { nav.go("session") }, { i -> vm.training.startProgramDay(p, i, p.cycleIndex); nav.go("session") }, { i -> nav.go("programday/${p.id}/$i") })
            else Tq(tr("Créer un programme"), 15.sp, weight = FontWeight.SemiBold)
        }
        CardSize.W -> Tile(c, m.fillMaxWidth().heightIn(min = 68.dp), open, 14.dp, 8.dp, center = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp, 50.dp).clip(RoundedCornerShape(12.dp)).background(Mf.colors.chip)) { if (next != null) XRayBody(muscles(next), st.settings.sex, c, Modifier.fillMaxSize().padding(vertical = 3.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Tq(name, 20.sp, c.onContainer(), FontWeight.SemiBold, true, Modifier.alignByBaseline().weight(1f, fill = false))
                        if (minutes != null) Tq("  " + trf("%1\$s min", minutes), 12.sp, sub, modifier = Modifier.alignByBaseline())
                    }
                    Tq(if (rank > 0) "${tr("Prochaine")} · $rank / ${train.size}" else tr("Prochaine"), 12.sp, sub)
                }
                if (next != null || resume) Play(42.dp)
            }
        }
        else -> Large(m, open, icon, if (p != null) "${p.name} · ${trf("semaine %1\$s", p.cycleIndex + 1)}" else tr("Prochaine séance"), c,
            if (train.isNotEmpty()) "${train.count { it.value.id in p!!.doneDayIds }} / ${train.size}" else null, sub) {
            if (p == null) Tq(tr("Créer un programme"), 15.sp, weight = FontWeight.SemiBold)
            else app.superhealth.training.ui.screens.ProgramPicker(p, db, st.settings.sex, resume, 88.dp, { nav.go("session") }, { i -> vm.training.startProgramDay(p, i, p.cycleIndex); nav.go("session") }, { i -> nav.go("programday/${p.id}/$i") })
        }
    }
}

// ---- Semaine -------------------------------------------------------------------------------------------------------

@Composable
private fun WSemaine(sz: CardSize, icon: ImageVector, vm: SuperViewModel, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val c0 = DashCard.TRAINING_WEEK.hue()
    val st by vm.training.state.collectAsState()
    val today = LocalDate.now(); val start = TrainingAnalytics.weekStart(today)
    val all = h.recent
    val week = all.filter { !localDate(it.start).isBefore(start) }
    val last = all.filter { localDate(it.start).let { d -> !d.isBefore(start.minusDays(7)) && d.isBefore(start) } }
    val byDay = (0L..6L).map { i -> week.filter { localDate(it.start) == start.plusDays(i) }.sumOf { it.minutes } }
    val idx = (today.toEpochDay() - start.toEpochDay()).toInt()
    val minutes = week.sumOf { it.minutes }; val c = c0; val t = if (Hues.gradient) Tone(c, partnerOf(c)) else Tone(c, c); val n = week.size
    val words = if (n > 1) tr("activités") else tr("activité")
    val diff = minutes - last.sumOf { it.minutes }
    val dur = formatDuration(minutes.toDouble())
    @Composable fun D() { if (last.isNotEmpty() && diff != 0L) Delta(formatDuration(abs(diff).toDouble()), up = diff > 0, good = diff > 0) }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, minutes / 150f) { RingCenter(icon, c, "$n", words) }
        CardSize.M -> Medium(m, onClick, icon, tr("Cette semaine"), c, dur, Mf.colors.text, chartW = 124.dp, chartH = 62.dp, left = { V("$n", words, 26.sp); D() }) { WeekBars(byDay, idx, start, t, Modifier.fillMaxWidth(), 42.dp) }
        CardSize.W -> Wide(m, onClick, icon, c, tr("Cette semaine"), { V(dur, "· $n $words", 20.sp) }, 128.dp) { WeekBars(byDay, idx, start, t, Modifier.width(124.dp), 30.dp) }
        else -> Large(m, onClick, icon, tr("Semaine d'entraînement"), c, if (last.isNotEmpty() && diff != 0L) (if (diff > 0) "+ " else "− ") + formatDuration(abs(diff).toDouble()) else null, Sc.good) {
            val kcal = week.sumOf { it.hc?.kcal ?: it.run?.kcal ?: 0.0 }
            val volume = st.history.filter { !localDate(it.startTime).isBefore(start) }.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.volume(it.sets) } }
            Row {
                Stat(tr("Durée"), dur, Modifier.weight(1f)); Stat(tr("Activités"), "$n", Modifier.weight(1f))
                if (kcal > 0) Stat(tr("Calories"), formatNumber(kcal), Modifier.weight(1f))
                if (volume > 0) Stat(tr("Volume"), if (volume >= 1000) formatNumber(volume / 1000, 1) + " t" else formatNumber(volume) + " kg", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                byDay.forEachIndexed { i, mn ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            DialT(mn / 60f, t, Modifier.size(40.dp), 4.dp, 360f)
                            if (mn > 0) Tq("$mn", 12.sp, weight = FontWeight.SemiBold, num = true)
                        }
                        Tq(dayLetter(start.plusDays(i.toLong())), 12.sp, if (i == idx) null else sub, FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Tq(tr("Anneau plein : 60 min"), 12.sp, sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---- Course --------------------------------------------------------------------------------------------------------

@Composable
private fun WCourse(sz: CardSize, icon: ImageVector, h: HealthSnap, m: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val runs = h.recent.filter { it.kind == Bridge.Kind.RUN && (it.distanceM ?: 0.0) >= 100 }
    val a = runs.firstOrNull()
    val route by produceState<List<Pair<Double, Double>>?>(null, a?.key) { value = a?.let { routeOf(ctx, it) } }
    val laps by produceState<List<app.superhealth.health.data.Lap>>(emptyList(), a?.key, sz) {
        val id = a?.watch?.id ?: a?.run?.id
        value = if (sz == CardSize.L && id != null) runCatching { app.superhealth.health.data.WorkoutDetails.load(ctx, id)?.laps.orEmpty() }.getOrDefault(emptyList()) else emptyList()
    }
    val c = DashCard.RUNNING.hue(); val t = if (Hues.gradient) Tone(c, partnerOf(c)) else Tone(c, c)
    val km = a?.distanceM?.let { formatNumber(it / 1000, 1) } ?: "—"
    val label = a?.let { "${tr("Course")} · ${relativeDay(localDate(it.start)).lowercase()}" } ?: tr("Course")
    val paceTxt = a?.let(::pace); val time = a?.let { clock((it.end - it.start) / 1000) }
    val ink = c
    @Composable fun Trace(mod: Modifier, stroke: Dp = 3.dp) { route?.let { RouteTrace(it, ink, mod, stroke) } }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, null, ring = route == null) {
            if (route != null) { Trace(Modifier.size(70.dp, 42.dp), 2.5.dp); Spacer(Modifier.height(4.dp))
                Text(km, style = MaterialTheme.typography.titleMedium, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onContainer(), maxLines = 1)
                Text("km", style = MaterialTheme.typography.labelMedium, color = sub, maxLines = 1)
            } else RingCenter(icon, c, km, if (a != null) "km" else tr("Aucune course"))
        }
        CardSize.M -> Medium(m, onClick, icon, label, c, chartH = 62.dp, left = { V(km, "km", 26.sp); if (paceTxt != null) Tq(paceTxt, 12.sp, sub) }) { Trace(Modifier.fillMaxWidth().height(62.dp)) }
        CardSize.W -> Wide(m, onClick, icon, c, label, { V(km, "km", 20.sp) }, 120.dp) {
            Column(horizontalAlignment = Alignment.End) {
                if (paceTxt != null) Tq(paceTxt, 16.sp, weight = FontWeight.SemiBold, num = true)
                if (time != null) Tq(time, 12.sp, sub)
            }
        }
        else -> Large(m, onClick, icon, label, c) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { V(km, "km", 30.sp) }
                if (time != null) Stat(tr("Durée"), time, Modifier.padding(start = 14.dp))
                if (paceTxt != null) Stat(tr("Allure"), paceTxt.substringBefore(' '), Modifier.padding(start = 14.dp))
            }
            Spacer(Modifier.height(10.dp))
            val splits = laps.filter { it.distanceM > 100 && it.sec > 0 }.take(8).map { it.sec / (it.distanceM / 1000) }
            // sans temps au kilomètre, le tracé prend toute la largeur
            Row(verticalAlignment = Alignment.CenterVertically) {
                Trace(if (splits.isEmpty()) Modifier.fillMaxWidth().height(110.dp) else Modifier.size(128.dp, 100.dp))
                if (splits.isNotEmpty()) {
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val best = splits.min(); val slow = splits.max().coerceAtLeast(best + 1)
                        splits.forEachIndexed { i, sp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Tq("km ${i + 1}", 12.sp, sub, modifier = Modifier.width(42.dp))
                                Bar(if (sp == best) 1f else (1f - ((sp - best) / (slow - best)).toFloat() * 0.5f), if (sp == best) t else Tone(sub.copy(alpha = 0.5f), sub.copy(alpha = 0.5f)), Modifier.weight(1f), 8.dp)
                                val s = sp.roundToInt()
                                Tq("%d:%02d".format(s / 60, s % 60), 13.sp, if (sp == best) c else null, FontWeight.SemiBold, true, Modifier.width(56.dp).padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
