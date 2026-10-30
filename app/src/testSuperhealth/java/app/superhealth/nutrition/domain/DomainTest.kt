package app.superhealth.nutrition.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertFalse

class DomainTest {

    @Test fun cunningham() = assertEquals(500 + 22 * 60.0, Bmr.cunningham(60.0), 1e-9)
    @Test fun estimatedBodyFat() {
        // IMC 22, homme de 40 ans : CUN-BAE sous 25 % → défaut MacroFactor gardé
        assertEquals(25.0, Bmr.estimatedBodyFat(Sex.MALE, 40, 67.4, 175.0), 1e-9)
        // IMC 52, homme de 40 ans : CUN-BAE ≈ 50,9 %
        assertEquals(50.9, Bmr.estimatedBodyFat(Sex.MALE, 40, 159.25, 175.0), 0.1)
        // sans taille : défaut
        assertEquals(35.0, Bmr.estimatedBodyFat(Sex.FEMALE, 40, 120.0, null), 1e-9)
    }
    @Test fun mifflinInObesity() {
        assertEquals(10 * 140.0 + 6.25 * 175 - 5 * 40 + 5, Bmr.mifflin(Sex.MALE, 140.0, 175.0, 40), 1e-9)
        val p = Profile(sex = Sex.MALE, heightCm = 175.0)
        // IMC 45,7 sans masse grasse : Mifflin ; avec masse grasse mesurée : équation de MacroFactor
        assertEquals(Bmr.mifflin(Sex.MALE, 140.0, 175.0, 40), Bmr.bmr(p, 140.0, null, 40), 1e-6)
        assertTrue(Bmr.bmr(p, 140.0, 40.0, 40) != Bmr.mifflin(Sex.MALE, 140.0, 175.0, 40))
    }

    @Test fun adaptationFactors() {
        assertEquals(0.92, Bmr.adaptationFactor(WeightChange.LOSING, LifetimeWeight.HIGHER), 0.0)
        assertEquals(0.95, Bmr.adaptationFactor(WeightChange.LOSING, LifetimeWeight.LOWER), 0.0)
        assertEquals(0.97, Bmr.adaptationFactor(WeightChange.GAINING, LifetimeWeight.HIGHER), 0.0)
        assertEquals(1.0, Bmr.adaptationFactor(WeightChange.UNKNOWN, LifetimeWeight.UNKNOWN), 0.0)
    }

    @Test fun equationsArePlausible() {
        // 80 kg, 20 % MG, 35 ans → BMR attendu ~1700–1900 kcal
        val lbm = Bmr.leanMass(80.0, 20.0)
        val fm = Bmr.fatMass(80.0, 20.0)
        val a = Bmr.equationA(lbm, 1.0)
        val b = Bmr.equationB(lbm, fm, 35, 1.0)
        assertTrue("A=$a", a in 1500.0..2100.0)
        assertTrue("B=$b", b in 1500.0..2100.0)
        // pénalité d'âge au-delà de 60 ans
        assertEquals(1.1 * 10 + 2.75 * 10, Bmr.equationB(lbm, fm, 60, 1.0) - Bmr.equationB(lbm, fm, 70, 1.0), 1e-6)
    }

    @Test fun storedEnergyAsymmetric() {
        assertEquals(-7700.0, Expenditure.storedEnergy(-1.0), 0.0)
        assertEquals(4400.0, Expenditure.storedEnergy(1.0), 0.0)
        assertEquals(0.0, Expenditure.storedEnergy(0.0), 0.0)
    }

    @Test fun emaTrendConverges() {
        val w = (0L until 60L).associateWith { 80.0 }
        val t = WeightTrend.compute(w + (0L to 85.0), TrendAlpha.EMA9.alpha, 0, 59)
        assertTrue(abs(t.last().trend - 80.0) < 0.1)
    }

    @Test fun outlierIsDamped() {
        val w = (0L until 20L).associate { it to 80.0 + (if (it % 2 == 0L) 0.2 else -0.2) }.toMutableMap()
        w[20L] = 90.0
        val t = WeightTrend.compute(w, TrendAlpha.EMA9.alpha, 0, 20)
        // sans bornage, la tendance sauterait de ~2,2 kg
        assertTrue("trend=${t.last().trend}", t.last().trend < 80.6)
    }

    /** Simulation : dépense réelle 2500 kcal, apport 2000 kcal → perte ~0,45 kg/sem ; l'estimation doit converger vers 2500. */
    @Test fun expenditureRecoversTrueTdee() {
        val trueTdee = 2500.0
        val intake = 2000.0
        var weight = 85.0
        val days = ArrayList<DayData>()
        for (d in 0L until 70L) {
            val noise = if (d % 3 == 0L) 0.3 else if (d % 3 == 1L) -0.2 else -0.1
            days += DayData(d, intake, weight + noise, null)
            weight += (intake - trueTdee) / Expenditure.KCAL_PER_KG_LOSS
        }
        val trend = WeightTrend.compute(days.associate { it.epochDay to it.weightKg!! }, TrendAlpha.EMA9.alpha, 0, 69)
        val exp = Expenditure.compute(days, trend.associate { it.epochDay to it.trend }, initialTdee = 2200.0)
        val last = exp.last()
        assertEquals(Expenditure.Status.UPDATING, last.status)
        assertTrue("tdee=${last.tdee}", abs(last.tdee - trueTdee) < 120)
    }

    @Test fun holdsWithoutData() {
        val days = (0L until 30L).map { DayData(it, if (it < 15) 2000.0 else null, if (it < 15) 80.0 else null, null) }
        val trend = WeightTrend.compute(days.filter { it.weightKg != null }.associate { it.epochDay to it.weightKg!! }, 0.2, 0, 29)
        val exp = Expenditure.compute(days, trend.associate { it.epochDay to it.trend }, 2300.0)
        assertEquals(Expenditure.Status.HOLDING, exp.last().status)
    }

    @Test fun fastingCountsAsZeroPartialExcluded() {
        assertEquals(0.0, Expenditure.usableIntake(DayData(0, null, null, null, fasted = true))!!, 0.0)
        assertEquals(null, Expenditure.usableIntake(DayData(0, 1500.0, null, null, partial = true)))
    }

    @Test fun cutTargetBelowTdeeAndFloor() {
        val program = Program(calorieFloor = CalorieFloor.STANDARD, proteinPreference = ProteinPreference.MODERATE)
        val goal = Goal(startDay = 0, type = GoalType.CUT, startWeightKg = 80.0, ratePctPerWeek = 0.5)
        val c = Targets.checkIn(7, 2500.0, 80.0, 64.0, program, goal)
        // 0,5 % de 80 kg = 0,4 kg/sem → −0,4×7700/7 = −440 kcal
        assertEquals(2060.0, c.kcal, 1.0)
        assertEquals(1.7 * 64, c.protein, 1e-6) // perte, sans entraînement, préférence modérée
        val low = Targets.checkIn(7, 1300.0, 80.0, 64.0, program, goal)
        assertTrue(low.limitedByFloor)
        // plancher dynamique standard, 175 cm, MM 64 kg : 1,5 × (43×9 + 90×4 + 45×4) = 1390,5
        assertEquals(1390.5, low.kcal, 1e-9)
    }

    @Test fun macroMinimumsFromHeight() {
        assertEquals(43.0, Targets.minFatG(175.0), 0.0)      // ½·25 + 30 = 42,5 → 43
        assertEquals(30.0, Targets.minFatG(150.0), 0.0)
        assertEquals(45.0, Targets.minCarbsG(175.0), 0.0)
        assertEquals(80.0, Targets.ketoCarbsG(240.0), 0.0)
        assertEquals(112.0, Targets.minProteinG(95.0), 0.0)   // masse maigre plafonnée à 80 kg
        assertEquals(1200.0, Targets.dynamicFloor(CalorieFloor.STANDARD, 160.0, 45.0), 0.0)
        assertEquals(927.0, Targets.dynamicFloor(CalorieFloor.LOW, 175.0, 64.0), 0.0)
    }

    @Test fun dynamicMaintenanceBand() {
        val g = Goal(startDay = 0, type = GoalType.MAINTENANCE, startWeightKg = 80.0, targetWeightKg = 80.0, ratePctPerWeek = 0.0)
        assertEquals(0.0, Targets.weeklyRateKg(g, 80.6), 0.0)                 // dans ±0,68 kg
        assertEquals(-0.0015 * 81.0, Targets.weeklyRateKg(g, 81.0), 1e-12)   // retour lent à 0,15 %/sem
        assertEquals(0.0015 * 79.0, Targets.weeklyRateKg(g, 79.0), 1e-12)
    }

    @Test fun calorieShiftKeepsWeeklyAverage() {
        val one = Targets.distribute(2000.0, 1200.0, setOf(6))
        assertTrue(one.shifted)
        assertEquals(1.15, one.kcal[5] / one.kcal[0], 0.002)
        assertEquals(2000.0 * 7, one.kcal.sum(), 4.0)
        val two = Targets.distribute(2000.0, 1200.0, setOf(6, 7))
        assertEquals(1944.0, two.kcal[0], 0.0); assertEquals(2139.0, two.kcal[6], 0.0)
        val none = Targets.distribute(2000.0, 1200.0, emptySet())
        assertTrue(!none.shifted && none.kcal.all { it == 2000.0 })
        // près du plancher : jours normaux au plancher, excédent sur les jours hauts (≤ 1,1 × moyenne)
        val tight = Targets.distribute(1210.0, 1200.0, setOf(1))
        assertEquals(1270.0, tight.kcal[0], 0.0)
        assertTrue(tight.kcal.drop(1).all { it == 1200.0 })
        assertTrue(Targets.distribute(1100.0, 1200.0, setOf(1)).limitedByFloor)
    }

    @Test fun dietSplits() {
        val b = Targets.split(2000.0, 600.0, DietPreference.BALANCED)
        assertEquals(600.0 / 9, b.fat, 1e-9); assertEquals(800.0 / 4, b.carbs, 1e-9)
        val lc = Targets.split(2000.0, 600.0, DietPreference.LOW_CARB)
        assertEquals(800.0 / 9, lc.fat, 1e-9)
        val k = Targets.split(2000.0, 600.0, DietPreference.KETO, ketoCarbsG = 45.0)
        assertEquals(45.0, k.carbs, 1e-9)
        assertEquals((1400.0 - 45.0 * 4) / 9, k.fat, 1e-9)
    }

    @Test fun proteinTable() {
        assertEquals(3.5, Targets.proteinPerKgLbm(GoalType.CUT, Training.BOTH, ProteinPreference.EXTRA), 0.0)
        assertEquals(1.25, Targets.proteinPerKgLbm(GoalType.BULK, Training.NONE, ProteinPreference.LOW), 0.0)
    }

    @Test fun goalEndDateRoundTrip() {
        val end = Targets.endDayFromRate(0, 90.0, 80.0, 0.5)!!
        val rate = Targets.rateFromEndDay(0, 90.0, 80.0, end)!!
        assertTrue("rate=$rate", abs(rate - 0.5) < 0.02)
    }

    /** Sujet virtuel : pesées bruitées (eau ±, bruit 0,5 kg), dépense réelle connue. */
    private fun simulate(days: Int, trueTdee: (Int) -> Double, intake: (Int) -> Double, w0: Double = 85.0, seed: Long = 7): List<Triple<Long, Double, Double>> {
        val rnd = java.util.Random(seed)
        var fat = w0; var water = 0.0
        return (0 until days).map { d ->
            fat += (intake(d) - trueTdee(d)) / 7700.0
            water = water * 0.7 + rnd.nextGaussian() * 0.35
            Triple(d.toLong(), fat + water + rnd.nextGaussian() * 0.25, fat)
        }
    }

    @Test fun trendV2TracksTrueWeight() {
        val sim = simulate(90, { 2500.0 }, { 2000.0 })
        val t = WeightTrendV2.compute(sim.associate { it.first to it.second }, 89)
        val errV2 = sim.zip(t).drop(10).map { (s, p) -> abs(p.trend - s.third) }.average()
        val ema = WeightTrend.compute(sim.associate { it.first to it.second }, TrendAlpha.EMA9.alpha, 0, 89)
        val errV1 = sim.zip(ema).drop(10).map { (s, p) -> abs(p.trend - s.third) }.average()
        println("trend error V2=$errV2 V1=$errV1")
        assertTrue("V2 err=$errV2", errV2 < 0.35)
    }

    @Test fun expenditureV3RecoversTrueTdee() {
        val sim = simulate(84, { 2500.0 }, { 2000.0 })
        val t = WeightTrendV2.compute(sim.associate { it.first to it.second }, 83).associateBy { it.epochDay }
        val days = sim.map { ExpenditureV3.Day(it.first, t.getValue(it.first).tween, it.second, 2000.0, false, false) }
        val exp = ExpenditureV3.compute(days, 2200.0, emptyList(), predictive = true)
        exp.take(12).forEach { println("V3dbg ${it.epochDay} tdee=${it.tdee} paused=${it.paused} miss=${it.missingDays} adj=${it.goalAdjustment}") }
        println("V3 day 21=${exp[21].tdee} day 42=${exp[42].tdee} day 83=${exp.last().tdee} short=${exp.last().short} long=${exp.last().long}")
        assertTrue(exp[2].paused)
        assertTrue("tdee=${exp.last().tdee}", exp.last().tdee > 2300)
    }

    @Test fun expenditureV3RealisticSubjects() {
        // Maintien : apport = dépense
        val m = simulate(84, { 2500.0 }, { 2500.0 })
        val tm = WeightTrendV2.compute(m.associate { it.first to it.second }, 83).associateBy { it.epochDay }
        val em = ExpenditureV3.compute(m.map { ExpenditureV3.Day(it.first, tm.getValue(it.first).tween, it.second, 2500.0, false, false) }, 2200.0, emptyList(), false)
        // Déficit : la dépense réelle suit le poids (∝ poids) et s'adapte de 50 % du déficit relatif
        var w = 85.0
        val rnd = java.util.Random(3); var water = 0.0
        val truth = ArrayList<Double>(); val obs = ArrayList<Triple<Long, Double, Double>>()
        for (d in 0 until 84) {
            val t = 2500.0 * (w / 85.0) * (1 - 0.5 * 500 / 2500 * kotlin.math.min(1.0, d / 28.0))
            truth += t
            w += (2000.0 - t) / 7700.0
            water = water * 0.7 + rnd.nextGaussian() * 0.35
            obs += Triple(d.toLong(), w + water + rnd.nextGaussian() * 0.25, w)
        }
        val td = WeightTrendV2.compute(obs.associate { it.first to it.second }, 83).associateBy { it.epochDay }
        val ed = ExpenditureV3.compute(obs.map { ExpenditureV3.Day(it.first, td.getValue(it.first).tween, it.second, 2000.0, false, false) }, 2300.0, emptyList(), false)
        println("REAL maint=${em.last().tdee} deficit est=${ed.last().tdee} true=${truth.last()} est42=${ed[42].tdee} true42=${truth[42]}")
    }

    @Test fun expenditureV3HoldsWithoutNutrition() {
        val sim = simulate(30, { 2500.0 }, { 2500.0 })
        val t = WeightTrendV2.compute(sim.associate { it.first to it.second }, 29).associateBy { it.epochDay }
        val days = sim.map { ExpenditureV3.Day(it.first, t.getValue(it.first).tween, it.second, if (it.first < 15) 2500.0 else null, false, false) }
        val exp = ExpenditureV3.compute(days, 2300.0, emptyList(), predictive = false)
        assertTrue(exp.last().paused)
    }

    @Test fun floorFormula() = assertEquals(maxOf(598.0, 48.1 * Math.pow(70.0, 0.783)), Expenditure.floor(70.0), 1e-9)
}
