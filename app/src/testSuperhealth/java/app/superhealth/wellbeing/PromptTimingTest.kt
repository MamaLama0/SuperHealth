package app.superhealth.wellbeing

import app.superhealth.SuperSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PromptTimingTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val date = LocalDate.of(2026, 11, 3)
    private val day = date.toEpochDay()
    private val settings = SuperSettings(promptSeed = 17423)
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun due(now: Long, stepsAt: Long? = null, ends: List<Long> = emptyList(), calm: List<Long> = emptyList(), dinner: Long? = null,
                    s: SuperSettings = settings) =
        PromptTiming.moodDue(s, now, PromptTiming.Signals(stepsAt, ends, calm, dinner), zone)

    @Test fun neverBeforeNoonWhateverHappened() {
        assertFalse(due(at(7)))
        assertFalse(due(at(10), stepsAt = at(8)))
        assertFalse(due(at(11, 59), ends = listOf(at(10, 30))))
        assertFalse(due(at(11, 30), calm = listOf(at(11))))
    }

    @Test fun stepGoalPromptsSomeTensOfMinutesLater() {
        val reached = at(14)
        val chosen = reached + PromptTiming.stepsDelayMinutes(reached, settings.promptSeed) * 60_000L
        assertFalse(due(reached, stepsAt = reached))
        assertFalse(due(chosen - 1, stepsAt = reached))
        assertTrue(due(chosen, stepsAt = reached))
        assertTrue((0L..200).all { PromptTiming.stepsDelayMinutes(reached + it * 77_000, settings.promptSeed) in 15..45 })
    }

    @Test fun stepGoalCrossingIsInterpolated() {
        val hourly = (0..23).map { at(it) to if (it in 9..15) 1500.0 else 0.0 }
        // 9 h–13 h : 7 500 pas, il en manque 500 sur les 1 500 de 14 h → 14 h 20
        assertEquals(at(14, 20), PromptTiming.stepGoalCrossing(hourly, 8000))
        assertNull(PromptTiming.stepGoalCrossing(hourly, 20000))
        assertNull(PromptTiming.stepGoalCrossing(hourly, 0))
    }

    @Test fun completedActivityOnlyTriggersWithinThirtyMinutesToTwoHours() {
        val end = at(14)
        assertFalse(due(end - 1, ends = listOf(end)))
        assertFalse(due(end + 29 * 60_000, ends = listOf(end)))
        val chosen = end + PromptTiming.activityDelayMinutes(end, settings.promptSeed) * 60_000L
        assertFalse(due(chosen - 1, ends = listOf(end)))
        assertTrue(due(chosen, ends = listOf(end)))
        assertTrue(due(end + 120 * 60_000, ends = listOf(end)))
        assertFalse(due(end + 120 * 60_000 + 1, ends = listOf(end)))
    }

    @Test fun shortOrIncidentalActivitiesDoNotCount() {
        assertFalse(PromptTiming.meaningful(walk = true, at(8), at(8, 12)))
        assertFalse(PromptTiming.meaningful(walk = false, at(8), at(8, 10)))
        assertTrue(PromptTiming.meaningful(walk = false, at(8), at(8, 15)))
        assertTrue(PromptTiming.meaningful(walk = true, at(8), at(8, 25)))
    }

    @Test fun calmSessionsAndDinnerAreGoodMoments() {
        val end = at(13)
        val chosen = end + PromptTiming.calmDelayMinutes(end, settings.promptSeed) * 60_000L
        assertFalse(due(chosen - 1, calm = listOf(end)))
        assertTrue(due(chosen, calm = listOf(end)))
        // un repas noté l'après-midi n'est pas le dîner ; noté après 18 h, il ouvre le moral tout de suite
        assertFalse(due(at(16, 30), dinner = at(16)))
        assertEquals(PromptTiming.Trigger.DINNER, PromptTiming.moodTrigger(settings, at(18, 1), PromptTiming.Signals(dinnerAt = at(18)), zone))
    }

    @Test fun threeSkipsInARowWaitForTheEvening() {
        val s = settings.copy(moodSkips = listOf(day - 1, day - 2, day - 3))
        val end = at(13)
        assertFalse(due(end + 120 * 60_000, ends = listOf(end), s = s))
    }

    @Test fun fallbackWorksWithoutHealthSourcesAndIsStableThroughoutTheDay() {
        val minute = PromptTiming.eveningMinute(day, settings.promptSeed)
        val chosen = at(minute / 60, minute % 60)
        assertFalse(due(at(16, 59)))
        assertFalse(due(chosen - 1))
        assertTrue(due(chosen))
        assertTrue(due(at(19)))
        assertTrue(due(at(23, 59)))
        assertEquals(minute, PromptTiming.eveningMinute(day, settings.promptSeed))
        val times = (0L..365).map { PromptTiming.eveningMinute(day + it, settings.promptSeed) }
        assertTrue(times.all { it in 17 * 60..19 * 60 })
        assertTrue(times.toSet().size > 1)
        assertTrue((0L..365).all { PromptTiming.activityDelayMinutes(at(10) + it * 86_400_000, settings.promptSeed) in 30..120 })
    }

    @Test fun shownPromptStaysUntilMidnightAndDoesNotCarryIntoTomorrow() {
        val shown = settings.copy(moodPromptShownDay = day)
        assertTrue(due(at(14), s = shown))
        val tomorrow = date.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertFalse(due(tomorrow, s = shown))
        assertFalse(due(at(0, 30), ends = listOf(at(0) - 60_000)))
    }

    @Test fun weighAndCheckInStartAtChosenLocalTimeAndRespectLegacyReminder() {
        val chosen = settings.copy(weighPromptMinute = 10 * 60 + 15, remindWeigh = 7 * 60)
        assertFalse(PromptTiming.weighDue(chosen, at(10, 14), zone))
        assertTrue(PromptTiming.weighDue(chosen, at(10, 15), zone))
        assertTrue(PromptTiming.weighDue(chosen, at(23, 59), zone))
        assertFalse(PromptTiming.weighDue(chosen, at(0), zone))
        assertEquals(7 * 60, PromptTiming.weighMinute(settings))
        val legacy = Json.decodeFromString(SuperSettings.serializer(), """{"remindWeigh":570}""")
        assertEquals(570, PromptTiming.weighMinute(legacy))
        assertEquals(7 * 60, PromptTiming.weighMinute(settings.copy(weighPromptMinute = 1440, remindWeigh = -1)))
        assertTrue(PromptTiming.weighDue(settings.copy(weighPromptMinute = 0), at(0), zone))
        assertTrue(PromptTiming.weighDue(settings.copy(weighPromptMinute = 1439), at(23, 59), zone))
    }
}
