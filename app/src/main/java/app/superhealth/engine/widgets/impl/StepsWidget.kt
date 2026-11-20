/*  Copyright (C) 2026 José Rebelo

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.widgets.impl

import android.content.Context
import app.superhealth.engine.R
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityUser
import app.superhealth.engine.util.FormatUtils
import app.superhealth.engine.widgets.Stat
import app.superhealth.engine.widgets.TileModel
import app.superhealth.engine.widgets.WidgetColors
import app.superhealth.engine.widgets.WidgetDataScope
import app.superhealth.engine.widgets.WidgetHistory
import app.superhealth.engine.widgets.WidgetConfig
import java.text.NumberFormat

/**
 * Total steps for the day against the user's step goal.
 */
object StepsWidget : MetricWidget<StepsWidget.Data>() {
    override val id = "steps"
    override val label = R.string.steps
    override val icon = R.drawable.ic_steps
    override val chartTab = "stepsweek"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStepCounter(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.stepsTotal()
        val hourly = if (config.isLarge) {
            scope.hourlySums(scope.devices.filter { supportsStepCounter(it) }) { it.steps.toFloat() }
        } else {
            null
        }
        return Data(
            total,
            scope.stepsGoalFactor(total),
            ActivityUser().stepsGoal,
            hourly,
            scope.hoursStarted,
            if (config.isLargeStyle) scope.distanceTotal() else 0f,
            if (config.isLargeStyle) scope.activeMinutesTotal() else 0L,
        )
    }

    override fun previewData() = Data(
        total = 6400,
        goalFactor = 6400f / ActivityUser.defaultUserStepsGoal,
        goal = ActivityUser.defaultUserStepsGoal,
        hourly = floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 520f, 1340f, 310f, 240f, 420f,
            1610f, 830f, 360f, 770f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        ),
        hoursStarted = 16,
        distanceMeters = 4800f,
        activeMinutes = 42,
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val number = NumberFormat.getInstance()
        return goalTile(
            context, config, number.format(data.total), WidgetColors.activity, data.goalFactor,
            number.format(data.goal), data.hourly, data.hoursStarted,
            ratio = if (data.goal > 0) data.total / data.goal.toDouble() else 0.0,
            toGo = (data.goal - data.total).takeIf { it > 0 }?.let { number.format(it) },
            extraStats = listOf(
                Stat(FormatUtils.getFormattedDistanceLabel(data.distanceMeters.toDouble()), context.getString(R.string.distance)),
                Stat(clock(data.activeMinutes), context.getString(R.string.activity_list_summary_active_time)),
            ),
            lineGoal = data.goal.toFloat(),
        )
    }

    private fun supportsStepCounter(device: GBDevice) = device.deviceCoordinator.supportsStepCounter(device)

    /**
     * [hourly] holds steps per hour, only for the tall, wide and large styles. [distanceMeters] and
     * [activeMinutes] are only loaded for the large style.
     */
    class Data(
        val total: Int,
        val goalFactor: Float,
        val goal: Int,
        val hourly: FloatArray? = null,
        val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
        val distanceMeters: Float = 0f,
        val activeMinutes: Long = 0,
    )
}
