package app.superhealth.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versions() {
        assertTrue(Updater.newer("v1.1", "1.0"))
        // numérotation décimale : 1.4 → 1.41 (mineure) → 1.5 (majeure) → 1.51 → 1.6
        assertTrue(Updater.newer("v1.41", "1.4"))
        assertTrue(Updater.newer("v1.5", "1.41"))
        assertFalse(Updater.newer("v1.41", "1.5"))
        assertTrue(Updater.newer("1.6", "1.51"))
        assertTrue(Updater.newer("2.0", "1.99"))
        assertFalse(Updater.newer("1.5", "1.50"))
        assertTrue(Updater.newer("1.1.1", "1.1"))
        assertFalse(Updater.newer("1.1", "1.1.0"))
        assertFalse(Updater.newer("v1.0", "1.1"))
    }

    @Test fun displayedVersion() {
        assertEquals("1.6", Updater.display("v1.60"))
        assertEquals("1.51", Updater.display("v1.51"))
        assertEquals("2.0", Updater.display("v2.00"))
        assertEquals("1.1.0", Updater.display("1.1.0"))
        // étiquette v1.60 : vue comme plus récente par le comparateur décimal et par l'ancien (composante par composante)
        assertTrue(Updater.newer(Updater.display("v1.60"), "1.5"))
        assertFalse(Updater.newer(Updater.display("v1.60"), "1.6"))
    }

    @Test fun apkChoice() {
        val a = listOf(Updater.Asset("SuperHealth-1.1-debug.apk", "d", "d", 90), Updater.Asset("SuperHealth-1.1.apk", "p", "p", 50),
            Updater.Asset("notes.txt", "n", "n", 1))
        assertEquals("SuperHealth-1.1.apk", Updater.pickApk(a)?.name)
    }
}
