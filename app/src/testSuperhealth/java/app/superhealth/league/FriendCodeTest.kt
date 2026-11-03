package app.superhealth.league
import org.junit.Assert.*
import org.junit.Test

class FriendCodeTest {
    @Test fun codeIsShortCaseInsensitiveAndUnambiguous() {
        val code = FriendCode.create()
        assertEquals(12, code.length)
        assertFalse(code.contains('I')); assertFalse(code.contains('O')); assertFalse(code.contains('0')); assertFalse(code.contains('1'))
        assertEquals(code, FriendCode.normalize(FriendCode.display(code).lowercase()))
        assertNull(FriendCode.normalize("short"))
        assertNull(FriendCode.normalize("ABCD-EFGH-0000"))
    }
    @Test fun lookupAndEncryptionAreIndependentAndChangingOneCharacterChangesBoth() {
        val code = "ABCD-EFGH-2345"
        assertEquals(FriendCode.path(code), FriendCode.path(code.lowercase()))
        assertTrue(FriendCode.path(code).matches(Regex("/v2/invites/[a-f0-9]{64}")))
        assertTrue(RelayCrypto.validKey(FriendCode.key(code)))
        assertNotEquals(FriendCode.path(code), FriendCode.path("ABCD-EFGH-2346"))
        assertNotEquals(FriendCode.key(code), FriendCode.key("ABCD-EFGH-2346"))
        val raw = "test signed invite"
        val encrypted = RelayCrypto.encrypt(raw, FriendCode.key(code), FriendCode.path(code), 1)
        assertEquals(raw, RelayCrypto.decrypt(encrypted, FriendCode.key(code), FriendCode.path(code)))
        assertTrue(runCatching { RelayCrypto.decrypt(encrypted, FriendCode.key("ABCD-EFGH-2346"), FriendCode.path(code)) }.isFailure)
    }
    @Test fun wholeSharedMessageCanBePastedInEitherLanguage() {
        assertEquals("ABCDEFGH2345", FriendCode.fromText("Ajoute-moi sur SuperHealth : ABCD-EFGH-2345\nSocial → Ajouter un ami · valable 24 h"))
        assertEquals("ABCDEFGH2345", FriendCode.fromText("Add me on SuperHealth: abcd-efgh-2345\nSocial → Add a friend · valid for 24 hours"))
        assertEquals("ABCDEFGH2345", FriendCode.fromText("ABCD EFGH 2345"))
        assertNull(FriendCode.fromText("ABCD-EFGH-2345 ou ABCD-EFGH-6789"))
        assertNull(FriendCode.fromText("ABCD-EFGH-23456"))
        assertNull(FriendCode.fromText("ABCD-EFGH-0000"))
        assertNull(FriendCode.fromText("A".repeat(4097)))
    }
    @Test fun customServerIsReadOnlyFromAnExplicitServerLine() {
        val url = "https://friends.example.org"
        assertEquals(url, FriendCode.serverFromText("ABCD-EFGH-2345\nServeur : $url"))
        assertEquals(url, FriendCode.serverFromText("ABCD-EFGH-2345\nServer: $url"))
        assertNull(FriendCode.serverFromText("ABCD-EFGH-2345\n$url"))
        assertNull(FriendCode.serverFromText("Serveur : http://friends.example.org"))
        assertNull(FriendCode.serverFromText("Server: $url\nServer: https://other.example.org"))
    }
}
