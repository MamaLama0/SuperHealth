package app.superhealth.ui.health

import app.superhealth.ui.coach.askCoach
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Aggregation
import app.superhealth.health.data.ChartKind
import app.superhealth.health.data.DataSource
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLog
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.ui.charts.BarChart
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.Lang
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.go
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatValue
import app.superhealth.ui.formatWithUnit
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.Mf
import app.superhealth.bridge.LiveHeart
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.unitOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private enum class Span(val label: String) { DAY("Jour"), WEEK("Semaine"), MONTH("Mois"), QUARTER("3 mois"), YEAR("Année") }

private data class Period(val from: LocalDate, val to: LocalDate)

private fun period(span: Span, offset: Int, today: LocalDate): Period = when (span) {
    Span.DAY -> today.minusDays(offset.toLong()).let { Period(it, it) }
    Span.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong()).let { Period(it, it.plusDays(6)) }
    Span.MONTH -> YearMonth.from(today).minusMonths(offset.toLong()).let { Period(it.atDay(1), it.atEndOfMonth()) }
    Span.QUARTER -> YearMonth.from(today).minusMonths(offset * 3L).let { Period(it.minusMonths(2).atDay(1), it.atEndOfMonth()) }
    Span.YEAR -> YearMonth.from(today).minusMonths(offset * 12L).let { Period(it.minusMonths(11).atDay(1), it.atEndOfMonth()) }
}

/** Détail d'une mesure santé (montre, Health Connect ou saisie) : période, graphique, moyenne, saisies. */
@Composable
fun MetricScreen(vm: SuperViewModel, nav: NavHostController, metric: Metric) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val intradayOk = metric in setOf(Metric.STEPS, Metric.HEART_RATE, Metric.STRESS, Metric.BODY_ENERGY, Metric.SPO2) &&
        (HealthRepository.isSupported(metric) || metric == Metric.STEPS && app.superhealth.SuperPrefs.hc.value.stepsHourly.isNotEmpty())
    var span by remember { mutableStateOf(if (intradayOk) Span.DAY else Span.WEEK) }
    var offset by remember { mutableIntStateOf(0) }
    var logging by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val p = period(span, offset, today)

    val days by produceState(emptyList<Pair<LocalDate, Double?>>(), metric, span, offset, refresh) {
        // Toute la période (les jours à venir restent vides) pour garder des barres de largeur constante.
        val list = generateSequence(p.from) { it.plusDays(1) }.takeWhile { !it.isAfter(p.to) }.toList()
        value = coroutineScope {
            list.map { d -> async(Dispatchers.IO) { d to if (d.isAfter(today)) null else runCatching { Bridge.healthValue(ctx, metric, d) }.getOrNull() } }.awaitAll()
        }
    }
    val intraday by produceState(emptyList<Point>(), metric, span, offset, refresh) {
        value = if (span == Span.DAY && intradayOk) runCatching {
            // Pas : montre et téléphone fusionnés heure par heure, comme le total du jour.
            if (metric == Metric.STEPS) Bridge.hourlySteps(ctx, p.from) else HealthRepository.intraday(metric, p.from)
        }.getOrDefault(emptyList()) else emptyList()
    }
    // Source du dernier jour mesuré de la période (la montre, ou l'appli qui l'a écrit dans Health Connect).
    val lastDay = days.lastOrNull { it.second != null }?.first
    val source by produceState<DataSource?>(null, metric, lastDay, refresh) {
        value = lastDay?.let { runCatching { Bridge.dataSource(metric, it) }.getOrNull() }
    }
    // Sommeil : le score de chaque nuit de la période, quand une montre le calcule.
    val scores by produceState(emptyList<Pair<LocalDate, Double>>(), metric, span, offset, refresh) {
        value = if (metric == Metric.SLEEP && Bridge.hasSource(Metric.SLEEP_SCORE)) coroutineScope {
            generateSequence(p.from) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(p.to, today)) }.toList()
                .map { d -> async(Dispatchers.IO) { runCatching { Bridge.healthValue(ctx, Metric.SLEEP_SCORE, d) }.getOrNull()?.let { d to it } } }
                .awaitAll().filterNotNull()
        } else emptyList()
    }
    val scoreSource by produceState<DataSource?>(null, scores.lastOrNull()?.first, refresh) {
        value = scores.lastOrNull()?.first?.let { runCatching { Bridge.dataSource(Metric.SLEEP_SCORE, it) }.getOrNull() }
    }
    val logs by produceState(emptyList<ManualLog>(), metric, span, offset, refresh) {
        value = if (metric.manual) ManualLogStore.get(ctx).query(metric, HealthRepository.startMs(p.from), HealthRepository.endMs(p.to)) else emptyList()
    }

    DetailScaffold(tr(metric.label), onBack = { nav.popBackStack() }, action = {
        // module Coach : « explique-moi cette mesure » avec mes données
        if (app.superhealth.ui.coach.coachAvailable()) IconButton(onClick = {
            val q = app.superhealth.coach.Coach.prompts()?.metric?.takeIf { it.isNotBlank() }?.replace("{metric}", tr(metric.label))
                ?: app.superhealth.ui.trf("Explique-moi l'évolution de ma mesure « %1\$s ».", tr(metric.label))
            nav.askCoach(q)
        }) { Icon(Icons.Rounded.AutoAwesome, tr("Demander au coach"), tint = app.superhealth.ui.coach.CoachHue) }
        if (metric.manual) IconButton(onClick = { logging = true }) { Icon(Icons.Rounded.Add, tr("Enregistrer")) }
    }) {
        val spans = if (intradayOk) Span.entries else Span.entries - Span.DAY
        ChipChoice(spans, span, { tr(it.label) }) { span = it; offset = 0 }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Précédent")) { offset++ }
            Text(periodLabel(span, p), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Suivant"), enabled = offset > 0) { if (offset > 0) offset-- }
        }
        Spacer(Modifier.height(12.dp))

        val values = days.mapNotNull { it.second }
        val total = aggregate(metric, values)
        MfCard {
            Text(when (metric.aggregation) {
                Aggregation.SUM -> if (span == Span.DAY) tr("Total") else tr("Moyenne par jour")
                Aggregation.AVERAGE -> tr("Moyenne")
                Aggregation.LAST -> tr("Dernière valeur")
            }, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(total?.let { formatValue(metric, it) } ?: tr("Aucune donnée"), style = MaterialTheme.typography.displaySmall, maxLines = 1)
                if (total != null) unitOf(metric)?.let {
                    Text(" $it", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            if (total != null) source?.let { SourceLine(it) }
            Spacer(Modifier.height(14.dp))
            val color = metric.color
            when {
                span == Span.DAY && intraday.isNotEmpty() -> if (metric == Metric.STEPS) {
                    val byHour = (0..23).map { h -> intraday.filter { java.time.Instant.ofEpochMilli(it.timestampMs).atZone(java.time.ZoneId.systemDefault()).hour == h }.sumOf { it.value }.takeIf { v -> v > 0 } }
                    BarChart(byHour, byHour.map { null }, color, height = 150.dp, labels = (0..23).map { if (it % 6 == 0) "$it" else "" })
                } else if (metric == Metric.HEART_RATE) {
                    val start = HealthRepository.startMs(p.from)
                    HeartChart(intraday.map { it.timestampMs to it.value }, color, height = 170.dp, from = start, to = start + 86_400_000L,
                        xLabels = listOf(0, 6, 12, 18).map { start + it * 3_600_000L to "${it}h" })
                } else {
                    val start = HealthRepository.startMs(p.from) / 60_000
                    LineChart(listOf(Series(intraday.map { it.timestampMs / 60000 to it.value }, color, gap = 60.0)), height = 150.dp,
                        xFrom = start.toDouble(), xTo = (start + 1440).toDouble(), xLabels = listOf(0, 6, 12, 18).map { start + it * 60 to "${it}h" })
                }
                span == Span.DAY -> Text(tr("Pas de détail heure par heure pour ce jour."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
                span == Span.QUARTER || span == Span.YEAR -> {
                    // Agrégat par semaine (3 mois) ou par mois (année) pour garder des barres lisibles.
                    val buckets = days.groupBy { (d, _) -> if (span == Span.YEAR) d.withDayOfMonth(1) else d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
                    val keys = buckets.keys.sorted()
                    val vs = keys.map { k -> aggregate(metric, buckets[k].orEmpty().mapNotNull { it.second }) }
                    val labels = keys.mapIndexed { i, k -> if (span == Span.YEAR) k.month.getDisplayName(java.time.format.TextStyle.NARROW, Lang.locale) else if (i % 3 == 0) "${k.dayOfMonth}/${k.monthValue}" else "" }
                    if (metric.chart == ChartKind.BARS) BarChart(vs, vs.map { null }, color, height = 150.dp, labels = labels)
                    else LineChart(listOf(Series(keys.zip(vs).mapNotNull { (k, v) -> v?.let { k.toEpochDay() to it } }, color, markers = true)), height = 150.dp)
                }
                metric.chart == ChartKind.BARS -> {
                    val goal = HealthRepository.goal(metric)?.takeIf { metric != Metric.CARDIO_WEEK }
                    BarChart(days.map { it.second?.let { v -> if (metric == Metric.SLEEP) v / 60 else v } },
                        days.map { goal?.let { g -> if (metric == Metric.SLEEP) g / 60 else g } }, color, height = 150.dp,
                        labels = days.mapIndexed { i, (d, _) -> if (span == Span.WEEK) dayLetter(d) else if (i % 7 == 0) "${d.dayOfMonth}" else "" })
                }
                else -> LineChart(listOf(Series(days.mapNotNull { (d, v) -> v?.let { d.toEpochDay() to it } }, color, markers = days.size <= 31, gap = 3.0)), height = 150.dp)
            }
            if (values.size >= 2) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat(tr("Min."), formatWithUnit(metric, values.min()), Modifier.weight(1f))
                    Stat(tr("Max."), formatWithUnit(metric, values.max()), Modifier.weight(1f))
                    Stat(tr("Jours"), "${values.size}", Modifier.weight(1f))
                }
            }
        }

        if (scores.isNotEmpty()) SleepScoreCard(scores, span, scoreSource) { nav.go("h/metric/SLEEP_SCORE") }
        if (metric == Metric.HEART_RATE && LiveHeart.available()) LiveHeartCard()

        if (metric.manual) {
            SectionLabel(tr("Saisies"))
            if (logs.isEmpty()) Text(tr("Aucune saisie sur cette période."), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
            else GroupCard {
                logs.forEachIndexed { i, l ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatWithUnit(metric, l.value), style = MaterialTheme.typography.bodyLarge)
                            val d = app.superhealth.ui.localDate(l.timestampMs)
                            Text(relativeDay(d) + " · " + timeOf(l.timestampMs), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        IconButton(onClick = { vm.deleteLog(l.id) }) { Icon(Icons.Rounded.DeleteOutline, tr("Supprimer"), tint = Mf.colors.textSecondary) }
                    }
                }
            }
        }

        metric.about?.let {
            SectionLabel(trf("À propos : %1\$s", tr(metric.label).lowercase(Lang.locale)))
            MfCard { Text(tr(it), style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Ces mesures servent au suivi personnel et ne remplacent pas un avis médical."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (logging) LogSheet(vm, initial = metric, onDismiss = { logging = false })
}

private fun aggregate(metric: Metric, vs: List<Double>): Double? = if (vs.isEmpty()) null else when (metric.aggregation) {
    Aggregation.SUM, Aggregation.AVERAGE -> vs.average()
    Aggregation.LAST -> vs.last()
}

private fun periodLabel(span: Span, p: Period): String {
    val loc = Lang.locale
    return when (span) {
        Span.DAY -> relativeDay(p.from)
        Span.WEEK -> p.from.format(DateTimeFormatter.ofPattern("d MMM", loc)) + " – " + p.to.format(DateTimeFormatter.ofPattern("d MMM", loc))
        Span.MONTH -> p.from.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc)).replaceFirstChar { it.uppercase(loc) }
        Span.QUARTER, Span.YEAR -> p.from.format(DateTimeFormatter.ofPattern("MMM yyyy", loc)) + " – " + p.to.format(DateTimeFormatter.ofPattern("MMM yyyy", loc))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.chip).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = if (enabled) Mf.colors.text else Mf.colors.textTertiary)
    }
}

/** Fréquence cardiaque en direct : la montre envoie ses mesures tant que la carte est ouverte. */
@Composable
private fun LiveHeartCard() {
    var on by remember { mutableStateOf(false) }
    val pts = remember { androidx.compose.runtime.mutableStateListOf<Pair<Long, Double>>() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (on) androidx.compose.runtime.LaunchedEffect(Unit) {
        LiveHeart.samples.collect { s -> now = s.timeMs; s.bpm?.let { pts += s.timeMs to it.toDouble() } }
    }
    SectionLabel(tr("En direct"))
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(pts.lastOrNull()?.second?.let { "${it.toInt()}" } ?: "—", style = MaterialTheme.typography.displaySmall)
                    Text(" " + tr("bpm"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(tr(if (on) "Mesures envoyées par la montre en temps réel" else "Touchez Démarrer pour suivre votre cœur en direct"),
                    style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            app.superhealth.nutrition.ui.components.PillButton(tr(if (on) "Arrêter" else "Démarrer"), strong = !on) { on = !on; if (!on) pts.clear() }
        }
        if (on) {
            Spacer(Modifier.height(12.dp))
            val window = 5 * 60_000L
            HeartChart(pts.filter { it.first >= now - window }, Metric.HEART_RATE.color, height = 150.dp, from = now - window, to = now,
                gapMs = 60_000L, live = true)
        }
    }
}

/** « ⌚ Montre · Forerunner 165 · Garmin Connect » : type d'appareil et nom de la source d'une mesure. */
@Composable
internal fun SourceLine(src: DataSource, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(src.kind.icon, null, Modifier.size(14.dp), tint = Mf.colors.textSecondary)
        Spacer(Modifier.width(6.dp))
        Text((listOf(tr(src.kind.label)) + src.parts()).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Score de sommeil calculé par la montre : nuit la plus récente de la période, moyenne et appareil qui l'a calculé. */
@Composable
private fun SleepScoreCard(scores: List<Pair<LocalDate, Double>>, span: Span, src: DataSource?, onClick: () -> Unit) {
    SectionLabel(tr("Score de sommeil"))
    MfCard(onClick = onClick) {
        val (day, last) = scores.last()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatValue(Metric.SLEEP_SCORE, last), style = MaterialTheme.typography.displaySmall, color = Metric.SLEEP_SCORE.color, maxLines = 1)
            Text(" / 100", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            if (span != Span.DAY && scores.size >= 2)
                Text(trf("Moy. %1\$s", formatValue(Metric.SLEEP_SCORE, scores.map { it.second }.average())),
                    style = MaterialTheme.typography.titleSmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (span != Span.DAY) Text(relativeDay(day), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        src?.let { SourceLine(it) }
    }
}
