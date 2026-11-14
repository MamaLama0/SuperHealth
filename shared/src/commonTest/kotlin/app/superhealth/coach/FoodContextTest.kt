package app.superhealth.coach

import app.superhealth.platform.*
import app.superhealth.nutrition.data.DayFlagEntity
import kotlin.test.*
import kotlin.test.Test
class FoodContextTest {
    @Test fun explicitIncompleteAndFastingDaysOverrideRecordedTotals() {
        assertTrue(FoodContext.status(DayFlagEntity(1, partial = true), true, false).startsWith("incomplete"))
        assertTrue(FoodContext.status(DayFlagEntity(1, fasted = true), false, false).startsWith("fasted"))
        assertTrue(FoodContext.status(null, false, false).contains("unknown intake"))
        assertEquals("in progress today", FoodContext.status(null, true, true))
    }
}
