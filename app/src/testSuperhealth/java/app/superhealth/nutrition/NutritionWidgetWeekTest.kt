package app.superhealth.nutrition

import app.superhealth.nutrition.domain.Macros
import app.superhealth.nutrition.domain.NutritionWidgetWeek
import org.junit.Assert.*
import org.junit.Test

class NutritionWidgetWeekTest {
    private fun m(kcal: Double) = Macros(kcal, kcal / 20, kcal / 40, kcal / 10)
    @Test fun includesExactlyTheLastSevenDaysAndExcludesOlderAndFutureEntries() {
        val w = NutritionWidgetWeek.of(10, mapOf(3L to m(1000.0), 4L to m(2000.0), 10L to m(2400.0), 11L to m(5000.0))) { m(2200.0) }
        assertEquals((4L..10L).toList(), w.days.map { it.epochDay })
        assertEquals(2, w.loggedDays)
        assertEquals(2200.0, w.average!!.kcal, 0.0)
    }
    @Test fun missingDaysDoNotLowerTheAverageAndRealZerosRemainLogged() {
        val w = NutritionWidgetWeek.of(10, mapOf(8L to m(2000.0), 10L to m(0.0))) { m(2000.0) }
        assertNull(w.days.first().eaten)
        assertEquals(2, w.loggedDays)
        assertEquals(m(1000.0), w.average)
        assertEquals(0.0, w.days.last().eaten!!.kcal, 0.0)
    }
    @Test fun averagesTargetsForTheSameLoggedDatesOnly() {
        val w = NutritionWidgetWeek.of(10, mapOf(4L to m(2000.0), 10L to m(2400.0))) { m(it * 100.0) }
        assertEquals(m(700.0), w.averageTarget)
        assertEquals(listOf(400.0,500.0,600.0,700.0,800.0,900.0,1000.0), w.days.map { it.target.kcal })
    }
    @Test fun emptyWeekHasNoInventedAverage() {
        val w = NutritionWidgetWeek.of(10, emptyMap()) { m(2200.0) }
        assertEquals(7, w.days.size); assertEquals(0, w.loggedDays)
        assertNull(w.average); assertNull(w.averageTarget)
    }
    @Test fun editingOrDeletingAnOlderMealChangesTheWeeklyAverage() {
        val initial = mapOf(4L to m(1000.0), 10L to m(2000.0))
        fun week(values: Map<Long, Macros>) = NutritionWidgetWeek.of(10, values) { m(2200.0) }
        assertEquals(m(1500.0), week(initial).average)
        assertEquals(m(2000.0), week(initial + (4L to m(2000.0))).average)
        assertEquals(m(1000.0), week(initial - 10L).average)
    }
}
