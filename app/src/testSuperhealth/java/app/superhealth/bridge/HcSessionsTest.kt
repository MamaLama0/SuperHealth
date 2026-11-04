package app.superhealth.bridge

import app.superhealth.HcSession
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HcSessionsTest {
    private fun session(id: String, start: Long = 1000, app: String = "com.strava", client: String? = "strava-exercise-123") =
        HcSession(id, 56, null, start, start + 60000, app, clientRecordId = client)

    @Test fun sourceMetricIdsFollowTheObservedStravaAndGarminConventions() {
        assertEquals("strava-distance-123", HcSessions.metricClientId("com.strava", "strava-exercise-123", distance = true))
        assertEquals("strava-calories-123", HcSessions.metricClientId("com.strava", "strava-exercise-123", distance = false))
        assertEquals("123", HcSessions.metricClientId("com.garmin.android.apps.connectmobile", "123", distance = true))
        assertEquals("123", HcSessions.metricClientId("com.garmin.android.apps.connectmobile", "123", distance = false))
        assertNull(HcSessions.metricClientId("other.app", "123", distance = true))
        assertNull(HcSessions.metricClientId("com.strava", "strava-exercise-", distance = true))
        assertNull(HcSessions.metricClientId("com.strava", "strava-exercise-123/route", distance = true))
    }

    @Test fun refreshRetainsOlderHistoryAndRemovesDeletedSessionsInsideItsWindow() {
        val old = session("old", 100, client = "strava-exercise-1")
        val deleted = session("deleted", 500, client = "strava-exercise-2")
        val fresh = session("fresh", 700, client = "strava-exercise-3")
        assertEquals(listOf(fresh, old), HcSessions.merge(listOf(old, deleted), listOf(fresh), 500))
    }

    @Test fun recreatedRecordReplacesTheSameSourceActivityWithoutErasingOtherSources() {
        val original = session("original", 100)
        val recreated = session("new-hc-id", 700).copy(distanceM = 5000.0)
        val other = session("other", 100, app = "other.app")
        assertEquals(listOf(recreated, other), HcSessions.merge(listOf(original, other), listOf(recreated), 500))
    }

    @Test fun returnToTheAppDetectsNewEditedAndDeletedSessionsWithoutDependingOnTheFullSyncTimer() {
        val one = session("one").copy(modifiedAt = 5)
        val old = session("old", start = 1).copy(modifiedAt = 1)
        assertFalse(HcSessions.metadataChanged(listOf(old, one), listOf(one), 1000))
        assertTrue(HcSessions.metadataChanged(listOf(one), listOf(one.copy(modifiedAt = 6)), 1000))
        assertTrue(HcSessions.metadataChanged(emptyList(), listOf(one), 1000))
        assertTrue(HcSessions.metadataChanged(listOf(one), emptyList(), 1000))
        assertTrue(HcSessions.metadataChanged(listOf(one.copy(modifiedAt = null)), listOf(one), 1000))
    }

    @Test fun simultaneousActivitiesHaveDistinctStableDetailIds() {
        val one = session("one")
        val two = session("two", client = "strava-exercise-456")
        val other = one.copy(app = "other.app")
        assertNotEquals(HcWorkouts.idOf(one), HcWorkouts.idOf(two))
        assertNotEquals(HcWorkouts.idOf(one), HcWorkouts.idOf(other))
        assertEquals(HcWorkouts.idOf(one), HcWorkouts.idOf(one.copy(id = "recreated", start = 2000)))
        assertTrue(HcWorkouts.isHc(HcWorkouts.idOf(one)))
        assertNotEquals(HcWorkouts.idOf(one.copy(clientRecordId = null)), HcWorkouts.idOf(two.copy(clientRecordId = null)))
    }

    @Test fun pausesDoNotDuplicateAnImportedRunAndItsHealthConnectSummary() {
        val run = RunImport.Run(-1, "Run", "RUN", "FIT", 0, 3600000, 1800.0, distanceM = 5000.0)
        val imported = Bridge.Activity("i", Bridge.Kind.RUN, "Run", run.start, run.end, Bridge.Source.IMPORT, run = run)
        val mirrored = Bridge.Activity("h", Bridge.Kind.RUN, "Run", 30000, 1830000, Bridge.Source.HEALTH_CONNECT,
            hc = session("hc", 30000).copy(end = 1830000, distanceM = 5050.0))
        assertTrue(HcOutdoorSync.duplicate(imported.kind, imported.start, imported.end, mirrored.kind, mirrored.start, mirrored.end, 5000.0, 5050.0))
        assertEquals(1, Bridge.reconcile(listOf(imported, mirrored)).size)
        assertFalse(HcOutdoorSync.sameStartAndDistance(0, 3600000, 5000.0, 600000, 2400000, 5050.0))
        assertFalse(HcOutdoorSync.sameStartAndDistance(0, 3600000, 5000.0, 0, 1800000, 7000.0))
    }

    @Test fun detailLinksSurviveLegacyCacheEnrichment() {
        val old = session("hc-id", client = null)
        val migrated = old.copy(clientRecordId = "strava-exercise-123", modifiedAt = 99)
        assertEquals(migrated, HcWorkouts.sessionFor(HcWorkouts.idOf(old), listOf(migrated)))
        assertEquals(migrated, HcWorkouts.sessionFor(-((1L shl 47) + old.start / 1000), listOf(migrated)))
        assertNull(HcWorkouts.sessionFor(-999999, listOf(migrated)))
    }

    @Test fun legacyCacheLoadsAndTotalCaloriesRemainSeparateFromActiveCalories() {
        val legacy = """{"id":"one","type":56,"title":null,"start":1000,"end":61000,"app":"com.strava","kcal":125.0}"""
        val old = Json.decodeFromString(HcSession.serializer(), legacy)
        assertEquals(125.0, old.kcal!!, 0.0)
        assertNull(old.totalKcal)
        assertNull(old.clientRecordId)
        assertNull(old.heartRate)
        val updated = old.copy(totalKcal = 200.0, heartRate = emptyList())
        val restored = Json.decodeFromString(HcSession.serializer(), Json.encodeToString(HcSession.serializer(), updated))
        assertEquals(125.0, restored.kcal!!, 0.0)
        assertEquals(200.0, restored.totalKcal!!, 0.0)
        assertEquals(emptyList<Pair<Long, Int>>(), restored.heartRate)
    }
}
