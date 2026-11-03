package app.superhealth.league

import app.superhealth.platform.*

import kotlin.test.*
import kotlin.test.Test
import kotlinx.serialization.json.Json

class WeeklyQuestsTest {
    private val monday = LocalDate.of(2026, 11, 2).toEpochDay()
    private val tracks = Progression.Track.entries.toSet()
    private val all = WeeklyQuests.Focus.entries.toSet()
    @Test fun focusesAndWeaknessSelectRelevantPrimaryWithOptionalOtherDomain() {
        val evidence = WeeklyQuests.Evidence(mapOf(WeeklyQuests.Metric.FOOD to 1, WeeklyQuests.Metric.STRENGTH to 6,
            WeeklyQuests.Metric.STRENGTH_REVIEW to 6, WeeklyQuests.Metric.RUN to 4, WeeklyQuests.Metric.STEPS to 14, WeeklyQuests.Metric.MIND to 4), strengthGoal = 3)
        val w = WeeklyQuests.update(null, monday, tracks, emptyMap(), all, evidence)
        assertEquals(WeeklyQuests.Metric.FOOD, w.quests.first().metric)
        assertEquals(WeeklyQuests.Reason.FOOD_GAP, w.quests.first().reason)
        assertEquals(1, w.quests.size) // no artificial side quest when other habits are already consistent
        val onlyRun = WeeklyQuests.update(null, monday, tracks, emptyMap(), setOf(WeeklyQuests.Focus.RUN), evidence.copy(previous14 = emptyMap()))
        assertEquals(listOf(WeeklyQuests.Metric.RUN), onlyRun.quests.map { it.metric })
        assertEquals(1, onlyRun.quests.first().target)
    }
    @Test fun secondaryUsesDifferentFocusAndPreviousPrimaryGetsVarietyTieBreak() {
        val evidence = WeeklyQuests.Evidence()
        val focuses = setOf(WeeklyQuests.Focus.NUTRITION, WeeklyQuests.Focus.STRENGTH)
        val previous = WeeklyQuests.Week(monday - 7, listOf(WeeklyQuests.Quest(WeeklyQuests.Metric.FOOD, 2)))
        val w = WeeklyQuests.update(null, monday, tracks, emptyMap(), focuses, evidence, listOf(previous))
        assertEquals(WeeklyQuests.Metric.STRENGTH, w.quests.first().metric)
        assertEquals(2, w.quests.size)
        assertTrue(w.quests[1].secondary)
        assertNotEquals(w.quests[0].metric.focus, w.quests[1].metric.focus)
        assertEquals(80, w.quests[0].reward)
        assertEquals(30, w.quests[1].reward)
    }
    @Test fun plateauEncouragesUsefulFeedbackInsteadOfAForcedPersonalRecord() {
        assertFalse(WeeklyQuests.plateau(listOf(100.0, 100.0)))
        assertTrue(WeeklyQuests.plateau(listOf(100.0, 99.5, 100.0)))
        assertFalse(WeeklyQuests.plateau(listOf(100.0, 105.0, 110.0)))
        val w = WeeklyQuests.update(null, monday, tracks, emptyMap(), setOf(WeeklyQuests.Focus.STRENGTH),
            WeeklyQuests.Evidence(mapOf(WeeklyQuests.Metric.STRENGTH to 6), strengthGoal = 3, plateau = true))
        assertEquals(WeeklyQuests.Metric.STRENGTH_REVIEW, w.quests.single().metric)
        assertEquals(WeeklyQuests.Reason.PLATEAU, w.quests.single().reason)
        assertEquals(2, w.quests.single().target)
    }
    @Test fun planIsFrozenAndSundayStartRemainsAchievable() {
        val w = WeeklyQuests.update(null, monday + 6, tracks, emptyMap(), all, WeeklyQuests.Evidence())
        assertTrue(w.quests.all { it.target == 1 })
        val unchanged = WeeklyQuests.update(w, monday + 6, tracks, emptyMap(), setOf(WeeklyQuests.Focus.RUN), WeeklyQuests.Evidence(strengthGoal = 7))
        assertEquals(w, unchanged)
    }
    @Test fun rewardsAreClaimedOncePersistAndDoNotLeakIntoNextWeek() {
        val w = WeeklyQuests.update(null, monday, tracks, WeeklyQuests.Metric.entries.associateWith { 7 }, all, WeeklyQuests.Evidence())
        val metric = w.quests.first().metric
        val once = WeeklyQuests.claim(w, metric, monday)
        assertTrue(once.quests.first().claimed)
        assertEquals(once, WeeklyQuests.claim(once, metric, monday))
        assertEquals(w, WeeklyQuests.claim(w, metric, monday + 7))
        val restored = Json.decodeFromString(WeeklyQuests.Week.serializer(), Json.encodeToString(WeeklyQuests.Week.serializer(), once))
        assertEquals(once, restored)
        assertTrue(WeeklyQuests.update(restored, monday + 1, tracks, emptyMap(), all, WeeklyQuests.Evidence()).quests.first().complete)
        val next = WeeklyQuests.update(restored, monday + 7, tracks, emptyMap(), all, WeeklyQuests.Evidence())
        assertTrue(next.quests.none { it.claimed || it.complete })
        assertEquals(next, WeeklyQuests.claim(next, next.quests.first().metric, monday + 7))
    }
    @Test fun unconfiguredOrUnavailableFocusCannotGenerateAnUnrelatedQuest() {
        assertTrue(WeeklyQuests.update(null, monday, tracks, emptyMap(), emptySet(), WeeklyQuests.Evidence()).quests.isEmpty())
        assertTrue(WeeklyQuests.update(null, monday, setOf(Progression.Track.FOOD), emptyMap(), setOf(WeeklyQuests.Focus.RUN), WeeklyQuests.Evidence()).quests.isEmpty())
    }
    @Test fun missingHealthDataIsAStarterGoalRatherThanAnInventedFailure() {
        val w = WeeklyQuests.update(null, monday, tracks, emptyMap(), setOf(WeeklyQuests.Focus.DAILY),
            WeeklyQuests.Evidence(mapOf(WeeklyQuests.Metric.FOOD to 10, WeeklyQuests.Metric.MIND to 4), healthData = false))
        assertEquals(WeeklyQuests.Metric.STEPS, w.quests.single().metric)
        assertEquals(WeeklyQuests.Reason.START, w.quests.single().reason)
    }
}
