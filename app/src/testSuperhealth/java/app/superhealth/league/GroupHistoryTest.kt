package app.superhealth.league

import app.superhealth.training.domain.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GroupHistoryTest {
    private val now = 1800000000000L
    private val event = GroupHistory.Event("a".repeat(32), "b".repeat(124), "Alex", now,
        post = SocialPosts.Post("a".repeat(32), now, "Séance"))
    @Test fun deletionAndNewerEventsCannotBeUndoneByOldPages() {
        val deleted = event.copy(revision = now + 1, post = null, deleted = true)
        val merged = GroupHistory.merge(listOf(event), deleted)
        assertEquals(listOf(deleted), GroupHistory.merge(merged, event))
        assertEquals(merged, GroupHistory.merge(merged, deleted))
        assertTrue(GroupHistory.valid(deleted, now + 2))
        assertFalse(GroupHistory.valid(deleted.copy(post = event.post), now + 2))
    }
    @Test fun repliesAreBoundedAndCannotSmuggleAHealthCard() {
        val reply = event.copy(post = null, reply = GroupHistory.Reply("c".repeat(16), "d".repeat(32), "Bravo !", now))
        assertTrue(GroupHistory.valid(reply, now))
        assertFalse(GroupHistory.valid(reply.copy(reply = reply.reply!!.copy(text = "a".repeat(1001))), now))
        val payload = Json.encodeToString(GroupHistory.Event.serializer(), reply)
        assertTrue(runCatching { Json.decodeFromString(GroupHistory.Event.serializer(), payload.dropLast(1) + ",\"healthJournal\":[]}") }.isFailure)
    }
    @Test fun structuredPrCanCarryVideoWithoutLosingItsComparisonFields() {
        val record = GroupRecords.Record("bench", "Développé couché", "WEIGHT", 100.0, "kg", now)
        val media = SocialPosts.Media("b".repeat(64), RelayCrypto.newKey(), "video/mp4", "c".repeat(64), 1000)
        val post = event.post!!.copy(kind = "PR", record = record, media = media)
        assertTrue(SocialPosts.valid(post, now))
        assertEquals(record, Json.decodeFromString(SocialPosts.Post.serializer(), Json.encodeToString(SocialPosts.Post.serializer(), post)).record)
        assertFalse(GroupRecords.comparable(record, record.copy(metric = "E1RM")))
        assertFalse(GroupRecords.comparable(record, record.copy(exercise = "squat")))
        assertFalse(GroupRecords.valid(record.copy(unit = "lbs"), now))
        assertFalse(GroupRecords.valid(record.copy(value = Double.NaN), now))
    }
    @Test fun recentPrUsesHistoricalReferenceAndIgnoresWarmupsAndUndoneSets() {
        val exercise = Exercise("bench", "Bench", metrics = listOf("w", "r"))
        val db = ExerciseDb(ExerciseDbFile("test", listOf(exercise), emptyList(), emptyMap()))
        fun workout(id: String, ago: Int, weight: Double, type: SetType = SetType.STANDARD, done: Boolean = true) = Workout(id, id,
            now - ago.toLong() * 86400000, 60, listOf(SessionExercise(id, "bench", listOf(SetLog(id, type, weight, 5, done = done)))))
        val history = listOf(workout("reference", 150, 100.0), workout("below-reference", 10, 90.0),
            workout("warmup", 9, 200.0, SetType.WARMUP), workout("not-finished", 8, 300.0, done = false), workout("record", 7, 110.0))
        val records = GroupRecords.fromHistory(history, db, now, 90)
        assertEquals(110.0, records.first { it.metric == "WEIGHT" }.value, 0.0)
        assertTrue(records.all { it.achieved == now - 7L * 86400000 })
        assertTrue(GroupRecords.fromHistory(history, db, now, 3).isEmpty())
    }
    @Test fun groupSignatureIsBoundToItsDomainAndExpectedAuthor() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val text = Json.encodeToString(GroupHistory.Event.serializer(), event)
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update("SuperHealth group v1:".toByteArray()); update(text.toByteArray()); sign() }
        val signed = "SHG1.${b64.encodeToString(text.toByteArray())}.${b64.encodeToString(signature)}"
        assertEquals(text, League.verifySocial(signed, b64.encodeToString(pair.public.encoded)))
        assertNull(League.verifySocial(signed.replace("SHG1", "SH1"), b64.encodeToString(pair.public.encoded)))
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertNull(League.verifySocial(signed, b64.encodeToString(other.public.encoded)))
    }
    @Test fun recoverableIdentityRequiresMatchingKeysAndAnAuthenticLegacyEndorsement() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val old = generator.generateKeyPair(); val next = generator.generateKeyPair(); val b64 = Base64.getUrlEncoder().withoutPadding()
        val public = b64.encodeToString(next.public.encoded)
        val proof = Signature.getInstance("SHA256withECDSA").run { initSign(old.private); update(("SuperHealth identity successor v1:" + public).toByteArray()); sign() }
        val identity = GroupIdentity.Identity(b64.encodeToString(next.private.encoded), public, b64.encodeToString(old.public.encoded), b64.encodeToString(proof))
        GroupIdentity.validate(identity)
        assertTrue(runCatching { GroupIdentity.validate(identity.copy(private = b64.encodeToString(old.private.encoded))) }.isFailure)
        assertFalse(GroupIdentity.validEndorsement(identity.previous, b64.encodeToString(old.public.encoded), identity.endorsement))
    }
    @Test fun customReactionsSupportUnicodeSequencesAndRejectTextOrMultipleEmoji() {
        listOf("🎉", "💪🏽", "🇫🇷", "👨‍👩‍👧‍👦", "1️⃣", "🔥", "©️", "↔️", "⭐", "🫶").forEach { emoji ->
            val kind = SocialReactions.customKind(emoji)
            assertNotNull(emoji, kind); assertTrue(SocialReactions.validKind(kind!!)); assertEquals(emoji, SocialReactions.emoji(kind))
        }
        listOf("", "bonjour", "🔥🔥", "a🔥", "\u200D", "<script>", "🇫").forEach { assertNull(it, SocialReactions.customKind(it)) }
    }
    @Test fun feedKeepsFourYearOldPostsWithoutIncludingPostsPastFiveYears() {
        val old = event.post!!.copy(created = now - 4L * 365 * 86400000)
        assertEquals(listOf(old), SocialPosts.visible(listOf(old), now))
        assertTrue(SocialPosts.visible(listOf(old.copy(created = now - 6L * 365 * 86400000)), now).isEmpty())
    }

    @Test fun profileSnapshotClearsRemovedDecorAndRejectsOlderArchiveState() {
        val friend = League.Friend("a".repeat(16), "key", "Alex", "week", 10, 0, revision = 100,
            profileTitle = "xp_100", profileBadges = listOf("xp_100"), profileRevision = 150)
        val card = League.Card(k = "key", n = "Alex", w = "week", t = 20, s = 0, revision = 200, detailSnapshot = true)
        val cleared = League.mergeFriend(friend, card, 200)!!
        assertNull(cleared.profileTitle); assertTrue(cleared.profileBadges.isEmpty()); assertEquals(200L, cleared.profileRevision)
        val laterProfile = friend.copy(profileRevision = 300)
        assertEquals("xp_100", League.mergeFriend(laterProfile, card, 200)!!.profileTitle)
        assertEquals("xp_100", League.mergeFriend(friend, card.copy(detailSnapshot = false), 200)!!.profileTitle)
    }

}
