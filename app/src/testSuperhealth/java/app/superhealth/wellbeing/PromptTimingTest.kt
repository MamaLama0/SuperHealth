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
    private fun due(now: Long, steps: Double? = null, goal: Int = 8000, ends: List<Long> = emptyList(), s: SuperSettings = settings) =
        PromptTiming.moodDue(s, now, steps, goal, ends, zone)

    @Test fun morningWithoutCompletedGoalDoesNotPrompt() {
        assertFalse(due(at(7)))
        assertFalse(due(at(10), steps = 7999.0))
        assertFalse(due(at(10), steps = 8000.0, goal = 0))
        assertTrue(due(at(10), steps = 8000.0))
    }

    @Test fun completedActivityOnlyTriggersWithinThirtyMinutesToTwoHours() {
        val end = at(10)
        assertFalse(due(end - 1, ends = listOf(end)))
        assertFalse(due(end + 29 * 60_000, ends = listOf(end)))
        val chosen = end + PromptTiming.activityDelayMinutes(end, settings.promptSeed) * 60_000L
        assertFalse(due(chosen - 1, ends = listOf(end)))
        assertTrue(due(chosen, ends = listOf(end)))
        assertTrue(due(end + 120 * 60_000, ends = listOf(end)))
        assertFalse(due(end + 120 * 60_000 + 1, ends = listOf(end)))
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
