package app.superhealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChartsTest {

    @Test fun ticksAreRoundAndCoverData() {
        val cases = listOf(72.1 to 72.6, 0.0 to 11850.0, 1830.0 to 2410.0, 48.0 to 131.0, 7.2 to 7.2, -340.0 to 610.0, 0.0 to 0.4, 95.0 to 99.0)
        cases.forEach { (lo, hi) ->
            val s = Scales.nice(lo, hi)
            assertTrue("$lo..$hi → $s", s.min <= lo + 1e-9 && s.max >= hi - 1e-9)
            assertTrue("$lo..$hi → ${s.ticks.size} ticks", s.ticks.size in 3..6)
            assertTrue("pas rond $s", listOf(1.0, 2.0, 2.5, 5.0).any { abs(s.step / Scales.niceStep(s.step) - it) < 1e-9 } || abs(s.step - Scales.niceStep(s.step)) < 1e-9)
            s.ticks.forEach { t -> assertEquals("graduation $t multiple du pas", 0.0, abs(t / s.step - Math.round(t / s.step)), 1e-6) }
            // la donnée occupe l'essentiel de la hauteur (sauf série plate)
            if (hi > lo) assertTrue("$lo..$hi remplit ${(hi - lo) / (s.max - s.min)}", (hi - lo) / (s.max - s.min) >= 0.5)
        }
    }

    @Test fun zeroBasedScaleForBars() {
        val s = Scales.nice(0.0, 11850.0, zero = true)
        assertEquals(0.0, s.min, 0.0)
        assertTrue(s.max in 11850.0..15000.0)
    }

    @Test fun paceUsesTimeSteps() {
        val s = Scales.nice(285.0, 372.0, time = true)
        assertTrue("pas ${s.step}", s.step in listOf(15.0, 20.0, 30.0, 60.0))
    }

    @Test fun smoothPathHitsPointsWithoutOvershoot() {
        val xs = floatArrayOf(0f, 1f, 2f, 3f, 5f, 6f, 9f)
        val ys = floatArrayOf(10f, 12f, 12f, 30f, 31f, 20f, 20f)
        val ends = mutableListOf<Pair<Float, Float>>()
        var seg = 0
        monotone(xs, ys, { x, y -> ends += x to y }, { x, y -> ends += x to y }) { _, c1y, _, c2y, x, y ->
            val lo = minOf(ys[seg], ys[seg + 1]); val hi = maxOf(ys[seg], ys[seg + 1])
            assertTrue("dépassement segment $seg", c1y in lo - 1e-4f..hi + 1e-4f && c2y in lo - 1e-4f..hi + 1e-4f)
            ends += x to y; seg++
        }
        assertEquals(xs.toList(), ends.map { it.first })
        assertEquals(ys.toList(), ends.map { it.second })
    }
}
