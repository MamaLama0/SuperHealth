package app.superhealth.ui

import app.superhealth.DashCard
import app.superhealth.SuperSettings
import app.superhealth.nutrition.data.Net
import app.superhealth.ui.dashboard.Rings
import app.superhealth.withRings
import app.superhealth.CardSize
import app.superhealth.packRows
import app.superhealth.sizeOf
import app.superhealth.sizes
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShellTest {
    @Test fun ringsFractionAndOverflow() {
        assertEquals(0.5f, Rings.fraction(5000.0, 10000.0), 1e-6f)
        assertEquals(0f, Rings.fraction(null, 10000.0), 0f)
        assertEquals(0f, Rings.fraction(100.0, 0.0), 0f)
        assertEquals(0f, Rings.fraction(Double.NaN, 10.0), 0f)
        assertEquals(2f, Rings.fraction(50.0, 10.0), 0f) // plafonné à 2 tours
        val r = Rings.of(8000.0, 10000, 72.0, 1100.0, 2200.0)
        assertEquals(listOf(Rings.Kind.STEPS, Rings.Kind.ENERGY, Rings.Kind.KCAL), r.map { it.kind })
        assertEquals(listOf(0.8f, 0.72f, 0.5f), r.map { it.progress })
    }

    @Test fun ringsGoRightAboveVitalsOnce() {
        assertEquals(listOf(DashCard.CALORIES, DashCard.RINGS, DashCard.VITALS), withRings(listOf(DashCard.CALORIES, DashCard.VITALS)))
        assertEquals(listOf(DashCard.RINGS, DashCard.CALORIES), withRings(listOf(DashCard.CALORIES)))
        val l = listOf(DashCard.VITALS, DashCard.RINGS)
        assertEquals(l, withRings(l))
        assertTrue(DashCard.defaults.indexOf(DashCard.RINGS) == DashCard.defaults.indexOf(DashCard.VITALS) - 1)
    }

    @Test fun unknownDashboardCardsDoNotWipeSettings() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val s = json.decodeFromString(SuperSettings.serializer(),
            """{"onboarded":true,"dashboard":["CALORIES","FUTURE_CARD","STEPS","STEPS"]}""")
        assertTrue(s.onboarded)
        assertEquals(listOf(DashCard.CALORIES, DashCard.STEPS), s.dashboard)
        val back = json.decodeFromString(SuperSettings.serializer(), json.encodeToString(SuperSettings.serializer(), s))
        assertEquals(s, back)
    }

    @Test fun externalRoutesAreWhitelisted() {
        listOf("home", "log", "weight", "training:startnext", "training:session", "search/0/-1", "h/metric/STEPS").forEach {
            assertEquals(it, Routes.external(it))
        }
        listOf(null, "", "program/abc", "training:program/x", "h/metric/steps;x", "h/workout/1", "dash/edit", "search/0/5", "settings/../x").forEach {
            assertNull(it, Routes.external(it))
        }
    }

    @Test fun widgetsAndShortcutsOnlyUseWhitelistedRoutes() {
        // routes littérales des widgets et raccourcis de l'icône
        val res = File("src/superhealth/res/xml/shortcuts.xml").readText()
        Regex("""android:name="route" android:value="([^"]+)"""").findAll(res).forEach { assertEquals(it.groupValues[1], Routes.external(it.groupValues[1])) }
        listOf("home", "log", "h/metric/STEPS", "training:startnext", "training:startempty", "training:workout", "training:session").forEach {
            assertEquals(it, Routes.external(it))
        }
    }

    @Test fun apiKeyNeverLeavesInCleartext() {
        assertTrue(Net.canSendSecret("https://openrouter.ai/api/v1/chat/completions"))
        assertTrue(Net.canSendSecret("http://localhost:11434/v1/chat/completions"))
        assertTrue(Net.canSendSecret("http://192.168.1.20:11434/v1"))
        assertTrue(Net.canSendSecret("http://172.20.0.2/v1"))
        assertTrue(Net.canSendSecret("http://pc.local/v1"))
        assertFalse(Net.canSendSecret("http://api.example.com/v1"))
        assertFalse(Net.canSendSecret("http://172.32.0.1/v1"))
        assertFalse(Net.canSendSecret("http://192.168.1.300/v1"))
        assertFalse(Net.canSendSecret("ftp://localhost/x"))
        assertFalse(Net.canSendSecret("not a url"))
        assertFalse(Net.canSendSecret("http://localhost.evil.com/v1"))
    }

    @Test fun responsesAreCapped() {
        assertEquals("héllo", Net.readCapped("héllo".toByteArray().inputStream()))
        val big = ByteArray(100) { 'a'.code.toByte() }
        assertEquals(100, Net.readCapped(big.inputStream(), 100).length)
        assertTrue(runCatching { Net.readCapped(big.inputStream(), 99) }.exceptionOrNull() is java.io.IOException)
    }

    @Test fun gridPacksRowsOfSixColumns() {
        // S=2, M=3, L=6
        val rows = packRows(listOf(2, 2, 2, 3, 3, 6, 2, 3, 2)) { it }
        assertEquals(listOf(listOf(2, 2, 2), listOf(3, 3), listOf(6), listOf(2, 3), listOf(2)), rows)
        assertEquals(emptyList<List<Int>>(), packRows(emptyList<Int>()) { it })
        assertEquals(listOf(listOf(9), listOf(6)), packRows(listOf(9, 6)) { it }) // largeur bornée à 6 : une rangée chacun
    }

    @Test fun cardSizesFallBackToWhatTheCardAccepts() {
        val s = SuperSettings(dashSizes = mapOf("STEPS" to "S", "VITALS" to "S", "WEIGHT" to "BOGUS"))
        assertEquals(CardSize.S, s.sizeOf(DashCard.STEPS))
        assertEquals(CardSize.L, s.sizeOf(DashCard.VITALS))      // pas de bulle pour les constantes
        assertEquals(CardSize.M, s.sizeOf(DashCard.WEIGHT))      // valeur inconnue : taille par défaut
        assertEquals(CardSize.L, SuperSettings().sizeOf(DashCard.CALORIES))
        DashCard.entries.forEach { assertTrue(it.name, SuperSettings().sizeOf(it) in it.sizes) }
    }

    @Test fun ringKindsAreSanitized() {
        assertEquals(Rings.defaults, Rings.kinds(emptyList()))
        assertEquals(Rings.defaults, Rings.kinds(listOf("NOPE")))
        assertEquals(listOf(Rings.Kind.WATER, Rings.Kind.STEPS), Rings.kinds(listOf("WATER", "STEPS", "WATER")))
        assertEquals(Rings.MAX, Rings.kinds(Rings.Kind.entries.map { it.name }).size)
        val r = Rings.build(listOf(Rings.Kind.PROTEIN), { 75.0 }, { 150.0 })
        assertEquals(0.5f, r.single().progress, 1e-6f)
    }
}
