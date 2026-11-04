package app.superhealth.health.data

import app.superhealth.platform.Date
import kotlin.test.*

class WorkoutMergeTest {
    private fun workout(id: Long, source: String?, start: Long = 0, end: Long = 3600000, distance: Double? = 5000.0) =
        Workout(id, null, ActivityKind.RUNNING, Date(start), Date(end), SourceDevice("healthkit", source ?: "Health"), distance, 300.0, source)

    @Test fun mirroredWorkoutsPreferAnEquivalentDirectSourceAndKeepSourceRecordsIntact() {
        val direct = workout(1, "com.apple.health")
        val strava = workout(2, "com.strava.Strava")
        assertEquals(listOf(direct), WorkoutMerge.reconcile(listOf(strava, direct)))
        assertEquals(listOf(direct), WorkoutMerge.reconcile(listOf(direct, strava)))
    }

    @Test fun repeatedSameSourceSessionsAndDifferentSportsAreKept() {
        val run = workout(1, "watch")
        val sameSource = workout(2, "watch")
        val bike = workout(3, "other").copy(kind = ActivityKind.CYCLING)
        assertEquals(3, WorkoutMerge.reconcile(listOf(run, sameSource, bike)).size)
    }

    @Test fun activeAndElapsedTimeCopiesMatchWhenStartsAndDistancesAgree() {
        val run = workout(1, "watch")
        val active = workout(2, "other", start = 30000, end = 1830000, distance = 5050.0)
        assertEquals(1, WorkoutMerge.reconcile(listOf(run, active)).size)
        assertEquals(2, WorkoutMerge.reconcile(listOf(run, active.copy(distanceM = 7000.0))).size)
    }

    @Test fun missingDistanceRequiresTightlyAlignedTimesAndKnownDifferentSources() {
        val run = workout(1, "watch")
        assertEquals(1, WorkoutMerge.reconcile(listOf(run, workout(2, "other", distance = null))).size)
        assertEquals(2, WorkoutMerge.reconcile(listOf(run, workout(2, null, distance = null))).size)
        assertEquals(2, WorkoutMerge.reconcile(listOf(run, workout(2, "other", start = 120000, distance = null))).size)
    }
}
