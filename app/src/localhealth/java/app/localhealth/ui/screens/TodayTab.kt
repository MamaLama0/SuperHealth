package app.localhealth.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localhealth.HealthViewModel
import app.localhealth.data.CardioWeek
import app.localhealth.data.HealthRepository
import app.localhealth.data.Hue
import app.localhealth.data.LOGGABLE_METRICS
import app.localhealth.data.Metric
import app.localhealth.data.TimelineEvent
import app.localhealth.ui.Navigator
import app.localhealth.ui.Route
import app.localhealth.ui.components.CardioRing
import app.localhealth.ui.components.EmptyPill
import app.localhealth.ui.components.GsIcon
import app.localhealth.ui.components.OutlinedWideButton
import app.localhealth.ui.components.PagerDots
import app.localhealth.ui.components.PillStat
import app.localhealth.ui.components.RoundIconButton
import app.localhealth.ui.components.TonalWideButton
import app.localhealth.ui.formatNumber
import app.localhealth.ui.formatWithUnit
import app.localhealth.ui.theme.familyFor
import app.localhealth.ui.theme.neutralFamily
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import kotlin.math.roundToInt
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutDetailsActivity
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser

fun userInitial(): String = ActivityUser().name?.trim()?.firstOrNull()?.uppercase() ?: "?"

/** Pseudo-metric slot for the "Jours d'exercice" pill (workout days this week vs 5). */
private sealed interface Slot {
    data class Of(val metric: Metric) : Slot
    data object ExerciseDays : Slot
    data object Empty : Slot
}

private const val EXERCISE_DAYS_GOAL = 5

@Composable
fun TodayTab(vm: HealthViewModel, nav: Navigator, refresh: Int) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val devices by vm.devices.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var showLog by rememberSaveable { mutableStateOf(false) }
    var logMetric by rememberSaveable { mutableStateOf<String?>(null) }

    val readinessSlot = if (HealthRepository.isSupported(Metric.READINESS)) Metric.READINESS else Metric.BODY_ENERGY
    val pages = listOf(
        listOf(Slot.Of(Metric.STEPS), Slot.Of(readinessSlot), Slot.Of(Metric.SLEEP)),
        listOf(Slot.Of(Metric.DISTANCE), Slot.Of(Metric.CALORIES), Slot.ExerciseDays,
            Slot.Of(Metric.ACTIVE_MINUTES), Slot.Of(Metric.RESTING_HR), Slot.Of(Metric.HRV)),
        listOf(Slot.Of(Metric.WEIGHT), Slot.Of(Metric.HYDRATION), Slot.Of(Metric.STRESS),
            Slot.Of(Metric.SPO2), Slot.Of(Metric.SLEEP_SCORE), Slot.Empty),
    )

    val values by produceState(emptyMap<Metric, Double?>(), refresh, devices.size) {
        val metrics = pages.flatten().filterIsInstance<Slot.Of>().map { it.metric }
        value = metrics.associateWith { m ->
            if (m.manual || m == Metric.VO2MAX) HealthRepository.latestValue(context, m, today)?.second
            else HealthRepository.dayValue(context, m, today)
        }
    }
    val cardio by produceState<CardioWeek?>(null, refresh, devices.size) { value = HealthRepository.cardioWeek(today) }
    val exerciseDays by produceState(0, refresh) {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        value = HealthRepository.workouts(HealthRepository.startMs(monday), HealthRepository.endMs(today))
            .map { Instant.ofEpochMilli(it.start.time).atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().size
    }
    val timeline by produceState(emptyList<TimelineEvent>(), refresh) { value = HealthRepository.timeline(context, today) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val pager = rememberPagerState { pages.size }
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 8.dp), pageSpacing = 16.dp) { page ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (page == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = cardio
                        CardioRing(
                            progress = c?.progress ?: 0f,
                            percentText = "${((c?.progress ?: 0f) * 100).roundToInt()} %",
                            topCaption = stringResource(R.string.lh_cardio_week),
                            bottomCaption = if (c == null) stringResource(R.string.lh_cardio_ring_no_device)
                            else stringResource(R.string.lh_cardio_ring_bottom, formatNumber(c.total.toDouble()), formatNumber(c.target.toDouble())),
                            badge = "+${c?.today ?: 0}",
                            family = familyFor(Hue.BLUE),
                            modifier = Modifier.clip(CircleShape).clickable { nav.push(Route.Detail(Metric.CARDIO_WEEK)) },
                            size = 201.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pages[0].forEach { SlotPill(it, values, exerciseDays, nav, Modifier.fillMaxWidth()) }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages[page].chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { SlotPill(it, values, exerciseDays, nav, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        PagerDots(pages.size, pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TonalWideButton(stringResource(R.string.lh_log), R.drawable.gs_add_vd_theme_24, { showLog = true })
            TonalWideButton(stringResource(R.string.lh_start), R.drawable.gs_directions_run_vd_theme_24, { nav.push(Route.Start) })
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            RoundIconButton(R.drawable.gs_edit_fill1_vd_theme_24, stringResource(R.string.lh_customize), onClick = { nav.push(Route.Profile) })
        }

        // White sheet with today's timeline
        Surface(
            color = cs.surface,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                if (timeline.isEmpty()) {
                    Text(stringResource(R.string.lh_nothing_today), style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
                timeline.take(5).forEach { TimelineCard(it, vm) }
                Spacer(Modifier.height(16.dp))
                OutlinedWideButton(stringResource(R.string.lh_history), R.drawable.gs_history_vd_theme_24, { nav.push(Route.History) })
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showLog) {
        LogSheet(onDismiss = { showLog = false }, onPick = { showLog = false; logMetric = it.name })
    }
    logMetric?.let { name ->
        LogValueDialog(Metric.valueOf(name), onDismiss = { logMetric = null }) { v -> vm.log(Metric.valueOf(name), v); logMetric = null }
    }
}

@Composable
private fun SlotPill(slot: Slot, values: Map<Metric, Double?>, exerciseDays: Int, nav: Navigator, modifier: Modifier) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    when (slot) {
        Slot.Empty -> EmptyPill(modifier)
        Slot.ExerciseDays -> PillStat(
            R.drawable.gs_exercise_vd_theme_24, stringResource(R.string.lh_exercise_days), familyFor(Hue.TEAL), modifier,
            value = stringResource(R.string.lh_of, exerciseDays.toString(), EXERCISE_DAYS_GOAL.toString()),
            onClick = { nav.push(Route.History) },
        )
        is Slot.Of -> {
            val m = slot.metric
            val v = values[m]
            // Activity pills are teal, sleep lilac; everything else is a grey pill with a coloured icon
            val family = when (m.hue) {
                Hue.TEAL, Hue.LILAC -> familyFor(m.hue)
                else -> neutralFamily(cs)
            }
            val iconTint = when (m.hue) {
                Hue.TEAL, Hue.LILAC, Hue.NEUTRAL -> family.onContainer
                else -> familyFor(m.hue).color
            }
            val supported = HealthRepository.isSupported(m)
            PillStat(
                m.icon, stringResource(m.label), family, modifier,
                value = when {
                    v != null -> formatWithUnit(context, m, v)
                    m == Metric.STEPS && supported -> "0"
                    else -> null
                },
                subtitle = when {
                    m == Metric.READINESS && supported -> stringResource(R.string.lh_calibrating)
                    else -> stringResource(R.string.lh_no_data)
                },
                iconTint = iconTint,
                onClick = { nav.push(Route.Detail(m)) },
            )
        }
    }
}

@Composable
private fun TimelineCard(e: TimelineEvent, vm: HealthViewModel) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val time = DateFormat.getTimeFormat(context).format(Date(e.timestampMs))
    var menu by remember { mutableStateOf(false) }
    val icon = when (e) {
        is TimelineEvent.Logged -> e.log.metric.icon
        is TimelineEvent.WorkoutDone -> R.drawable.gs_directions_run_vd_theme_24
    }
    Row(Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GsIcon(icon, cs.primary)
        Spacer(Modifier.width(14.dp))
        Text(time, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = cs.onSurface)
    }
    Surface(
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (e is TimelineEvent.WorkoutDone) {
                context.startActivity(WorkoutDetailsActivity.createSingleWorkoutIntent(context, e.workout.id, e.workout.device))
            }
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                GsIcon(icon, cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                when (e) {
                    is TimelineEvent.Logged -> {
                        val m = e.log.metric
                        Text(stringResource(R.string.lh_you_logged, stringResource(m.label).lowercase()),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = cs.onSurfaceVariant)
                        Text(formatWithUnit(context, m, e.log.value), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            color = cs.onSurface)
                    }
                    is TimelineEvent.WorkoutDone -> {
                        Text(stringResource(R.string.lh_you_did), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = cs.onSurfaceVariant)
                        val w = e.workout
                        Text((w.name?.takeIf { it.isNotBlank() } ?: w.kind.getLabel(context)) + " · " +
                            app.localhealth.ui.formatDuration(context, (w.end.time - w.start.time) / 60000.0),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = cs.onSurface)
                    }
                }
                Text(stringResource(R.string.lh_today_at, time), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = cs.onSurfaceVariant)
            }
            if (e is TimelineEvent.Logged) {
                Box {
                    IconButton(onClick = { menu = true }) { GsIcon(R.drawable.gs_more_vert_vd_theme_24, cs.onSurfaceVariant) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lh_delete)) }, onClick = { menu = false; vm.deleteLog(e.log.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(onDismiss: () -> Unit, onPick: (Metric) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.lh_log_manually), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = cs.onSurface, modifier = Modifier.padding(bottom = 24.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)) {
                LOGGABLE_METRICS.sortedBy { context.getString(it.label) }.forEach { m ->
                    Surface(shape = CircleShape, color = cs.surfaceContainer, onClick = { onPick(m) }) {
                        Row(Modifier.height(48.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            GsIcon(m.icon, cs.onSurface, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(m.label), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = cs.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogValueDialog(metric: Metric, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.replace(',', '.').toDoubleOrNull()
    val unit = metric.unit?.let { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { GsIcon(metric.icon, MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(metric.label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.lh_value)) },
                suffix = unit?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null && value > 0) { Text(stringResource(R.string.lh_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lh_cancel)) } },
    )
}
