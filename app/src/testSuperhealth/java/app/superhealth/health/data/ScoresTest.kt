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
}
