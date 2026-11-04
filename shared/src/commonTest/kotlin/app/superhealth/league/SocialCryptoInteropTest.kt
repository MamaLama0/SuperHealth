package app.superhealth.league
import app.superhealth.platform.*
import app.superhealth.social.P256Encoding
import kotlin.test.*
/** Fixed P-256 scalars 1 and 2 verify that Security.framework returns the raw shared x-coordinate. */
class SocialCryptoInteropTest {
 private fun hex(value:String)=value.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
 @Test fun rawEcdhMatchesThePortableP256Vector() {
  val prefix=hex("3059301306072a8648ce3d020106082a8648ce3d030107034200")
  val point=hex("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");val public=prefix+point
  val apple=point+ByteArray(31)+byteArrayOf(1)
  val identity=normalizeSigningIdentity(SigningIdentity(P256Encoding.pkcs8(apple,public),public))
  val peer=prefix+hex("047cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc4766997807775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1")
  assertContentEquals(hex("7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978"),identitySharedSecret(identity.privateKey,peer))
 }
 @Test fun canonicalRecoveryKeysPreserveTheSignature() {
  val pair=generateSigningIdentity();val canonical=P256Encoding.pkcs8(pair.privateKey,pair.publicKey)
  val recovered=normalizeSigningIdentity(SigningIdentity(canonical,pair.publicKey))
  val message="SuperHealth identity recovery interop".toByteArray()
  assertTrue(verifyIdentity(pair.publicKey,message,signIdentity(recovered.privateKey,message)))
  assertFalse(verifyIdentity(pair.publicKey,"altered".toByteArray(),signIdentity(recovered.privateKey,message)))
 }
}
