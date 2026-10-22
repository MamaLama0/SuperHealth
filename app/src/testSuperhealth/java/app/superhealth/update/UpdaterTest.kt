package app.superhealth.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versions() {
        assertTrue(Updater.newer("v1.1", "1.0"))
        assertTrue(Updater.newer("1.10", "1.9"))
        assertTrue(Updater.newer("1.1.1", "1.1"))
        assertFalse(Updater.newer("1.1", "1.1.0"))
        assertFalse(Updater.newer("v1.0", "1.1"))
    }

    @Test fun apkChoice() {
        val a = listOf(Updater.Asset("SuperHealth-1.1-debug.apk", "d", "d", 90), Updater.Asset("SuperHealth-1.1.apk", "p", "p", 50),
            Updater.Asset("notes.txt", "n", "n", 1))
        assertEquals("SuperHealth-1.1.apk", Updater.pickApk(a)?.name)
    }
}
