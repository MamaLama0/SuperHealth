package app.superhealth.league

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CardBudgetTest {
    @Test fun longPostsRecordsAndReactionsCannotMakeTheSignedFeedUnreadable() {
        val now = System.currentTimeMillis()
        val posts = (1..12).map { n -> SocialPosts.Post(n.toString(16).padStart(32, '0'), now - n,
            text = "漢".repeat(500), title = "漢".repeat(100), detail = "漢".repeat(1500)) }
        val card = League.Card(k = "key", n = "name", w = "2026-W45", t = now / 1000, s = 600,
            posts = posts, metrics = (1..128).map { League.Comparison("record-$it".padEnd(160, 'x'), "漢".repeat(160), "kg", 40.0) },
            reactions = (1..64).map { SocialReactions.Reaction("a".repeat(16), it.toString(16).padStart(32, '0'), "LIKE", now) })
        val bounded = League.boundedCard(card)
        val bytes = Json { encodeDefaults = true }.encodeToString(League.Card.serializer(), bounded).toByteArray()
        assertTrue(((bytes.size + 2) / 3) * 4 + 256 <= League.MAX_CARD)
        assertEquals(posts.first(), bounded.posts.first())
        assertEquals(card.reactions, bounded.reactions)
        assertEquals(card.s, bounded.s)
        assertEquals(128, card.metrics.size)
        assertEquals(12, card.posts.size)
        val small = card.copy(posts = posts.take(1), metrics = emptyList(), reactions = emptyList())
        assertEquals(small, League.boundedCard(small))
    }
}
