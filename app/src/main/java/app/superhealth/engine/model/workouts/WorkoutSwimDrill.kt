package app.superhealth.engine.model.workouts

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.dsl.LabeledEntry

/**
 * Pool swim step drill.
 */
enum class WorkoutSwimDrill(override val label: Int) : LabeledEntry {
    NONE(R.string.none),
    KICK(R.string.workout_swim_drill_kick),
    PULL(R.string.workout_swim_drill_pull),
    DRILL(R.string.swim_style_drill),
    ;

    companion object {
        @JvmStatic
        fun fromName(name: String?): WorkoutSwimDrill? = enumByName(name)
    }
}
