package app.superhealth.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 21)
    private fun d(k: Long) = today.minusDays(k)

    @Test fun liftComparesBestOfFirstAndLastTwoSessions() {
        // 100 → 104 kg en 6 semaines (séances 41, 34, …, 6 jours avant)
        val pts = listOf(41L to 99.0, 34L to 100.0, 27L to 101.0, 20L to 102.0, 13L to 103.0, 6L to 104.0).map { (k, v) -> d(k) to v }
        val i = Insights.lift(Insights.Input(today, lifts = listOf(Insights.Lift("row", "Tirage", pts))))!!
        assertEquals(4.0, i.value, 1e-9)       // max(103, 104) / max(99, 100) − 1
        assertEquals(5, i.count)               // 35 jours ≈ 5 semaines
        assertEquals("Tirage", i.subject)
    }

    @Test fun liftNeedsEnoughSessionsSpreadAndChange() {
        val few = listOf(d(30) to 100.0, d(20) to 105.0, d(10) to 110.0)
        assertNull(Insights.lift(Insights.Input(today, lifts = listOf(Insights.Lift("a", "A", few)))))
        val tight = listOf(d(15) to 100.0, d(12) to 105.0, d(8) to 108.0, d(1) to 110.0)
        assertNull(Insights.lift(Insights.Input(today, lifts = listOf(Insights.Lift("a", "A", tight)))))
        val flat = listOf(d(40) to 100.0, d(30) to 101.0, d(20) to 100.0, d(5) to 101.5)
        assertNull(Insights.lift(Insights.Input(today, lifts = listOf(Insights.Lift("a", "A", flat)))))
    }

    @Test fun liftPicksLargestChangeAndIgnoresOldSessions() {
        val small = Insights.Lift("a", "A", listOf(d(40), d(30), d(20), d(5)).zip(listOf(100.0, 100.0, 103.0, 103.0)))
        val big = Insights.Lift("b", "B", listOf(d(41), d(30), d(20), d(2)).zip(listOf(80.0, 80.0, 72.0, 72.0)))
        val old = Insights.Lift("c", "C", listOf(d(90), d(80), d(70), d(60)).zip(listOf(50.0, 60.0, 70.0, 80.0)))
        val i = Insights.lift(Insights.Input(today, lifts = listOf(small, big, old)))!!
        assertEquals("b", i.subjectId)
        assertEquals(-10.0, i.value, 1e-9)
    }

    @Test fun sleepAfterRestVsTrainingDays() {
        // séances un jour sur deux ; nuit suivante : 7 h après séance, 6 h 20 après repos (± bruit)
        val training = (1L..42L).filter { it % 2 == 0L }.map(::d).toSet()
        val sleep = (0L..41L).associate { k ->
            val dayBefore = d(k + 1)
            d(k) to (if (dayBefore in training) 420.0 else 380.0) + (k % 3) * 5.0
        }
        val i = Insights.sleepTraining(Insights.Input(today, sleepMin = sleep, trainingDays = training))!!
        assertTrue(i.value < -35 && i.value > -45)  // ≈ 40 min de moins après les jours sans séance
        assertEquals(21, i.count); assertEquals(21, i.total)
    }

    @Test fun sleepNeedsSignificanceAndEnoughNights() {
        val training = setOf(d(2), d(4), d(6))
        val sleep = (0L..20L).associate { d(it) to 420.0 + (it % 2) * 60 }
        assertNull(Insights.sleepTraining(Insights.Input(today, sleepMin = sleep, trainingDays = training)))
        // écart de 20 min noyé dans ± 90 min de bruit : |t| < 2
        val tr2 = (1L..42L).filter { it % 2 == 0L }.map(::d).toSet()
        val noisy = (0L..41L).associate { k -> d(k) to (if (d(k + 1) in tr2) 420.0 else 400.0) + listOf(-90.0, 90.0, 0.0, 45.0, -45.0)[(k % 5).toInt()] }
        assertNull(Insights.sleepTraining(Insights.Input(today, sleepMin = noisy, trainingDays = tr2)))
    }

    @Test fun stepsWeekVsFourWeeks() {
        val steps = (1L..35L).associate { k -> d(k) to if (k <= 7) 9000.0 else 7500.0 } + (d(0) to 100.0) // aujourd'hui ignoré
        val i = Insights.steps(Insights.Input(today, steps = steps))!!
        assertEquals(20.0, i.value, 1e-9)
        assertEquals(9000, i.count)
        assertNull(Insights.steps(Insights.Input(today, steps = (1L..35L).associate { d(it) to 8000.0 })))
    }

    @Test fun weightRatePerWeekOverFourWeeks() {
        val trend = (0L..40L).associate { k -> d(k) to 80.0 + k * 0.05 } // −0,35 kg / semaine
        val i = Insights.weight(Insights.Input(today, trendKg = trend))!!
        assertEquals(-0.35, i.value, 1e-9)
        assertNull(Insights.weight(Insights.Input(today, trendKg = (0L..10L).associate { d(it) to 80.0 })))
        val stale = (20L..60L).associate { d(it) to 80.0 }
        assertNull(Insights.weight(Insights.Input(today, trendKg = stale)))
    }

    @Test fun kcalAndProteinOnCompleteLoggedDays() {
        val days = (1L..7L).map(::d)
        val i = Insights.Input(today,
            intakeKcal = days.associateWith { 1840.0 } + (d(0) to 300.0),
            kcalTarget = (0L..7L).associate { d(it) to 2000.0 },
            protein = days.withIndex().associate { (n, x) -> x to if (n < 5) 150.0 else 100.0 },
            proteinTarget = (0L..7L).associate { d(it) to 160.0 })
        assertEquals(-8.0, Insights.kcal(i)!!.value, 1e-9)
        val p = Insights.protein(i)!!
        assertEquals(5, p.count); assertEquals(7, p.total)    // 150 ≥ 0,9 × 160 = 144
        assertNull(Insights.kcal(i.copy(intakeKcal = mapOf(d(1) to 2000.0, d(2) to 2000.0, d(3) to 2000.0))))
    }

    @Test fun restingHrDropIsSignificant() {
        val rhr = (0L..34L).associate { k -> d(k) to (if (k <= 6) 55.0 else 58.0) + (k % 2) }
        val i = Insights.restingHr(Insights.Input(today, restingHr = rhr))!!
        assertEquals(-3.0, i.value, 0.2)
    }

    @Test fun trainingMonthOverMonth() {
        val t = (setOf(1L, 5L, 9L, 13L, 17L, 21L) + setOf(30L, 40L)).map(::d).toSet()
        val i = Insights.training(Insights.Input(today, trainingDays = t))!!
        assertEquals(4.0, i.value, 0.0); assertEquals(6, i.count); assertEquals(2, i.total)
        assertNull(Insights.training(Insights.Input(today, trainingDays = setOf(d(1), d(30)))))
    }

    @Test fun computeSortsByScore() {
        val trend = (0L..40L).associate { k -> d(k) to 80.0 }
        val steps = (1L..35L).associate { k -> d(k) to if (k <= 7) 12000.0 else 6000.0 }
        val all = Insights.compute(Insights.Input(today, trendKg = trend, steps = steps))
        assertEquals(listOf(Insights.Type.STEPS_WEEK, Insights.Type.WEIGHT_RATE), all.map { it.type })
    }

    @Test fun pearsonOnCommonDays() {
        val a = (0L..9L).associate { d(it) to it.toDouble() }
        val b = (0L..9L).associate { d(it) to 2.0 * it + 1 } + (d(20) to 99.0)
        val r = Correlation.pearson(a, b)!!
        assertEquals(1.0, r.r, 1e-12); assertEquals(10, r.n); assertTrue(r.significant)
        val c = (0L..9L).associate { d(it) to listOf(3.0, 1.0, 4.0, 1.0, 5.0, 9.0, 2.0, 6.0, 5.0, 3.0)[it.toInt()] }
        val rc = Correlation.pearson(a, c)!!
        assertTrue(kotlin.math.abs(rc.r) < 0.5); assertFalse(rc.significant)
        assertNull(Correlation.pearson(a, (0L..9L).associate { d(it) to 1.0 }))
        assertEquals(Correlation.Strength.MODERATE, Correlation.strength(-0.42))
    }

    @Test fun pearsonLagShiftsSecondSeries() {
        // b = a du jour d'avant : corrélation parfaite avec un décalage d'un jour
        val a = (0L..14L).associate { d(it) to ((it * 7) % 5).toDouble() }
        val b = a.mapKeys { it.key.plusDays(1) }
        assertEquals(1.0, Correlation.pearson(a, b, lag = 1)!!.r, 1e-12)
        assertNotNull(Correlation.pearson(a, b))
    }
}
