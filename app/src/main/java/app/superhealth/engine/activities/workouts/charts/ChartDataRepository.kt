package app.superhealth.engine.activities.workouts.charts

import app.superhealth.engine.activities.charts.IntervalChartUtils
import app.superhealth.engine.model.workout.WorkoutChart

object ChartDataRepository {
    var chartData: List<WorkoutChart>? = null

    // Workout-global interval rows, carried so the compare/overlay view can draw interval splits.
    var intervals: List<IntervalChartUtils.IntervalRow>? = null

    fun clear() {
        chartData = null
        intervals = null
    }
}
