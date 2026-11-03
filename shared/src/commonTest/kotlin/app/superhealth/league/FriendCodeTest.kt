package app.superhealth.league

import app.superhealth.platform.*
import kotlin.test.*
import kotlin.test.Test

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
}
