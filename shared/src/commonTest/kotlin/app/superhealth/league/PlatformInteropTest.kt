package app.superhealth.league

import app.superhealth.platform.*
import kotlin.test.*

/** Vecteurs produits par les fournisseurs EC/AES de Java, identiques au protocole Android. */
class PlatformInteropTest {
    private fun decode(value: String)=Base64.getUrlDecoder().decode(value)
    private val message="SuperHealth Android ↔ iOS 3.0".encodeToByteArray()
    @Test fun verifiesAndroidP256SignatureAndRejectsTampering() {
        val public=decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEoUd4WUMfESxzXGk7EEayTnAW6nG7ppM3I2WkhGbH2kVsf02g5TDrTQIQxuJYrTMjroxRBq5B0FR4kJi7ABHxrg")
        val sig=decode("MEUCIQCP6zZgDFxRzzEZrzXl1vwqIdCgdCJXgpCLN9E-J1l1TwIgH0WxIvSld7eV9L-s-0DFPjtYYCW4xb3BQut7Ij5bUMY")
        assertTrue(verifyIdentity(public,message,sig))
        assertFalse(verifyIdentity(public,message+byteArrayOf(1),sig))
        assertFalse(verifyIdentity(public,message,sig.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}))
    }
    @Test fun nativeIdentityUsesPortableSpkiPublicKeyAndDerSignature() {
        val identity=generateSigningIdentity()
        assertEquals(91,identity.publicKey.size)
        val sig=signIdentity(identity.privateKey,message)
        assertTrue(sig.size in 68..72)
        assertEquals(0x30,sig[0].toInt())
        assertTrue(verifyIdentity(identity.publicKey,message,sig))
        assertFalse(verifyIdentity(generateSigningIdentity().publicKey,message,sig))
    }
    @Test fun decryptsAndroidAesGcmCiphertextWithItsAuthenticatedMailbox() {
        val key=ByteArray(32){it.toByte()};val nonce=ByteArray(12){(it+50).toByte()}
        val aad="SuperHealth relay v2:/v2/cards/test/mailbox".encodeToByteArray()
        assertContentEquals(message,AesGcm(key).decrypt(nonce,decode("lS6fpbd05IIBc4KmGoObignaf6FryzPLuFVYuIQdq1FqD_A_nEPHOCUSqxsAPjw"),aad))
        assertFails {AesGcm(key).decrypt(nonce,decode("lS6fpbd05IIBc4KmGoObignaf6FryzPLuFVYuIQdq1FqD_A_nEPHOCUSqxsAPjw"),aad+byteArrayOf(1))}
    }
    @Test fun endpointValidationRetainsCapabilityBoundaries() {
        val good="https://relay.example/v2/cards/"+"a".repeat(64)+"/"+"b".repeat(64)
        assertTrue(Relay.validEndpoint(good))
        listOf(good+"?",good+"#",good+"/",good.replace("https:","http:"),good.replace("relay.example","user:password@relay.example")).forEach {assertFalse(Relay.validEndpoint(it),it)}
        assertTrue(Relay.validBase("https://relay.example"))
        assertFalse(Relay.validBase("https://relay.example/path"))
    }
    @Test fun isoWeekHandlesCalendarYearBoundaries() {
        assertEquals("2020-W53",League.weekKey(LocalDate.parse("2021-01-01")))
        assertEquals("2020-W53",League.weekKey(LocalDate.parse("2020-12-31")))
        assertEquals("2021-W01",League.weekKey(LocalDate.parse("2021-01-04")))
    }
}
