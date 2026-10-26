package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.training.WorkoutApp
import app.superhealth.training.data.AppState
import app.superhealth.training.domain.SetType

/**
 * Séance en cours hors de l'app (équivalent des Live Activities de MacroFactor Workouts ; notification permanente sur
 * Android) : exercice et série à faire, séries faites, chronomètre de séance ou compte à rebours du repos.
 */
object SessionNotification {
    fun update(ctx: Context, st: AppState) {
        val s = st.session ?: run { showSessionActivity(null); return }
        val done = s.exercises.sumOf { e -> e.sets.count { it.done } }
        val total = s.exercises.sumOf { it.sets.size }
        // prochaine série à faire à partir de l'exercice affiché
        val next = (s.exercises.indices.drop(s.current) + s.exercises.indices.take(s.current)).firstNotNullOfOrNull { i ->
            s.exercises[i].sets.indexOfFirst { !it.done }.takeIf { it >= 0 }?.let { i to it }
        }
        var line = tr("Toutes les séries sont faites")
        var load = ""
        next?.let { (i, si) ->
            val ex = s.exercises[i]
            val set = ex.sets[si]
            val work = ex.sets.filter { it.type != SetType.WARMUP }
            val label = if (set.type == SetType.WARMUP) tr("Échauffement") else trf("Série %1\$s/%2\$s", (work.indexOf(set) + 1), work.size)
            line = "${WorkoutApp.exerciseName(ex.exerciseId)} · $label"
            val wgt = set.weight ?: set.autoWeight
            val reps = set.reps ?: set.autoReps
            load = listOfNotNull(wgt?.let { "${clean(st.settings.w(it))} ${st.settings.unit.label.tr()}" }, reps?.let { trf("× %1\$s", it) }).joinToString(" ")
        }
        val now = System.currentTimeMillis()
        val resting = s.restEndsAt != null && s.restEndsAt > now && s.pausedAt == null
        showSessionActivity(SessionActivity(
            title = s.name, line = line, load = load, done = done, total = total,
            startMs = if (s.pausedAt != null) now - s.elapsedSec(now) * 1000 else s.startTime + s.pausedMs,
            restEndMs = if (resting) s.restEndsAt else null, paused = s.pausedAt != null,
            restLabel = tr("Repos"), pausedLabel = tr("En pause"),
        ))
    }
}
