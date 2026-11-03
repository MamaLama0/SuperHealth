package app.superhealth.ui.league

import app.superhealth.league.Progression
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

/** L'interface montre l'objectif entier ; aucune réduction de cible pour les périodes ouvertes. */
fun trackingProgress(streak: Progression.Streak): String = when (streak.track) {
    "FOOD" -> streak.percent?.let { trf("%1\$s %%", it) } ?: "—"
    "STRENGTH" -> trf("%1\$s / %2\$s séances", streak.progress, streak.target)
    else -> trf("%1\$s / %2\$s", streak.progress, streak.target)
}

fun trackingFraction(streak: Progression.Streak): Float =
    (if (streak.track == "FOOD") (streak.percent ?: 0) / 100f else streak.progress.toFloat() / streak.target.coerceAtLeast(1)).coerceIn(0f, 1f)

@Composable
fun trackingGoal(track: Progression.Track): String {
    if (track == Progression.Track.FOOD) {
        val range = app.superhealth.league.ProgressRepository.foodRange.collectAsState().value
        return trf("Validation entre %1\$s et %2\$s %% de votre cible calorique", range.minPercent, range.maxPercent)
    }
    return tr(when (track) {
    Progression.Track.FOOD -> "Objectif calorique du jour"
    Progression.Track.STRENGTH -> "Séances prévues cette semaine"
    Progression.Track.RUN -> "Jours de course cette semaine"
    Progression.Track.HIKE -> "Jours de randonnée cette semaine"
    Progression.Track.STEPS -> "Objectif de pas du jour"
    Progression.Track.SLEEP -> "Objectif de durée de sommeil"
    Progression.Track.MIND -> "10 minutes d'esprit et mobilité"
    })
}
