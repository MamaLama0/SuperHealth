package app.superhealth.coach
import org.junit.Assert.*
import org.junit.Test

class SettingsHelpTest {
    @Test fun findsDailyMacrosAndAccentedQueries() {
        assertEquals("nutrition_program", SettingsHelp.search("calories lundi macros par jour").first().key)
        assertTrue(SettingsHelp.search("ÉCHAUFFEMENT pourcentage").take(3).any { it.key == "training_warmups" })
        assertTrue(SettingsHelp.search("serveur personnalisé").any { it.key == "league_settings" })
        assertTrue(SettingsHelp.search("notifications").any { it.key == "reminders" })
        assertEquals("activities", CoachTools.SCREENS["activities"])
    }
    @Test fun eachSearchResultHasAnAllowedActionAndNoArbitraryRouteIsAccepted() {
        assertEquals(SettingsHelp.destinations.size, SettingsHelp.destinations.map { it.key }.distinct().size)
        SettingsHelp.destinations.forEach { assertEquals(it.route, CoachTools.SCREENS[it.key]) }
        assertFalse(CoachTools.SCREENS.values.any { it.startsWith("http") })
    }
}
