package app.localhealth.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.localhealth.data.DaySummary
import app.localhealth.ui.components.MetricTile
import app.localhealth.ui.components.SectionHeader
import app.localhealth.ui.components.TileRow
import app.localhealth.ui.theme.extendedColors
import nodomain.freeyourgadget.gadgetbridge.R

@Composable
fun HealthScreen(summary: DaySummary?, contentPadding: PaddingValues) {
    val ext = MaterialTheme.extendedColors
    val noData = stringResource(R.string.lh_no_data)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item { SectionHeader(stringResource(R.string.lh_section_vitals)) }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.Favorite, stringResource(R.string.lh_heart_rate),
                        summary?.heartRate?.let { formatInt(it) }, stringResource(R.string.lh_unit_bpm), ext.magenta, m,
                        emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.ShowChart, "HRV",
                        summary?.hrvWeeklyAverage?.let { formatInt(it) }, stringResource(R.string.lh_unit_ms), ext.teal, m,
                        emptyText = noData)
                },
            )
        }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.Air, stringResource(R.string.lh_spo2),
                        summary?.spo2?.let { formatInt(it) }, "%", ext.blue, m, emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.SelfImprovement, stringResource(R.string.lh_stress),
                        summary?.stress?.let { formatInt(it) }, "/ 100", ext.goldenrod, m, emptyText = noData)
                },
            )
        }
        item { SectionHeader(stringResource(R.string.lh_sleep)) }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.Bedtime, stringResource(R.string.lh_sleep),
                        summary?.sleepMinutes?.let { formatDuration(it.value) }, null, ext.lilac, m,
                        progress = summary?.sleepMinutes?.progress, emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.Bedtime, stringResource(R.string.lh_sleep_score),
                        summary?.sleepScore?.let { formatInt(it) }, "/ 100", ext.lilac, m, emptyText = noData)
                },
            )
        }
        item { SectionHeader(stringResource(R.string.lh_section_body)) }
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.MonitorWeight, stringResource(R.string.lh_weight),
                        summary?.weightKg?.let { formatDecimal(it) }, stringResource(R.string.lh_unit_kg), ext.green, m,
                        emptyText = noData)
                },
                null,
            )
        }
    }
}
