package app.superhealth.engine.devices.huami

import app.superhealth.engine.devices.SleepSessionProvider
import app.superhealth.engine.devices.miband.MiBand2SampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.MiBandActivitySample
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.ActivityKind
import app.superhealth.engine.model.SleepSession
import app.superhealth.engine.model.SleepStage
import app.superhealth.engine.util.sleep.EstimatedStage
import app.superhealth.engine.util.sleep.SleepMinute
import app.superhealth.engine.util.sleep.SleepMinuteKind
import app.superhealth.engine.util.sleep.SleepStageEstimator
import java.time.Instant

class HuamiEstimatedSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession,
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val samples = MiBand2SampleProvider(device, session).getDeviceSamples(
            tsFrom - LOOK_BACK_SECONDS,
            tsTo + LOOK_AHEAD_SECONDS
        )

        return SleepStageEstimator.estimateAll(samples.map(::toMinute))
            .filter { it.onset.epochSecond in tsFrom..<tsTo }
            .map { estimate ->
                SleepSession(estimate.stages.map {
                    SleepStage(toActivityKind(it.stage), it.start.toEpochMilli(), it.end.toEpochMilli())
                })
            }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60
        private const val LOOK_AHEAD_SECONDS: Int = 24 * 60 * 60

        /**
         * Divisor to convert intensity to movement, matched against 3 nights from Amazfit Bip samples.
         */
        private const val INTENSITY_TO_MOVEMENT = 8f

        private fun toMinute(sample: MiBandActivitySample): SleepMinute {
            val time = Instant.ofEpochSecond(sample.timestamp.toLong())
            return when (sample.rawKind) {
                HuamiConst.TYPE_NONWEAR, HuamiConst.TYPE_CHARGING -> SleepMinute(time, SleepMinuteKind.UNMEASURABLE)
                else -> SleepMinute(
                    time,
                    SleepMinuteKind.RAW,
                    sample.rawIntensity / INTENSITY_TO_MOVEMENT,
                    sample.heartRate.takeIf { it in 1..254 }?.toFloat()
                )
            }
        }

        private fun toActivityKind(stage: EstimatedStage): ActivityKind = when (stage) {
            EstimatedStage.AWAKE -> ActivityKind.AWAKE_SLEEP
            EstimatedStage.LIGHT -> ActivityKind.LIGHT_SLEEP
            EstimatedStage.DEEP -> ActivityKind.DEEP_SLEEP
            EstimatedStage.REM -> ActivityKind.REM_SLEEP
            EstimatedStage.UNKNOWN -> ActivityKind.SLEEP_ANY
        }
    }
}
