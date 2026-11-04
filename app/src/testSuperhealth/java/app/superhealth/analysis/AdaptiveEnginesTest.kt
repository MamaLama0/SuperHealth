package app.superhealth.analysis

import app.superhealth.adaptive.Gate
import app.superhealth.adaptive.LevelTrend
import app.superhealth.league.DailyScore
import app.superhealth.league.Progression
import app.superhealth.league.WeeklyQuests
import app.superhealth.training.domain.*
import app.superhealth.wellbeing.Attention
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Moteurs unifiés de la 3.5 : filtre commun, porte de confiance, force, cibles, score quotidien, quêtes et attention. */
class AdaptiveEnginesTest {
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private val press = Exercise("p", "Press", region = "upper", metrics = listOf("w", "r"))
    private val db = ExerciseDb(ExerciseDbFile("test", listOf(press), emptyList(), emptyMap()))
    private val gym = Gym("g", "Gym", equipmentIds = emptySet())

    private fun set(kg: Double, reps: Int, rir: Int? = 2, partials: Int = 0, target: TargetSet? = TargetSet(minReps = 6, maxReps = 10, rir = 2)) =
        SetLog("s$kg$reps$partials", weight = kg, reps = reps, rir = rir, partialReps = partials, done = true, target = target)

    private fun workout(daysAgo: Int, sets: List<SetLog>, gymId: String = gym.id) = Workout("w$daysAgo$gymId", "W", now - daysAgo * day, 3600,
        listOf(SessionExercise("e$daysAgo", press.id, sets, restSec = 180)), gym.name, gymId = gymId)

    // ----- Socle -----

    @Test fun levelTrendLearnsASteadySlopeAndResistsAnOutlier() {
        val f = LevelTrend(qLevel = 1e-5, qSlope = 1e-8, slopeSd0 = 0.01)
        var s = f.start(0.0, ln(100.0), 0.03 * 0.03)
        for (d in 1..20) s = f.update(s, d * 3.0, ln(100.0) + 0.002 * d * 3, 0.03 * 0.03)
        assertEquals(0.002, s.slope, 0.0006)
        val before = s.level
        val after = f.update(s, 63.0, ln(150.0), 0.03 * 0.03)
        // une séance à +40 % n'emporte pas l'estimation (Huber)
        assertTrue(after.level - f.predict(s, 63.0).level < 0.05)
        assertTrue(before.isFinite())
    }

    @Test fun gateNeedsTwoWinningBlocksAndFallsBackAfterTwoLosses() {
        val gate = Gate(blockSize = 4)
        val win = List(4) { 1.0 to 0.5 }; val lose = List(4) { 1.0 to 2.0 }
        assertEquals(0.0, gate.evaluate(win).weight, 0.0)
        assertEquals(0.5, gate.evaluate(win + win).weight, 1e-9)
        assertEquals(0.75, gate.evaluate(win + win + win).weight, 1e-9)
        assertEquals(0.0, gate.evaluate(win + win + lose + lose).weight, 0.0)
        assertEquals(1.5, Gate.blend(1.0, 2.0, 0.5), 1e-9)
    }

    // ----- Force -----

    @Test fun partialsAnchorTheRirBiasOfTheOtherSets() {
        // Première série à l'échec (partielles), puis des séries notées RIR 2 qui sont en réalité à environ RIR 1.
        val oneRm = 100.0
        val r1 = Strength.rtfAt(80.0, oneRm, true).toInt()
        val drop = Strength.fatigueDrop(Strength.recover(Strength.fatiguingReps(r1, 2), 3.0))
        val rtf2 = Strength.rtfAt(72.0, oneRm * (1 - drop), true)
        val r2 = rtf2.toInt() // RIR réel entre 0 et 1
        val history = (1..5).map { k -> workout(20 - k * 3, listOf(set(80.0, r1, rir = 0, partials = 2), set(72.0, r2, rir = 2))) }
        val bias = StrengthModel.rirBias(history, db)
        assertTrue("biais $bias", bias in 0.7..2.0)
        assertEquals(0.0, StrengthModel.rirBias(history.take(2), db), 0.0)
        // sans série à l'échec, aucun biais n'est inventé
        assertEquals(0.0, StrengthModel.rirBias(history.map { w -> w.copy(exercises = w.exercises.map { e -> e.copy(sets = e.sets.drop(1)) }) }, db), 0.0)
    }

    @Test fun strengthEstimateIsRobustToOneBadSessionAndStaysInItsGym() {
        val steady = (1..8).map { workout(30 - it * 3, listOf(set(80.0, 8), set(80.0, 7), set(80.0, 6))) }
        val bad = workout(2, listOf(set(50.0, 5, rir = 4)))
        val elsewhere = workout(1, listOf(set(140.0, 8)), gymId = "other")
        val base = StrengthModel.estimate(press, steady, gym, Experience.INTERMEDIATE, 0.0, trust = 1.0, now = now)!!
        val withBad = StrengthModel.estimate(press, steady + bad + elsewhere, gym, Experience.INTERMEDIATE, 0.0, trust = 1.0, now = now)!!
        assertTrue("${withBad.oneRm} vs ${base.oneRm}", withBad.oneRm > base.oneRm * 0.93)
        assertEquals(9, withBad.sessions)
        // sans confiance, règle d'origine : dernière séance + 1 %
        val origin = StrengthModel.estimate(press, steady, gym, Experience.INTERMEDIATE, 0.0, trust = 0.0, now = now)!!
        val last = StrengthModel.measure(steady.maxBy { it.startTime }.exercises, true, 0.0, 0)!!
        assertEquals(exp(last.z) * Strength.SESSION_PROGRESS, origin.oneRm, 1e-6)
    }

    @Test fun progressionIsLearntInsteadOfAFixedOnePercent() {
        val rising = (0 until 12).map { k -> workout(48 - k * 4, listOf(set(70.0 + k * 1.5, 8), set(70.0 + k * 1.5, 7))) }
        val flat = (0 until 12).map { k -> workout(48 - k * 4, listOf(set(80.0, 8), set(80.0, 7))) }
        val up = StrengthModel.trend(press, rising, Experience.INTERMEDIATE, 0.0, now)!!
        val still = StrengthModel.trend(press, flat, Experience.INTERMEDIATE, 0.0, now)!!
        assertTrue("pente ${up.first}", up.first > 0.008)
        assertTrue("plat ${still.first}", abs(still.first) < 0.003)
        assertTrue(DailyScore.progress(up.first, up.second) > DailyScore.progress(still.first, still.second))
        assertNull(StrengthModel.trend(press, flat.take(3), Experience.INTERMEDIATE, 0.0, now))
    }

    @Test fun inSessionStrengthBlendsOpeningEstimateWithDoneSets() {
        val engine = SessionEngine(db)
        val history = (1..6).map { workout(20 - it * 3, listOf(set(80.0, 8), set(80.0, 7))) }
        val opening = engine.openingStrength(press, history, Settings(), gym, now = now)!!
        val sloppy = engine.inSessionStrength(press, listOf(set(60.0, 5, rir = 4)), 180, history, Settings(), gym)!!
        val alone = exp(StrengthModel.measure(listOf(SessionExercise("x", press.id, listOf(set(60.0, 5, rir = 4)), restSec = 180)), true, 0.0, 0)!!.z)
        // une série approximative ne remplace pas tout l'historique
        assertTrue(abs(sloppy - opening) < abs(alone - opening))
    }

    // ----- Cibles -----

    @Test fun targetTipsFollowPracticedRirRepsAndPartials() {
        val target = TargetSet(minReps = 8, maxReps = 12, rir = 3)
        val program = Program("pr", "P", days = listOf(ProgramDay("d", "D", listOf(ProgramExercise("pe", press.id, listOf(listOf(target, target, target)))))))
        val history = (1..5).map { k -> workout(k * 3, listOf(set(60.0, 9, rir = 1, target = target), set(60.0, 9, rir = 1, target = target), set(60.0, 8, rir = 0, partials = 2, target = target))) }
        val tips = TargetTuning.tips(program, history)
        val rir = tips.first { it.kind == TargetTuning.Kind.RIR }
        assertEquals(3, rir.fromRir); assertEquals(1, rir.toRir)
        assertTrue(tips.any { it.kind == TargetTuning.Kind.LAST_SET_FAILURE })
        val applied = TargetTuning.apply(program, rir)
        assertEquals(listOf(1, 1, 1), applied.days[0].exercises[0].cycles[0].map { it.rir })
        assertTrue(TargetTuning.tips(applied, history).none { it.kind == TargetTuning.Kind.RIR })
        assertTrue(TargetTuning.tips(program, history, setOf(rir.key)).none { it.key == rir.key })
        assertTrue(TargetTuning.tips(program, history.take(3)).isEmpty())

        val high = (1..5).map { k -> workout(k * 3, List(3) { set(50.0, 15, rir = 3, target = target) }) }
        val reps = TargetTuning.tips(program, high).first { it.kind == TargetTuning.Kind.REPS }
        assertEquals(13, reps.toMin); assertEquals(17, reps.toMax)
    }

    // ----- Score quotidien -----

    @Test fun xpValidationAndIndexShareOneGreenZone() {
        val range = Progression.nutritionRange()
        assertTrue(Progression.nutritionQualified(2000.0, 2000.0, range))
        assertEquals(1.0, DailyScore.adherence(1700.0, 2000.0, DailyScore.food(range))!!, 1e-9)
        assertFalse(Progression.nutritionQualified(1500.0, 2000.0, range))
        assertEquals(0.9, DailyScore.adherence(1500.0, 2000.0, DailyScore.food(range))!!, 1e-9)
        assertEquals(0.6, DailyScore.adherence(6000.0, 10000.0, DailyScore.STEPS)!!, 1e-9)
        assertNull(DailyScore.adherence(null, 10000.0, DailyScore.STEPS))
        assertNull(DailyScore.regularity(listOf(1.0, 1.0, 1.0, null, null, null, null)))
        assertEquals(100, DailyScore.pillar(1.0))
        assertEquals(93, DailyScore.pillar(1.0, 0.5))
        assertEquals(0.5, DailyScore.progress(0.0, 0.01), 1e-9)
        assertTrue(DailyScore.progress(-0.001, 0.01) > 0.45) // une baisse mal mesurée ne coûte presque rien
    }

    @Test fun coverageWeightsShrinkSparseDomains() {
        val index = app.superhealth.league.HealthIndex
        val full = index.total(listOf(100, 100, 40, null, null, null), listOf(1.0, 1.0, 1.0))!!
        val sparse = index.total(listOf(100, 100, 40, null, null, null), listOf(1.0, 1.0, 4 / 7.0))!!
        assertTrue(sparse > full)
        assertNull(index.total(listOf(100, 100, null, null, null, null), listOf(1.0, 1.0)))
    }

    // ----- Quêtes -----

    @Test fun questDifficultyAimsForThreeSuccessesOutOfFour() {
        fun week(k: Int, progress: Int, target: Int) = WeeklyQuests.Week(k * 7L, listOf(WeeklyQuests.Quest(WeeklyQuests.Metric.FOOD, target, progress)))
        val easy = (0 until 6).map { week(it, 5, 3) }
        val hard = (0 until 6).map { week(it, 1, 4) }
        assertEquals(4, WeeklyQuests.tune(WeeklyQuests.Metric.FOOD, 3, easy))
        assertEquals(3, WeeklyQuests.tune(WeeklyQuests.Metric.FOOD, 4, hard))
        assertEquals(3, WeeklyQuests.tune(WeeklyQuests.Metric.FOOD, 3, easy.take(2)))
        val strength = (0 until 6).map { WeeklyQuests.Week(it * 7L, listOf(WeeklyQuests.Quest(WeeklyQuests.Metric.STRENGTH, 2, 3))) }
        assertEquals(2, WeeklyQuests.tune(WeeklyQuests.Metric.STRENGTH, 2, strength))
    }

    // ----- Attention -----

    @Test fun preferredMinuteFollowsHabitsByDayType() {
        val today = LocalDate.of(2026, 11, 4).toEpochDay() // mercredi
        val weekdays = (1L..27L).map { today - it }.filter { LocalDate.ofEpochDay(it).dayOfWeek.value < 6 }
        val examples = weekdays.map { Attention.Example(it, 18 * 60 + 20) } +
            (1L..27L).map { today - it }.filter { LocalDate.ofEpochDay(it).dayOfWeek.value >= 6 }.map { Attention.Example(it, 17 * 60) }
        assertEquals(18 * 60 + 20, Attention.preferredMinute(examples, 17 * 60..19 * 60, today))
        val saturday = LocalDate.of(2026, 11, 7).toEpochDay()
        assertEquals(17 * 60, Attention.preferredMinute(examples + (1L..10L).map { Attention.Example(saturday - 7 * it, 17 * 60) }, 17 * 60..19 * 60, saturday))
        assertNull(Attention.preferredMinute(examples.take(3), 17 * 60..19 * 60, today))
    }

    @Test fun notificationBudgetAndQuietHours() {
        val zone = ZoneId.of("Europe/Paris")
        val night = LocalDate.of(2026, 11, 4).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val noon = LocalDate.of(2026, 11, 4).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(Attention.quiet(night, zone, 22 * 60 + 30 to 7 * 60))
        assertFalse(Attention.quiet(noon, zone, 22 * 60 + 30 to 7 * 60))
        assertTrue(Attention.allowed(noon, social = false, sent = emptyList(), quiet = false))
        assertFalse(Attention.allowed(noon, social = false, sent = listOf(noon - 30 * 60_000), quiet = false))
        assertFalse(Attention.allowed(noon, social = false, sent = listOf(1L, 2L, 3L), quiet = false))
        assertTrue(Attention.allowed(noon, social = true, sent = listOf(1L, 2L, 3L), quiet = false))
        assertFalse(Attention.allowed(night, social = true, sent = emptyList(), quiet = true))
    }
}
