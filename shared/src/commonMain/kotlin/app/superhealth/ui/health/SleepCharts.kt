package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.NightSummary
import app.superhealth.health.data.Nights
import app.superhealth.health.data.Scores
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.ui.charts.ChartStyle
import app.superhealth.ui.formatDuration
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import app.superhealth.health.data.ActivityKind

/*
 * Graphiques du sommeil, communs à l'écran Sommeil et aux cartes du tableau de bord : hypnogramme, phases nuit par nuit
 * (barres empilées, comme Garmin), horaires de coucher et de lever (comme Santé), parties de Repos (comme Oura).
 */

/** Couleur de chaque phase (profond foncé, paradoxal clair, éveil corail). */
fun stageColor(k: ActivityKind): Color = when (k) {
    ActivityKind.DEEP_SLEEP -> Color(0xFF3634A3)
    ActivityKind.REM_SLEEP -> Color(0xFF5AC8FA)
    ActivityKind.AWAKE_SLEEP -> Color(0xFFFF7F66)
    else -> Color(0xFF0A84FF)
}

/** Phases dans l'ordre de l'hypnogramme (éveil en haut, profond en bas) et leur nom. */
val STAGES = listOf(ActivityKind.AWAKE_SLEEP to "Éveil", ActivityKind.REM_SLEEP to "Paradoxal", ActivityKind.LIGHT_SLEEP to "Léger", ActivityKind.DEEP_SLEEP to "Profond")

/** Minutes d'une phase dans la nuit (le léger compte aussi le sommeil sans phase). */
fun NightSummary.minutesOf(k: ActivityKind): Double = when (k) {
    ActivityKind.AWAKE_SLEEP -> awakeMin
    ActivityKind.REM_SLEEP -> remMin
    ActivityKind.DEEP_SLEEP -> deepMin
    else -> lightMin
}

/**
 * Hypnogramme : un palier par phase, arrondi, de la couleur de la phase, reliés par un trait fin. [axis] : noms des phases
 * à gauche et heures dessous (écran détaillé).
 */
@Composable
fun Hypnogram(night: List<HealthRepository.SleepPhase>, modifier: Modifier, axis: Boolean = false) {
    if (night.isEmpty()) return
    val grid = Mf.colors.divider
    val link = Mf.colors.textTertiary.copy(alpha = 0.5f)
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = ChartStyle.label, color = Mf.colors.textSecondary)
    val staged = Bridge.hasStages(night)
    val names = if (staged) STAGES.map { tr(it.second) } else listOf(tr("Endormi"))
    Canvas(modifier) {
        val t0 = night.first().start; val t1 = night.last().end
        val left = if (axis) names.maxOf { measurer.measure(it, label).size.width } + ChartStyle.axisGap.toPx() else 0f
        val bottom = if (axis) measurer.measure("0", label).size.height + 4.dp.toPx() else 0f
        val w = size.width - left; val h = size.height - bottom
        // sans phases : une seule bande « endormi », les réveils en creux
        val levels = if (staged) 4 else 1
        val rowH = h / levels
        for (k in 0 until levels) {
            drawLine(grid, Offset(left, rowH * (k + 0.5f)), Offset(size.width, rowH * (k + 0.5f)), ChartStyle.grid.toPx(), pathEffect = ChartStyle.dash(this))
            if (axis) {
                val r = measurer.measure(names[k], label)
                drawText(r, topLeft = Offset(0f, rowH * (k + 0.5f) - r.size.height / 2))
            }
        }
        fun row(k: ActivityKind) = if (!staged) 0 else when (k) { ActivityKind.AWAKE_SLEEP -> 0; ActivityKind.REM_SLEEP -> 1; ActivityKind.DEEP_SLEEP -> 3; else -> 2 }
        fun x(t: Long) = left + w * (t - t0) / (t1 - t0).coerceAtLeast(1).toFloat()
        val shown = night.filter { staged || it.kind != ActivityKind.AWAKE_SLEEP }
        val bh = if (staged) rowH * 0.62f else rowH * 0.42f
        // traits verticaux d'une phase à la suivante : la nuit se lit d'un seul tenant
        if (staged) shown.zipWithNext().forEach { (a, b) ->
            if (b.start - a.end < 60_000L) drawLine(link, Offset(x(b.start), rowH * (row(a.kind) + 0.5f)), Offset(x(b.start), rowH * (row(b.kind) + 0.5f)), 1.dp.toPx())
        }
        shown.forEach { p ->
            val x0 = x(p.start); val x1 = x(p.end)
            drawRoundRect(stageColor(p.kind), Offset(x0, rowH * row(p.kind) + (rowH - bh) / 2), Size((x1 - x0).coerceAtLeast(2f), bh), CornerRadius(bh / 2))
        }
        if (axis) {
            // heures pleines, espacées pour tenir dans la largeur
            val hours = Nights.hourMarks(t0, t1)
            val step = ceil(hours.size / 6.0).toInt().coerceAtLeast(1)
            hours.filterIndexed { i, _ -> i % step == 0 }.forEach { z ->
                val r = measurer.measure(app.superhealth.ui.timeOf(z), label)
                val cx = x(z)
                drawText(r, topLeft = Offset((cx - r.size.width / 2).coerceIn(left, size.width - r.size.width), h + 4.dp.toPx()))
            }
        }
    }
}

/** Légende des phases : pastille, nom, durée et part de la nuit ; [usual] : durée habituelle de chaque phase (min). */
@Composable
fun StageLegend(n: NightSummary, usual: Map<ActivityKind, Double>? = null) {
    val total = (n.asleepMin + n.awakeMin).coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        STAGES.forEach { (k, name) ->
            val m = n.minutesOf(k)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(stageColor(k)))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    usual?.get(k)?.let {
                        Text(app.superhealth.ui.trf("hab. %1\$s", formatDuration(it)), style = MaterialTheme.typography.bodySmall,
                            color = Mf.colors.textTertiary, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(formatDuration(m), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("  ${(100 * m / total).roundToInt()} %", style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar((m / total).toFloat(), stageColor(k), Modifier.fillMaxWidth(), height = 5.dp, track = Mf.colors.chip)
            }
        }
    }
}

/**
 * Phases nuit par nuit : barres empilées (profond en bas, puis léger, paradoxal, éveil en haut), une nuit sans phases en
 * une seule couleur ; [need] (min) : besoin en pointillés.
 */
@Composable
fun StageBars(nights: List<NightSummary?>, need: Double?, modifier: Modifier, labels: List<String> = emptyList(), height: Dp = 120.dp) {
    val track = Mf.colors.chip
    val ref = Mf.colors.textSecondary.copy(alpha = 0.7f)
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = ChartStyle.label, color = Mf.colors.textSecondary)
    val max = (nights.mapNotNull { it?.let { n -> n.asleepMin + n.awakeMin } } + listOfNotNull(need)).maxOrNull()?.coerceAtLeast(60.0)?.times(1.08) ?: 480.0
    val labelRow = if (labels.isNotEmpty()) 16.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + labelRow)) {
        val h = height.toPx()
        val n = nights.size.coerceAtLeast(1)
        val slot = size.width / n
        val bw = (slot * ChartStyle.BAR_FILL).coerceAtMost(ChartStyle.barMax.toPx() * 1.4f)
        nights.forEachIndexed { i, night ->
            val x = i * slot + (slot - bw) / 2
            drawRoundRect(track, Offset(x, 0f), Size(bw, h), CornerRadius(bw / 2))
            if (night != null) {
                val parts = if (night.staged) listOf(ActivityKind.DEEP_SLEEP, ActivityKind.LIGHT_SLEEP, ActivityKind.REM_SLEEP, ActivityKind.AWAKE_SLEEP).map { it to night.minutesOf(it) }
                    else listOf(ActivityKind.LIGHT_SLEEP to night.asleepMin)
                val total = parts.sumOf { it.second }
                val top = h - (h * total / max).toFloat()
                // une seule forme arrondie, découpée en tranches : les phases s'empilent sans joints visibles
                clipPathRounded(x, top, bw, h - top) {
                    var y = h
                    parts.forEach { (k, m) ->
                        val ph = (h * m / max).toFloat()
                        drawRect(stageColor(k), Offset(x, y - ph), Size(bw, ph))
                        y -= ph
                    }
                }
            }
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let {
                val r = measurer.measure(it, label)
                drawText(r, topLeft = Offset(i * slot + (slot - r.size.width) / 2, h + 3.dp.toPx()))
            }
        }
        need?.let { val y = h - (h * it / max).toFloat(); drawLine(ref, Offset(0f, y), Offset(size.width, y), ChartStyle.reference.toPx(), pathEffect = ChartStyle.dash(this)) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.clipPathRounded(x: Float, y: Float, w: Float, h: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    if (h <= 0f) return
    val p = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, CornerRadius(w / 2)))
    }
    clipPath(p) { block() }
}

/**
 * Horaires : une barre par nuit du coucher au lever (heure en ordonnée, le soir en haut), coucher et lever habituels
 * (médianes) en pointillés.
 */
@Composable
fun ScheduleChart(nights: List<NightSummary?>, modifier: Modifier, labels: List<String> = emptyList(), height: Dp = 130.dp, color: Color = app.superhealth.ui.theme.HealthHues.Sleep) {
    val track = Mf.colors.chip
    val grid = Mf.colors.divider
    val ref = Mf.colors.textSecondary.copy(alpha = 0.7f)
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = ChartStyle.label, color = Mf.colors.textSecondary)
    val present = nights.filterNotNull()
    if (present.isEmpty()) return
    // bornes à l'heure pleine, de part et d'autre des couchers et levers
    val lo = floor(present.minOf { it.bedClock } / 60) * 60
    val hi = ceil(present.maxOf { it.wakeClock } / 60) * 60
    val medBed = Nights.median(present.map { it.bedClock })
    val medWake = Nights.median(present.map { it.wakeClock })
    val labelRow = if (labels.isNotEmpty()) 16.dp else 0.dp
    Canvas(modifier.fillMaxWidth().height(height + labelRow)) {
        val h = height.toPx()
        val ticks = generateSequence(lo) { it + 60 }.takeWhile { it <= hi }.toList()
        val every = ceil(ticks.size / 5.0).toInt().coerceAtLeast(1)
        val shown = ticks.filterIndexed { i, _ -> i % every == 0 }
        val axisW = shown.maxOf { measurer.measure(Nights.clock(it), label).size.width } + ChartStyle.axisGap.toPx()
        val w = size.width - axisW
        fun y(m: Double) = (h * (m - lo) / (hi - lo).coerceAtLeast(1.0)).toFloat()
        shown.forEach { t ->
            drawLine(grid, Offset(0f, y(t)), Offset(w, y(t)), ChartStyle.grid.toPx())
            val r = measurer.measure(Nights.clock(t), label)
            drawText(r, topLeft = Offset(w + ChartStyle.axisGap.toPx(), (y(t) - r.size.height / 2).coerceIn(0f, h - r.size.height)))
        }
        val n = nights.size.coerceAtLeast(1)
        val slot = w / n
        val bw = (slot * ChartStyle.BAR_FILL).coerceAtMost(ChartStyle.barMax.toPx() * 1.4f)
        nights.forEachIndexed { i, night ->
            val x = i * slot + (slot - bw) / 2
            if (night == null) drawRoundRect(track, Offset(x, y(medBed ?: lo)), Size(bw, (y(medWake ?: hi) - y(medBed ?: lo)).coerceAtLeast(bw)), CornerRadius(bw / 2))
            else drawRoundRect(color, Offset(x, y(night.bedClock)), Size(bw, (y(night.wakeClock) - y(night.bedClock)).coerceAtLeast(bw)), CornerRadius(bw / 2))
            labels.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let {
                val r = measurer.measure(it, label)
                drawText(r, topLeft = Offset(i * slot + (slot - r.size.width) / 2, h + 3.dp.toPx()))
            }
        }
        listOfNotNull(medBed, medWake).forEach { m -> drawLine(ref, Offset(0f, y(m)), Offset(w, y(m)), ChartStyle.reference.toPx(), pathEffect = ChartStyle.dash(this)) }
    }
}

/** Les quatre parties de Repos (0–100) en barres, avec leur poids dans le score. */
@Composable
fun RestPartsRows(p: Scores.RestParts, color: Color, track: Color = Mf.colors.chip) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Durée", p.duration, 50), Triple("Efficacité", p.efficiency, 20), Triple("Sommeil réparateur", p.restorative, 20),
            Triple("Régularité", p.regular, 10)).forEach { (name, v, w) ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(tr(name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(restLevel(v), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Mf.colors.textSecondary, maxLines = 1)
                    Text("  $w %", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar((v / 100).toFloat(), color, Modifier.fillMaxWidth(), height = 6.dp, track = track)
            }
        }
    }
}

/** Partie de Repos en mots (le nombre ne s'affiche que sur l'écran Sommeil). */
fun restLevel(v: Double): String = tr(when { v >= 85 -> "Optimal"; v >= 70 -> "Bon"; v >= 50 -> "Moyen"; else -> "À soigner" })

/** Phrase courte sur le pouls le plus bas de la nuit : tôt (récupération rapide) ou tard. */
fun lowestWhen(n: NightSummary, at: Long): String =
    if (at - n.bedMs < (n.wakeMs - n.bedMs) / 2) tr("Au plus bas en première moitié de nuit") else tr("Au plus bas en fin de nuit")

/** Mise en forme d'une durée ou « — ». */
fun durationOrDash(m: Double?): String = m?.let { formatDuration(it) } ?: "—"

