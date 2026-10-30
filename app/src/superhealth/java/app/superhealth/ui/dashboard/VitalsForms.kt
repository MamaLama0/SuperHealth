package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightVitals
import app.superhealth.ui.formatValue
import app.superhealth.ui.health.RangeBar
import app.superhealth.ui.health.VitalTrend
import app.superhealth.ui.health.color
import app.superhealth.ui.health.word
import app.superhealth.ui.theme.CardRadius
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.dashCardBackground
import app.superhealth.ui.theme.dataBrush
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.unitOf
import java.time.LocalDate

/*
 * 2.4 : les trois cartes de constantes (Constantes, Signes vitaux de la nuit, Tendances) parlent la même langue que les
 * autres widgets. Une mesure = symbole, valeur, unité, pastille de statut (vert dans l'habituel, ambre au-dehors).
 * Petit : trois mesures. Moyen : quatre cellules avec leur plage. Bandeau : le verdict en mots et trois valeurs.
 * Grand : une ligne par mesure (courbe ou barre de plage). Ce qui les distingue : la période (l'instant, la nuit, 30 jours).
 */

/** Constantes du jour : dernière valeur, 14 derniers jours, plage tirée des valeurs précédentes (même règle que la nuit). */
internal fun vitalsNow(metrics: List<Metric>, series: (Metric) -> List<Pair<LocalDate, Double?>>): List<VitalTrend> = metrics.map { m ->
    val s = series(m)
    val lastIdx = s.indexOfLast { it.second != null }
    val past = if (lastIdx > 0) s.subList(0, lastIdx).mapNotNull { it.second } else emptyList()
    VitalTrend(m, s.takeLast(14), NightVitals.range(past, m))
}

/** Signes vitaux d'une nuit : une valeur par mesure, face à la plage des 28 nuits précédentes. */
internal fun vitalsOfNight(n: NightVitals.Night?): List<VitalTrend> = n?.items.orEmpty().filter { it.value != null }.map { i ->
    VitalTrend(i.metric, listOf(n!!.date to i.value), if (i.low != null && i.high != null) i.low to i.high else null)
}

/** Nom court d'une mesure (cellules et bandeau). */
internal fun Metric.shortName(): String = when (this) {
    Metric.RESTING_HR -> tr("FC repos"); Metric.HRV -> tr("VFC"); Metric.RESPIRATORY_RATE -> tr("Respiration"); Metric.SPO2 -> "SpO₂"
    Metric.TEMPERATURE -> tr("Température"); Metric.SLEEP -> tr("Sommeil"); Metric.VO2MAX -> "VO₂ max"; Metric.HEART_RATE -> tr("Pouls")
    Metric.STEPS -> tr("Pas")
    else -> tr(label)
}

/** Verdict d'ensemble : « Normal », ou le nombre de mesures hors de la plage ; null sans plage connue. */
private fun verdict(items: List<VitalTrend>): Pair<String, Color>? {
    val known = items.filter { it.status != NightVitals.Status.UNKNOWN }
    if (known.isEmpty()) return null
    val out = known.count { it.status == NightVitals.Status.HIGH || it.status == NightVitals.Status.LOW }
    return if (out == 0) tr("Normal") to NightVitals.Status.TYPICAL.color() else trf("%1\$s hors plage", out) to NightVitals.Status.HIGH.color()
}

/**
 * Une carte de constantes dans ses quatre tailles. [spark] : la ligne du grand porte la courbe de la période (sinon la
 * barre de plage) ; [span] : la période affichée à droite de l'en-tête du grand (« 30 j »).
 */
@Composable
internal fun VitalsKitCard(c: DashCard, size: CardSize, title: String, items: List<VitalTrend>, spark: Boolean, span: String?, lines: Boolean = false, empty: String,
                           modifier: Modifier, onOpen: () -> Unit, onMetric: ((Metric) -> Unit)? = null) {
    val hue = c.ink()
    val rows = items.filter { it.last != null }
    val v = verdict(rows)
    val box = Modifier.clip(RoundedCornerShape(CardRadius)).dashCardBackground(hue).cardClick(onOpen)
    when (size) {
        CardSize.S -> Column(modifier.square().then(box).padding(12.dp)) {
            CardHead(c.icon(), c.short(), hue)
            Spacer(Modifier.weight(1f))
            if (rows.isEmpty()) Note(empty)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { rows.take(3).forEach { InlineVital(it, 15.sp) } }
        }
        CardSize.W -> Row(modifier.fillMaxWidth().heightIn(min = 72.dp).then(box).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rows.isEmpty()) Note(empty)
            rows.take(3).forEach { VitalTile(it, Modifier.weight(1f), false, compact = true) }
        }
        CardSize.M -> Column(modifier.fillMaxWidth().heightIn(min = 118.dp).then(box).padding(14.dp)) {
            CardHead(c.icon(), c.short(), hue, v?.first, v?.second)
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) Note(empty)
            Grid(rows.take(4), false, onMetric)
        }
        CardSize.L -> Column(modifier.fillMaxWidth().then(box).padding(16.dp)) {
            CardHead(c.icon(), title, hue, span, Mf.colors.textSecondary)
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) { Note(empty); return@Column }
            v?.let { (word, col) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (col == NightVitals.Status.TYPICAL.color()) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = col)
                    Spacer(Modifier.width(6.dp))
                    Text(if (word == tr("Normal")) tr("Dans votre plage habituelle") else word, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = col, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (lines) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { rows.take(5).forEach { Line(it, spark, onMetric) } }
            else Grid(rows.take(6), spark, onMetric)
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 3)
}

/** Symbole, valeur (et unité), pastille de statut : la mesure en une ligne. */
@Composable
private fun InlineVital(t: VitalTrend, size: androidx.compose.ui.unit.TextUnit, unit: Boolean = true) {
    val m = t.metric
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(m.icon, null, Modifier.size(14.dp), tint = m.color)
        Spacer(Modifier.width(5.dp))
        Text(formatValue(m, t.last!!.second), style = MaterialTheme.typography.titleMedium, fontSize = size, fontWeight = FontWeight.Bold,
            color = m.color, maxLines = 1)
        // unités courtes seulement (bpm, ms, %) : « resp./min » ne tient pas dans un petit
        if (unit) unitOf(m)?.takeIf { it.length <= 3 }?.let { Text(" $it", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
        Spacer(Modifier.width(5.dp))
        Dot(t.status)
    }
}

@Composable
private fun Dot(s: NightVitals.Status) {
    if (s != NightVitals.Status.UNKNOWN) Box(Modifier.size(6.dp).clip(CircleShape).background(s.color()))
}

/** Cellule du moyen : nom court, valeur et unité, barre de plage (ou rien sans plage). */
@Composable
private fun Cell(t: VitalTrend, modifier: Modifier) {
    val m = t.metric
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(12.dp), tint = m.color)
            Spacer(Modifier.width(4.dp))
            Text(m.shortName(), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CardValue(formatValue(m, t.last!!.second), unitOf(m), 19.sp)
        RangeBar(t.last!!.second, t.range?.first, t.range?.second, t.status.color(), Modifier.padding(top = 2.dp))
    }
}

/** Ligne du grand : symbole, nom et statut, courbe (ou barre de plage), valeur. */
@Composable
private fun Line(t: VitalTrend, spark: Boolean, onMetric: ((Metric) -> Unit)?) {
    val m = t.metric
    val last = t.last!!
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(if (onMetric != null) Modifier.clickable { onMetric(m) } else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(16.dp), tint = m.color)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(m.label), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                t.status.word()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = t.status.color(), maxLines = 1) }
            }
            if (spark) TrendSpark(t, Modifier.padding(horizontal = 8.dp).width(84.dp).height(28.dp))
            Row(Modifier.width(if (spark) 92.dp else 104.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                Text(formatValue(m, last.second), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                unitOf(m)?.let { Text(" $it", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 2.dp)) }
            }
        }
        if (!spark) RangeBar(last.second, t.range?.first, t.range?.second, t.status.color(), Modifier.padding(top = 5.dp))
    }
}

/**
 * Courbe de la période posée sur la bande habituelle de la mesure (gris neutre : une bande teintée vire au brun sur fond
 * sombre). Le dernier point est marqué dans la couleur du statut.
 */
@Composable
internal fun TrendSpark(t: VitalTrend, modifier: Modifier) {
    val hue = t.metric.color
    val band = Mf.colors.chip
    val dot = if (t.status == NightVitals.Status.UNKNOWN) hue else t.status.color()
    val halo = Mf.colors.card
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
        drawPath(path, dataBrush(hue, Offset.Zero, Offset(size.width, 0f)), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        last?.let { drawCircle(halo, 4.dp.toPx(), it); drawCircle(dot, 2.8.dp.toPx(), it) }
    }
}

/** Grille de constantes sur deux colonnes. */
@Composable
private fun Grid(rows: List<VitalTrend>, spark: Boolean, onMetric: ((Metric) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t -> VitalTile(t, Modifier.weight(1f).then(if (onMetric != null) Modifier.clip(RoundedCornerShape(18.dp)).clickable { onMetric(t.metric) } else Modifier), spark) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Une constante, tuile douce posée dans la carte : symbole et nom, valeur dans sa couleur, tendance (ou plage habituelle) dessous. */
@Composable
private fun VitalTile(t: VitalTrend, modifier: Modifier, spark: Boolean, compact: Boolean = false) {
    val m = t.metric
    val narrow = LocalCardHalf.current || compact
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Mf.colors.chip).padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, Modifier.size(14.dp), tint = m.color)
            Spacer(Modifier.width(4.dp))
            // tuile étroite : le symbole et l'unité suffisent, le nom serait coupé
            Text(if (narrow) unitOf(m).orEmpty() else m.shortName(), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(formatValue(m, t.last!!.second), style = MaterialTheme.typography.titleMedium, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold, color = m.color, maxLines = 1,
                modifier = Modifier.alignByBaseline())
            if (!narrow) unitOf(m)?.takeIf { it.length <= 4 }?.let { Text(" $it", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1, modifier = Modifier.alignByBaseline()) }
        }
        if (!compact) {
            if (spark) TrendSpark(t, Modifier.padding(top = 4.dp).fillMaxWidth().height(30.dp))
            else RangeBar(t.last!!.second, t.range?.first, t.range?.second, t.status.color(), Modifier.padding(top = 4.dp))
        }
    }
}
