package app.localhealth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.localhealth.data.DaySummary
import app.localhealth.data.GoalMetric
import app.localhealth.ui.components.GoalRings
import app.localhealth.ui.components.HealthCard
import app.localhealth.ui.components.LegendDot
import app.localhealth.ui.components.MetricTile
import app.localhealth.ui.components.SectionHeader
import app.localhealth.ui.components.TileRow
import app.localhealth.ui.theme.ColorFamily
import app.localhealth.ui.theme.extendedColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import nodomain.freeyourgadget.gadgetbridge.R

@Composable
fun TodayScreen(
    date: LocalDate,
    summary: DaySummary?,
    onDateChange: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    val ext = MaterialTheme.extendedColors
    val noData = stringResource(R.string.lh_no_data)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item { Greeting(date, onDateChange) }
        item { GoalsHero(summary) }

        item { SectionHeader(stringResource(R.string.lh_section_activity)) }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.Route, stringResource(R.string.lh_distance),
                        summary?.distanceMeters?.let { formatDecimal(it.value / 1000.0, 2) },
                        stringResource(R.string.lh_unit_km), ext.blueMarine, m,
                        progress = summary?.distanceMeters?.progress, emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.Favorite, stringResource(R.string.lh_heart_rate),
                        summary?.heartRate?.let { formatInt(it) }, stringResource(R.string.lh_unit_bpm),
                        ext.magenta, m, emptyText = noData)
                },
            )
        }

        item { SectionHeader(stringResource(R.string.lh_section_sleep_recovery)) }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.Bedtime, stringResource(R.string.lh_sleep),
                        summary?.sleepMinutes?.let { formatDuration(it.value) }, null, ext.lilac, m,
                        progress = summary?.sleepMinutes?.progress,
                        caption = summary?.sleepScore?.let { stringResource(R.string.lh_sleep_score) + " · " + it },
                        emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.Bolt, stringResource(R.string.lh_body_energy),
                        summary?.bodyEnergy?.let { formatInt(it) }, "/ 100", ext.green, m,
                        progress = summary?.bodyEnergy?.let { it / 100f }, emptyText = noData)
                },
            )
        }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.SelfImprovement, stringResource(R.string.lh_stress),
                        summary?.stress?.let { formatInt(it) }, "/ 100", ext.goldenrod, m,
                        progress = summary?.stress?.let { it / 100f }, emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.Favorite, "HRV",
                        summary?.hrvWeeklyAverage?.let { formatInt(it) }, stringResource(R.string.lh_unit_ms),
                        ext.teal, m, emptyText = noData)
                },
            )
        }
    }
}

@Composable
private fun Greeting(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val title = if (date == today) {
        val h = LocalTime.now().hour
        stringResource(
            when {
                h < 12 -> R.string.lh_greeting_morning
                h < 18 -> R.string.lh_greeting_afternoon
                else -> R.string.lh_greeting_evening
            }
        )
    } else {
        date.format(DateTimeFormatter.ofPattern("EEEE"))
            .replaceFirstChar { it.titlecase() }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
            Icon(Icons.Rounded.ChevronLeft, null)
        }
        IconButton(onClick = { onDateChange(date.plusDays(1)) }, enabled = date < today) {
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun GoalsHero(summary: DaySummary?) {
    val ext = MaterialTheme.extendedColors
    val rows = listOf(
        Triple(Icons.AutoMirrored.Rounded.DirectionsWalk, R.string.lh_steps, summary?.steps) to ext.blue,
        Triple(Icons.Rounded.Timer, R.string.lh_active_minutes, summary?.activeMinutes) to ext.teal,
        Triple(Icons.Rounded.LocalFireDepartment, R.string.lh_active_calories, summary?.activeCalories) to ext.tangerine,
    )
    HealthCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                GoalRings(rows.map { (it.first.third?.progress ?: 0f) to it.second }, size = 132.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { (t, family) -> GoalLine(stringResource(t.second), t.third, family, t.second) }
            }
        }
    }
}

@Composable
private fun GoalLine(label: String, metric: GoalMetric?, family: ColorFamily, labelRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(family.data)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (metric == null) {
                Text("—", style = MaterialTheme.typography.titleLarge)
            } else {
                val unit = when (labelRes) {
                    R.string.lh_active_minutes -> " " + stringResource(R.string.lh_unit_min)
                    R.string.lh_active_calories -> " " + stringResource(R.string.lh_unit_kcal)
                    else -> ""
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatInt(metric.value), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium))
                    Text(
                        unit + " / " + formatInt(metric.goal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp, start = 2.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
