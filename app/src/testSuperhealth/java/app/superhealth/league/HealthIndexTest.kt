package app.superhealth.league

import app.superhealth.bridge.Bridge
import app.superhealth.bridge.RunImport
import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test

class HealthIndexTest {
    @Test fun insufficientDomainsDoNotProduceAHealthScore() {
        assertNull(HealthIndex.total(listOf(100, null, 100, null, null, null)))
    }
    @Test fun missingDomainsAreRenormalizedAndExcessCannotBoostTheScore() {
        assertEquals(1000, HealthIndex.total(listOf(100, 100, 100, null, null, null)))
        assertEquals(1000, HealthIndex.total(listOf(200, 100, 100, null, null, null)))
        assertEquals(500, HealthIndex.total(List(6) { 50 }))
    }
    @Test fun missingDaysAreNotZeroAndSparseDataIsNotEnough() {
        assertNull(HealthIndex.daily(listOf(8000.0, 8000.0, 8000.0, null, null, null, null), 8000.0))
        assertEquals(100, HealthIndex.daily(listOf(8000.0, 8000.0, 8000.0, 16000.0, null, null, null), 8000.0))
        assertEquals(50, HealthIndex.daily(List(7) { 4000.0 }, 8000.0))
    }

    @Test fun performanceChangesTheScoreAtEqualVolumeWithoutStackingUnlimitedVolumeOrOutliers() {
        fun score(sessions: Int, current: Double?, previous: Double?): Int? {
            val strength = HealthIndex.sport(sessions.toDouble(), 2.0, HealthIndex.gain(current, previous))
            return HealthIndex.total(listOf(strength, null, null, null, 100, 0))
        }
        assertEquals(825, score(2, 100.0, 100.0))
        assertEquals(850, score(2, 105.0, 100.0))
        assertEquals(875, score(2, 110.0, 100.0))
        assertEquals(825, score(20, 95.0, 100.0))
        assertEquals(825, score(2, 100.0, null))
        assertEquals(825, score(2, Double.NaN, 100.0))
        assertEquals(875, score(2, Double.MAX_VALUE, 100.0))
        assertEquals(HealthIndex.gain(11.0, 10.0), HealthIndex.gain(110.0, 100.0), 0.00001)
    }

    @Test fun monthlyAverageKeepsOnlyMatchingModelDaysAndCoverage() {
        val history = mapOf("v2:50:49" to 900, "v3:50:49" to 999, "v4:50:48" to 900, "v4:22:49" to 900,
            "v4:23:49" to 800, "v4:50:49" to 850)
        assertEquals(825.0, HealthIndex.mean28(history, 50, 49)!!, 0.0)
        assertNull(HealthIndex.mean28(history, 50, 7))
    }

    @Test fun onlyComparableRunsContributeAndFasterFiveKCanOutscoreTheSameDistance() {
        fun run(metres: Double, seconds: Int): Bridge.Activity {
            val r = RunImport.Run(1, "QA", "RUN", "", 0, seconds * 1000L, seconds.toDouble(), metres)
            return Bridge.Activity("qa", Bridge.Kind.RUN, "QA", r.start, r.end, Bridge.Source.IMPORT, run = r)
        }
        val previous = listOf(run(5000.0, 1800))
        val current = listOf(run(5000.0, 1680))
        val gain = HealthIndex.runGain(current, previous)
        assertEquals(7.142857, gain, 0.00001)
        assertEquals(24, HealthIndex.sport(28.0, 150.0, gain))
        assertEquals(18, HealthIndex.sport(30.0, 150.0, 0.0))
        assertEquals(0.0, HealthIndex.runGain(listOf(run(1000.0, 200), run(999.0, 100), run(5000.0, 0)), previous), 0.0)
        assertEquals(0.0, HealthIndex.runGain(current, previous.map { it.copy(kind = Bridge.Kind.BIKE) }), 0.0)
    }

    @Test fun strengthComparesOnlyValidatedWorkingSetsWithTheSameExerciseAndUnit() {
        val bar = Exercise("bar", "Bar", metrics = listOf("w", "r"))
        val reps = Exercise("reps", "Reps", metrics = listOf("r"))
        val assisted = Exercise("assisted", "Assisted", metrics = listOf("aw", "r"))
        val db = ExerciseDb(ExerciseDbFile("qa", listOf(bar, reps, assisted), emptyList(), emptyMap()))
        val set = SetLog("a", weight = 50.0, reps = 10, done = true)
        val workout = Workout("w", "QA", 0, 1000, listOf(
            SessionExercise("e", bar.id, listOf(set, set.copy(weight = 900.0, done = false),
                set.copy(weight = 900.0, type = SetType.WARMUP), set.copy(weight = 900.0, reps = 13))),
            SessionExercise("r", reps.id, listOf(set)), SessionExercise("assisted", assisted.id, listOf(set))))
        val values = HealthIndex.strengthBests(listOf(workout), db)
        assertEquals(setOf("bar", "reps"), values.keys)
        assertEquals(Strength.e1rm(50.0, 10, null, bar.upperBody), values.getValue("bar"), 0.0)
        assertEquals(10.0, values.getValue("reps"), 0.0)
    }
}
