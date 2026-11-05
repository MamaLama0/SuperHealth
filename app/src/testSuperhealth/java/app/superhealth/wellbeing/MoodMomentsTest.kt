package app.superhealth.wellbeing

import app.superhealth.SuperSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MoodMomentsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val start = LocalDate.of(2026, 9, 1).toEpochDay()
    private fun at(day: Long, hour: Int, minute: Int = 0) = LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** [days] jours : bandeau à [shownHour] ; répondu en 5 min si [answers], sinon replié. */
    private fun history(days: LongProgression, shownHour: Int, answers: Boolean): Triple<List<MoodMoments.Shown>, Map<Long, Journal.Entry>, List<Long>> {
        val shown = days.map { MoodMoments.Shown(it, at(it, shownHour)) }
        val journal = if (answers) days.associateWith { Journal.Entry(4, "", at(it, shownHour, 5)) } else emptyMap()
        val skips = if (answers) emptyList() else days.toList()
        return Triple(shown, journal, skips)
    }

    private fun model(vararg parts: Triple<List<MoodMoments.Shown>, Map<Long, Journal.Entry>, List<Long>>, today: Long): MoodMoments.Model {
        val obs = parts.flatMap { (s, j, k) -> MoodMoments.observations(s, j, k, zone) }
        return MoodMoments.model(obs, today)
    }

    @Test fun learnsTheHourWhenThePersonAnswers() {
        // soirs à 20 h répondus, midis à 13 h repliés, en alternance
        val evenings = history((start until start + 28 step 2), 20, true)
        val lunches = history((start + 1 until start + 28 step 2), 13, false)
        val m = model(evenings, lunches, today = start + 28)
        assertEquals(20, m.best())
        assertTrue(m.welcome(20))
        assertFalse(m.welcome(13))
    }

    @Test fun keepsSomeVarianceToAvoidOverfitting() {
        val m = model(history(start until start + 42, 20, true), today = start + 42)
        val minutes = (0L until 365).map { MoodMoments.dailyMinute(m, start + 42 + it, 17423) }
        val hours = minutes.map { it / 60 }
        assertTrue(minutes.all { it in 12 * 60..22 * 60 + 30 })
        // la bonne heure domine…
        assertTrue(hours.count { it in 19..20 } > 365 * 0.5)
        // …mais l'app continue d'essayer d'autres moments
        assertTrue(hours.count { it !in 19..21 } > 365 * 0.08)
        assertTrue(hours.toSet().size >= 6)
        // et l'heure ne tombe pas pile au même instant chaque jour
        assertTrue(minutes.filter { it / 60 == 20 }.toSet().size > 20)
    }

    @Test fun dailyMinuteIsStableWithinADay() {
        val m = model(history(start until start + 20, 18, true), today = start + 20)
        assertEquals(MoodMoments.dailyMinute(m, start + 20, 5), MoodMoments.dailyMinute(m, start + 20, 5))
    }

    @Test fun adaptsWhenTheHabitChanges() {
        // six semaines à 20 h, puis 10 jours où seul midi répond
        val old = history(start until start + 42, 20, true)
        val ignored = history(start + 42 until start + 52, 20, false)
        val now = history(start + 42 until start + 52, 13, true).let { (s, j, k) ->
            Triple(emptyList<MoodMoments.Shown>(), j, k)
        }
        val m = model(old, ignored, now, today = start + 52)
        assertEquals(13, m.best())
    }

    @Test fun spontaneousEntriesCountAsGoodMoments() {
        val journal = (start until start + 10).associateWith { Journal.Entry(3, "", at(it, 15, 10)) }
        val obs = MoodMoments.observations(emptyList(), journal, emptyList(), zone)
        assertTrue(obs.all { it.hour == 15 && it.reward == 1.0 })
        assertEquals(15, MoodMoments.model(obs, start + 10).best())
    }

    @Test fun notesAndDelaysShapeTheReward() {
        val d = start
        val s = listOf(MoodMoments.Shown(d, at(d, 18)))
        fun reward(e: Journal.Entry?) = MoodMoments.observations(s, e?.let { mapOf(d to it) } ?: emptyMap(), emptyList(), zone).single().reward
        assertEquals(1.0, reward(Journal.Entry(4, "", at(d, 18, 10))), 1e-9)
        assertEquals(0.75, reward(Journal.Entry(4, "bonne journée", at(d, 21))), 1e-9)
        assertEquals(0.5, reward(Journal.Entry(4, "", at(d, 21))), 1e-9)
        assertEquals(0.0, reward(null), 1e-9)
    }

    @Test fun startsCloseToTheOldEveningWindow() {
        val m = MoodMoments.model(emptyList(), start)
        assertTrue(m.best() in 17..19)
        assertTrue((12..21).all { m.welcome(it) })
    }

    @Test fun modelDrivesTheEveningPromptAndGatesEvents() {
        val m = model(history(start until start + 28 step 2, 20, true), history(start + 1 until start + 28 step 2, 13, false), today = start + 28)
        val settings = SuperSettings(promptSeed = 99)
        val day = start + 28
        val minute = MoodMoments.dailyMinute(m, day, settings.promptSeed)
        assertNull(PromptTiming.moodTrigger(settings, at(day, minute / 60, minute % 60) - 60_000, model = m, zone = zone))
        assertEquals(PromptTiming.Trigger.EVENING, PromptTiming.moodTrigger(settings, at(day, minute / 60, minute % 60), model = m, zone = zone))
        // une activité finie à midi : à 13 h 30, heure où la personne replie le bandeau, rien
        val end = at(day, 12)
        val chosen = end + PromptTiming.activityDelayMinutes(end, settings.promptSeed) * 60_000L
        val signals = PromptTiming.Signals(activityEnds = listOf(end))
        val t = PromptTiming.moodTrigger(settings, chosen, signals, zone, model = m)
        if (java.time.Instant.ofEpochMilli(chosen).atZone(zone).hour == 13) assertNotEquals(PromptTiming.Trigger.ACTIVITY, t)
    }
}
