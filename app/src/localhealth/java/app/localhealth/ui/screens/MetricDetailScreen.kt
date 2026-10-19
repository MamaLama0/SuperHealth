package app.localhealth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localhealth.HealthViewModel
import app.localhealth.data.Aggregation
import app.localhealth.data.ChartKind
import app.localhealth.data.HealthRepository
import app.localhealth.data.Metric
import app.localhealth.ui.Navigator
import app.localhealth.ui.components.DetailChart
import app.localhealth.ui.components.DetailTopBar
import app.localhealth.ui.components.GsIcon
import app.localhealth.ui.components.RoundIconButton
import app.localhealth.ui.formatNumber
import app.localhealth.ui.formatValue
import app.localhealth.ui.formatWithUnit
import app.localhealth.ui.theme.familyFor
import app.localhealth.ui.unitOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import nodomain.freeyourgadget.gadgetbridge.R

enum class Range(val label: Int) {
    DAY(R.string.lh_range_day), WEEK(R.string.lh_range_week), MONTH(R.string.lh_range_month),
    QUARTER(R.string.lh_range_3months), YEAR(R.string.lh_range_year),
}

private data class Period(val from: LocalDate, val to: LocalDate)

private fun period(range: Range, offset: Int, today: LocalDate): Period = when (range) {
    Range.DAY -> today.minusDays(offset.toLong()).let { Period(it, it) }
    Range.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong())
        .let { Period(it, it.plusDays(6)) }
    Range.MONTH -> YearMonth.from(today).minusMonths(offset.toLong()).let { Period(it.atDay(1), it.atEndOfMonth()) }
    Range.QUARTER -> YearMonth.from(today).minusMonths(offset * 3L).let { Period(it.minusMonths(2).atDay(1), it.atEndOfMonth()) }
    Range.YEAR -> YearMonth.from(today).minusMonths(offset * 12L).let { Period(it.minusMonths(11).atDay(1), it.atEndOfMonth()) }
}

/** One chart bucket: a day, or a month for the year view. */
private data class Bucket(val start: LocalDate, val value: Double?)

private data class DetailData(val buckets: List<Bucket>, val intraday: List<Pair<Long, Double>>?)

@Composable
fun MetricDetailScreen(vm: HealthViewModel, nav: Navigator, metric: Metric) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val family = familyFor(metric.hue)
    val today = LocalDate.now()
    val hasIntraday = metric in setOf(Metric.STEPS, Metric.HEART_RATE, Metric.STRESS, Metric.BODY_ENERGY, Metric.SPO2)
    var range by rememberSaveable { mutableStateOf(if (metric.aggregation == Aggregation.LAST && !hasIntraday) Range.QUARTER else Range.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var logging by rememberSaveable { mutableStateOf(false) }
    val p = period(range, offset, today)
    val clampedTo = if (p.to.isAfter(today)) today else p.to

    val data by produceState<DetailData?>(null, metric, range, offset, refresh) {
        value = null
        value = if (range == Range.DAY) {
            val pts = if (hasIntraday) HealthRepository.intraday(metric, p.from).map { it.timestampMs to it.value } else null
            DetailData(listOf(Bucket(p.from, HealthRepository.dayValue(context, metric, p.from))), pts)
        } else {
            val daily = HealthRepository.series(context, metric, p.from, clampedTo)
            val buckets = if (range == Range.YEAR) {
                daily.groupBy { YearMonth.from(it.first) }.map { (ym, days) ->
                    val vs = days.mapNotNull { it.second }
                    Bucket(ym.atDay(1), aggregate(metric, vs, perDay = true))
                }
            } else {
                val all = generateSequence(p.from) { it.plusDays(1) }.takeWhile { !it.isAfter(p.to) }.toList()
                val byDay = daily.toMap()
                all.map { Bucket(it, byDay[it]) }
            }
            DetailData(buckets, null)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        DetailTopBar(stringResource(metric.label), onBack = nav::pop) {
            if (metric.manual) {
                IconButton(onClick = { logging = true }) { GsIcon(R.drawable.gs_add_vd_theme_24, cs.onSurface) }
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Range selector
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Range.entries.forEach { r ->
                    val sel = r == range
                    Surface(
                        onClick = { range = r; offset = 0; selected = null },
                        shape = CircleShape,
                        color = if (sel) cs.secondary else cs.surfaceContainer,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(r.label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                                color = if (sel) cs.onSecondary else cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            // Period label + navigation
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(periodLabel(range, p), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = cs.onSurface, modifier = Modifier.weight(1f))
                RoundIconButton(R.drawable.gs_chevron_right_vd_theme_24, stringResource(R.string.lh_previous),
                    { offset++; selected = null }, size = 40.dp, container = cs.surfaceContainer, mirrored = true)
                Spacer(Modifier.width(16.dp))
                RoundIconButton(R.drawable.gs_chevron_right_vd_theme_24, stringResource(R.string.lh_next),
                    { offset--; selected = null }, size = 40.dp, enabled = offset > 0, container = cs.surfaceContainer)
                Spacer(Modifier.width(16.dp))
                RoundIconButton(R.drawable.gs_restart_alt_vd_theme_24, stringResource(R.string.lh_go_today),
                    { offset = 0; selected = null }, size = 40.dp, enabled = offset > 0, container = cs.surfaceContainer)
            }

            val d = data
            if (d == null) {
                Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val sel = selected?.let { d.buckets.getOrNull(it) }
                val values = d.buckets.map { it.value }
                val headline = sel?.value ?: aggregate(metric, values.filterNotNull(), perDay = false)
                val caption = when {
                    sel != null -> dateLabel(range, sel.start)
                    range == Range.DAY -> dateLabel(range, p.from)
                    else -> stringResource(if (metric.aggregation == Aggregation.SUM) R.string.lh_total else R.string.lh_average, periodLabel(range, p))
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                    if (headline == null) {
                        Text(stringResource(R.string.lh_no_data), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
                            color = cs.onSurface)
                    } else {
                        val unit = unitOf(context, metric)?.takeIf { metric != Metric.SLEEP }
                        Text(buildAnnotatedString {
                            append(formatValue(context, metric, headline))
                            if (unit != null) withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium)) { append(" " + unit) }
                        }, style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Medium),
                            color = cs.onSurface)
                    }
                    Text(caption, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                val goal = HealthRepository.goal(metric)?.takeIf { metric.aggregation == Aggregation.SUM && range != Range.YEAR }
                if (range == Range.DAY && d.intraday != null) {
                    val pts = d.intraday
                    // 24 hourly buckets for steps; 144 ten-minute buckets otherwise
                    val n = if (metric == Metric.STEPS) 24 else 144
                    val start = HealthRepository.startMs(p.from)
                    val slot = 86_400_000L / n
                    val arr = arrayOfNulls<Double>(n)
                    pts.forEach { (t, v) -> ((t - start) / slot).toInt().takeIf { it in 0 until n }?.let { arr[it] = (arr[it] ?: 0.0).let { o -> if (metric == Metric.STEPS) o + v else v } } }
                    DetailChart(arr.toList(), (0 until n step n / 4).map { it to "${it * 24 / n} h" },
                        if (metric == Metric.STEPS) ChartKind.BARS else ChartKind.LINE, family, { formatNumber(it) },
                        Modifier.padding(horizontal = 16.dp))
                } else {
                    DetailChart(
                        values, xLabels(range, d.buckets.map { it.start }), metric.chart, family,
                        { axisLabel(metric, it) }, Modifier.padding(horizontal = 16.dp),
                        selected = selected, goal = goal, onSelect = { selected = if (selected == it) null else it },
                    )
                }

                // Summary card ("Cette semaine   133 kg")
                Spacer(Modifier.height(24.dp))
                val weekVals = HealthSummary.thisWeek(values, d.buckets.map { it.start }, today)
                Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (range == Range.WEEK && offset == 0 || range != Range.WEEK) R.string.lh_this_week else R.string.lh_this_period),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = cs.onSurface, modifier = Modifier.weight(1f))
                        val agg = aggregate(metric, weekVals, perDay = false)
                        Text(agg?.let { formatWithUnit(context, metric, it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), color = cs.onSurface)
                    }
                }
            }

            metric.about?.let { about ->
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.lh_about_metric, stringResource(metric.label).lowercase(Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(about), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Spacer(Modifier.height(28.dp))
            GsIcon(R.drawable.gs_info_vd_theme_24, cs.onSurfaceVariant, Modifier.padding(horizontal = 24.dp), size = 26.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.lh_disclaimer), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(32.dp))
        }
    }

    if (logging) {
        LogValueDialog(metric, onDismiss = { logging = false }) { v -> vm.log(metric, v); logging = false }
    }
}

private object HealthSummary {
    /** Values of the current week within the shown buckets (falls back to all of them). */
    fun thisWeek(values: List<Double?>, starts: List<LocalDate>, today: LocalDate): List<Double> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val wk = values.zip(starts).filter { !it.second.isBefore(monday) && !it.second.isAfter(today) }.mapNotNull { it.first }
        return wk.ifEmpty { values.filterNotNull() }
    }
}

/** Sum for SUM metrics (or the daily average when [perDay]), mean for AVERAGE, last for LAST. */
private fun aggregate(metric: Metric, vs: List<Double>, perDay: Boolean): Double? {
    if (vs.isEmpty()) return null
    return when (metric.aggregation) {
        Aggregation.SUM -> if (perDay) vs.average() else vs.sum()
        Aggregation.AVERAGE -> vs.average()
        Aggregation.LAST -> vs.last()
    }
}

private fun axisLabel(metric: Metric, v: Double): String = when (metric) {
    Metric.SLEEP -> "${(v / 60).toInt()} h"
    Metric.DISTANCE, Metric.WEIGHT, Metric.VO2MAX, Metric.TEMPERATURE, Metric.BODY_TEMPERATURE, Metric.RESPIRATORY_RATE, Metric.BODY_FAT ->
        formatNumber(v, if (v < 10) 1 else 0)
    else -> if (v >= 10_000) formatNumber(v / 1000) + "k" else formatNumber(v)
}

@Composable
private fun periodLabel(range: Range, p: Period): String {
    val loc = Locale.getDefault()
    return when (range) {
        Range.DAY -> dateLabel(range, p.from)
        Range.WEEK -> if (p.from.month == p.to.month) "${p.from.dayOfMonth}–${p.to.dayOfMonth} ${p.to.month.getDisplayName(TextStyle.SHORT, loc)}"
        else "${p.from.dayOfMonth} ${p.from.month.getDisplayName(TextStyle.SHORT, loc)} – ${p.to.dayOfMonth} ${p.to.month.getDisplayName(TextStyle.SHORT, loc)}"
        Range.MONTH -> p.from.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) } + " " + p.from.year
        Range.QUARTER, Range.YEAR -> "${p.from.month.getDisplayName(TextStyle.SHORT, loc)}–${p.to.month.getDisplayName(TextStyle.SHORT, loc)}" +
            if (range == Range.YEAR || p.from.year != LocalDate.now().year) " ${p.to.year}" else ""
    }
}

@Composable
private fun dateLabel(range: Range, d: LocalDate): String {
    return when {
        range == Range.YEAR -> d.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + d.year
        d == LocalDate.now() -> stringResource(R.string.lh_today)
        d == LocalDate.now().minusDays(1) -> stringResource(R.string.lh_yesterday)
        else -> d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

private fun xLabels(range: Range, starts: List<LocalDate>): List<Pair<Int, String>> {
    val loc = Locale.getDefault()
    return when (range) {
        Range.WEEK -> starts.mapIndexed { i, d -> i to d.dayOfWeek.getDisplayName(TextStyle.NARROW, loc).uppercase(loc) }
        Range.MONTH -> starts.mapIndexedNotNull { i, d -> if (d.dayOfMonth == 1 || d.dayOfMonth % 7 == 1) i to d.dayOfMonth.toString() else null }
        Range.QUARTER -> starts.mapIndexedNotNull { i, d -> if (d.dayOfMonth == 1) i to d.month.getDisplayName(TextStyle.FULL_STANDALONE, loc) else null }
        Range.YEAR -> starts.mapIndexedNotNull { i, d -> if (i % 3 == 0) i to d.month.getDisplayName(TextStyle.SHORT, loc) else null }
        Range.DAY -> emptyList()
    }
}
