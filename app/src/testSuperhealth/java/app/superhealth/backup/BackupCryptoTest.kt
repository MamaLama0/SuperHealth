package app.superhealth.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class BackupCryptoTest {
    private val data = "nutrition, musculation, montre".repeat(500).toByteArray()

    @Test fun roundTrip() {
        val code = BackupCrypto.newCode()
        val enc = BackupCrypto.encrypt(data, code, iterations = 1000)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertArrayEquals(data, BackupCrypto.decrypt(enc, code))
        // le code se recopie sans tirets, en minuscules, avec O pour 0
        assertArrayEquals(data, BackupCrypto.decrypt(enc, code.lowercase().replace("-", " ").replace('0', 'o')))
    }

    @Test(expected = BackupCrypto.WrongCode::class) fun wrongCode() {
        val enc = BackupCrypto.encrypt(data, BackupCrypto.newCode(), iterations = 1000)
        BackupCrypto.decrypt(enc, BackupCrypto.newCode())
    }

    @Test(expected = BackupCrypto.WrongCode::class) fun tamperedHeader() {
        val code = BackupCrypto.newCode()
        val enc = BackupCrypto.encrypt(data, code, iterations = 1000)
        enc[12] = (enc[12] + 1).toByte() // sel modifié : authentifié, donc refusé
        BackupCrypto.decrypt(enc, code)
    }

    @Test fun codeShape() {
        val code = BackupCrypto.newCode(SecureRandom())
        assertEquals(39, code.length)
        assertTrue(BackupCrypto.isValidCode(code))
        assertFalse(BackupCrypto.isValidCode("ABCD-1234"))
        assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004 zip".toByteArray()))
    }
}

class PropfindTest {
    @Test fun nextcloudListing() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/remote.php/dav/files/bad/SuperHealth/</d:href>
            <d:propstat><d:prop/></d:propstat></d:response><d:response><d:href>/remote.php/dav/files/bad/SuperHealth/superhealth-2026-10-22.shbak</d:href>
            <d:propstat><d:prop><d:getcontentlength>12345</d:getcontentlength></d:prop></d:propstat></d:response></d:multistatus>"""
        assertEquals(listOf("/remote.php/dav/files/bad/SuperHealth/superhealth-2026-10-22.shbak" to 12345L), Remote.parsePropfind(xml))
    }
}
