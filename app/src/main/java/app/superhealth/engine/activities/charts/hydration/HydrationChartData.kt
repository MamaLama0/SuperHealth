package app.superhealth.engine.activities.charts.hydration

import app.superhealth.engine.activities.charts.spec.AxisSpec
import app.superhealth.engine.activities.charts.spec.ChartPoint
import app.superhealth.engine.activities.charts.spec.ChartSeries
import app.superhealth.engine.activities.charts.spec.ChartSpec
import app.superhealth.engine.activities.charts.spec.ChartValueFormat
import app.superhealth.engine.activities.charts.spec.LimitLineSpec
import app.superhealth.engine.activities.charts.spec.SeriesStyle

object HydrationChartData {
    private const val HEADROOM = 1.1

    /**
     * Intake per day as columns, with the daily goal as a dashed line.
     */
    @JvmStatic
    fun periodSpec(epochDays: LongArray, volumes: DoubleArray, goal: Double, label: String, color: Int): ChartSpec {
        if (epochDays.isEmpty() || volumes.all { it <= 0.0 }) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    "intake", label,
                    epochDays.indices.filter { volumes[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), volumes[it]) },
                    SeriesStyle.Column(color),
                ),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = 0.0,
                maximum = maxOf(volumes.max(), goal) * HEADROOM,
            ),
            limitLines = if (goal > 0) listOf(LimitLineSpec(value = goal, color = color)) else emptyList(),
        )
    }
}
