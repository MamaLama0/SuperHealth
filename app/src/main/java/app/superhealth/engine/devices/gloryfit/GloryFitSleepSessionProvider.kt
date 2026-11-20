package app.superhealth.engine.devices.gloryfit

import app.superhealth.engine.devices.GenericSleepStageSampleProvider
import app.superhealth.engine.devices.SleepSessionProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityKind
import app.superhealth.engine.model.SleepSession
import app.superhealth.engine.model.SleepStage

class GloryFitSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        val toMillis: Long = (tsTo + LOOK_AHEAD_SECONDS) * 1000L

        val sleepStagesSampleProvider = GenericSleepStageSampleProvider(device, session)
        val sleepStageSamples = sleepStagesSampleProvider.getAllSamples(fromMillis, toMillis)

        val unsanitizedStages = sleepStageSamples.map {
            SleepStage(
                sleepStageToActivityKind(it.stage),
                it.timestamp,
                it.timestamp + it.duration * 60 * 1000L
            )
        }
        return SleepSessionProvider.groupIntoSessions(unsanitizedStages)
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60
        private const val LOOK_AHEAD_SECONDS: Int = 12 * 60 * 60

        fun sleepStageToActivityKind(stage: Int): ActivityKind {
            return when (stage) {
                1 -> ActivityKind.DEEP_SLEEP
                2 -> ActivityKind.LIGHT_SLEEP
                3 -> ActivityKind.AWAKE_SLEEP
                4 -> ActivityKind.REM_SLEEP
                else -> ActivityKind.UNKNOWN
            }
        }
    }
}
