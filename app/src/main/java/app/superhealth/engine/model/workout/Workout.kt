package app.superhealth.engine.model.workout

import app.superhealth.engine.activities.charts.IntervalChartUtils
import app.superhealth.engine.entities.BaseActivitySummary
import app.superhealth.engine.model.ActivitySummaryData

data class Workout @JvmOverloads constructor(
    val summary: BaseActivitySummary,
    val data: ActivitySummaryData,
    val charts: List<WorkoutChart> = emptyList(),
    // Derived from the activity track's segments; drives the interval lines on the charts.
    val intervals: List<IntervalChartUtils.IntervalRow> = emptyList()
)
