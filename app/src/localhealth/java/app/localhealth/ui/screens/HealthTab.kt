package app.localhealth.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import app.localhealth.HealthViewModel
import app.localhealth.data.Aggregation
import app.localhealth.data.Category
import app.localhealth.data.ChartKind
import app.localhealth.data.HealthRepository
import app.localhealth.data.Metric
import app.localhealth.ui.Navigator
import app.localhealth.ui.Route
import app.localhealth.ui.components.CategoryTile
import app.localhealth.ui.components.SectionTitle
import app.localhealth.ui.components.TonalChipButton
import app.localhealth.ui.components.TwoColumnGrid
import app.localhealth.ui.components.ValueChip
import app.localhealth.ui.components.WeekMiniChart
import app.localhealth.ui.dayLetters
import app.localhealth.ui.formatValue
import app.localhealth.ui.formatWithUnit
import app.localhealth.ui.theme.familyFor
import app.localhealth.ui.unitOf
import java.time.LocalDate
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AboutUserPreferencesActivity

private val DEFAULT_KEY_STATS = listOf(
    Metric.WEIGHT, Metric.CALORIES, Metric.STEPS, Metric.SLEEP,
    Metric.RESTING_HR, Metric.HRV, Metric.STRESS, Metric.CARDIO_WEEK,
)

private const val PREFS = "localhealth"
private const val KEY_STATS = "key_stats"

fun keyStats(context: Context): List<Metric> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATS, null)
        ?.split(',')?.mapNotNull { runCatching { Metric.valueOf(it) }.getOrNull() }
        ?: DEFAULT_KEY_STATS

private fun saveKeyStats(context: Context, metrics: List<Metric>) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_STATS, metrics.joinToString(",") { it.name }).apply()

@Composable
fun HealthTab(vm: HealthViewModel, nav: Navigator, refresh: Int) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var editing by rememberSaveable { mutableStateOf(false) }
    var stats by remember { mutableStateOf(keyStats(context)) }

    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            SectionTitle(stringResource(R.string.lh_key_stats), Modifier.height(56.dp)) {
                TonalChipButton(stringResource(R.string.lh_edit), R.drawable.gs_edit_fill1_vd_theme_24, { editing = true })
            }
            Spacer(Modifier.height(12.dp))
            TwoColumnGrid(stats) { m, mod -> KeyStatCard(m, refresh, mod) { nav.push(Route.Detail(m)) } }

            Spacer(Modifier.height(40.dp))
            SectionTitle(stringResource(R.string.lh_important_stats))
            Spacer(Modifier.height(16.dp))
            TwoColumnGrid(Category.entries.toList(), gap = 10.dp) { c, mod -> CategoryStatus(c, refresh, mod) { nav.push(Route.CategoryPage(c)) } }

            Spacer(Modifier.height(40.dp))
            SectionTitle(stringResource(R.string.lh_health_checks))
            Spacer(Modifier.height(16.dp))
            val hrAlerts = HealthRepository.isSupported(Metric.HEART_RATE)
            TwoColumnGrid(listOf(R.string.lh_high_hr to R.drawable.gs_heart_plus_fill1_vd_theme_24,
                R.string.lh_low_hr to R.drawable.gs_heart_minus_fill1_vd_theme_24), gap = 10.dp) { (label, icon), mod ->
                CategoryTile(icon, stringResource(label),
                    stringResource(if (hrAlerts) R.string.lh_on_watch else R.string.lh_unavailable),
                    familyFor(app.localhealth.data.Hue.GOLDENROD).color, cs.onSurfaceVariant, mod.heightIn(min = 100.dp),
                    onClick = { nav.push(Route.Connections) })
            }

            Spacer(Modifier.height(40.dp))
            SectionTitle(stringResource(R.string.lh_personal_info))
            Spacer(Modifier.height(16.dp))
            val goals = HealthRepository.goals().size
            TwoColumnGrid(listOf(0, 1, 2, 3), gap = 10.dp) { i, mod ->
                when (i) {
                    0 -> CategoryTile(R.drawable.gs_user_attributes_vd_theme_24, stringResource(R.string.lh_profile),
                        stringResource(R.string.lh_profile_edit), cs.primary, cs.primary, mod.heightIn(min = 90.dp)) {
                        context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java))
                    }
                    1 -> CategoryTile(R.drawable.gs_flag_fill1_vd_theme_24, stringResource(R.string.lh_goals),
                        stringResource(R.string.lh_goals_active, goals), cs.primary, cs.primary, mod.heightIn(min = 90.dp)) {
                        context.startActivity(Intent(context, AboutUserPreferencesActivity::class.java))
                    }
                    2 -> CategoryTile(R.drawable.gs_trophy_fill1_vd_theme_24, stringResource(R.string.lh_records),
                        stringResource(R.string.lh_history), cs.primary, cs.primary, mod.heightIn(min = 90.dp)) {
                        nav.push(Route.History)
                    }
                    else -> CategoryTile(R.drawable.gs_shield_with_heart_vd_theme_24, stringResource(R.string.lh_your_data_short),
                        stringResource(R.string.lh_local_only), cs.onSurfaceVariant, cs.onSurfaceVariant, mod.heightIn(min = 90.dp)) {
                        nav.push(Route.Profile)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editing) {
        KeyStatsDialog(stats, onDismiss = { editing = false }) { picked ->
            stats = picked
            saveKeyStats(context, picked)
            editing = false
        }
    }
}

private data class CardData(
    val headline: Double?,
    val week: List<Double?>,
    val weekDays: List<LocalDate>,
)

/** Google Health key-stat card: label, headline value, mini week chart, summary chip. */
@Composable
fun KeyStatCard(metric: Metric, refresh: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val family = familyFor(metric.hue)
    val today = LocalDate.now()
    val data by produceState<CardData?>(null, metric, refresh) {
        // Google Health shows the last seven days ending today, today's letter highlighted last
        val days = (6L downTo 0L).map { today.minusDays(it) }
        val week = HealthRepository.series(context, metric, days.first(), today).map { it.second }
        val headline = when (metric.aggregation) {
            Aggregation.LAST -> HealthRepository.latestValue(context, metric, today)?.second
            else -> HealthRepository.dayValue(context, metric, today)
        }
        value = CardData(headline, week, days)
    }
    val goal = HealthRepository.goal(metric)
    Surface(
        modifier = modifier.heightIn(min = 197.dp),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainer,
        onClick = onClick,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Text(stringResource(metric.label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val d = data
            val v = d?.headline
            if (v == null) {
                Text(stringResource(R.string.lh_no_data), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 37.sp),
                    color = cs.onSurface, maxLines = 2)
            } else {
                val unit = unitOf(context, metric)?.takeIf { metric != Metric.SLEEP }
                Text(buildAnnotatedString {
                    append(formatValue(context, metric, v))
                    if (unit != null) withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) { append(" " + unit) }
                }, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 37.sp), color = cs.onSurface, maxLines = 1)
            }
            if (goal != null && metric.aggregation == Aggregation.SUM) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ((v ?: 0.0) / goal).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = family.data, trackColor = family.data.copy(alpha = 0.25f), drawStopIndicator = {}, gapSize = 0.dp,
                )
            }
            Spacer(Modifier.weight(1f, fill = false).height(16.dp))
            if (d != null) {
                WeekMiniChart(
                    values = d.week,
                    dayLetters = dayLetters(d.weekDays),
                    todayIndex = d.weekDays.indexOf(today),
                    kind = if (metric.chart == ChartKind.BARS && metric.aggregation == Aggregation.SUM) ChartKind.BARS else ChartKind.LINE,
                    family = family,
                    goal = goal?.takeIf { metric.aggregation == Aggregation.SUM },
                )
                Spacer(Modifier.height(8.dp))
                val present = d.week.filterNotNull()
                val chip = when {
                    present.isEmpty() -> stringResource(R.string.lh_no_data)
                    goal != null && metric.aggregation == Aggregation.SUM && v != null && v < goal ->
                        stringResource(R.string.lh_remaining, formatWithUnit(context, metric, goal - v))
                    metric.aggregation == Aggregation.SUM -> stringResource(R.string.lh_week_total, formatValue(context, metric, present.sum()))
                    else -> stringResource(R.string.lh_week_avg, formatValue(context, metric, present.average()))
                }
                ValueChip(chip)
            }
        }
    }
}

@Composable
private fun CategoryStatus(category: Category, refresh: Int, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val tracked by produceState(0, category, refresh) {
        value = Metric.entries.filter { it.category == category && HealthRepository.isSupported(it) }
            .count { HealthRepository.latestValue(context, it, LocalDate.now(), lookbackDays = 7) != null }
    }
    CategoryTile(
        category.icon, stringResource(category.label),
        if (tracked > 0) stringResource(R.string.lh_tracked, tracked) else stringResource(R.string.lh_not_tracked),
        if (tracked > 0) cs.primary else cs.onSurfaceVariant,
        if (tracked > 0) cs.primary else cs.onSurfaceVariant,
        modifier.heightIn(min = 74.dp),
        onClick = onClick,
    )
}

@Composable
private fun KeyStatsDialog(current: List<Metric>, onDismiss: () -> Unit, onSave: (List<Metric>) -> Unit) {
    val picked = remember { mutableStateListOf<Metric>().apply { addAll(current) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lh_customize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Metric.entries.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { if (m in picked) picked.remove(m) else picked.add(m) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m in picked, { if (it) picked.add(m) else picked.remove(m) })
                        Text(stringResource(m.label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked.toList()) }) { Text(stringResource(R.string.lh_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lh_cancel)) } },
    )
}
