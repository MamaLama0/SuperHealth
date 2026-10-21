package app.superhealth.training

import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramEdit
import app.superhealth.training.domain.ProgramEdit.moved
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Supersets
import app.superhealth.training.domain.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class ProgramEditTest {
    private val ex = listOf(ProgramExercise("x", "e", listOf(emptyList())))
    private fun day(id: String, rest: Boolean = false) = ProgramDay(id, id, if (rest) emptyList() else ex)
    private fun program(vararg days: ProgramDay, cursor: Int = 0, done: List<String> = emptyList()) =
        Program("p", "P", days = days.toList(), dayIndex = cursor, doneDayIds = done)
    private fun Program.current() = days[dayIndex].id

    @Test fun movedKeepsOtherItemsAndIgnoresBadIndices() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        val l = listOf("a", "b")
        assertSame(l, l.moved(0, 5))
        assertSame(l, l.moved(1, 1))
    }

    @Test fun movingDaysKeepsTheNextWorkout() {
        val p = program(day("A"), day("B"), day("C"), cursor = 1)
        val moved = ProgramEdit.moveDay(p, 1, 0)
        assertEquals(listOf("B", "A", "C"), moved.days.map { it.id })
        assertEquals("B", moved.current())
        // déplacer un autre jour par-dessus le curseur
        assertEquals("B", ProgramEdit.moveDay(p, 2, 0).current())
    }

    @Test fun removingADayBeforeTheCursorShiftsIt() {
        val p = program(day("A"), day("B"), day("C"), cursor = 2, done = listOf("A", "B"))
        val r = ProgramEdit.removeDay(p, 0)
        assertEquals("C", r.current())
        assertEquals(listOf("B"), r.doneDayIds)
    }

    @Test fun removingTheCurrentDayGoesToTheNextOne() {
        val p = program(day("A"), day("B"), day("C"), cursor = 1)
        assertEquals("C", ProgramEdit.removeDay(p, 1).current())
        // dernier jour retiré : on revient au début
        assertEquals("A", ProgramEdit.removeDay(program(day("A"), day("B"), cursor = 1), 1).current())
        // jamais de programme vide
        val one = program(day("A"))
        assertSame(one, ProgramEdit.removeDay(one, 0))
    }

    @Test fun cursorNeverPointsToARestOrDoneDay() {
        val p = program(day("A"), day("R", rest = true), day("B"), cursor = 1)
        assertEquals("B", ProgramEdit.fixCursor(p).current())
        val done = program(day("A"), day("B"), day("C"), cursor = 0, done = listOf("A", "B"))
        assertEquals("C", ProgramEdit.fixCursor(done).current())
        // tous faits : premier jour d'entraînement
        val all = program(day("R", rest = true), day("A"), cursor = 0, done = listOf("A"))
        assertEquals("A", ProgramEdit.fixCursor(all).current())
        // jours faits disparus oubliés
        assertEquals(listOf("A"), ProgramEdit.fixCursor(program(day("A"), day("B"), done = listOf("A", "gone"))).doneDayIds)
    }

    @Test fun turningTheCurrentDayIntoRestMovesTheCursor() {
        val p = program(day("A"), day("B"), cursor = 0)
        val r = ProgramEdit.setDay(p, 0, day("A", rest = true))
        assertEquals("B", r.current())
        assertSame(p, ProgramEdit.setDay(p, 9, day("Z")))
    }

    @Test fun addingDaysDoesNotChangeTheNextWorkout() {
        val p = program(day("A"), day("B"), cursor = 1)
        assertEquals("B", ProgramEdit.addDay(p, day("R", rest = true)).current())
        assertEquals(3, ProgramEdit.addDay(p, day("C")).days.size)
    }

    @Test fun movingOutOfASupersetUngroupsIt() {
        // A1 A2 X : A2 envoyé en bas → plus de superset
        val g = listOf(1, 1, null).moved(1, 2)
        assertEquals(listOf(null, null, null), Supersets.normalize(g))
        // triset A1 A2 A3 X : X monté en tête, le groupe reste entier
        assertEquals(listOf(null, 1, 1, 1), Supersets.normalize(listOf(1, 1, 1, null).moved(3, 0)))
        // X glissé au milieu d'un triset : deux morceaux, le morceau isolé est défait
        assertEquals(listOf(1, 1, null, null), Supersets.normalize(listOf(1, 1, 1, null).moved(3, 2)))
    }
}

class TrainingTrendsTest {
    private val db by lazy { ExerciseDb.parse(File("src/superhealth/assets/db/exercises.json").readText()) }
    private val bench = "19f5c6f170d8808bb424e98de4472a7e"
    private val zone = ZoneId.systemDefault()
    private fun ms(d: LocalDate) = d.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun workout(d: LocalDate, sets: Int, weight: Double = 100.0, warmups: Int = 1) = Workout("w$d", "W", ms(d), 3600,
        listOf(SessionExercise("e", bench, List(warmups) { SetLog("w$it", SetType.WARMUP, 40.0, 10, done = true) } +
            List(sets) { SetLog("s$it", weight = weight, reps = 5, done = true) } + SetLog("undone", weight = weight, reps = 5))))

    @Test fun weeklyTotalsCountOnlyWorkingSets() {
        val today = LocalDate.of(2026, 10, 21) // mercredi
        val h = listOf(workout(today, 3), workout(today.minusDays(1), 2), workout(today.minusWeeks(2), 4))
        val w = Analytics.weeklyTotals(h, 4, today)
        assertEquals(4, w.size)
        assertEquals(LocalDate.of(2026, 10, 19), w.last().start)
        assertEquals(2, w.last().sessions)
        assertEquals(5, w.last().sets)
        assertEquals(5 * 5 * 100.0, w.last().volume, 1e-9)
        assertEquals(7200L, w.last().durationSec)
        assertEquals(0, w[2].sets)
        assertEquals(4, w[1].sets)
    }

    @Test fun topBySetsRanksAndRespectsWindow() {
        val now = LocalDate.of(2026, 10, 21)
        val h = listOf(workout(now, 3), workout(now.minusDays(40), 10))
        val top = Analytics.topBySets(h, ms(now.minusDays(28)), 3)
        assertEquals(listOf(bench to 3), top)
        assertTrue(Analytics.topBySets(emptyList(), 0, 3).isEmpty())
    }

    @Test fun weeklySetsByMuscleAveragesOverWeeks() {
        val now = LocalDate.of(2026, 10, 21)
        val h = listOf(workout(now, 4), workout(now.minusDays(8), 4))
        val sets = Analytics.weeklySetsByMuscle(h, db, 4, ms(now) + 1)
        assertEquals(2.0, sets[MuscleGroup.CHEST] ?: 0.0, 1e-9) // 8 séries / 4 semaines
    }
}
