package app.superhealth.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Chaque texte passé à tr()/trf() a sa traduction anglaise, sauf les libellés identiques dans les deux langues. */
class I18nTest {
    private val same = setOf(
        "Photo", "%1\$s cm", "%1\$s g", "%1\$s (%2\$s g)", "100 g", "Sodium", "= %1\$s g", "Macros", "×", "Style", "%1\$s, %2\$s cm",
        "%1\$s h %2\$s min", "%1\$s s", "× %1\$s", "Type", "Isolation", "Muscles", "%1\$s–%2\$s reps", "RIR %1\$s", "Technique", "Notes",
        " · cycle %1\$s", "%1\$s  %2\$s × %3\$s%4\$s", "Cycle %1\$s", "%1\$s × %2\$s-%3\$s · RIR %4\$s", "Info", "Note", " · RIR %1\$s",
        "%1\$s reps", "Active", "%1\$s %% × %2\$s", "%1\$s · %2\$s %%", "Date", "RIR", "Cycle %1\$s/%2\$s", "≈ %1\$s min", "%1\$s × %2\$s",
    )

    @Test fun everyLabelIsTranslated() {
        val dict = SuperStrings.map.keys + Strings11.map.keys + Strings12.map.keys + Strings13.map.keys + app.superhealth.nutrition.ui.EnStrings.map.keys + app.superhealth.training.ui.EnStrings.map.keys
        val lit = Regex("""\btrf?\(\s*"([^"\\]*+(?:\\.[^"\\]*+)*+)"|"([^"\\]*+(?:\\.[^"\\]*+)*+)"\.tr\(\)""")
        val missing = File("src/superhealth/java").walk().filter { it.extension == "kt" && !it.name.endsWith("Strings.kt") }.flatMap { f ->
            lit.findAll(f.readText()).map { m ->
                // littéral source → valeur Kotlin (\$ → $)
                (m.groups[1] ?: m.groups[2])!!.value.replace("\\$", "$").replace("\\\"", "\"") to f.name
            }
        }.filter { (k, _) -> k.any { it.isLetter() } && k !in dict && k !in same }.toList()
        assertTrue("Sans traduction : ${missing.distinct().joinToString("\n")}", missing.isEmpty())
    }

    @Test fun dashboardCardLabelsAreTranslated() {
        val dict = SuperStrings.map.keys + Strings11.map.keys + Strings12.map.keys + Strings13.map.keys + app.superhealth.nutrition.ui.EnStrings.map.keys + app.superhealth.training.ui.EnStrings.map.keys
        val missing = app.superhealth.DashCard.entries.map { it.label }.filter { it !in dict } +
            app.superhealth.ui.dashboard.Rings.Kind.entries.map { it.label }.filter { it !in dict && it != "Body Battery" }
        assertTrue("Sans traduction : $missing", missing.isEmpty())
    }
}
