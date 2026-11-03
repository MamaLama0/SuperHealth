package app.superhealth.backup

import org.junit.Assert.*
import org.junit.Test

class DavOriginTest {
    @Test fun credentialsStayOnTheConfiguredHttpsOriginAndInsideTheBackupFolder() {
        val base = "https://backup.example/dav/SuperHealth/"
        assertEquals(base + "file.shbak", Remote.safeDavEntry(base, "file.shbak"))
        assertEquals(base + "file.shbak", Remote.safeDavEntry(base, "/dav/SuperHealth/file.shbak"))
        listOf("http://backup.example/dav/SuperHealth/file.shbak", "https://evil.example/file.shbak", "//evil.example/file.shbak",
            "../private.shbak", "https://backup.example:444/dav/SuperHealth/file.shbak", "https://user:password@backup.example/dav/SuperHealth/a.shbak").forEach {
            assertNull(it, Remote.safeDavEntry(base, it))
        }
    }
}
