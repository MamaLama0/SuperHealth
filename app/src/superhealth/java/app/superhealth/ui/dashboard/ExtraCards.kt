package app.superhealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.bridge.Bridge
import app.superhealth.has
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.Metric
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.wellbeing.moodColor
import app.superhealth.wellbeing.Journal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/*
 * Cartes 3.6 : des formes qu'on n'avait pas encore sur l'accueil, sans score — humeur en pastilles (façon « année en
 * pixels »), semaine comparée à la précédente, calendrier des jours actifs (façon heatmap de Strava) et pas heure par
 * heure (façon frise d'activité de Polar).
 */

/** Cinq semaines d'humeur, une pastille par jour, lundi en tête de ligne. */
@Composable
internal fun MoodCard(c: DashCard, modifier: Modifier, nav: NavHostController) {
    val days by Journal.days.collectAsState()
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(4)
    val counts = Journal.counts(today.minusDays(29), today)
    val top = counts.indices.maxByOrNull { counts[it] }?.takeIf { counts[it] > 0 }
    val empty = Mf.colors.chip
    Tile(c, tr("Humeur"), modifier, onClick = { nav.go("journal") }) {
        Value(top?.let { tr(Journal.MOODS[it]) } ?: "—", color = c.ink().onContainer())
        Sub(if (top == null) tr("Notez votre moral pour remplir la grille") else trf("Le plus fréquent · %1\$s jours notés sur 30", counts.sum()))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { d ->
                Text(dayLetter(start.plusDays(d.toLong())), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Mf.colors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..4).forEach { w ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..6).forEach { d ->
                        val day = start.plusDays(w * 7L + d)
                        val mood = days[day.toEpochDay()]?.mood
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .background(when { day.isAfter(today) -> Color.Transparent; mood != null -> moodColor(mood); else -> empty })
                            .then(if (day == today) Modifier.border(1.5.dp, Mf.colors.text, CircleShape) else Modifier))
                    }
                }
            }
        }
    }
}

private class WeekRow(val label: String, val now: Double?, val before: Double?, val show: (Double) -> String)

/** Cette semaine face à la précédente, aux mêmes jours (lundi → aujourd'hui) : moyennes par jour et minutes d'activité. */
@Composable
internal fun WeekCompareCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val an by vm.nutrition.analytics.collectAsState()
    val s by vm.nutrition.settings.collectAsState()
    val sh by vm.settings.collectAsState()
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val elapsed = (today.toEpochDay() - monday.toEpochDay()).toInt()
    val rows by produceState(emptyList<WeekRow>(), refresh, an) {
        value = withContext(Dispatchers.IO) {
            fun span(weeksAgo: Long) = (0..elapsed).map { monday.minusWeeks(weeksAgo).plusDays(it.toLong()) }
            suspend fun avg(m: Metric, ds: List<LocalDate>) = coroutineScope {
                ds.map { d -> async { runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull() } }.awaitAll().filterNotNull().filter { it > 0 }
                    .takeIf { it.isNotEmpty() }?.average()
            }
            suspend fun active(weeksAgo: Long): Double? = runCatching {
                val from = HealthRepository.startMs(monday.minusWeeks(weeksAgo))
                Bridge.activityFacts(from, HealthRepository.endMs(today.minusWeeks(weeksAgo))).sumOf { (it.end - it.start) / 60_000.0 }
            }.getOrNull()
            buildList {
                if (sh.has(app.superhealth.Feature.HEALTH) || Bridge.hasSource(Metric.STEPS))
                    add(WeekRow(tr("Pas par jour"), avg(Metric.STEPS, span(0)), avg(Metric.STEPS, span(1))) { formatNumber(it) })
                if (Bridge.hasSource(Metric.SLEEP))
                    add(WeekRow(tr("Sommeil par nuit"), avg(Metric.SLEEP, span(0)), avg(Metric.SLEEP, span(1))) { formatDuration(it) })
                add(WeekRow(tr("Activité"), active(0), active(1)) { trf("%1\$s min", it.roundToInt()) })
                an?.takeIf { sh.has(app.superhealth.Feature.NUTRITION) }?.let { a ->
                    fun kcal(ds: List<LocalDate>) = ds.mapNotNull { d -> a.days.firstOrNull { it.epochDay == d.toEpochDay() }?.intakeKcal }
                        .filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
                    add(WeekRow(tr("Calories par jour"), kcal(span(0)), kcal(span(1))) { formatNumber(s.energyValue(it)) + " " + tr(s.energyUnit.label) })
                }
            }
        }
    }
    Tile(c, tr("Cette semaine"), modifier, trailing = tr("vs semaine dernière"), onClick = onOpen) {
        if (rows.isEmpty()) Sub(tr("Pas encore assez de données"))
        rows.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.now?.let(r.show) ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                // flèche neutre : on montre le sens, sans juger
                val pct = if (r.now != null && r.before != null && r.before > 0) ((r.now - r.before) / r.before * 100).roundToInt() else null
                Row(Modifier.width(64.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (pct != null) {
                        Icon(when { pct > 3 -> Icons.AutoMirrored.Rounded.TrendingUp; pct < -3 -> Icons.AutoMirrored.Rounded.TrendingDown
                            else -> Icons.AutoMirrored.Rounded.TrendingFlat }, null, Modifier.padding(end = 2.dp), tint = c.ink().onContainer())
                        Text((if (pct > 0) "+" else "") + "$pct %", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Douze semaines de jours actifs : une case par jour, plus foncée avec les minutes d'activité. */
@Composable
internal fun ActiveDaysCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, onOpen: () -> Unit) {
    val refresh by vm.refresh.collectAsState()
    val today = LocalDate.now()
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
    val minutes by produceState(emptyMap<Long, Double>(), refresh) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Bridge.activityFacts(HealthRepository.startMs(start)).groupBy { app.superhealth.ui.localDate(it.end).toEpochDay() }
                    .mapValues { e -> e.value.sumOf { (it.end - it.start) / 60_000.0 } }
            }.getOrDefault(emptyMap())
        }
    }
    val last28 = (0L..27L).count { (minutes[today.minusDays(it).toEpochDay()] ?: 0.0) >= 10 }
    val ink = c.ink()
    val empty = Mf.colors.chip
    Tile(c, tr("Jours actifs"), modifier, onClick = onOpen) {
        Value("$last28", tr("sur 28 jours"), color = ink.onContainer())
        Sub(trf("%1\$s min d'activité cette semaine", (0..(today.dayOfWeek.value - 1)).sumOf { (minutes[today.minusDays(it.toLong()).toEpochDay()] ?: 0.0) }.roundToInt()))
        Spacer(Modifier.height(10.dp))
        // colonnes = semaines (la plus ancienne à gauche), lignes = lundi → dimanche
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0..11).forEach { w ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0..6).forEach { d ->
                        val day = start.plusDays(w * 7L + d)
                        val m = minutes[day.toEpochDay()] ?: 0.0
                        val tone = when { day.isAfter(today) -> Color.Transparent; m <= 0 -> empty; m < 20 -> ink.copy(alpha = 0.3f)
                            m < 45 -> ink.copy(alpha = 0.55f); m < 90 -> ink.copy(alpha = 0.8f); else -> ink }
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(tone))
                    }
                }
            }
        }
    }
}

/** Pas d'aujourd'hui heure par heure : on lit chaque heure au doigt. */
@Composable
internal fun HourlyStepsCard(c: DashCard, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val today = LocalDate.now()
    val hours by produceState(List<Double?>(24) { null }, refresh) {
        value = withContext(Dispatchers.IO) {
            val pts = runCatching { Bridge.hourlySteps(ctx, today) }.getOrDefault(emptyList())
            (0..23).map { h -> pts.filter { java.time.Instant.ofEpochMilli(it.timestampMs).atZone(java.time.ZoneId.systemDefault()).hour == h }.sumOf { it.value }.takeIf { it > 0 } }
        }
    }
    val total = hours.sumOf { it ?: 0.0 }
    val peak = hours.indices.maxByOrNull { hours[it] ?: 0.0 }?.takeIf { (hours[it] ?: 0.0) > 0 }
    Tile(c, tr("Pas par heure"), modifier, onClick = { nav.go("h/metric/STEPS") }) {
        Value(formatNumber(total), tr("pas"), color = c.ink().onContainer())
        Sub(peak?.let { trf("Heure la plus active : %1\$s h", it) } ?: tr("Pas encore de pas aujourd'hui"))
        Spacer(Modifier.height(10.dp))
        BarChart(hours, hours.map { null }, c.ink(), height = 64.dp, track = softTrack(), labels = (0..23).map { if (it % 6 == 0) "$it h" else "" },
            valueFormat = { trf("%1\$s pas", formatNumber(it)) }, pointLabel = { "$it h – ${it + 1} h" })
    }
}
