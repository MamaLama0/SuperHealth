package app.localhealth.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveTimeWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyEnergyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesActiveWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.DistanceWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HeartRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HrvWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepScoreWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Spo2Widget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressSimpleWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxAnyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.WeightWidget
import org.slf4j.LoggerFactory

/** A metric with an optional goal; null [value] means the paired devices don't record it. */
data class GoalMetric(val value: Double, val goal: Double) {
    val progress: Float get() = if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f
}

data class DaySummary(
    val date: LocalDate,
    val steps: GoalMetric? = null,
    val distanceMeters: GoalMetric? = null,
    val activeCalories: GoalMetric? = null,
    val activeMinutes: GoalMetric? = null,
    val sleepMinutes: GoalMetric? = null,
    val sleepScore: Int? = null,
    val heartRate: Int? = null,
    val bodyEnergy: Int? = null,
    val stress: Int? = null,
    val hrvWeeklyAverage: Int? = null,
    val spo2: Int? = null,
    val vo2Max: Float? = null,
    val weightKg: Double? = null,
)

data class Workout(
    val id: Long,
    val name: String?,
    val kind: ActivityKind,
    val start: Date,
    val end: Date,
    val device: GBDevice,
)

/**
 * Reads daily health data from the Gadgetbridge database through its device-agnostic dashboard
 * widgets, so every watch Gadgetbridge supports (Garmin or not) feeds the same screens.
 */
object HealthRepository {
    private val LOG = LoggerFactory.getLogger(HealthRepository::class.java)

    fun devices(): List<GBDevice> = GBApplication.app().deviceManager.devices

    suspend fun daySummary(date: LocalDate): DaySummary {
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toInt() - 1
        val query = DashboardQuery(endOfDay - 86400, endOfDay)
        val user = ActivityUser()

        val steps = load(StepsWidget, query)
        val distance = load(DistanceWidget, query)
        val calories = load(CaloriesActiveWidget, query)
        val activeTime = load(ActiveTimeWidget, query)
        val sleep = load(SleepWidget, query)

        return DaySummary(
            date = date,
            steps = steps?.let { GoalMetric(it.total.toDouble(), user.stepsGoal.toDouble()) },
            distanceMeters = distance?.let { GoalMetric(it.total.toDouble(), user.distanceGoalMeters.toDouble()) },
            activeCalories = calories?.let { GoalMetric(it.total.toDouble(), user.caloriesBurntGoal.toDouble()) },
            activeMinutes = activeTime?.let { GoalMetric(it.total.toDouble(), user.activeTimeGoalMinutes.toDouble()) },
            sleepMinutes = sleep?.takeIf { it.total > 0 }?.let { GoalMetric(it.total.toDouble(), user.sleepDurationGoal.toDouble()) },
            sleepScore = load(SleepScoreWidget, query)?.value?.takeIf { it > 0 },
            heartRate = load(HeartRateWidget, query)?.heartRate?.takeIf { it > 0 },
            bodyEnergy = load(BodyEnergyWidget, query)?.value?.takeIf { it >= 0 },
            stress = load(StressSimpleWidget, query)?.value?.takeIf { it > 0 },
            hrvWeeklyAverage = load(HrvWidget, query)?.weeklyAverage?.takeIf { it > 0 },
            spo2 = load(Spo2Widget, query)?.spo2?.takeIf { it > 0 },
            vo2Max = load(Vo2MaxAnyWidget, query)?.value?.takeIf { it > 0 },
            weightKg = load(WeightWidget, query)?.weightKg?.takeIf { it > 0 },
        )
    }

    suspend fun recentWorkouts(days: Long = 30): List<Workout> = withContext(Dispatchers.IO) {
        val from = Date(System.currentTimeMillis() - days * 86_400_000L)
        GBApplication.acquireDbReadOnly().use { db ->
            val byId = devices().associateBy { DBHelper.getDevice(it, db.daoSession).id ?: -1L }
            db.daoSession.baseActivitySummaryDao.queryBuilder()
                .where(BaseActivitySummaryDao.Properties.StartTime.gt(from))
                .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                .list()
                .mapNotNull { s ->
                    val device = byId[s.deviceId] ?: return@mapNotNull null
                    Workout(s.id ?: return@mapNotNull null, s.name, ActivityKind.fromCode(s.activityKind), s.startTime, s.endTime, device)
                }
        }
    }

    /** Returns null when no paired device supports [widget], or loading fails. */
    private suspend fun <D> load(widget: GBWidget<D>, query: DashboardQuery): D? {
        val instanceId = "localhealth_" + widget.id
        val config = WidgetConfig(
            WidgetInstance(instanceId, widget.id, 1),
            Prefs(GBApplication.getWidgetSharedPrefs(instanceId)),
            true,
            emptySet(),
        )
        val devices = config.resolveDevices(widget)
        if (devices.isEmpty()) return null
        return try {
            widget.loadData(WidgetDataScope(query, devices), config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Failed to load {}", widget.id, e)
            null
        }
    }
}
