package app.superhealth.health
import app.superhealth.health.data.Performance
import org.junit.Assert.*
import org.junit.Test
class PerformanceAvailabilityTest {
    private val now = 1800000000000L
    private fun entry(age: Long, recovery: Double? = null) = Performance.Entry(age, now - age * 86400000, now - age * 86400000, true, 5000.0, 1800.0, recoverySec = recovery)
    @Test fun firstImportHasNoLoadVerdictOrInventedRecovery() {
        val snapshot = Performance.compute(listOf(entry(1)), now)
        assertNull(snapshot.acwr); assertNull(snapshot.recoveryLeftH)
    }
    @Test fun elapsedRecoveryIsZeroRatherThanMissing() {
        assertEquals(0.0, Performance.compute(listOf(entry(2, 3600.0)), now).recoveryLeftH!!, .001)
        assertNotNull(Performance.compute(listOf(entry(1), entry(30)), now).acwr)
    }
}
