package app.superhealth.training.domain

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Historique de musculation en CSV : une ligne par série (séparateur virgule, décimales point). */
object WorkoutCsv {
    const val HEADER = "date,heure,seance,programme,salle,duree_min,exercice,serie,type,poids_kg,reps,reps_partielles,rir,faite,rpe_seance,note"

    private fun q(s: String?) = if (s.isNullOrEmpty()) "" else "\"" + s.replace("\"", "\"\"") + "\""
    private fun d(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else String.format(Locale.US, "%.2f", it) } ?: ""

    fun export(history: List<Workout>, nameOf: (String) -> String, zone: ZoneId = ZoneId.systemDefault()) = buildString {
        appendLine(HEADER)
        history.sortedBy { it.startTime }.forEach { w ->
            val t = Instant.ofEpochMilli(w.startTime).atZone(zone)
            val head = listOf(t.toLocalDate().toString(), "%02d:%02d".format(t.hour, t.minute), q(w.name), q(w.programName), q(w.gymName),
                (w.durationSec / 60).toString())
            w.exercises.forEach { e ->
                val name = nameOf(e.exerciseId).ifBlank { e.exerciseId }
                e.sets.forEachIndexed { i, s ->
                    appendLine((head + listOf(q(name), (i + 1).toString(), s.type.name.lowercase(), d(s.weight), s.reps?.toString() ?: "",
                        s.partialReps.takeIf { it > 0 }?.toString() ?: "", s.rir?.toString() ?: "", if (s.done) "1" else "0",
                        w.rpe?.toString() ?: "", q(listOf(w.note, e.note).filter { it.isNotBlank() }.joinToString(" / ")))).joinToString(","))
                }
            }
        }
    }
}
