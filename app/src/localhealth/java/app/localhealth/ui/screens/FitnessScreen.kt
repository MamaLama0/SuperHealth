package app.localhealth.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.localhealth.data.DaySummary
import app.localhealth.data.Workout
import app.localhealth.ui.components.HealthCard
import app.localhealth.ui.components.IconBadge
import app.localhealth.ui.components.MetricTile
import app.localhealth.ui.components.SectionHeader
import app.localhealth.ui.components.TileRow
import app.localhealth.ui.theme.extendedColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutDetailsActivity

@Composable
fun FitnessScreen(summary: DaySummary?, workouts: List<Workout>, contentPadding: PaddingValues) {
    val ext = MaterialTheme.extendedColors
    val context = LocalContext.current
    val noData = stringResource(R.string.lh_no_data)
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            TileRow(
                { m ->
                    MetricTile(Icons.Rounded.MonitorHeart, stringResource(R.string.lh_vo2max),
                        summary?.vo2Max?.let { formatDecimal(it.toDouble(), 0) }, null, ext.tangerine, m,
                        emptyText = noData)
                },
                { m ->
                    MetricTile(Icons.Rounded.LocalFireDepartment, stringResource(R.string.lh_active_calories),
                        summary?.activeCalories?.let { formatInt(it.value) }, stringResource(R.string.lh_unit_kcal),
                        ext.tangerine, m, progress = summary?.activeCalories?.progress, emptyText = noData)
                },
            )
        }
        item { SectionHeader(stringResource(R.string.lh_section_exercise)) }
        if (workouts.isEmpty()) {
            item {
                Text(noData, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
        }
        items(workouts, key = { it.id }) { w ->
            HealthCard(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                onClick = {
                    context.startActivity(WorkoutDetailsActivity.createSingleWorkoutIntent(context, w.id, w.device))
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.AutoMirrored.Rounded.DirectionsRun, ext.tangerine, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name?.takeIf { it.isNotBlank() } ?: w.kind.getLabel(context),
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            DateUtils.formatDateTime(context, w.start.time,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatDuration((w.end.time - w.start.time) / 60000.0), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
