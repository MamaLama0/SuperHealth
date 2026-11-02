package app.superhealth.league

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class RelayCryptoTest {
    private val json = Json { encodeDefaults = true }
    private val path = "/v2/cards/0123456789abcdef/" + "a".repeat(64)
    private val revision = 1_793_620_000_123L
    private val payload = "SH1.signed-card.avec-données-é"
    private fun bytes(s: String) = Base64.getUrlDecoder().decode(s)
    private fun encoded(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun mutate(raw: String, field: String, value: JsonElement): String = JsonObject(json.parseToJsonElement(raw).jsonObject + (field to value)).toString()
    private fun rejected(action: () -> Unit) { assertTrue("Input should be rejected", runCatching(action).isFailure) }

    @Test fun generatedKeysContainExactly32RandomBytes() {
        val first = RelayCrypto.newKey()
        val second = RelayCrypto.newKey()
        assertEquals(32, bytes(first).size)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertTrue(RelayCrypto.validKey(first))
        assertNotEquals(first, second)
        listOf(0, 16, 31, 33, 64).forEach { size -> assertFalse(RelayCrypto.validKey(encoded(ByteArray(size)))) }
        assertFalse(RelayCrypto.validKey("bad%key"))
    }

    @Test fun aesGcmRoundTripPreservesTheExactUtf8PayloadAndEnvelopeVersion() {
        val key = RelayCrypto.newKey()
        val raw = RelayCrypto.encrypt(payload, key, path, revision)
        val envelope = json.decodeFromString(RelayCrypto.Envelope.serializer(), raw)
        assertEquals(2, envelope.v)
        assertEquals(revision, envelope.revision)
        assertEquals(12, bytes(envelope.nonce).size)
        assertEquals(payload, RelayCrypto.decrypt(raw, key, path))
        assertFalse(raw.contains(payload))
    }

    @Test fun encryptingTwiceUsesDifferentNoncesAndCiphertexts() {
        val key = RelayCrypto.newKey()
        val a = json.decodeFromString(RelayCrypto.Envelope.serializer(), RelayCrypto.encrypt(payload, key, path, revision))
        val b = json.decodeFromString(RelayCrypto.Envelope.serializer(), RelayCrypto.encrypt(payload, key, path, revision))
        assertNotEquals(a.nonce, b.nonce)
        assertNotEquals(a.ciphertext, b.ciphertext)
    }

    @Test fun anotherKeyOrAnotherMailboxCannotDecryptTheCard() {
        val key = RelayCrypto.newKey()
        val raw = RelayCrypto.encrypt(payload, key, path, revision)
        rejected { RelayCrypto.decrypt(raw, RelayCrypto.newKey(), path) }
        rejected { RelayCrypto.decrypt(raw, key, path + "/") }
        rejected { RelayCrypto.decrypt(raw, key, path.replace("0123456789abcdef", "fedcba9876543210")) }
    }

    @Test fun ciphertextTagAndNonceTamperingAreAuthenticated() {
        val key = RelayCrypto.newKey()
        val raw = RelayCrypto.encrypt(payload, key, path, revision)
        val envelope = json.decodeFromString(RelayCrypto.Envelope.serializer(), raw)
        for (index in listOf(0, bytes(envelope.ciphertext).lastIndex)) {
            val cipher = bytes(envelope.ciphertext).also { it[index] = (it[index].toInt() xor 1).toByte() }
            rejected { RelayCrypto.decrypt(mutate(raw, "ciphertext", JsonPrimitive(encoded(cipher))), key, path) }
        }
        val nonce = bytes(envelope.nonce).also { it[0] = (it[0].toInt() xor 1).toByte() }
        rejected { RelayCrypto.decrypt(mutate(raw, "nonce", JsonPrimitive(encoded(nonce))), key, path) }
    }

    @Test fun invalidFieldsAndMalformedOrTruncatedEnvelopesAreRejected() {
        val key = RelayCrypto.newKey()
        val raw = RelayCrypto.encrypt(payload, key, path, revision)
        val invalid = listOf(
            mutate(raw, "v", JsonPrimitive(1)),
            mutate(raw, "v", JsonPrimitive(3)),
            mutate(raw, "revision", JsonPrimitive(0)),
            mutate(raw, "revision", JsonPrimitive(-1)),
            mutate(raw, "nonce", JsonPrimitive(encoded(ByteArray(11)))),
            mutate(raw, "nonce", JsonPrimitive(encoded(ByteArray(13)))),
            mutate(raw, "nonce", JsonPrimitive("?")),
            mutate(raw, "ciphertext", JsonPrimitive(encoded(ByteArray(15)))),
            mutate(raw, "ciphertext", JsonPrimitive("?")),
            mutate(raw, "extra", JsonPrimitive("unexpected")),
            "{}", raw.dropLast(1), raw.replace("\"nonce\":", "\"missingNonce\":")
        )
        invalid.forEach { value -> rejected { RelayCrypto.decrypt(value, key, path) } }
        rejected { RelayCrypto.encrypt(payload, encoded(ByteArray(31)), path, revision) }
        rejected { RelayCrypto.decrypt(raw, encoded(ByteArray(31)), path) }
    }

    @Test fun byteLimitsApplyToUtf8RatherThanCharacterCount() {
        val key = RelayCrypto.newKey()
        val maximum = "é".repeat(League.MAX_CARD / 2)
        val raw = RelayCrypto.encrypt(maximum, key, path, revision)
        assertEquals(maximum, RelayCrypto.decrypt(raw, key, path))
        rejected { RelayCrypto.encrypt(maximum + "a", key, path, revision) }
        rejected { RelayCrypto.decrypt(" ".repeat(98305), key, path) }
    }

    @Test fun encryptRefusesRevisionsThatCannotBeAcceptedByItsOwnDecryptor() {
        val key = RelayCrypto.newKey()
        rejected { RelayCrypto.encrypt(payload, key, path, 0) }
        rejected { RelayCrypto.encrypt(payload, key, path, -1) }
    }
}
