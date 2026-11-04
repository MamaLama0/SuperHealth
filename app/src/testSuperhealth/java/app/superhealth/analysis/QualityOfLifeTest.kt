package app.superhealth.analysis

import app.superhealth.league.WeeklyRecap
import app.superhealth.nutrition.data.FoodLexicon
import app.superhealth.training.domain.*
import app.superhealth.wellbeing.Reminders
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Recherche multilingue, pesée depuis la notification et bilan de la semaine (3.5). */
class QualityOfLifeTest {
    @Test fun lexiconTranslatesBothWaysWithPluralsAndPhrases() {
        assertEquals("chicken breast", FoodLexicon.translate("Blanc de poulet", toEnglish = true))
        assertEquals("apple", FoodLexicon.translate("pommes", toEnglish = true))
        assertEquals("potato", FoodLexicon.translate("pomme de terre", toEnglish = true))
        assertEquals("corn", FoodLexicon.translate("maïs", toEnglish = true))
        assertEquals("rice cooked", FoodLexicon.translate("riz cuit", toEnglish = true))
        assertEquals("poulet", FoodLexicon.translate("Chickens", toEnglish = false))
        assertEquals("oeuf", FoodLexicon.translate("eggs", toEnglish = false))
        assertNull(FoodLexicon.translate("quinzoubli", toEnglish = true))
    }

    @Test fun weightTypedInANotificationIsParsedSafely() {
        assertEquals(72.4, Reminders.parseWeight("72,4", lb = false)!!, 1e-9)
        assertEquals(72.4, Reminders.parseWeight(" 72.4 kg", lb = false)!!, 1e-9)
        assertEquals(160 * 0.45359237, Reminders.parseWeight("160", lb = true)!!, 1e-9)
        assertNull(Reminders.parseWeight("abc", lb = false))
        assertNull(Reminders.parseWeight("7", lb = false))
        assertNull(Reminders.parseWeight(null, lb = false))
    }

    @Test fun recapWindowIsSundayEveningToMondayNoon() {
        val sunday = LocalDate.of(2026, 11, 8)
        val week = WeeklyRecap.weekFor(sunday)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), week)
        assertEquals(week, WeeklyRecap.weekFor(sunday.plusDays(1)))
        assertFalse(WeeklyRecap.due(sunday.atTime(16, 59), 0))
        assertTrue(WeeklyRecap.due(sunday.atTime(17, 0), 0))
        assertTrue(WeeklyRecap.due(sunday.plusDays(1).atTime(11, 59), 0))
        assertFalse(WeeklyRecap.due(sunday.plusDays(1).atTime(12, 0), 0))
        assertFalse(WeeklyRecap.due(sunday.atTime(20, 0), week))
        assertFalse(WeeklyRecap.due(LocalDateTime.of(2026, 11, 5, 18, 0), 0))
    }

    @Test fun recapRanksStrengthProgressAgainstThePreviousMonth() {
        val bench = Exercise("b", "Bench", region = "upper", metrics = listOf("w", "r"))
        val curl = Exercise("c", "Curl", region = "upper", metrics = listOf("w", "r"))
        val db = ExerciseDb(ExerciseDbFile("t", listOf(bench, curl), emptyList(), emptyMap()))
        val monday = LocalDate.of(2026, 11, 2)
        fun at(d: LocalDate) = d.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun w(d: LocalDate, ex: Exercise, kg: Double) = Workout("${ex.id}$d", "W", at(d), 3600,
            listOf(SessionExercise("e", ex.id, listOf(SetLog("s", weight = kg, reps = 8, rir = 2, done = true)))))
        val history = listOf(w(monday.minusDays(10), bench, 80.0), w(monday.minusDays(10), curl, 20.0),
            w(monday.plusDays(1), bench, 84.0), w(monday.plusDays(2), curl, 20.0))
        val lifts = WeeklyRecap.lifts(history, db, monday.toEpochDay())
        assertEquals(listOf("b", "c"), lifts.map { it.exerciseId })
        assertEquals(0.05, lifts[0].change!!, 1e-9)
        assertEquals(2, lifts[0].sessions.size)
    }

    @Test fun recapGroupsSportsAndStaysOffWithoutTrackedActivity() {
        fun act(kind: app.superhealth.bridge.Bridge.Kind, minutes: Long) =
            app.superhealth.bridge.Bridge.Activity("k$kind$minutes", kind, "", 0, minutes * 60_000, app.superhealth.bridge.Bridge.Source.IMPORT)
        val sports = WeeklyRecap.sports(listOf(act(app.superhealth.bridge.Bridge.Kind.BIKE, 90), act(app.superhealth.bridge.Bridge.Kind.SWIM, 30),
            act(app.superhealth.bridge.Bridge.Kind.BIKE, 45), act(app.superhealth.bridge.Bridge.Kind.STRENGTH, 60)))
        assertEquals(listOf(app.superhealth.bridge.Bridge.Kind.BIKE, app.superhealth.bridge.Bridge.Kind.SWIM), sports.map { it.kind })
        assertEquals(2, sports[0].count); assertEquals(135L, sports[0].minutes)
        val empty = WeeklyRecap.Data(0, 0, 0.0, List(6) { 0.0 }, null, emptyList(), emptyList(), 0, 8000.0, 420.0, null, List(7) { 10 }, 70, 800, 0, 0, false)
        assertTrue(empty.empty) // pas et sommeil seuls ne suffisent pas
        assertFalse(empty.copy(mindMinutes = 10).empty)
        assertEquals("XP", empty.copy(mindMinutes = 10).headline().last().label)
    }
}
