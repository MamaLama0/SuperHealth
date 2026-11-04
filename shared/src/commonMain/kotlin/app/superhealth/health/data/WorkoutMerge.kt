package app.superhealth.health.data

import kotlin.math.abs

/** Une même sortie peut être écrite dans Santé par sa montre puis par une application liée. */
internal object WorkoutMerge {
    fun reconcile(records: List<Workout>): List<Workout> {
        val selected = ArrayList<Workout>()
        val byMinute = HashMap<Long, MutableList<Workout>>()
        val ordered = records.distinctBy { it.id }.sortedWith(
            compareByDescending<Workout> { it.distanceM != null }
                .thenByDescending { it.kcal != null }
                .thenBy { it.sourceApp?.contains("strava", ignoreCase = true) == true }
                .thenBy { it.id })
        for (workout in ordered) {
            val minute = workout.start.time / 60000
            val copies = (minute - 1..minute + 1).flatMap { byMinute[it].orEmpty() }
            if (copies.none { sameEvent(it, workout) }) {
                selected += workout
                byMinute.getOrPut(minute) { ArrayList() } += workout
            }
        }
        return selected.sortedByDescending { it.start.time }
    }

    private fun sameEvent(a: Workout, b: Workout): Boolean {
        if (a.sourceApp == null || b.sourceApp == null || a.sourceApp == b.sourceApp || a.kind != b.kind) return false
        if (a.kind in setOf(ActivityKind.UNKNOWN, ActivityKind.ACTIVITY, ActivityKind.EXERCISE, ActivityKind.NOT_MEASURED)) return false
        val duration = a.end.time - a.start.time
        val otherDuration = b.end.time - b.start.time
        if (duration <= 0 || otherDuration <= 0 || abs(a.start.time - b.start.time) > 60000) return false
        val distance = a.distanceM?.takeIf { it.isFinite() && it > 0 }
        val otherDistance = b.distanceM?.takeIf { it.isFinite() && it > 0 }
        val overlap = (minOf(a.end.time, b.end.time) - maxOf(a.start.time, b.start.time)).coerceAtLeast(0)
        if (distance == null || otherDistance == null) {
            return abs(a.start.time - b.start.time) <= 15000 && overlap >= 0.95 * maxOf(duration, otherDuration)
        }
        val delta = abs(distance - otherDistance)
        if (delta > maxOf(100.0, 0.15 * maxOf(distance, otherDistance))) return false
        return overlap >= 0.8 * maxOf(duration, otherDuration) ||
            minOf(duration, otherDuration) >= 60000 && delta <= maxOf(50.0, 0.03 * maxOf(distance, otherDistance))
    }
}
