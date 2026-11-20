package app.superhealth.engine.devices.xiaomi

import app.superhealth.engine.devices.SleepSessionProvider
import app.superhealth.engine.devices.XiaomiSleepStageSampleProvider
import app.superhealth.engine.devices.XiaomiSleepTimeSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.SleepSession
import app.superhealth.engine.model.SleepStage

class XiaomiSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val sleepSessions = ArrayList<SleepSession>()

        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        val toMillis: Long = tsTo * 1000L

        // Get sleep sessions
        val sleepTimeSampleProvider = XiaomiSleepTimeSampleProvider(device, session)
        val sleepTimeSamples = sleepTimeSampleProvider.getAllSamples(fromMillis, toMillis)
        if (sleepTimeSamples.isEmpty()) {
            return emptyList()
        }

        val sleepStagesSampleProvider = XiaomiSleepStageSampleProvider(device, session)

        for (sessionSample in sleepTimeSamples) {
            val wakeupTime = sessionSample.wakeupTime ?: continue
            val stageSamples = sleepStagesSampleProvider.getAllSamples(sessionSample.timestamp, wakeupTime)

            val unsanitizedStages = stageSamples.map {
                SleepStage(
                    XiaomiSampleProvider.getActivityKindForSample(it),
                    it.timestamp,
                    -1L
                )
            }

            SleepSessionProvider.sanitizeStages(
                unsanitizedStages,
                sessionSample.timestamp,
                wakeupTime
            )?.let { sleepSessions.add(it) }
        }

        return sleepSessions
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60
    }
}
