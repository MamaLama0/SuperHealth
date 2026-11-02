package app.superhealth.training

import app.superhealth.training.data.StoreJson
import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SessionAndGenerationPreferencesTest {
    private val db by lazy { ExerciseDb.parse(File("src/superhealth/assets/db/exercises.json").readText()) }
    private fun Session.done() = copy(exercises = exercises.map { e -> e.copy(sets = e.sets.map { it.copy(done = true) }) })
    private fun input(preferences: Map<Generator.Template, Generator.TargetPreference> = emptyMap()) = Generator.Input(
        Goal.HYPERTROPHY, 3, Split.FULL_BODY, 70, Periodization.LINEAR, Deload.LAST, Experience.INTERMEDIATE,
        equipment = db.equipment.map { it.id }.toSet(), numCycles = 4, targetPreferences = preferences)

    @Test fun freeSessionStillAcceptsAnotherExerciseAfterAllCurrentSetsAreDoneAndAfterRestore() {
        val engine = SessionEngine(db)
        val ids = db.exercises.filter { it.usesWeight && it.usesReps }.take(2).map { it.id }
        val first = engine.addExercise(engine.empty(null), ids[0], emptyList(), Settings(), null).done()
        assertFalse(first.shouldSuggestFinish())
        val restored = StoreJson.decodeFromString(Session.serializer(), StoreJson.encodeToString(Session.serializer(), first))
        assertTrue(restored.freeForm)
        val next = engine.addExercise(restored, ids[1], emptyList(), Settings(), null)
        assertEquals(2, next.exercises.size)
        assertFalse(next.done().shouldSuggestFinish())
    }

    @Test fun plannedAndLibrarySessionsKeepTheFinishSuggestionButNotWhenEmpty() {
        val engine = SessionEngine(db)
        val e = ProgramExercise("e", db.exercises.first { it.usesReps }.id, listOf(listOf(TargetSet())))
        val day = ProgramDay("d", "D", listOf(e))
        val program = Program("p", "P", days = listOf(day))
        val planned = engine.fromProgram(program, day, 0, emptyList(), Settings(), null)
        assertFalse(planned.shouldSuggestFinish())
        assertTrue(planned.done().shouldSuggestFinish())
        val library = engine.fromCustom(CustomWorkout("w", "W", listOf(e)), emptyList(), Settings(), null)
        assertTrue(library.done().shouldSuggestFinish())
        assertFalse(library.copy(exercises = emptyList()).shouldSuggestFinish())
    }

    @Test fun legacyUnplannedSessionDefaultsToContinuingInsteadOfPrompting() {
        val legacy = """{"id":"s","name":"Libre","startTime":1,"exercises":[{"id":"e","exerciseId":"x","sets":[{"id":"set","done":true}]}]}"""
        assertFalse(StoreJson.decodeFromString(Session.serializer(), legacy).shouldSuggestFinish())
    }

    @Test fun targetPreferencesOnlyApplyToTheirFamilyAndCompatibleMetrics() {
        val preference = Generator.TargetPreference(7, 11, 4)
        val input = input(mapOf(Generator.Template.PRIMARY to preference))
        val weighted = Exercise("e", "Press", metrics = listOf("w", "r"))
        val picked = Generator.chosenTarget(input, weighted, Generator.Template.PRIMARY)
        assertEquals(7, picked.minReps); assertEquals(11, picked.maxReps); assertEquals(4, picked.rir)
        assertEquals(Generator.baseTarget(input.goal, Generator.Template.PRIMARY).restSec, picked.restSec)
        assertEquals(Generator.baseTarget(input.goal, Generator.Template.SECONDARY), Generator.chosenTarget(input, weighted, Generator.Template.SECONDARY))
        for (exercise in listOf(weighted.copy(metrics = listOf("r")), weighted.copy(metrics = listOf("w", "d")), weighted.copy(type = "core"))) {
            assertEquals(Generator.baseTarget(input.goal, Generator.Template.PRIMARY), Generator.chosenTarget(input, exercise, Generator.Template.PRIMARY))
        }
    }

    @Test fun customTargetsStayFixedDuringWorkingCyclesAndDeloadRetainsRecovery() {
        val input = input()
        val base = TargetSet(minReps = 7, maxReps = 11, rir = 4, restSec = 180)
        val cycles = Generator.cycles(input, base, 4, fixedTargets = true)
        cycles.take(3).forEach { sets -> assertEquals(List(4) { base }, sets) }
        assertEquals(List(2) { base.copy(rir = 6) }, cycles.last())
        val generated = Generator.generate(db, input(Generator.Template.entries.associateWith { Generator.TargetPreference(7, 11, 4) }))
        val affected = generated.days.flatMap { it.exercises }.filter { Generator.acceptsTargetPreference(db.exercise(it.exerciseId)!!) }
        assertTrue(affected.isNotEmpty())
        affected.forEach { e -> e.cycles.take(3).flatten().forEach { assertEquals(7, it.minReps); assertEquals(11, it.maxReps); assertEquals(4, it.rir) } }
    }

    @Test fun impossibleTargetsAreRejectedBeforeGeneration() {
        assertTrue(runCatching { Generator.TargetPreference(12, 8, 2) }.isFailure)
        assertTrue(runCatching { Generator.TargetPreference(0, 8, 2) }.isFailure)
        assertTrue(runCatching { Generator.TargetPreference(8, 12, 11) }.isFailure)
        assertEquals(10, Generator.TargetPreference(8, 12, 10).rir)
    }
}
