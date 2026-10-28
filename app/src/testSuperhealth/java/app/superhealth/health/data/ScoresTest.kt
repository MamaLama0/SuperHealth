package app.superhealth.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class ScoresTest {
    private fun nights(n: Int) = List(n) { i -> Scores.Night(50.0 + (i % 3 - 1) * 4, 55.0 + (i % 2), 0.85, 14.0, null) }

    @Test fun chargeFollowsHrvAgainstBaseline() {
        assertNull(Scores.charge(nights(3), Scores.Night(60.0, 55.0, 0.85, 14.0, null)))
        val neutral = Scores.charge(nights(20), Scores.Night(50.0, 55.5, 0.85, 14.0, null))!!
        assertEquals(58.0, neutral.score.toDouble(), 3.0)       // z ≈ 0 → ancre à 58 %
        val high = Scores.charge(nights(20), Scores.Night(65.0, 54.0, 0.95, 14.0, null))!!.score
        val low = Scores.charge(nights(20), Scores.Night(35.0, 60.0, 0.6, 16.0, null))!!.score
        assertTrue("high=$high low=$low", high > 75 && low < 30)
        assertEquals(2, Scores.band(high)); assertEquals(0, Scores.band(low))
    }

    @Test fun effortEdwardsLogScale() {
        // 60 min à 95 % de la réserve (zone 5) : TRIMP 300 → 100·ln(301)/ln(7201)
        val hr = List(61) { (it * 60L) to 188 }
        val e = Scores.effort(hr, rest = 50.0, max = 195.0)!!
        assertEquals(100 * ln(301.0) / ln(7201.0), e, 0.6)
        assertNull(Scores.effort(hr.take(30), 50.0, 195.0))
        assertEquals(61.0, Scores.zones(hr, 50.0, 195.0)[5].toDouble(), 1.0)
    }

    @Test fun sleepDebtConvergesWithoutCredit() {
        // 7 h chaque nuit pour 8 h de besoin : d = 0,55 (60 + d) → 73 min
        assertEquals(73.3, Scores.sleepDebt(List(14) { 420.0 }, 480.0), 0.5)
        assertEquals(0.0, Scores.sleepDebt(List(14) { 600.0 }, 480.0), 0.0)
    }

    @Test fun hrMaxPrefersObservedWhenHigher() {
        assertEquals(208 - 0.7 * 30, Scores.hrMax(30, emptyList()), 1e-9)
        assertEquals(199.0, Scores.hrMax(30, List(1000) { if (it > 990) 199 else 120 }), 1e-9)
    }

    @Test fun chargeWithoutHrvUsesRestingHrAndSleep() {
        val h = nights(20).map { it.copy(hrv = null) }
        val c = Scores.charge(h, Scores.Night(null, 55.5, 0.85, 14.0, null))!!
        assertTrue(c.withoutHrv)
        assertEquals(58.0, c.score.toDouble(), 3.0)
        assertNull(Scores.charge(h, Scores.Night(null, 55.5, null, null, null)))   // pouls seul : pas assez
        assertTrue(Scores.charge(h, Scores.Night(null, 62.0, 0.6, 14.0, null))!!.score < 30)
    }

    @Test fun restCompositeMatchesNoopWeights() {
        // 7 h endormi / 7 h 30 au lit, 15 % profond, 25 % paradoxal, besoin 8 h, régularité 0,9
        assertEquals(87.42, Scores.rest(420.0, 450.0, 63.0, 105.0, 480.0, 0.9)!!, 0.01)
        // presque pas de profond : terme réparateur divisé par deux au plus
        val noDeep = Scores.rest(420.0, 450.0, 0.0, 168.0, 480.0, 0.9)!!
        assertEquals(87.42 - 0.2 * 80 * 0.5, noDeep, 0.01)
        assertNull(Scores.rest(0.0, 0.0, 0.0, 0.0, 480.0, null))
    }

    @Test fun personalNeedIsUpperQuartileFlooredAtGoal() {
        val n = listOf(400.0, 420.0, 440.0, 460.0, 480.0, 500.0, 520.0)
        assertEquals(490.0, Scores.personalNeed(n, 480.0), 1e-9)
        assertEquals(500.0, Scores.personalNeed(n, 500.0), 1e-9)
        assertEquals(480.0, Scores.personalNeed(n.take(6), 480.0), 1e-9)
        assertEquals(570.0, Scores.personalNeed(List(10) { 700.0 }, 480.0), 1e-9)
        assertEquals(1.0, Scores.consistency(listOf(420.0, 420.0, 420.0))!!, 1e-9)
        assertNull(Scores.consistency(listOf(420.0, 420.0)))
    }

    @Test fun fitnessAgeNes2011() {
        assertEquals(40.0, Scores.fitnessAge(40.0, false, 65.0, 5.0), 1e-9)
        assertEquals(40 - 0.155 * 10 / 0.296, Scores.fitnessAge(40.0, false, 55.0, 5.0), 1e-9)
        assertEquals(40 - 0.198 * 2 / 0.247, Scores.fitnessAge(40.0, true, 65.0, 7.0), 1e-9)
        assertEquals(20.0, Scores.fitnessAge(22.0, false, 40.0, 15.0), 1e-9)
        assertEquals(5 * 40.0 / 30, Scores.activityIndex(listOf(40.0, 40.0, 40.0, 40.0, 40.0, 10.0, 0.0)), 1e-9)
        assertEquals(0.0, Scores.activityIndex(listOf(10.0, 20.0)), 1e-9)
        assertEquals(2.5 * 3, Scores.activityIndex(listOf(95.0, 95.0, 95.0)), 1e-9)
    }
}
