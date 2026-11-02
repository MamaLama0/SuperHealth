package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test

class HealthIndexTest {
    @Test fun insufficientDomainsDoNotProduceAHealthScore() {
        assertNull(HealthIndex.total(listOf(100, null, 100, null, null, null)))
    }
    @Test fun missingDomainsAreRenormalizedAndExcessCannotBoostTheScore() {
        assertEquals(1000, HealthIndex.total(listOf(100, 100, 100, null, null, null)))
        assertEquals(1000, HealthIndex.total(listOf(200, 100, 100, null, null, null)))
        assertEquals(500, HealthIndex.total(List(6) { 50 }))
    }
    @Test fun missingDaysAreNotZeroAndSparseDataIsNotEnough() {
        assertNull(HealthIndex.daily(listOf(8000.0, 8000.0, 8000.0, null, null, null, null), 8000.0))
        assertEquals(100, HealthIndex.daily(listOf(8000.0, 8000.0, 8000.0, 16000.0, null, null, null), 8000.0))
        assertEquals(50, HealthIndex.daily(List(7) { 4000.0 }, 8000.0))
    }
}
