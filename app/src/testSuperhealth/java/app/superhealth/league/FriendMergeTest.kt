package app.superhealth.league

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** Signature validation happens before merging; no Context, WorkManager or Android Keystore is involved here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class FriendMergeTest {
    private val key = Base64.getUrlEncoder().withoutPadding().encodeToString("friend-public-key-placeholder".toByteArray())
    private val endpointOld = "https://old.example/v2/cards/old/mailbox"
    private val endpointNew = "https://new.example/v2/cards/new/mailbox"
    private val readOld = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 })
    private val readNew = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 })
    private val day = 20759L
    private val now = 1_793_700_000_000L
    private fun revision(n: Int) = 1_793_600_000_000L + n * 1000L
    private fun progress(xp: Long = 125) = Progression.Summary(totalXp = xp, weekXp = 40, level = Progression.level(xp), weekStart = Progression.week(day), throughDay = day)
    private fun metric(value: Double = 100.0) = League.Comparison("pr:bench:e1rm", "Bench · 1RM", "kg", value, scope = "all")
    private fun invite(n: Int, endpoint: String? = endpointNew, readKey: String? = readNew) = League.Card(
        v = 2, k = key, n = "New friend name", w = "2026-W45", t = revision(n) / 1000, s = 0, model = HealthIndex.MODEL,
        endDay = day, remote = endpoint, revision = revision(n), relayKey = readKey, invite = true)
    private fun health(n: Int, value: Int = 700) = League.Card(
        v = 2, k = key, n = "Old friend name", w = "2026-W45", t = revision(n) / 1000, s = value,
        p = listOf(60, 80, null, 90, null, null), st = 3, model = HealthIndex.MODEL, ready = true, endDay = day,
        metrics = listOf(metric()), detailSnapshot = true, remote = endpointOld, revision = revision(n), coverage = 11,
        relayKey = readOld, progress = progress())
    private fun merge(old: League.Friend?, c: League.Card) = requireNotNull(League.mergeFriend(old, c, now))
    private fun healthFields(f: League.Friend): List<Any?> = listOf(f.week, f.time, f.revision, f.score, f.pillars, f.streak,
        f.history, f.model, f.ready, f.endDay, f.metrics, f.metricsTime, f.coverage, f.metricsEndDay, f.progress, f.trends)

    @Test fun firstInvitationDoesNotPretendToContainAHealthSnapshot() {
        val f = merge(null, invite(2))
        assertEquals(0L, f.time)
        assertEquals(0L, f.revision)
        assertEquals(0, f.score)
        assertFalse(f.ready)
        assertTrue(f.metrics.isEmpty())
        assertTrue(f.history.isEmpty())
        assertNull(f.progress)
        assertEquals(now, f.addedAt)
        assertEquals(revision(2), f.inviteRevision)
    }

    @Test fun healthOlderThanTheInvitationStillFillsStatsWithoutRollingBackMetadata() {
        val invited = merge(null, invite(2))
        val f = merge(invited, health(1))
        assertEquals(700, f.score)
        assertEquals(revision(1), f.revision)
        assertEquals(progress(), f.progress)
        assertEquals(listOf(metric()), f.metrics)
        assertEquals(700, f.history["v2:$day:11"])
        assertEquals("New friend name", f.name)
        assertEquals(endpointNew, f.remote)
        assertEquals(readNew, f.relayKey)
        assertEquals(revision(2), f.inviteRevision)
        assertEquals(now, f.addedAt)
    }

    @Test fun reinvitationUpdatesConnectionButNeverErasesRecordedHealthOrLocalAlias() {
        val old = merge(null, health(1)).copy(alias = "My friend", relayEtag = "old-etag")
        val next = merge(old, invite(4))
        assertEquals(healthFields(old), healthFields(next))
        assertEquals(old.alias, next.alias)
        assertEquals(old.addedAt, next.addedAt)
        assertEquals(endpointNew, next.remote)
        assertEquals(readNew, next.relayKey)
        assertEquals(revision(4), next.inviteRevision)
        assertNull(next.relayEtag)
        assertNull(League.mergeFriend(next, invite(3), now))
        assertNull(League.mergeFriend(next, invite(4), now))
    }

    @Test fun invitationCannotRollBackMetadataFromANewerHealthCardIncludingLegacyStoredFriends() {
        val old = merge(null, health(4)).copy(inviteRevision = 0)
        assertNull(League.mergeFriend(old, invite(3), now))
        val upgraded = merge(old, health(5).copy(n = "Current name"))
        assertEquals("Current name", upgraded.name)
        assertEquals(revision(5), upgraded.inviteRevision)
        assertNull(League.mergeFriend(upgraded, invite(4), now))
    }

    @Test fun olderHealthCannotReactivateARevokedRemoteConnection() {
        val old = merge(null, health(1))
        val revoked = merge(old, invite(4, endpoint = null, readKey = null))
        val next = merge(revoked, health(3).copy(s = 850, progress = progress(300)))
        assertEquals(850, next.score)
        assertEquals(progress(300), next.progress)
        assertEquals(revision(3), next.revision)
        assertEquals(revision(4), next.inviteRevision)
        assertNull(next.remote)
        assertNull(next.relayKey)
        assertEquals("New friend name", next.name)
    }

    @Test fun healthReplayProtectionRemainsIndependentOfInvitationOrdering() {
        val old = merge(merge(null, invite(5)), health(1))
        val next = merge(old, health(3).copy(s = 800))
        assertEquals(800, next.score)
        assertEquals(revision(5), next.inviteRevision)
        assertNull(League.mergeFriend(next, health(2), now))
        assertNull(League.mergeFriend(next, health(3), now))
    }

    @Test fun normalRelayCardsKeepTheKnownSecretWhenEndpointIsUnchanged() {
        val old = merge(null, invite(2)).copy(relayEtag = "etag-current")
        val next = merge(old, health(3).copy(remote = endpointNew, relayKey = null, n = "Latest name"))
        assertEquals(readNew, next.relayKey)
        assertEquals(endpointNew, next.remote)
        assertEquals("Latest name", next.name)
        assertEquals(revision(3), next.inviteRevision)
        assertEquals("etag-current", next.relayEtag)
        val changed = merge(next, health(4).copy(remote = endpointOld, relayKey = null))
        assertNull(changed.relayKey)
        assertNull(changed.relayEtag)
    }

    @Test fun aliasesAndHistoricalTrendsRemainWhenANewerMetricArrives() {
        val old = merge(null, health(1).copy(endDay = day - 1)).copy(alias = "Local alias")
        val next = merge(old, health(2).copy(metrics = listOf(metric(110.0))))
        assertEquals("Local alias", next.alias)
        assertEquals(listOf(League.TrendPoint(day - 1, 100.0), League.TrendPoint(day, 110.0)), next.trends[metric().key])
        assertEquals(2, next.history.size)
        val corrected = merge(next, health(3).copy(metrics = listOf(metric(115.0))))
        assertEquals(listOf(League.TrendPoint(day - 1, 100.0), League.TrendPoint(day, 115.0)), corrected.trends[metric().key])
    }

    @Test fun compactHealthKeepsPreviouslyReceivedDetailedMetricsAndHelloDoesNothing() {
        val old = merge(null, health(1))
        val compact = merge(old, health(2).copy(detailSnapshot = false, metrics = emptyList()))
        assertEquals(old.metrics, compact.metrics)
        assertEquals(old.metricsTime, compact.metricsTime)
        assertEquals(old.metricsEndDay, compact.metricsEndDay)
        assertEquals(old.trends, compact.trends)
        assertNull(League.mergeFriend(old, health(3).copy(hello = true), now))
        assertNull(League.mergeFriend(old, health(3).copy(k = "different-key"), now))
    }
}
