package app.superhealth.league

import app.superhealth.bridge.Bridge
import org.junit.Assert.*
import org.junit.Test

class SocialNotificationsTest {
    private val now = 1800000000000L
    private fun post(id: Char, at: Long = now) = SocialPosts.Post(id.toString().repeat(32), at)
    private fun friend(posts: List<SocialPosts.Post>, time: Long = now / 1000) = League.Friend("id", "key", "Alex", "w", time, 0, posts = posts)
    @Test fun onlyNewRecentPostsNotifyAndTheFirstSyncIsQuiet() {
        val old = friend(listOf(post('a')))
        val next = friend(listOf(post('b'), post('a'), post('c', now - 2 * 86400000)))
        assertEquals(listOf(post('b')), SocialNotifications.newPosts(old, next, now))
        assertTrue(SocialNotifications.newPosts(next, next, now).isEmpty())
        assertTrue(SocialNotifications.newPosts(null, next, now).isEmpty())
        assertTrue(SocialNotifications.newPosts(friend(emptyList(), time = 0), next, now).isEmpty())
    }
    @Test fun allSportKindsHaveACompatibleSummaryWithoutAnyTrack() {
        Bridge.Kind.entries.forEach { kind ->
            val a = Bridge.Activity("id", kind, "Activity", now, now + 1800000, Bridge.Source.IMPORT)
            val draft = SocialPosts.activityDraft(a)
            assertEquals(if (kind == Bridge.Kind.RUN) "RUN" else "WORKOUT", draft.kind)
            assertEquals("30 min", draft.detail)
            assertEquals("Activity", draft.title)
            assertTrue(SocialPosts.valid(post('a').copy(kind = draft.kind, title = draft.title, detail = draft.detail), now))
        }
    }
    @Test fun avatarIsOptionalOnOldCardsAndRejectsVideosAndOversizedImages() {
        val media = SocialPosts.Media("a".repeat(64), RelayCrypto.newKey(), "image/jpeg", "b".repeat(64), 4000)
        assertTrue(ProfileAvatars.valid(media))
        assertFalse(ProfileAvatars.valid(media.copy(mime = "video/mp4")))
        assertFalse(ProfileAvatars.valid(media.copy(bytes = 100029)))
        val old = """{"id":"id","key":"k","name":"Alex","week":"w","time":0,"score":0}"""
        assertNull(kotlinx.serialization.json.Json.decodeFromString(League.Friend.serializer(), old).avatar)
    }
}
