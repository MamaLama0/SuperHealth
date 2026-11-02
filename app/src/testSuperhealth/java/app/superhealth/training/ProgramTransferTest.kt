package app.superhealth.training

import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test

class ProgramTransferTest {
    @Test fun importingAProgramCreatesIndependentIdsAndKeepsExerciseAssociations() {
        val exercise = ProgramExercise("ex", "bench", listOf(listOf(TargetSet())))
        val p = Program("old", "Test", days = listOf(ProgramDay("day", "Jour", listOf(exercise))),
            cycleIndex = 3, doneDayIds = listOf("day"), gymId = "old-gym")
        val next = ProgramTransfer.read(ProgramTransfer.export(p), setOf("bench"))
        assertNotEquals(p.id, next.id)
        assertNotEquals(p.days[0].id, next.days[0].id)
        assertNotEquals(p.days[0].exercises[0].id, next.days[0].exercises[0].id)
        assertEquals("bench", next.days[0].exercises[0].exerciseId)
        assertEquals(p.days[0].exercises[0].cycles, next.days[0].exercises[0].cycles)
        assertEquals(0, next.cycleIndex)
        assertTrue(next.doneDayIds.isEmpty())
        assertNull(next.gymId)
    }

    private val workouts = """{"name":"Imported","numCycles":2,"isPeriodized":true,"days":[{"name":"A","blocks":[{"exercises":[
        {"exerciseId":"bench","periodizedTargets":{"values":[{"sets":[{"setType":"standard","log":{"minFullReps":5,"maxFullReps":8,"rir":2,"restTimer":90000000}}]},{"sets":[{"setType":"standard","log":{"minFullReps":8,"maxFullReps":12,"rir":1,"restTimer":60000000}}]}]}}
    ]}]}]}"""

    @Test fun workouts142TargetsRetainCyclesRepsRirAndRest() {
        val p = ProgramTransfer.read(workouts, setOf("bench"))
        assertEquals(2, p.numCycles)
        val cycles = p.days.single().exercises.single().cycles
        assertEquals(TargetSet(minReps = 5, maxReps = 8, rir = 2, restSec = 90), cycles[0].single())
        assertEquals(TargetSet(minReps = 8, maxReps = 12, rir = 1, restSec = 60), cycles[1].single())
    }

    @Test fun unknownExerciseAndUnsupportedDurationAreRejectedBeforeImport() {
        assertTrue(runCatching { ProgramTransfer.read(workouts, emptySet()) }.isFailure)
        assertTrue(runCatching { ProgramTransfer.read(workouts.replace("\"rir\":2", "\"duration\":60,\"rir\":2"), setOf("bench")) }.isFailure)
    }

    @Test fun unreasonableCyclesAreRejected() {
        assertTrue(runCatching { ProgramTransfer.read(workouts.replace("\"numCycles\":2", "\"numCycles\":50000"), setOf("bench")) }.isFailure)
    }
    @Test fun bundledWorkouts142ProgramKeepsAllScheduledAndSkippedTargets() {
        val text = java.io.File("src/superhealth/assets/programs/arms-30-days.json").readText()
        val ids = Regex(""""exerciseId"\s*:\s*"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
        val p = ProgramTransfer.read(text, ids)
        assertEquals(5, p.numCycles)
        assertEquals(Deload.NONE, p.deload)
        assertEquals(7, p.days.size)
        assertEquals(33, p.days.sumOf { it.exercises.size })
        assertEquals(24, p.days.sumOf { it.exercises.sumOf { e -> e.skippedCycles.size } })
        p.days.flatMap { it.exercises }.forEach { e ->
            e.skippedCycles.forEach { cycle -> assertTrue(Generator.targetsFor(e, cycle).isEmpty()) }
        }
        val roundtrip = ProgramTransfer.read(ProgramTransfer.export(p), ids)
        assertEquals(p.days.map { it.exercises.map { e -> e.skippedCycles } }, roundtrip.days.map { it.exercises.map { e -> e.skippedCycles } })
    }

}
