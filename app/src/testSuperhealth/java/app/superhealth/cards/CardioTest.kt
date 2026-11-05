package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.*
import org.junit.Assert.*
import org.junit.Test

class CardioTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val db = ExerciseDb(ExerciseDbFile("test", emptyList(), emptyList(), emptyMap()))
    private fun car(vo2: Double? = null, runs: List<AthleteStatsEngine.Run> = emptyList(), acts: List<AthleteStatsEngine.Endurance>? = null,
                    steps: List<Pair<Long, Double>>? = null, at: Long = now) =
        AthleteStatsEngine.compute(AthleteStatsEngine.Input(emptyList(), db, 75.0, Sex.MALE, 30, at, vo2max = vo2, runs = runs, endurance = acts, steps = steps,
            heightCm = 178.0))[Stat.CAR]

    /** Un coureur régulier : 3 sorties de 8 km à 5:30/km par semaine depuis 8 semaines. */
    private val runner = (0 until 24).map { i -> now - (i * 56 / 24) * day - day }
    private val runs = runner.map { AthleteStatsEngine.Run(it, 8000.0, 8 * 330.0) }
    private val acts = runner.map { AthleteStatsEngine.Endurance(it, 44.0, 8.0) }

    @Test fun normsMatchTheCharts() {
        assertEquals(75.0, CardioNorms.steps(10000.0), 0.01)
        assertEquals(50.0, CardioNorms.steps(5000.0), 0.01)
        assertTrue(CardioNorms.pace(330.0, Sex.MALE, 30) > CardioNorms.pace(420.0, Sex.MALE, 30))
        assertTrue("même allure, femme mieux notée", CardioNorms.pace(390.0, Sex.FEMALE, 30) > CardioNorms.pace(390.0, Sex.MALE, 30))
        // 5 km en 25 min ≈ VDOT 38-40 (tables de Daniels).
        val vdot = CardioNorms.vdot(listOf(AthleteStatsEngine.Run(0, 5000.0, 1500.0)))!!
        assertTrue("VDOT $vdot", vdot in 37.0..41.0)
        assertTrue(CardioNorms.uth(30, 50.0) in 55.0..60.0)
    }

    @Test fun everyCriterionCountsAndStepsToo() {
        val regular = car(runs = runs, acts = acts)!!
        assertTrue("coureur régulier $regular", regular in 62..85)
        assertTrue("une VO₂max élevée monte la note", car(vo2 = 58.0, runs = runs, acts = acts)!! > regular)
        val walker = (1..56).map { now - it * day to 12000.0 }
        val sedentary = (1..56).map { now - it * day to 3000.0 }
        assertTrue("les pas comptent", car(steps = walker)!! > car(steps = sedentary)!! + 15)
        assertNull("rien de mesuré : pas de note", car())
    }

    @Test fun oldActivityFadesAndStatsDrop() {
        val fresh = car(runs = runs, acts = acts)!!
        // Mêmes sorties, mais vieilles d'un mois de plus : pondérées moins, et un mois sans rien.
        val later = car(runs = runs, acts = acts, at = now + 30 * day)!!
        assertTrue("la note baisse sans activité récente : $fresh → $later", later < fresh - 5)
        // Des pas récents pèsent plus que des pas anciens.
        val rising = (1..56).map { now - it * day to if (it <= 28) 12000.0 else 4000.0 }
        val falling = (1..56).map { now - it * day to if (it <= 28) 4000.0 else 12000.0 }
        assertTrue(car(steps = rising)!! > car(steps = falling)!!)
    }

    @Test fun oldRecordsStillCountButLess() {
        // Un 10 km rapide il y a 5 mois : il compte encore, moins que s'il datait d'hier.
        val old = AthleteStatsEngine.Run(now - 150 * day, 10000.0, 10 * 270.0)
        val withOld = car(runs = runs + old, acts = acts)!!
        val without = car(runs = runs, acts = acts)!!
        val fresh = car(runs = runs + old.copy(start = now - day), acts = acts)!!
        assertTrue("ancien record : $without < $withOld < $fresh", withOld > without && fresh > withOld)
    }

    @Test fun oldStrengthRecordsBlendWithRecentTraining() {
        val bench = Exercise("b", "Barbell bench press", metrics = listOf("w", "r"), region = "upper", primaryGroups = listOf("chest"), secondaryGroups = listOf("triceps", "frontDelts"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(bench), emptyList(), emptyMap()))
        fun w(daysAgo: Int, kg: Double) = Workout("w$daysAgo", "S", now - daysAgo * day, 3600,
            listOf(SessionExercise("e", bench.id, List(4) { SetLog("s$it", weight = kg, reps = 5, rir = 2, done = true) })))
        val recent = (1..40 step 3).map { w(it, 70.0) }
        fun pec(history: List<Workout>) = AthleteStatsEngine.compute(AthleteStatsEngine.Input(history, db, 80.0, Sex.MALE, 30, now, weeklyTarget = 2))[Stat.PEC]
        val base = pec(recent)!!
        val withPr = pec(recent + w(200, 100.0))!!
        assertTrue("un ancien record relève la note : $base → $withPr", withPr > base)
        val onlyOld = pec(listOf(w(200, 100.0)))
        assertNotNull("même sans séance récente, l'ancien record compte", onlyOld)
        assertTrue("mais pas comme s'il était récent", onlyOld!! < pec((1..40 step 3).map { w(it, 100.0) })!!)
    }
}
