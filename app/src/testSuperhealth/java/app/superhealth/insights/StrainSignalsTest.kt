package app.superhealth.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StrainSignalsTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun series(base: Double, recent: Double) = (0L..30L).associate { k -> today.minusDays(k) to if (k <= 1) recent else base }

    @Test fun twoSignalsTogether() {
        val i = Insights.strain(Insights.Input(today, restingHr = series(52.0, 58.0), hrv = series(60.0, 45.0)))!!
        assertEquals(2, i.count)
        assertEquals(25.0, i.value, 1e-9)
    }

    @Test fun oneSignalIsNotEnough() {
        assertNull(Insights.strain(Insights.Input(today, restingHr = series(52.0, 58.0), hrv = series(60.0, 58.0))))
    }
}
