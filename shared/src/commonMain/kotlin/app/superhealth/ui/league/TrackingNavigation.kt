package app.superhealth.ui.league

import app.superhealth.platform.*

import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.league.Progression
import app.superhealth.modules.Modules
import app.superhealth.ui.go

/** Les raccourcis d'un suivi ouvrent sa propre activité, même après avoir consulté un autre sport. */
internal fun NavHostController.continueTracking(track: Progression.Track) {
    val route = when (track) {
        Progression.Track.FOOD -> "log"
        Progression.Track.STEPS -> "h/metric/STEPS"
        Progression.Track.SLEEP -> "h/metric/SLEEP"
        else -> {
            val section = when (track) {
                Progression.Track.STRENGTH -> "STRENGTH"
                Progression.Track.RUN -> "RUN"
                else -> if (Modules.isActive(Modules.STRETCH)) "STRETCH" else "MEDITATION"
            }
            SuperPrefs.update { it.copy(activityCurrent = section, activityAsked = true,
                activitySections = (it.activitySections + section).distinct()) }
            "activities"
        }
    }
    go(route)
}
