package app.superhealth.training

import app.superhealth.training.domain.*
import kotlinx.serialization.json.Json
import kotlin.test.*


class SessionWarmUpsTest {
    private val exercise = Exercise("press", "Press", metrics = listOf("w", "r"))
    private val db = ExerciseDb(ExerciseDbFile("test", listOf(exercise), emptyList(), emptyMap()))
    private fun warm(id: String = "warm", done: Boolean = false) = SetLog(id, SetType.WARMUP, weight = 20.0, reps = 8, done = done)
    private fun work(id: String = "work", done: Boolean = false) = SetLog(id, weight = 80.0, reps = 8, done = done)
    private fun block(id: String, sets: List<SetLog>) = SessionExercise(id, exercise.id, sets, supersetGroup = 0)
    private fun session(vararg blocks: SessionExercise) = Session("s", "S", 0, blocks.toList())
    private val history get() = listOf(Workout("old", "Old", 1, 600, listOf(block("previous", listOf(work(done = true))))))

    @Test fun everyWarmUpStaysOnItsExerciseIncludingTheLastWarmUp() {
        val s = session(block("a", listOf(warm("w1", true), warm("w2", true), work())), block("b", listOf(work())))
        assertNull(SessionFlow.nextFocus(s, 0, 0)); assertNull(SessionFlow.nextFocus(s, 0, 1))
    }
    @Test fun workingSetsStillAlternateAndSkipFinishedPartners() {
        val s = session(block("a", listOf(work(done = true), work("next"))), block("b", listOf(work())))
        assertEquals(1, SessionFlow.nextFocus(s, 0, 0))
        assertNull(SessionFlow.nextFocus(s.copy(exercises = listOf(s.exercises[0], block("b", listOf(work(done = true))))), 0, 0))
    }
    @Test fun disablingKeepsCompletedWarmUpsAndAllWorkingSets() {
        val s = session(block("a", listOf(warm(done = true), warm("pending"), work())), SessionExercise("b", "other", listOf(warm(), work())))
        val changed = SessionWarmUps.withoutPending(s, exercise.id)
        assertEquals(listOf(warm(done = true), work()), changed.exercises[0].sets)
        assertEquals(s.exercises[1], changed.exercises[1])
        assertTrue(SessionWarmUps.withoutPending(s).exercises.all { it.sets.none { set -> set.type == SetType.WARMUP && !set.done } })
    }
    @Test fun globalOffWinsOverAnExerciseOverrideAndExerciseOffWinsOverGlobalOn() {
        assertEquals(WarmUpMode.NONE, SessionWarmUps.mode(Settings(warmUp = WarmUpMode.NONE), ExerciseConfig(warmUp = WarmUpMode.SCHEME)))
        assertEquals(WarmUpMode.NONE, SessionWarmUps.mode(Settings(), ExerciseConfig(warmUp = WarmUpMode.NONE)))
        assertEquals(WarmUpMode.SMART, SessionWarmUps.mode(Settings(), null))
    }
    @Test fun pendingBlockIsReplacedWithoutAccumulation() {
        val ex = block("a", listOf(warm(), work()))
        val warm = listOf(warm("new1"), warm("new2"))
        val replaced = SessionWarmUps.replace(ex, warm)
        assertEquals(3, replaced.sets.size)
        assertEquals(replaced, SessionWarmUps.replace(replaced, warm))
    }
    @Test fun manualRegenerationIsRefusedOnceAnySetHasBeenCompleted() {
        for (sets in listOf(listOf(warm(done = true), work()), listOf(work(done = true), work("next")))) {
            val ex = block("a", sets)
            assertFalse(SessionWarmUps.canAdd(ex))
            assertEquals(ex, SessionWarmUps.replace(ex, List(6) { warm("new$it") }))
        }
    }
    @Test fun persistentExerciseSettingActuallyAffectsNewAndAddedExercises() {
        val enabled = SessionEngine(db).buildExercise(exercise.id, listOf(TargetSet()), history, Settings(), null, emptySet())
        assertTrue(enabled.sets.any { it.type == SetType.WARMUP })
        val engine = SessionEngine(db, mapOf(exercise.id to ExerciseConfig(warmUp = WarmUpMode.NONE)))
        val built = engine.buildExercise(exercise.id, listOf(TargetSet()), history, Settings(), null, emptySet())
        assertTrue(built.sets.none { it.type == SetType.WARMUP })
        assertTrue(engine.addExercise(engine.empty(null), exercise.id, history, Settings(), null).exercises.single().sets.none { it.type == SetType.WARMUP })
    }
    @Test fun explicitProgrammedWarmUpsAreNotDoubledAndGlobalOffOmitsThem() {
        val targets = listOf(TargetSet(type = SetType.WARMUP), TargetSet())
        val engine = SessionEngine(db)
        val on = engine.buildExercise(exercise.id, targets, history, Settings(), null, emptySet())
        assertEquals(1, on.sets.count { it.type == SetType.WARMUP })
        assertTrue(engine.buildExercise(exercise.id, targets, history, Settings(warmUp = WarmUpMode.NONE), null, emptySet()).sets.none { it.type == SetType.WARMUP })
    }
    @Test fun recalculatingLoadsNeverInsertsWarmUpsDuringTheExercise() {
        val engine = SessionEngine(db)
        var s = session(block("a", listOf(work(done = true), work("next").copy(target = TargetSet()))))
        val ids = s.exercises.single().sets.map { it.id }
        repeat(6) { s = engine.afterSetCompleted(s, 0, Settings(), null) }
        assertEquals(ids, s.exercises.single().sets.map { it.id })
        assertTrue(s.exercises.single().sets.none { it.type == SetType.WARMUP })
    }
    @Test fun librarySupersetIsPreservedAndDisabledChoiceSurvivesSerialization() {
        val pe = ProgramExercise("p", exercise.id, listOf(listOf(TargetSet())), supersetGroup = 0)
        val s = SessionEngine(db).fromCustom(CustomWorkout("c", "C", listOf(pe, pe.copy(id = "p2"))), history, Settings(), null)
        assertEquals(listOf(0, 0), s.exercises.map { it.supersetGroup })
        val config = ExerciseConfig(warmUp = WarmUpMode.NONE)
        assertEquals(config, Json.decodeFromString(ExerciseConfig.serializer(), Json.encodeToString(ExerciseConfig.serializer(), config)))
    }
}
