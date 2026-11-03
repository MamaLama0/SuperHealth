package app.superhealth.league

import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import kotlinx.serialization.Serializable

object GroupRecords {
    @Serializable data class Record(val exercise: String, val name: String, val metric: String,
        val value: Double, val unit: String, val achieved: Long)
    fun valid(r: Record, now: Long = System.currentTimeMillis()) = r.exercise.length in 1..120 && r.name.length in 1..160 &&
        r.metric in setOf("WEIGHT", "E1RM", "REPS") && r.value.isFinite() && r.value > 0 && r.value <= 100000 &&
        r.unit == (if (r.metric == "REPS") "reps" else "kg") && r.achieved in 1..(now + 300000)
    fun comparable(a: Record, b: Record) = a.exercise == b.exercise && a.metric == b.metric && a.unit == b.unit
    fun label(record: Record) = when (record.metric) { "WEIGHT" -> "Charge maximale"; "E1RM" -> "1RM estimé"; else -> "Répétitions" }
    fun draft(record: Record) = SocialPosts.Draft(record.name,
        app.superhealth.ui.tr(label(record)) + " · " + app.superhealth.nutrition.ui.num(record.value, 1) + " " + record.unit,
        "PR", record)
    /** Actual improvements in chronological history, excluding warm-up and assisted movements. */
    fun recent(now: Long = System.currentTimeMillis(), days: Int = 90): List<Record> {
        val db = WorkoutApp.db.value ?: return emptyList()
        return fromHistory(WorkoutApp.store.state.value.history, db, now, days)
    }
    internal fun fromHistory(history: List<app.superhealth.training.domain.Workout>, db: app.superhealth.training.domain.ExerciseDb,
        now: Long, days: Int): List<Record> {
        val best = mutableMapOf<Pair<String, String>, Double>(); val records = mutableListOf<Record>()
        for (workout in history.sortedBy { it.startTime }) {
            if (workout.startTime > now) continue
            for (entry in workout.exercises) {
                val exercise = db.exercise(entry.exerciseId) ?: continue
                if ("aw" in exercise.metrics) continue
                val sets = Analytics.workingSets(entry)
                val values = buildList<Pair<String, Double>> {
                    if (exercise.usesWeight) sets.mapNotNull { it.weight }.maxOrNull()?.let { add("WEIGHT" to it) }
                    if (exercise.usesWeight && exercise.usesReps) Analytics.bestPerformance(exercise, sets)?.let { add("E1RM" to it) }
                    if (!exercise.usesWeight && exercise.usesReps) sets.mapNotNull { it.reps }.maxOrNull()?.let { add("REPS" to it.toDouble()) }
                }
                for ((metric, value) in values) {
                    if (!value.isFinite() || value <= 0) continue
                    val key = exercise.id to metric; val old = best[key]
                    if (old == null || value > old) {
                        best[key] = value
                        if (workout.startTime >= now - days.toLong() * 86400000) records += Record(exercise.id, exercise.name, metric, value,
                            if (metric == "REPS") "reps" else "kg", workout.startTime)
                    }
                }
            }
        }
        return records.sortedByDescending { it.achieved }.distinctBy { it.exercise to it.metric }
    }
}
