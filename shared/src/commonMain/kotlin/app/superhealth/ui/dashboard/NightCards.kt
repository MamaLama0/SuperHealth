package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.health.data.NightSummary
import app.superhealth.health.data.NightVitals
import app.superhealth.health.data.Nights
import app.superhealth.health.data.Scores
import app.superhealth.ui.dayLetter
import app.superhealth.ui.formatDuration
import app.superhealth.ui.formatNumber
import app.superhealth.ui.formatValue
import app.superhealth.ui.go
import app.superhealth.ui.health.HeartChart
import app.superhealth.ui.health.NightVitalsBody
import app.superhealth.ui.health.RangeBar
import app.superhealth.ui.health.RestPartsRows
import app.superhealth.ui.health.ScheduleChart
import app.superhealth.ui.health.StageBars
import app.superhealth.ui.health.VitalTrend
import app.superhealth.ui.health.color
import app.superhealth.ui.health.lowestWhen
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.timeOf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.unitOf
import kotlin.math.roundToInt

/*
 * Cartes 2.1 du sommeil et des constantes : horaires (coucher et lever des deux semaines), qualité (parties de Repos),
 * cœur la nuit (pouls du coucher au lever et son point bas), signes vitaux de la nuit face à l'habituel et tendances des
 * constantes avec leur plage habituelle. Toutes ouvrent l'écran Sommeil ou Constantes.
 */

internal val NIGHT_CARDS = setOf(DashCard.SLEEP_SCHEDULE, DashCard.SLEEP_QUALITY, DashCard.NIGHT_HEART, DashCard.NIGHT_VITALS, DashCard.VITALS_TRENDS)

/** Nuits des 14 derniers jours (la dernière en fin de liste). */
internal fun WatchSnap.lastNights(n: Int): List<NightSummary?> = nights.takeLast(n).map { it.second }

/** Petites tailles des cartes 2.1. */
internal fun nightCompact(c: DashCard, w: WatchSnap, nav: NavHostController): CompactData? {
    val sleep: () -> Unit = { nav.go("h/metric/SLEEP") }
    val vitals: () -> Unit = { nav.go("h/vitals") }
    return when (c) {
        DashCard.SLEEP_SCHEDULE -> {
            val week = w.lastNights(7).filterNotNull()
            CompactData(Nights.median(week.map { it.bedClock })?.let(Nights::clock) ?: "—", null, tr("Coucher"), null,
                w.lastNights(14).map { it?.bedClock }, bars = false, onClick = sleep)
        }
        DashCard.SLEEP_QUALITY -> {
            val n = w.lastNight
            val word = w.sleepScore?.takeIf { n != null }?.let { tr(Scores.restWord(it, n!!.asleepMin, w.needMin ?: Scores.sleepNeed()).label) }
            CompactData(word ?: "—", null, tr("Qualité"), w.sleepScore?.div(100)?.toFloat(), w.restHistory, goal = 75.0, onClick = sleep)
        }
        DashCard.NIGHT_HEART -> {
            val low = Nights.lowest(w.nightHeart)
            // la note dit quand arrive le point bas ; pas d'écart « vs moy. » (une moyenne de tranches de 5 min ne dit rien)
            CompactData(low?.let { formatNumber(it.second) } ?: "—", tr("bpm"), low?.let { trf("Point bas %1\$s", timeOf(it.first)) } ?: tr("Cœur la nuit"), null,
                // tranches de 15 min : une courbe lisible à cette taille (le grand garde les 5 min)
                w.nightHeart.groupBy { it.first / 900_000L }.toSortedMap().values.map { v -> v.map { it.second }.average() }, bars = false, onClick = sleep)
        }
        DashCard.NIGHT_VITALS -> {
            val n = w.nightVitals
            val out = n?.outliers ?: 0
            CompactData(when { n == null || n.measured == 0 -> "—"; out == 0 -> tr("Normal"); else -> out.toString() }, null,
                if (out > 0) tr("Hors de l'habituel") else tr("Signes vitaux"), null, onClick = vitals)
        }
        DashCard.VITALS_TRENDS -> {
            val t = w.trends.firstOrNull { it.last != null }
            CompactData(t?.last?.let { formatValue(t.metric, it.second) } ?: "—", t?.metric?.let(::unitOf), t?.metric?.label?.let(::tr) ?: tr("Constantes"), null,
                t?.days?.map { it.second }.orEmpty(), bars = false, onClick = vitals)
        }
        else -> null
    }
}

/** Horaires : barres coucher → lever des 14 nuits, coucher et lever habituels, régularité (SRI). */
@Composable
internal fun SleepScheduleCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val nights = w.lastNights(14)
    val present = nights.filterNotNull()
    Tile(c, tr("Horaires de sommeil"), modifier, onClick = { nav.go("h/metric/SLEEP") }, trailing = tr("14 nuits")) {
        if (present.isEmpty()) {
            Text(tr("La nuit apparaîtra après la prochaine synchronisation."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            return@Tile
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Value(Nights.clock(Nights.median(present.map { it.bedClock })!!) + " – " + Nights.clock(Nights.median(present.map { it.wakeClock })!!),
                color = c.ink().onContainer())
            Spacer(Modifier.weight(1f))
            w.sri?.let { Sub(trf("Régularité %1\$s", it.roundToInt())) }
        }
        Sub(tr("Coucher et lever habituels"))
        Spacer(Modifier.height(10.dp))
        val days = w.nights.takeLast(14).map { it.first }
        ScheduleChart(nights, Modifier, days.mapIndexed { i, d -> if (i % 2 == 1) dayLetter(d) else "" }, height = 92.dp, color = c.ink())
    }
}

/** Qualité : Repos en mots et ses quatre parties (durée, efficacité, sommeil réparateur, régularité). */
@Composable
internal fun SleepQualityCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val n = w.lastNight
    val p = w.restParts
    Tile(c, tr("Qualité du sommeil"), modifier, onClick = { nav.go("h/metric/SLEEP") }) {
        if (n == null || p == null) {
            Text(tr("Il faut une nuit avec ses phases (léger, profond, paradoxal)."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
            return@Tile
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Value(tr(Scores.restWord(p.total, n.asleepMin, w.needMin ?: Scores.sleepNeed()).label), color = c.ink().onContainer())
            Spacer(Modifier.weight(1f))
            Sub(formatDuration(n.asleepMin))
        }
        Spacer(Modifier.height(10.dp))
        RestPartsRows(p, c.ink(), track = softTrack())
    }
}

/** Cœur la nuit : pouls du coucher au lever, son point bas (moyenne sur 5 min) et le moment où il arrive. */
@Composable
internal fun NightHeartCard(c: DashCard, w: WatchSnap, modifier: Modifier, nav: NavHostController) {
    val n = w.lastNight
    val heart = w.nightHeart
    val low = Nights.lowest(heart)
    Tile(c, tr("Cœur la nuit"), modifier, onClick = { nav.go("h/metric/SLEEP") }, bottom = {
        if (n != null && heart.size >= 30) HeartChart(Nights.fiveMinutes(heart), c.ink(), height = 48.dp, from = n.bedMs, to = n.wakeMs,
            reference = low?.second, gapMs = 20 * 60_000L, showAxis = false)
    }) {
        Value(low?.let { formatNumber(it.second) } ?: "—", tr("bpm"), color = c.ink().onContainer())
        Sub(if (n != null && low != null) lowestWhen(n, low.first) + " · " + timeOf(low.first) else tr("Pas de pouls cette nuit"))
    }
}

/** Sommeil, en grand : la nuit, son Repos en mots et les 7 nuits en phases empilées face au besoin. */
@Composable
internal fun SleepWeekCard(c: DashCard, h: HealthSnap, modifier: Modifier, nav: NavHostController) {
    val w = h.watch
    val week = w.nights.takeLast(7)
    val last = w.lastNight
    Tile(c, tr("Sommeil"), modifier, onClick = { nav.go("h/metric/SLEEP") }, trailing = last?.let { timeOf(it.bedMs) + " – " + timeOf(it.wakeMs) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Value(last?.let { formatDuration(it.asleepMin) } ?: h.latest(app.superhealth.health.data.Metric.SLEEP)?.let { formatValue(app.superhealth.health.data.Metric.SLEEP, it.second) } ?: "—",
                color = c.ink().onContainer())
            w.sleepScore?.takeIf { last != null }?.let { Sub(" · " + tr(Scores.restWord(it, last!!.asleepMin, w.needMin ?: Scores.sleepNeed()).label)) }
            Spacer(Modifier.weight(1f))
            week.mapNotNull { it.second?.asleepMin }.takeIf { it.isNotEmpty() }?.let { Sub(trf("Moy. %1\$s", formatDuration(it.average()))) }
        }
        Spacer(Modifier.height(10.dp))
        if (week.any { it.second != null }) StageBars(week.map { it.second }, w.needMin, Modifier, week.map { dayLetter(it.first) }, height = 84.dp)
        else {
            // pas de nuits détaillées (appli qui ne donne que la durée) : les durées seules
            val series = h.series[app.superhealth.health.data.Metric.SLEEP].orEmpty()
            app.superhealth.ui.charts.BarChart(series.map { it.second?.div(60) }, series.map { (w.needMin ?: 480.0) / 60 }, c.ink(), height = 60.dp, track = softTrack(),
                dimBelowGoal = true, labels = series.map { dayLetter(it.first) })
        }
    }
}
