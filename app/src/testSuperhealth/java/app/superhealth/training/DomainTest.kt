package app.superhealth.training

import app.superhealth.training.domain.Deload
import app.superhealth.training.domain.Experience
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Goal
import app.superhealth.training.domain.LoadLadder
import app.superhealth.training.domain.Periodization
import app.superhealth.training.domain.Plates
import app.superhealth.training.domain.Progression
import app.superhealth.training.domain.SessionEngine
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Split
import app.superhealth.training.domain.Strength
import app.superhealth.training.domain.TargetSet
import app.superhealth.training.domain.Workout
import app.superhealth.training.domain.SessionExercise
import app.superhealth.training.domain.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainTest {
    private val db by lazy { ExerciseDb.parse(File("src/superhealth/assets/db/exercises.json").readText()) }
    private val bench = "19f5c6f170d8808bb424e98de4472a7e" // Barbell bench press

    @Test fun curveIsAnchoredAtOneRep() {
        assertEquals(1.0, Strength.pct(1.0, true), 1e-12)
        // 10 répétitions jusqu'à l'échec : 0,8·0,965⁹ + 0,2 = 0,780 (haut) ; 0,8·0,975⁹ + 0,2 = 0,837 (bas)
        assertEquals(0.7803, Strength.pct(10.0, true), 1e-3)
        assertEquals(0.8371, Strength.pct(10.0, false), 1e-3)
        val one = Strength.e1rm(80.0, 8, 2, true)
        assertEquals(10.0, Strength.rtfAt(80.0, one, true), 1e-9)
    }

    @Test fun fatigueModel() {
        assertEquals(0.0, Strength.fatigueDrop(0.0), 0.0)
        assertEquals(2.481 * Math.pow(10.0, 0.582) / 100, Strength.fatigueDrop(10.0), 1e-12)
        // sans repos, la conversion aller-retour conserve les répétitions fatigantes
        assertEquals(12.0, Strength.recover(12.0, 0.0), 1e-9)
        assertTrue(Strength.recover(12.0, 3.0) < 12.0 * 0.3)
    }

    @Test fun smartWarmUps() {
        // ratios de base < ratio de travail ; le premier tel quel, les suivants comblent 60 % de l'écart restant
        val r = Strength.smartWarmUpRatios(0.85)
        assertEquals(3, r.size)
        assertEquals(0.4, r[0], 1e-12); assertEquals(0.67, r[1], 1e-12); assertEquals(0.778, r[2], 1e-12)
        assertEquals(2, Strength.smartWarmUpRatios(0.8).size)
        assertEquals(10, Strength.warmUpReps(0.4))   // (1 − 32,8·ln 0,4)/3 = 10,35 → arrondi à 5
        assertEquals(3, Strength.warmUpReps(0.8))    // exemple du centre d'aide : 80 % × 3
        assertEquals(null, Strength.warmUpReps(0.97))
    }

    @Test fun plateCalculator() {
        assertEquals(listOf(25.0, 15.0), Plates.perSide(100.0, 20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)))
        assertEquals(null, Plates.perSide(101.0, 20.0, listOf(25.0, 20.0, 10.0, 5.0)))
        val ladder = LoadLadder.barbell(20.0, listOf(20.0, 10.0, 5.0, 2.5, 1.25))
        assertTrue(102.5 in ladder.loads)
    }

    @Test fun progressionStaysInRange() {
        val ladder = LoadLadder.barbell(20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
        val t = List(3) { TargetSet(minReps = 8, maxReps = 12, rir = 2, restSec = 150) }
        val one = Strength.e1rm(80.0, 10, 2, true)
        val s = Progression.suggest(one, t, true, ladder, 80.0, weightMatch = true, expandRange = true)
        assertEquals(3, s.size)
        assertTrue(s.all { it.reps in 6..14 })
        assertTrue(s[0].weight >= 80.0)                  // +1 % : au moins la charge précédente
        assertTrue(s.zipWithNext().all { (a, b) -> b.reps <= a.reps || b.weight < a.weight }) // la fatigue réduit la suite
    }

    @Test fun databaseLoads() {
        assertTrue(db.exercises.size > 1300)
        assertEquals("Barbell bench press", db.exercise(bench)?.name)
        assertTrue(db.search("bench press").first().name.contains("bench", true))
        val home = db.equipment.filter { "homeGym" in it.gyms }.map { it.id }.toSet()
        assertTrue(db.exercises.count { db.isAvailable(it, home) } > 100)
    }

    @Test fun generatorBuildsPrograms() {
        val all = db.equipment.map { it.id }.toSet()
        for (split in listOf(Split.FULL_BODY, Split.UPPER_LOWER_X2, Split.PUSH_PULL_LEGS, Split.PPHS_UPPER_LOWER)) {
            val p = Generator.generate(db, Generator.Input(Goal.HYPERTROPHY, split.days.size, split, 60, Periodization.LINEAR,
                Deload.LAST, Experience.INTERMEDIATE, equipment = all))
            val training = p.days.filter { !it.isRest }
            assertEquals(split.days.size, training.size)
            assertTrue("${split}", training.all { it.exercises.size in 3..8 })
            assertTrue(training.all { d -> d.exercises.all { pe -> pe.cycles.size == 6 } })
            assertTrue(Generator.estimateMinutes(training[0], 0) <= 80)
        }
        // salle commerciale : les exercices principaux sont les grands classiques (niveau 1)
        val commercial = db.equipment.filter { "commercialGym" in it.gyms }.map { it.id }.toSet()
        val fb = Generator.generate(db, Generator.Input(Goal.STRENGTH, 3, Split.FULL_BODY, 60, Periodization.LINEAR,
            Deload.LAST, Experience.INTERMEDIATE, equipment = commercial))
        val names = fb.days.filter { !it.isRest }.flatMap { d -> d.exercises.take(2).map { db.exercise(it.exerciseId)!!.name } }
        println("PRIMARY " + names)
        assertTrue(names.any { it.contains("Barbell", true) })
        assertEquals(3, fb.days.count { !it.isRest })
        assertTrue(names.toSet().size >= 3)  // variantes A/B/C différentes
        val home = db.equipment.filter { "homeGym" in it.gyms }.map { it.id }.toSet()
        val p = Generator.generate(db, Generator.Input(Goal.STRENGTH, 3, Split.FULL_BODY, 45, Periodization.STATIC,
            Deload.NONE, Experience.BEGINNER, equipment = home))
        assertTrue(p.days.filter { !it.isRest }.all { d -> d.exercises.all { db.isAvailable(db.exercise(it.exerciseId)!!, home) } })
    }

    @Test fun sessionFromHistoryAddsWarmUpsAndProgress() {
        val engine = SessionEngine(db)
        val all = db.equipment.map { it.id }.toSet()
        val program = Generator.generate(db, Generator.Input(Goal.HYPERTROPHY, 3, Split.FULL_BODY, 60, Periodization.STATIC,
            Deload.NONE, Experience.INTERMEDIATE, equipment = all))
        val day = program.days.first { !it.isRest }
        val first = day.exercises.first()
        val past = Workout("w", "x", 0, 3600, listOf(SessionExercise("e", first.exerciseId,
            List(3) { SetLog("s$it", weight = 80.0, reps = 10, rir = 2, done = true) })))
        val s = engine.fromProgram(program, day, 0, listOf(past), Settings(), null)
        val ex = s.exercises.first()
        assertTrue(ex.sets.any { it.type == app.superhealth.training.domain.SetType.WARMUP })
        assertNotNull(ex.sets.last().autoWeight)
        val next = engine.advance(program, day.id)
        assertTrue(next.dayIndex > program.dayIndex || next.cycleIndex > 0)
    }
}
