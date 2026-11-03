package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test

class SocialReactionsTest {
    private val now = 1800000000000L
    private val author = "a".repeat(16)
    private val post = "b".repeat(32)
    @Test fun voteCanChangeOrBeRemovedButNeverCountsTwiceForOnePerson() {
        val liked = SocialReactions.toggle(emptyList(), author, post, "LIKE", now)
        val fire = SocialReactions.toggle(liked, author, post, "FIRE", now + 1)
        assertEquals(1, fire.size)
        assertEquals("FIRE", fire.single().kind)
        assertTrue(SocialReactions.toggle(fire, author, post, "FIRE", now + 2).isEmpty())
    }
    @Test fun commonFriendsSeeTheSameSignedVotesAndUnknownPeopleAreNotCounted() {
        val reaction = SocialReactions.Reaction(author, post, "LIKE", now)
        val common = League.Friend("c", "common-key", "Common", "w", 1, 0, reactions = listOf(reaction))
        val unrelatedPost = reaction.copy(post = "d".repeat(32), kind = "DISLIKE")
        val other = common.copy(id = "d", key = "other-key", reactions = listOf(unrelatedPost))
        assertEquals(mapOf("LIKE" to 2), SocialReactions.counts(listOf(reaction), listOf(common, common, other), author, post, now))
        assertEquals(mapOf("LIKE" to 1), SocialReactions.counts(emptyList(), listOf(common), author, post, now))
        assertTrue(SocialReactions.counts(emptyList(), emptyList(), author, post, now).isEmpty())
    }
    @Test fun staleMalformedAndDuplicateReactionsAreExcludedAndLegacyCardsStillLoad() {
        val reaction = SocialReactions.Reaction(author, post, "HEART", now)
        assertEquals(listOf(reaction), SocialReactions.visible(listOf(reaction, reaction, reaction.copy(post = "bad"),
            reaction.copy(at = now - 35L * 86400000), reaction.copy(kind = "script")), now))
        val json = kotlinx.serialization.json.Json
        val old = """{"id":"id","key":"k","name":"Alex","week":"w","time":0,"score":0}"""
        assertTrue(json.decodeFromString(League.Friend.serializer(), old).reactions.isEmpty())
    }
}
