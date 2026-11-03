package app.superhealth.analysis

import app.superhealth.coach.Coach
import app.superhealth.health.data.Performance
import app.superhealth.health.data.Scores
import app.superhealth.health.data.TrackPoint
import app.superhealth.league.League
import app.superhealth.nutrition.domain.WeightTrendV2
import app.superhealth.training.domain.*
import app.superhealth.wellbeing.Form
import org.junit.Assert.*
import org.junit.Test

class UnifiedAlgorithmsTest {
    @Test fun unknownWearTimeDoesNotCountAsRest() {
        val flat = List(25) { 50.0 }
        assertEquals(50.0 to 50.0, Scores.load(flat + listOf(null, null, null)))
        assertTrue(Scores.load(flat + List(3) { 0.0 })!!.first < 30)
        assertNull(Scores.load(flat + List(7) { null }))
        assertEquals(Form.Verdict.NORMAL, Form.verdict(90, emptyList(), 1.0, false, false, allowPush = false))
    }

    @Test fun filteredWeightUsesOnlyThePrefix() {
        val series = (1L..42L).associateWith { if (it < 36L) 80.0 else 81.0 }
        val then = WeightTrendV2.compute(series.filterKeys { it <= 36L }, 36).last()
        val later = WeightTrendV2.compute(series, 42)[35]
        assertEquals(then.tween, later.filteredTween, 1e-12)
        assertTrue(kotlin.math.abs(later.tween - then.tween) > 0.05)
    }

    @Test fun runningRecordsIncludeZeroAndInterpolateEitherBoundary() {
        fun point(seconds: Int, metres: Int) = TrackPoint(seconds.toDouble(), metres.toDouble(), null, null, null, null, null, null)
        val trace = listOf(point(0, 0), point(100, 100), point(110, 200), point(113, 215))
        assertEquals(113.0, Performance.bestEffort(trace, 215.0)!!, 1e-9)
        assertEquals(10.0, Performance.bestEffort(trace, 100.0)!!, 1e-9)
        val endAnchored = listOf(point(0, 0), point(100, 100), point(110, 190))
        assertEquals(20.0, Performance.bestEffort(endAnchored, 100.0)!!, 1e-9)
        assertNull(Performance.bestEffort(listOf(point(0, 0), point(60, 60), point(61, 0), point(90, 42)), 100.0))
    }

    @Test fun localStrengthIgnoresOtherGymsAndIsolatedSpikes() {
        val ex = Exercise("p", "Press", metrics = listOf("w","r"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(ex), emptyList(), emptyMap()))
        val engine = SessionEngine(db)
        val gym = Gym("g", "Gym", equipmentIds = emptySet())
        val now = System.currentTimeMillis()
        fun workout(days: Int, kg: Double, gymId: String = gym.id) =
            Workout("w$days", "Workout", now - days * 86400000L, 3600, listOf(SessionExercise("e", ex.id,
                listOf(SetLog("s", weight = kg, reps = 8, rir = 2, done = true)))), gym.name, gymId = gymId)
        val normal = listOf(workout(9, 40.0), workout(6, 40.0), workout(3, 40.0))
        val history = normal + workout(2, 80.0) + workout(1, 140.0, "other")
        val adaptive = Settings(adaptiveStrength = true)
        val base = engine.openingStrength(ex, normal, adaptive, gym, now = now)!!
        assertEquals(base * 1.05, engine.openingStrength(ex, history, adaptive, gym, now = now)!!, 1e-8)
        assertEquals(Strength.e1rm(140.0, 8, 2, ex.upperBody), engine.openingStrength(ex, history, Settings(), gym)!!, 1e-8)
        val future = SetLog("future", weight = 25.0, reps = 11, target = TargetSet(), repsEdited = true, weightEdited = true)
        val session = engine.empty(gym).copy(exercises = listOf(SessionExercise("e", ex.id, listOf(
            SetLog("done", weight = 50.0, reps = 8, done = true), future))))
        val next = engine.afterSetCompleted(session, 0, adaptive, gym)
        assertEquals(25.0, next.exercises.single().sets.last().weight!!, 0.0)
        assertEquals(11, next.exercises.single().sets.last().reps)
        assertEquals(session, engine.afterSetCompleted(session, 0, adaptive.copy(smartProgression = false), gym))
    }

    @Test fun optionalSignalsRefineExistingSocialPillarsAndDoNotCreateCoverage() {
        val index = app.superhealth.league.HealthIndex
        assertEquals(90, index.blend(90, null, 0.25))
        assertNull(index.blend(null, 100, 0.25))
        assertEquals(85, index.blend(100, 40, 0.25))
        val entries = mapOf(index.historyKey(70, 7, index.CLASSIC) to 900, index.historyKey(70, 7) to 810)
        assertEquals(900.0, index.mean28(entries, 70, 7, index.CLASSIC)!!, 0.0)
        assertEquals(810.0, index.mean28(entries, 70, 7)!!, 0.0)
    }

    @Test fun changingScoreModelWorksOfflineWithPrivatePillars() {
        val friend = League.Friend("id", "key", "Test", "2026-W44", 0, 900,
            classicIndex = League.IndexCard(810, 7, emptyList()), unifiedIndex = League.IndexCard(900, 11, emptyList()))
        val classic = with(League) { friend.withIndex(4) }
        assertEquals(810, classic.score); assertEquals(7, classic.coverage); assertTrue(classic.pillars.isEmpty())
        val unified = with(League) { classic.withIndex(5) }
        assertEquals(900, unified.score); assertEquals(11, unified.coverage); assertTrue(unified.pillars.isEmpty())
    }

    @Test fun coachHidesOverallIfAPartIsPrivate() {
        val score = League.Score("2026-W44", 800, mapOf(League.Pillar.FOOD to 70, League.Pillar.SLEEP to 90, League.Pillar.STEPS to 75), true, 20000)
        assertEquals("", Coach.indexContext(score) { it != Coach.Share.INDEX })
        val partial = Coach.indexContext(score) { it != Coach.Share.FOOD }
        assertFalse(partial.contains("800/1000")); assertFalse(partial.contains("food:"))
        assertTrue(partial.contains("sleep: 90/100"))
        assertTrue(Coach.indexContext(score) { true }.contains("800/1000"))
    }
}
