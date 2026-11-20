package app.superhealth.engine.activities.charts.pai

import app.superhealth.engine.activities.charts.spec.AxisSpec
import app.superhealth.engine.activities.charts.spec.ChartPoint
import app.superhealth.engine.activities.charts.spec.ChartSeries
import app.superhealth.engine.activities.charts.spec.ChartSpec
import app.superhealth.engine.activities.charts.spec.ChartValueFormat
import app.superhealth.engine.activities.charts.spec.SeriesStyle

object PaiChartData {
    private const val DAY_STACK = "day"
    private const val Y_HEADROOM = 20.0

    /**
     * Each day's total PAI as a column, split into what was earned before and on that day.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        totals: IntArray,
        today: IntArray,
        target: Int,
        totalLabel: String,
        totalColor: Int,
        dayLabel: String,
        dayColor: Int,
    ): ChartSpec {
        if (epochDays.isEmpty() || totals.all { it <= 0 }) {
            return ChartSpec.EMPTY
        }
        fun column(key: String, label: String, values: (Int) -> Int, color: Int) = ChartSeries(
            key, label,
            epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), values(it).toDouble()) },
            SeriesStyle.Column(color, stackKey = DAY_STACK),
        )
        return ChartSpec(
            series = listOf(
                column("earlier", totalLabel, { totals[it] - today[it] }, totalColor),
                column("today", dayLabel, { today[it] }, dayColor),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.INTEGER,
                minimum = 0.0,
                maximum = maxOf(totals.max(), target) + Y_HEADROOM,
            ),
        )
    }
}
