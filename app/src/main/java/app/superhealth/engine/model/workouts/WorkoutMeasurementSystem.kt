package app.superhealth.engine.model.workouts

import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.dsl.LabeledEntry
import app.superhealth.engine.model.DistanceUnit

/**
 * Per-step display unit override. Does not affect database storage unit.
 */
enum class WorkoutMeasurementSystem(override val label: Int) : LabeledEntry {
    METRIC(R.string.unit_metric),
    IMPERIAL(R.string.unit_imperial),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutMeasurementSystem? = enumByName(name)

        @JvmStatic
        fun globalDefault(): WorkoutMeasurementSystem = when (GBApplication.getPrefs().distanceUnit) {
            DistanceUnit.IMPERIAL -> IMPERIAL
            else -> METRIC
        }
    }
}
