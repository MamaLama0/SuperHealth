package app.superhealth.engine.activities.charts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.superhealth.engine.GBApplication
import app.superhealth.engine.database.DBHandler
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivitySample

/**
 * Background-thread data access for one chart refresh.
 */
class ChartDataScope {
    /**
     * Runs [block] against a read-only DB handle, closed when [block] returns.
     */
    suspend fun <T> db(block: (DBHandler) -> T): T = withContext(Dispatchers.IO) {
        GBApplication.acquireDbReadOnly().use { block(it) }
    }

    /**
     * All activity samples for [device] between [tsStart] and [tsEnd] (epoch seconds, inclusive).
     */
    suspend fun activitySamples(device: GBDevice, tsStart: Int, tsEnd: Int): List<ActivitySample> =
        db { db ->
            val provider = device.deviceCoordinator.getSampleProvider(device, db.daoSession)
            provider?.getAllActivitySamples(tsStart, tsEnd) ?: emptyList()
        }
}
