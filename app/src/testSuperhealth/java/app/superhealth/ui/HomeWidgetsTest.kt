package app.superhealth.ui

import app.superhealth.ui.widget.WidgetCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HomeWidgetsTest {
    @Test fun catalogueProvidersHaveManifestEntriesAndResizableHomeMetadata() {
        val entries = WidgetCatalog.entries
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
        assertEquals(entries.size, entries.map { it.receiver }.distinct().size)
        val manifest = File("src/superhealth/AndroidManifest.xml").readText()
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        entries.forEach { e ->
            assertTrue("Missing receiver ${e.key}", manifest.contains("android:name=\"${e.receiver.name}\""))
            assertTrue("Missing metadata ${e.key}", manifest.contains("@xml/widget_${e.key}"))
            val root = parser.parse(File("src/superhealth/res/xml/widget_${e.key}.xml")).documentElement
            assertEquals("horizontal|vertical", root.getAttribute("android:resizeMode"))
            assertEquals("home_screen", root.getAttribute("android:widgetCategory"))
        }
        assertEquals(6, entries.count { it.section == "Social" })
    }

    @Test fun socialWidgetsOnlyOpenSafeEntryScreens() {
        listOf("league", "progress", "quests", "friends", "social", "widgets").forEach { assertEquals(it, Routes.external(it)) }
        listOf("social/badges", "friends/delete/1", "stretch/edit", "widgets/../settings").forEach { assertNull(Routes.external(it)) }
    }
}
