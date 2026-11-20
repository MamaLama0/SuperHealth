package app.superhealth.engine.devices.garmin

import android.content.Context
import app.superhealth.engine.R
import app.superhealth.engine.activities.charts.DefaultChartsProvider
import app.superhealth.engine.activities.workouts.entries.ActivitySummarySimpleEntry
import app.superhealth.engine.database.DBHandler
import app.superhealth.engine.devices.GarminSleepRestlessMomentsSampleProvider
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivitySummaryEntries.UNIT_NONE

class GarminChartsProvider : DefaultChartsProvider() {
    override fun getDailySleepStats(
        context: Context,
        db: DBHandler,
        device: GBDevice,
        tsStart: Int,
        tsEnd: Int
    ): Map<String, ActivitySummarySimpleEntry> {
        val sleepRestlessMomentsSampleProvider = GarminSleepRestlessMomentsSampleProvider(device, db.getDaoSession())
        val restlessMoments = sleepRestlessMomentsSampleProvider.getAllSamples(tsStart * 1000L, tsEnd * 1000L)
        if (restlessMoments.isEmpty()) {
            return emptyMap()
        }
        restlessMoments.sumOf { it.count }
        return mapOf(
            context.getString(R.string.sleep_restless_moments) to ActivitySummarySimpleEntry(
                restlessMoments.sumOf { it.count },
                UNIT_NONE
            )
        )
    }
}
