package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class SocialPostsTest {
    private val now = 1800000000000L
    private fun post() = SocialPosts.Post("a".repeat(32), now, "Bravo !", "TEXT")
    @Test fun oldCardsRemainReadableAndOnlyExplicitlySharedPostsPersist() {
        val old = """{"id":"friend","key":"key","name":"Alex","week":"w","time":1,"score":2}"""
        assertTrue(Json.decodeFromString(League.Friend.serializer(), old).posts.isEmpty())
        val state = League.State(posts = listOf(post()))
        assertEquals(state, Json.decodeFromString(League.State.serializer(), Json.encodeToString(League.State.serializer(), state)))
    }
    @Test fun rejectsMalformedAndOversizedMediaBeforeAnyDownload() {
        val media = SocialPosts.Media("b".repeat(64), RelayCrypto.newKey(), "video/mp4", "c".repeat(64), 500)
        assertTrue(SocialPosts.valid(post().copy(kind = "VIDEO", media = media), now))
        assertFalse(SocialPosts.valid(post().copy(kind = "VIDEO"), now))
        assertFalse(SocialPosts.valid(post().copy(media = media.copy(id = "../../file")), now))
        assertFalse(SocialPosts.valid(post().copy(media = media.copy(mime = "text/html")), now))
        assertFalse(SocialPosts.valid(post().copy(media = media.copy(bytes = Int.MAX_VALUE)), now))
        assertFalse(SocialPosts.valid(post().copy(text = "a".repeat(501)), now))
        assertFalse(SocialPosts.valid(post().copy(created = now + 300001), now))
    }
    @Test fun feedRetentionDoesNotReviveExpiredPostsAndLimitsCardSize() {
        val expired = post().copy(created = now - 35L * 86400000)
        val posts = (0..30).map { post().copy(id = it.toString(16).padStart(32, '0'), created = now - it) } + expired
        assertEquals(12, SocialPosts.visible(posts, now).size)
        assertEquals(now, SocialPosts.visible(posts, now).first().created)
        assertFalse(expired in SocialPosts.visible(posts, now))
    }
}
