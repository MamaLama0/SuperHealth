package app.superhealth.league

import app.superhealth.training.domain.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SharedActivityTest {
    private val now = 1800000000000L
    private val id = "a".repeat(32)
    @Test fun routesRoundTripAndPreserveEndpointsWhenSampled() {
        val points = (0..1200).map { 48.85 + it * .00001 to 2.35 + it * .00002 }
        val encoded = SharedActivity.Route.encode(points)!!
        val decoded = SharedActivity.Route.decode(encoded)!!
        assertEquals(400, decoded.size)
        assertEquals(points.first().first, decoded.first().first, .00001)
        assertEquals(points.last().second, decoded.last().second, .00001)
        assertTrue(encoded.length <= 6000)
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), SharedActivity.Route.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@"))
    }
    @Test fun malformedOrUnboundedRoutesAreRejectedBeforeRendering() {
        listOf("", "!", "_", "~".repeat(6001), "~~~~~~~~~~~~?", "??").forEach { assertNull(it, SharedActivity.Route.decode(it)) }
        assertNull(SharedActivity.Route.encode(listOf(Double.NaN to 2.0, 100.0 to 2.0)))
        val tooMany = "??".repeat(401)
        assertNull(SharedActivity.Route.decode(tooMany))
    }
    @Test fun strengthUsesCompletedWorkingSetsAndNeverExportsPrivateNotes() {
        val db = ExerciseDb(ExerciseDbFile("test", listOf(Exercise("bench", "Bench", metrics = listOf("w", "r"), primaryGroups = listOf("chest"))), emptyList(), emptyMap()))
        val w = Workout("private-id", "Session", now, 1800, listOf(SessionExercise("entry", "bench", listOf(
            SetLog("warmup", SetType.WARMUP, 50.0, 10, done = true), SetLog("work", weight = 100.0, reps = 5, done = true),
            SetLog("unfinished", weight = 200.0, reps = 5, done = false)), note = "PRIVATE NOTE")), gymName = "PRIVATE GYM", note = "PRIVATE NOTE")
        val share = SharedActivity.strength(w, db)
        assertEquals(500.0, share.volumeKg, .0); assertEquals(1, share.setCount); assertEquals(5, share.repCount)
        assertEquals(listOf("chest"), share.exercises.single().muscles)
        val json = Json.encodeToString(SharedActivity.Strength.serializer(), share)
        assertFalse(json.contains("PRIVATE")); assertFalse(json.contains("private-id"))
        assertTrue(SharedActivity.valid(share, now))
    }
    @Test fun structuredPostsRemainInsideEncryptedRelayBudget() {
        val exercise = SharedActivity.Exercise("漢".repeat(80), listOf("chest", "frontDelts", "triceps"), List(8) { SharedActivity.Set(120.5, 12) })
        val strength = SharedActivity.Strength(now, 3600, 50000.0, 200, 1000, 30, List(12) { exercise })
        val original = SocialPosts.Post(id, now, "漢".repeat(500), "WORKOUT", "漢".repeat(100), "漢".repeat(1500), workout = strength)
        val bounded = SocialPosts.bounded(original)
        assertTrue(SocialPosts.valid(bounded, now))
        assertEquals(strength.setCount, bounded.workout!!.setCount)
        assertEquals(original.text, bounded.text)
        val event = GroupHistory.Event(id, "b".repeat(512), "漢".repeat(24), now, post = bounded)
        val raw = Json { encodeDefaults = true }.encodeToString(GroupHistory.Event.serializer(), event)
        val signedSize = (raw.toByteArray().size + 2) / 3 * 4 + 150
        assertTrue((signedSize + 28 + 2) / 3 * 4 + 128 < 24 * 1024)
    }
    @Test fun sharedRouteAndDetailsCanBeOmittedIndependently() {
        val route = SharedActivity.Route.encode(listOf(48.0 to 2.0, 48.1 to 2.1))!!
        val a = SharedActivity.Outdoor("RUN", now, 1800.0, 5000.0, avgHr = 150, route = route, speed = listOf(2.0, 3.0), heart = listOf(140, 150))
        assertTrue(SharedActivity.valid(a, now))
        val minimal = a.withoutDetails().copy(route = null)
        assertNull(minimal.route); assertNull(minimal.avgHr); assertTrue(minimal.heart.isEmpty())
        assertEquals(a.metres!!, minimal.metres!!, 0.0); assertEquals(a.seconds, minimal.seconds, 0.0)
        assertEquals(route, a.withoutDetails().route)
        assertFalse(SharedActivity.valid(a.copy(seconds = Double.NaN), now))
        assertFalse(SharedActivity.valid(a.copy(route = "invalid"), now))
    }
    @Test fun recordProgressIsRealAndValidatesItsHistory() {
        val record = GroupRecords.Record("bench", "Bench", "WEIGHT", 100.0, "kg", now, 80.0, listOf(60.0, 80.0, 100.0), listOf("chest"))
        assertTrue(GroupRecords.valid(record, now))
        assertFalse(GroupRecords.valid(record.copy(previous = 110.0), now))
        assertFalse(GroupRecords.valid(record.copy(trail = listOf(110.0, 100.0)), now))
        val post = SocialPosts.Post(id, now, kind = "PR", record = record)
        assertTrue(SocialPosts.valid(post, now))
        assertEquals(record, Json.decodeFromString(SocialPosts.Post.serializer(), Json.encodeToString(SocialPosts.Post.serializer(), post)).record)
    }
    @Test fun previousPostsCanShowTheirKnownFiguresWithoutInventingARoute() {
        val old = SocialPosts.Post(id, now, kind = "RUN", detail = "5,20 km · 30 min")
        val a = SharedActivity.legacyActivity(old, now)!!
        assertEquals(5200.0, a.metres!!, .0); assertEquals(1800.0, a.seconds, .0); assertNull(a.route)
        val workout = SocialPosts.Post(id, now, kind = "WORKOUT", detail = "40 min\nBench · 100.0 kg × 5 reps / 80.0 kg × 8 reps")
        val w = SharedActivity.legacyStrength(workout, now)!!
        assertEquals(1140.0, w.volumeKg, .0); assertEquals(13, w.repCount); assertEquals(2, w.setCount)
        assertNull(SharedActivity.legacyStrength(workout.copy(detail = "40 min\ntruncated record")))
    }

    @Test fun discoveryCardsDoNotDuplicateRouteAndDetailedSamples() {
        val a = SharedActivity.Outdoor("RUN", now, 1500.0, 5000.0, route = SharedActivity.Route.encode(listOf(48.0 to 2.0, 48.1 to 2.1)))
        val post = SocialPosts.Post(id, now, "A run", "RUN", "Run", "5 km · 25 min", activity = a)
        val compact = SocialPosts.compact(post)
        assertNull(compact.activity); assertEquals(post.detail, compact.detail); assertNotNull(post.activity!!.route)
        assertTrue(SocialPosts.valid(compact, now))
    }

    @Test fun hidingDetailsAlsoRemovesTheLegacyTextCopy() {
        val w = SharedActivity.Strength(now, 1200, 500.0, 1, 5, 1,
            listOf(SharedActivity.Exercise("Bench", listOf("chest"), listOf(SharedActivity.Set(100.0, 5)))))
        val draft = SocialPosts.Draft("Workout", "20 min\nBench · 100.0 kg × 5 reps", "WORKOUT", workout = w)
        val hidden = draft.shared(route = false, details = false)
        val json = Json.encodeToString(SocialPosts.Draft.serializer(), hidden)
        assertFalse(json.contains("Bench")); assertFalse(json.contains("100.0")); assertFalse(json.contains("chest"))
        assertEquals("20 min", hidden.detail); assertEquals(w.volumeKg, hidden.workout!!.volumeKg, .0)
        assertEquals(draft, draft.shared(route = true, details = true))
    }
    @Test fun workoutSharingOnlyOffersRecordsFromThatSession() {
        val db = ExerciseDb(ExerciseDbFile("test", listOf(Exercise("bench", "Bench", metrics = listOf("w", "r"))), emptyList(), emptyMap()))
        fun session(id: String, time: Long, weight: Double) = Workout(id, id, time, 1200,
            listOf(SessionExercise(id, "bench", listOf(SetLog(id, weight = weight, reps = 5, done = true)))))
        val baseline = session("before", now - 86400000, 80.0)
        val chosen = session("chosen", now, 100.0)
        val later = session("later", now + 86400000, 120.0)
        val records = GroupRecords.forWorkout(chosen, listOf(baseline, chosen, later), db)
        assertTrue(records.isNotEmpty()); assertTrue(records.all { it.achieved == chosen.startTime })
        assertEquals(100.0, records.first { it.metric == "WEIGHT" }.value, .0)
        assertEquals(80.0, records.first { it.metric == "WEIGHT" }.previous!!, .0)
        val noPr = session("no-pr", now, 70.0)
        assertTrue(GroupRecords.forWorkout(noPr, listOf(baseline, noPr, later), db).isEmpty())
    }

}
