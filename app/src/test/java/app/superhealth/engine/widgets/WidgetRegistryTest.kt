package app.superhealth.engine.widgets

import app.superhealth.engine.test.TestBase
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRegistryTest : TestBase() {
    @Test
    fun testNoDuplicateNames() {
        val duplicates = WidgetRegistry.all()
            .groupBy { context.getString(it.name) }
            .filterValues { it.size > 1 }
            .mapValues { (_, widgets) -> widgets.map { it.id } }

        assertTrue("Widgets with duplicate names: $duplicates", duplicates.isEmpty())
    }
}
