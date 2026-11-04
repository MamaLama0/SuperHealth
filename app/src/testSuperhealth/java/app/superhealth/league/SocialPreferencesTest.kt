package app.superhealth.league

import app.superhealth.ui.Routes
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SocialPreferencesTest {
    private val author = "a".repeat(16)
    private val post = "b".repeat(32)
    private val reply = "c".repeat(32)

    @Test fun notificationRoutesAcceptOnlyReadOnlyBoundedTargets() {
        val route = SocialPreferences.route(author, post, reply)
        assertEquals(route, Routes.external(route))
        assertEquals("group/post/$author/$post", Routes.external(SocialPreferences.route(author, post)))
        listOf("group/post/../$post", "$route&delete=true", "group/post/$author/$post?reply=bad",
            "group/post/$author/$post/extra", "group/post/$author/$post?reply=$reply?reply=$reply").forEach {
            assertNull(it, Routes.external(it))
        }
    }

    @Test fun oldStateHasNoReadingRestrictionsAndPreferencesRoundTrip() {
        val legacy = Json.decodeFromString(League.State.serializer(), "{}")
        assertTrue(legacy.hiddenSocialPosts.isEmpty())
        assertTrue(legacy.mutedSocialFriends.isEmpty())
        assertTrue(legacy.silentSocialThreads.isEmpty())
        val state = legacy.copy(hiddenSocialPosts = setOf(SocialPreferences.thread(author, post)),
            mutedSocialFriends = setOf(author), silentSocialThreads = setOf(SocialPreferences.thread(author, post)))
        assertEquals(state, Json.decodeFromString(League.State.serializer(), Json.encodeToString(League.State.serializer(), state)))
    }

    @Test fun hiddenIndexRemovesClassicAndUnifiedFieldsWithoutRemovingPosts() {
        val index = League.IndexCard(750, 7, listOf(70, 80, 75))
        val sharedPost = SocialPosts.Post(post, System.currentTimeMillis(), "Bravo")
        val card = League.Card(k = "key", n = "Alex", w = "2026-W45", t = 1, s = 750,
            p = index.parts, ready = true, coverage = 7, unified = index, classicIndex = index, posts = listOf(sharedPost))
        assertEquals(card, League.sharedIndex(card, true))
        val private = League.sharedIndex(card, false)
        assertEquals(0, private.s)
        assertFalse(private.ready)
        assertEquals(0, private.coverage)
        assertTrue(private.p.isEmpty())
        assertNull(private.unified)
        assertNull(private.classicIndex)
        assertEquals(card.posts, private.posts)
    }

    @Test fun hiddenAndSilentChoicesAreScopedToTheAuthorAndPost() {
        val previous = League.state.value
        try {
            League.state.value = League.State(hiddenSocialPosts = setOf(SocialPreferences.thread(author, post)),
                silentSocialThreads = setOf(SocialPreferences.thread(author, post)), mutedSocialFriends = setOf(author))
            assertTrue(SocialPreferences.hidden(author, post))
            assertFalse(SocialPreferences.hidden("d".repeat(16), post))
            assertFalse(SocialPreferences.silent(author, "e".repeat(32)))
            assertTrue(SocialPreferences.muted(author))
            assertFalse(SocialPreferences.muted("d".repeat(16)))
        } finally { League.state.value = previous }
    }
}
