package app.superhealth.training

import app.superhealth.training.domain.PlanDiff
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.Session
import app.superhealth.training.domain.SessionEngine
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetLog
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.TargetSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDiffTest {
    private fun pe(id: String, ex: String, sets: Int = 3) = ProgramExercise(id, ex, listOf(List(sets) { TargetSet() }, List(sets) { TargetSet(minReps = 6) }))
    private fun se(id: String, ex: String, sets: Int = 3, warm: Int = 0) =
        SessionExercise(id, ex, List(warm) { SetLog("w$it", SetType.WARMUP) } + List(sets) { SetLog("s$it", target = TargetSet(), reps = 10) })
    private val day = ProgramDay("d", "Push", listOf(pe("pa", "A"), pe("pb", "B"), pe("pc", "C")))
    private fun session(vararg ex: SessionExercise) = Session("s", "Push", 0, ex.toList(), programId = "p", cycleIndex = 0, dayId = "d")

    @Test fun unchangedSessionHasNoDiff() {
        assertTrue(PlanDiff.compute(day, session(se("1", "A", warm = 2), se("2", "B"), se("3", "C"))).isEmpty)
    }

    @Test fun detectsAndAppliesAllChanges() {
        // B retiré, D ajouté, C passe à 4 séries, ordre C avant A
        val s = session(se("3", "C", sets = 4), se("1", "A"), se("4", "D", sets = 2))
        val d = PlanDiff.compute(day, s)
        assertEquals(listOf("D"), d.added.map { it.exerciseId })
        assertEquals(listOf("pb"), d.removed.map { it.id })
        assertEquals(listOf("pc"), d.modified.map { it.first.id })
        assertTrue(d.reordered)
        val out = PlanDiff.apply(day, s, d, d.all())
        assertEquals(listOf("C", "A", "D"), out.exercises.map { it.exerciseId })
        // les deux cycles passent à 4 séries, la plage du cycle 2 est conservée
        assertEquals(listOf(4, 4), out.exercises[0].cycles.map { it.size })
        assertEquals(6, out.exercises[0].cycles[1][3].minReps)
        assertEquals(2, out.exercises[2].cycles.single().size)
    }

    @Test fun undoneChangesAreKept() {
        val s = session(se("3", "C", sets = 4), se("1", "A"), se("4", "D"))
        val d = PlanDiff.compute(day, s)
        // on garde B, on refuse l'ajout et l'ordre ; seule la modification de C passe
        val out = PlanDiff.apply(day, s, d, PlanDiff.Selection(emptySet(), emptySet(), setOf("pc"), false))
        assertEquals(listOf("A", "B", "C"), out.exercises.map { it.exerciseId })
        assertEquals(4, out.exercises[2].cycles[0].size)
        // ordre accepté mais B conservé : B reprend sa place d'origine
        val out2 = PlanDiff.apply(day, s, d, PlanDiff.Selection(emptySet(), emptySet(), emptySet(), true))
        assertEquals(listOf("C", "B", "A"), out2.exercises.map { it.exerciseId })
        assertEquals(3, out2.exercises[0].cycles[0].size)
    }

    @Test fun logAllSetsKeepsFilledUndoneSets() {
        val eng = SessionEngine(app.superhealth.training.domain.ExerciseDb.parse(java.io.File("src/superhealth/assets/db/exercises.json").readText()))
        val ex = SessionExercise("1", "A", listOf(SetLog("a", reps = 8, done = true), SetLog("b", reps = 8), SetLog("c")))
        val s = session(ex)
        assertEquals(1, eng.finish(s, 1000, null, null).exercises.single().sets.size)
        val all = eng.finish(s, 1000, null, null, logAll = true).exercises.single().sets
        assertEquals(2, all.size); assertFalse(all.any { !it.done })
    }
}
