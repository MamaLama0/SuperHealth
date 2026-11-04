package app.superhealth.ui.health

import app.superhealth.bridge.Bridge
import app.superhealth.ui.Lang
import org.junit.Assert.*
import org.junit.Test

class ActivityHistoryTest {
    private fun activity(id: String, kind: Bridge.Kind, start: Long, title: String = id) =
        Bridge.Activity(id, kind, title, start, start + 60000, Bridge.Source.WATCH)

    @Test fun runningFilterFindsOlderRunsBeyondTheFiveLatestOtherSports() {
        val run = activity("run", Bridge.Kind.RUN, 1)
        val cycling = (2L..9L).map { activity("bike-$it", Bridge.Kind.BIKE, it) }
        assertEquals(listOf(run), filterActivityHistory(cycling + run, Bridge.Kind.RUN))
        assertEquals(9, filterActivityHistory(cycling + run, null).size)
    }

    @Test fun searchCombinesSportAndNameAndKeepsNewestFirst() {
        val older = activity("older", Bridge.Kind.RUN, 1, "Parc")
        val newer = activity("newer", Bridge.Kind.RUN, 3, "Tour du PARC")
        val bike = activity("bike", Bridge.Kind.BIKE, 4, "Parc")
        val items = listOf(older, bike, newer)
        assertEquals(listOf(newer, older), filterActivityHistory(items, Bridge.Kind.RUN, " parc "))
        assertTrue(filterActivityHistory(items, Bridge.Kind.SWIM, "parc").isEmpty())
        assertEquals(listOf(bike, newer, older), filterActivityHistory(items, null, "  "))
    }

    @Test fun searchFindsTranslatedSportsAndSources() {
        val original = if (Lang.english) "EN" else "FR"
        try {
            val run = activity("r", Bridge.Kind.RUN, 1, "Morning")
            Lang.apply("FR")
            assertEquals(listOf(run), filterActivityHistory(listOf(run), null, "course"))
            Lang.apply("EN")
            assertEquals(listOf(run), filterActivityHistory(listOf(run), null, "run"))
            assertEquals(listOf(run), filterActivityHistory(listOf(run), null, "watch"))
        } finally { Lang.apply(original) }
    }

    @Test fun importedRunUsesTheSameMovingTimeForPaceAsItsDetail() {
        val run = app.superhealth.bridge.RunImport.Run(-1, "Run", "RUN", "GPX", 0, 1800000, 1700.0, distanceM = 5000.0)
        val a = Bridge.Activity("import", Bridge.Kind.RUN, run.name, run.start, run.end, Bridge.Source.IMPORT, run = run)
        assertEquals("5:40 /km", pace(a))
        assertEquals("6:00 /km", pace(a.copy(run = run.copy(activeSec = Double.NaN))))
        assertEquals("6:00 /km", pace(a.copy(run = run.copy(activeSec = 0.0))))
    }

    @Test fun treadmillRemainsRunningWithoutGps() {
        assertEquals(Bridge.Kind.RUN, Bridge.kindOf("TREADMILL"))
        assertEquals(Bridge.Kind.RUN, Bridge.kindOf("INDOOR_RUNNING"))
        assertEquals(Bridge.Kind.WALK, Bridge.kindOf("WALKING"))
        assertEquals(Bridge.Kind.BIKE, Bridge.kindOf("INDOOR_CYCLING"))
    }
}
