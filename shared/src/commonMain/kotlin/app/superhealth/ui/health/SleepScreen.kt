package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.DataSource
import app.superhealth.health.data.Metric
import app.superhealth.health.data.NightSummary
import app.superhealth.health.data.NightVitals
import app.superhealth.health.data.Nights
import app.superhealth.health.data.Scores
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.Lang
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.charts.BarChart
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.DayOfWeek
import app.superhealth.platform.LocalDate
import app.superhealth.platform.YearMonth
import app.superhealth.platform.DateTimeFormatter
import app.superhealth.platform.TemporalAdjusters
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import app.superhealth.health.data.ActivityKind

/*
 * Écran Sommeil : la nuit en détail (hypnogramme, phases face à vos habitudes, parties de Repos, cœur et signes vitaux de
 * la nuit), puis la semaine, le mois et l'année (phases nuit par nuit, horaires, Repos). Ouvert par toutes les cartes et
 * widgets de sommeil.
 */

private enum class SleepSpan(val label: String) { NIGHT("Nuit"), WEEK("Semaine"), MONTH("Mois"), YEAR("Année") }

private val SleepHue = HealthHues.Sleep

@Composable
fun SleepScreen(vm: SuperViewModel, nav: NavHostController) {
    var span by remember { mutableStateOf(SleepSpan.NIGHT) }
    var offset by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val (from, to) = when (span) {
        SleepSpan.NIGHT -> today.minusDays(offset.toLong()).let { it to it }
        SleepSpan.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong()).let { it to it.plusDays(6) }
        SleepSpan.MONTH -> YearMonth.from(today).minusMonths(offset.toLong()).let { it.atDay(1) to it.atEndOfMonth() }
        SleepSpan.YEAR -> YearMonth.from(today).minusMonths(offset * 12L).let { it.minusMonths(11).atDay(1) to it.atEndOfMonth() }
    }
    DetailScaffold(tr("Sommeil"), onBack = { nav.popBackStack() }, action = {
        if (app.superhealth.ui.coach.coachAvailable()) IconButton(onClick = {
            val q = app.superhealth.coach.Coach.prompts()?.metric?.takeIf { it.isNotBlank() }?.replace("{metric}", tr("Sommeil"))
                ?: trf("Explique-moi l'évolution de ma mesure « %1\$s ».", tr("Sommeil"))
            nav.askCoach(q)
        }) { Icon(Icons.Rounded.AutoAwesome, tr("Demander au coach"), tint = app.superhealth.ui.coach.CoachHue) }
    }) {
        ChipChoice(SleepSpan.entries, span, { tr(it.label) }) { span = it; offset = 0 }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Précédent")) { offset++ }
            Text(spanLabel(span, from, to), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Suivant"), enabled = offset > 0) { if (offset > 0) offset-- }
        }
        Spacer(Modifier.height(12.dp))
        when (span) {
            SleepSpan.NIGHT -> NightView(vm, nav, from)
            SleepSpan.WEEK, SleepSpan.MONTH -> NightsView(vm, nav, from, to, span == SleepSpan.WEEK)
            SleepSpan.YEAR -> YearView(vm, from, to)
        }
        SectionLabel(trf("À propos : %1\$s", tr("Sommeil").lowercase(Lang.locale)))
        MfCard { Text(tr(Metric.SLEEP.about!!), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(12.dp))
        Text(tr("Ces mesures servent au suivi personnel et ne remplacent pas un avis médical."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private fun spanLabel(span: SleepSpan, from: LocalDate, to: LocalDate): String {
    val loc = Lang.locale
    return when (span) {
        // la nuit porte le nom du jour où elle se termine
        SleepSpan.NIGHT -> trf("Nuit du %1\$s au %2\$s", from.minusDays(1).format(DateTimeFormatter.ofPattern("EEE d", loc)),
            from.format(DateTimeFormatter.ofPattern("EEE d MMM", loc)))
        SleepSpan.WEEK -> from.format(DateTimeFormatter.ofPattern("d MMM", loc)) + " – " + to.format(DateTimeFormatter.ofPattern("d MMM", loc))
        SleepSpan.MONTH -> from.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc)).replaceFirstChar { it.uppercase(loc) }
        SleepSpan.YEAR -> from.format(DateTimeFormatter.ofPattern("MMM yyyy", loc)) + " – " + to.format(DateTimeFormatter.ofPattern("MMM yyyy", loc))
    }
}

// ---- Une nuit ------------------------------------------------------------------------------

private class NightData(
    val night: NightSummary?,
    val parts: Scores.RestParts?,
    val score: Double?,
    val watchScore: Double?,
    val needMin: Double,
    val debt: Double?,
    val heart: List<Pair<Long, Int>>,
    val usual: Map<ActivityKind, Double>?,
    val vitals: NightVitals.Night?,
    val source: DataSource?,
    val compare: Map<Scores.SleepPart, Int>?,
)

/** Dernière nuit lue : réaffichée au retour sur l'écran, le temps de relire (pas de clignotement). */
private var lastNightData: Pair<LocalDate, NightData>? = null

@Composable
private fun NightView(vm: SuperViewModel, nav: NavHostController, date: LocalDate) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val sh by app.superhealth.SuperPrefs.settings.collectAsState()
    val data by produceState(lastNightData?.takeIf { it.first == date }?.second, date, refresh) {
        value = withContext(Dispatchers.IO) {
            coroutineScope {
                val night = async { Nights.of(date) }
                val parts = async { runCatching { Scores.restPartsOf(ctx, date) }.getOrNull() }
                val score = async { runCatching { Bridge.healthValue(ctx, Metric.SLEEP_SCORE, date) }.getOrNull() }
                val watch = async { runCatching { Bridge.watchSleepScore(ctx, date) }.getOrNull() }
                val need = async { runCatching { Scores.sleepHabits(ctx).needMin }.getOrDefault(Scores.sleepNeed()) }
                val debt = async { runCatching { Scores.sleepDebtOf(ctx, date) }.getOrNull() }
                val history = async { Nights.range(date.minusDays(28), date.minusDays(1)).mapNotNull { it.second } }
                val vitals = async { runCatching { NightVitals.of(ctx, date) }.getOrNull() }
                val source = async { runCatching { Bridge.dataSource(Metric.SLEEP, date) }.getOrNull() }
                val n = night.await()
                val heart = n?.let { runCatching { Nights.heart(it) }.getOrDefault(emptyList()) }.orEmpty()
                val past = history.await()
                // durée habituelle de chaque phase : moyenne des nuits avec phases des 4 semaines précédentes
                val staged = past.filter { it.staged }
                val usual = if (staged.size >= 7) STAGES.associate { (k, _) -> k to staged.map { it.minutesOf(k) }.average() } else null
                val compare = if (sh.sleepCompare) runCatching {
                    Scores.nightParts(date)?.let { last -> Scores.compareToUsual(last, (28L downTo 1L).mapNotNull { Scores.nightParts(date.minusDays(it)) }) }
                }.getOrNull() else null
                NightData(n, parts.await(), score.await(), watch.await(), need.await(), debt.await(), heart, usual, vitals.await(), source.await(), compare)
            }
        }.also { lastNightData = date to it }
    }
    val d = data ?: return
    val n = d.night
    if (n == null) {
        MfCard { Text(tr("Aucune nuit enregistrée ce jour-là."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
        return
    }
    // en-tête : durée endormi, Repos (mot et nombre), heures et hypnogramme
    MfCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(tr("Endormi"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
                Text(formatDuration(n.asleepMin), style = MaterialTheme.typography.displaySmall, color = SleepHue.onContainer(), maxLines = 1)
            }
            d.score?.let { s ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(tr(Scores.restWord(s, n.asleepMin, d.needMin).label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = SleepHue.onContainer(), maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(SleepHue.container(1.6f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(trf("Repos %1\$s / 100", s.roundToInt()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                }
            }
        }
        Text(timeOf(n.bedMs) + " – " + timeOf(n.wakeMs) + " · " + trf("%1\$s au lit", formatDuration(n.inBedMin)),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        d.source?.let { SourceLine(it) }
        Spacer(Modifier.height(14.dp))
        Hypnogram(n.phases, Modifier.fillMaxWidth().height(if (n.staged) 150.dp else 70.dp), axis = true)
    }

    if (n.staged) {
        SectionLabel(tr("Phases"))
        MfCard { StageLegend(n, d.usual) }
    }

    SectionLabel(tr("Détails"))
    MfCard {
        StatGrid(listOf(
            tr("Coucher") to timeOf(n.bedMs), tr("Lever") to timeOf(n.wakeMs), tr("Au lit") to formatDuration(n.inBedMin),
            tr("Éveillé") to formatDuration(n.awakeMin), tr("Réveils") to "${n.awakenings}", tr("Efficacité") to "${(n.efficiency * 100).roundToInt()} %",
        ))
        Spacer(Modifier.height(12.dp))
        Text(listOfNotNull(trf("Besoin %1\$s", formatDuration(d.needMin)),
            d.debt?.let { if (it <= 0) tr("Aucune dette de sommeil") else trf("Dette %1\$s", formatDuration(it)) }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
    }

    d.parts?.let { p ->
        SectionLabel(tr("Qualité"))
        MfCard(onClick = { nav.go("h/metric/SLEEP_SCORE") }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${p.total.roundToInt()}", style = MaterialTheme.typography.headlineMedium, color = SleepHue.onContainer())
                Text(" / 100", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                d.watchScore?.let { Text(trf("Score de la montre : %1\$s", it.roundToInt()), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
            }
            Spacer(Modifier.height(12.dp))
            RestPartsRows(p, SleepHue)
        }
    }

    NightHeartSection(n, d.heart, d.vitals)

    d.vitals?.takeIf { it.measured > 0 }?.let { v ->
        SectionLabel(tr("Signes vitaux de la nuit"))
        MfCard(onClick = { nav.go("h/vitals") }) { NightVitalsBody(v, onMetric = null) }
    }

    d.compare?.takeIf { it.isNotEmpty() }?.let { c ->
        SectionLabel(tr("Par rapport à vos habitudes"))
        GroupCard {
            c.forEach { (p, v) ->
                val (name, low, high) = when (p) {
                    Scores.SleepPart.DURATION -> Triple("Durée", "Plus courte", "Plus longue")
                    Scores.SleepPart.EFFICIENCY -> Triple("Efficacité", "Plus basse", "Plus haute")
                    Scores.SleepPart.RESTORATIVE -> Triple("Sommeil réparateur", "Moins", "Plus")
                    Scores.SleepPart.AWAKE -> Triple("Éveils", "Moins", "Plus")
                }
                SettingRow(tr(name), value = tr(when (v) { -1 -> low; 1 -> high; else -> "Habituelle" }))
            }
        }
    }
}

/** Pouls de la nuit : courbe du coucher au lever, le plus bas (moyenne sur 5 min) en pointillés, VFC et respiration de la nuit. */
@Composable
private fun NightHeartSection(n: NightSummary, heart: List<Pair<Long, Int>>, vitals: NightVitals.Night?) {
    if (heart.size < 30) return
    val low = Nights.lowest(heart)
    SectionLabel(tr("Cœur la nuit"))
    MfCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(low?.let { formatNumber(it.second) } ?: "—", style = MaterialTheme.typography.headlineMedium, color = HealthHues.Heart.onContainer())
            Text(" " + tr("bpm au plus bas"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Text(trf("Moy. %1\$s", heart.map { it.second }.average().roundToInt()), style = MaterialTheme.typography.titleSmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
        low?.let { Text(lowestWhen(n, it.first) + " · " + timeOf(it.first), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary) }
        Spacer(Modifier.height(12.dp))
        val hours = Nights.hourMarks(n.bedMs, n.wakeMs)
        val step = ((hours.size + 4) / 5).coerceAtLeast(1)
        HeartChart(Nights.fiveMinutes(heart), HealthHues.Heart, height = 140.dp, from = n.bedMs, to = n.wakeMs,
            reference = low?.second, gapMs = 20 * 60_000L, xLabels = hours.filterIndexed { i, _ -> i % step == 0 }.map { it to timeOf(it) })
        val extra = vitals?.items.orEmpty().filter { (it.metric == Metric.HRV || it.metric == Metric.RESPIRATORY_RATE) && it.value != null }
        if (extra.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StatGrid(extra.map { tr(it.metric.label) to app.superhealth.ui.formatWithUnit(it.metric, it.value!!) })
        }
    }
}

/** Valeurs en grille de 3 colonnes. */
@Composable
internal fun StatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(l, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---- Semaine et mois ------------------------------------------------------------------------

private class RangeData(val nights: List<Pair<LocalDate, NightSummary?>>, val scores: List<Pair<LocalDate, Double?>>, val needMin: Double, val sri: Double?)

private var lastRange: Pair<Pair<LocalDate, LocalDate>, RangeData>? = null

@Composable
private fun NightsView(vm: SuperViewModel, nav: NavHostController, from: LocalDate, to: LocalDate, week: Boolean) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val today = LocalDate.now()
    val data by produceState(lastRange?.takeIf { it.first == (from to to) }?.second, from, to, refresh) {
        value = withContext(Dispatchers.IO) {
            coroutineScope {
                val end = minOf(to, today)
                val nights = async { Nights.range(from, end) }
                val scores = async {
                    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
                        .map { d -> async { d to runCatching { Bridge.healthValue(ctx, Metric.SLEEP_SCORE, d) }.getOrNull() } }.awaitAll()
                }
                val need = async { runCatching { Scores.sleepHabits(ctx).needMin }.getOrDefault(Scores.sleepNeed()) }
                val sri = async { runCatching { Scores.sriOf(end) }.getOrNull() }
                // jours à venir vides : des barres de largeur constante
                val all = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
                val got = nights.await().toMap()
                RangeData(all.map { it to got[it] }, scores.await(), need.await(), sri.await())
            }
        }.also { lastRange = (from to to) to it }
    }
    val d = data ?: return
    val present = d.nights.mapNotNull { it.second }
    if (present.isEmpty()) {
        MfCard { Text(tr("Aucune nuit enregistrée sur cette période."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary) }
        return
    }
    val labels = d.nights.mapIndexed { i, (day, _) -> if (week) dayLetter(day) else if (i % 7 == 0) "${day.dayOfMonth}" else "" }
    MfCard {
        Text(tr("Moyenne endormi"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        Text(formatDuration(present.map { it.asleepMin }.average()), style = MaterialTheme.typography.displaySmall, color = SleepHue.onContainer(), maxLines = 1)
        Spacer(Modifier.height(12.dp))
        StatGrid(listOfNotNull(
            tr("Coucher") to Nights.clock(Nights.median(present.map { it.bedClock })!!),
            tr("Lever") to Nights.clock(Nights.median(present.map { it.wakeClock })!!),
            tr("Nuits") to "${present.size}",
            tr("La plus courte") to formatDuration(present.minOf { it.asleepMin }),
            tr("La plus longue") to formatDuration(present.maxOf { it.asleepMin }),
            d.sri?.let { tr("Régularité") to "${it.roundToInt()} / 100" },
        ))
    }

    SectionLabel(tr("Phases"))
    MfCard {
        StageBars(d.nights.map { it.second }, d.needMin, Modifier, labels, height = 150.dp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            STAGES.reversed().forEach { (k, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(k)))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(name), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
                }
            }
        }
        val staged = present.filter { it.staged }
        if (staged.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(tr("Moyenne par nuit"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            StatGrid(STAGES.map { (k, name) -> tr(name) to formatDuration(staged.map { it.minutesOf(k) }.average()) } +
                listOf(tr("Réveils") to formatNumber(staged.map { it.awakenings.toDouble() }.average(), 1)))
        }
        Spacer(Modifier.height(8.dp))
        Text(trf("Pointillés : votre besoin, %1\$s", formatDuration(d.needMin)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
    }

    SectionLabel(tr("Horaires"))
    MfCard {
        ScheduleChart(d.nights.map { it.second }, Modifier, labels, height = 150.dp)
        Spacer(Modifier.height(8.dp))
        Text(tr("Pointillés : coucher et lever habituels"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
    }

    val sc = d.scores.mapNotNull { (day, v) -> v?.let { day.toEpochDay() to it } }
    if (sc.isNotEmpty()) {
        SectionLabel(tr("Repos"))
        MfCard(onClick = { nav.go("h/metric/SLEEP_SCORE") }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${sc.map { it.second }.average().roundToInt()}", style = MaterialTheme.typography.headlineMedium, color = SleepHue.onContainer())
                Text(" / 100 " + tr("en moyenne"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            LineChart(listOf(Series(sc, SleepHue, markers = true, gap = 3.0)), height = 130.dp,
                xFrom = from.toEpochDay().toDouble(), xTo = to.toEpochDay().toDouble())
        }
    }
}

// ---- Année ----------------------------------------------------------------------------------

private var lastYear: Pair<LocalDate, List<Pair<LocalDate, Double?>>>? = null

@Composable
private fun YearView(vm: SuperViewModel, from: LocalDate, to: LocalDate) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val today = LocalDate.now()
    val days by produceState(lastYear?.takeIf { it.first == from }?.second, from, refresh) {
        value = NightVitals.series(ctx, Metric.SLEEP, from, minOf(to, today)).also { lastYear = from to it }
    }
    val list = days ?: return
    val values = list.mapNotNull { it.second }
    val months = list.groupBy { it.first.withDayOfMonth(1) }
    val keys = generateSequence(from) { it.plusMonths(1) }.takeWhile { !it.isAfter(to) }.toList()
    val avgs = keys.map { k -> months[k].orEmpty().mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()?.div(60) }
    val need = remember { Scores.sleepNeed() }
    MfCard {
        Text(tr("Moyenne endormi"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        if (values.isEmpty()) Text(tr("Aucune donnée"), style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary)
        else Text(formatDuration(values.average()), style = MaterialTheme.typography.displaySmall, color = SleepHue.onContainer(), maxLines = 1)
        Spacer(Modifier.height(14.dp))
        BarChart(avgs, keys.map { need / 60 }, SleepHue, height = 150.dp,
            labels = keys.map { it.month.getDisplayName(DateTextStyle.NARROW, Lang.locale) })
        if (values.size >= 2) {
            Spacer(Modifier.height(14.dp))
            StatGrid(listOf(tr("Min.") to formatDuration(values.min()), tr("Max.") to formatDuration(values.max()), tr("Nuits") to "${values.size}"))
        }
    }
}
