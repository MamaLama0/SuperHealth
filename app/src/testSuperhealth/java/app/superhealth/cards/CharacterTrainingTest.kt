package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CharacterTrainingTest {
    private val day = 86_400_000L
    private val now = 22000 * day
    private val bench = Exercise("bench", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"))
    private val db = ExerciseDb(ExerciseDbFile("test", listOf(bench), emptyList(), emptyMap()))
    private fun workout(ago: Int, kg: Double = 80.0, sets: Int = 4, reps: Int = 8) = Workout("w$ago", "Gym", now - ago * day, 3600,
        listOf(SessionExercise("e", bench.id, List(sets) { SetLog("s$it", weight = kg, reps = reps, rir = 0, done = true) })))
    private fun compute(vararg w: Workout) = CharacterTrainingEngine.compute(AthleteStatsEngine.Input(w.toList(), db, 80.0, Sex.MALE, 30, now))

    @Test fun heavierPrMattersMoreThanRepeatingLightSetsAndOldPrSurvives() {
        val heavy = compute(workout(400, 150.0, 1, 1), workout(1, 40.0))
        val light = compute(workout(1, 40.0, 40))
        assertTrue(heavy.prScore!! > light.prScore!!)
        assertEquals(150.0, heavy.evidence!!.records.single().loadKg, 0.0)
        assertEquals(compute(workout(400, 150.0, 1, 1)).prScore!!, heavy.prScore!!, .000001)
    }
    @Test fun annualVolumeIncludesIdleWeeksAndExcludesOldAndFutureWorkouts() {
        val result = compute(workout(364, 100.0, 10, 10), workout(1, 100.0, 10, 10), workout(400, 900.0), workout(-1, 900.0))
        assertEquals(365, result.coveredDays)
        assertEquals(20000.0 / (365.0 / 7), result.evidence!!.weeklyKg, .001)
        assertEquals(20.0 / (365.0 / 7), result.evidence!!.weeklySets, .001)
        assertTrue(result.consistencyScore!! < .02)
    }
    @Test fun warmupsUndoneAndInvalidSetsNeverCreatePrOrVolume() {
        val w = workout(1)
        val excluded = listOf(SetLog("warm", type = SetType.WARMUP, weight = 900.0, reps = 2, done = true),
            SetLog("undone", weight = 900.0, reps = 2, done = false), SetLog("bad", weight = Double.NaN, reps = 8, done = true))
        val result = compute(w.copy(exercises = listOf(w.exercises.single().copy(sets = excluded))))
        assertNull(result.prScore); assertNull(result.volumeScore)
    }
    @Test fun shortHistoryIsExplicitAndOneWorkoutCannotFillAnnualStats() {
        val result = compute(workout(1))
        assertEquals(28, result.coveredDays); assertTrue(result.provisional)
        assertTrue(result.volumeScore!! < .1); assertTrue(result.consistencyScore!! < .1)
        assertEquals(result.volumeScore, compute(workout(1), workout(1)).volumeScore)
    }
    @Test fun rawEvidenceIsNeverSerializedButNormalizedScoresSurviveRestore() {
        val result = compute(workout(1))
        val json = Json { encodeDefaults = true }
        val raw = json.encodeToString(CharacterTraining.serializer(), result)
        assertFalse(raw.contains("Barbell")); assertFalse(raw.contains("weeklyKg")); assertFalse(raw.contains("estimatedMaxKg"))
        val restored = json.decodeFromString(CharacterTraining.serializer(), raw)
        assertEquals(result.prScore, restored.prScore); assertEquals(result.volumeScore, restored.volumeScore); assertNull(restored.evidence)
    }
}
