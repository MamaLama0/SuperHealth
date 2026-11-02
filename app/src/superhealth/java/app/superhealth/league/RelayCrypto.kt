package app.superhealth.league

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Le relais ne reçoit ni la clé de lecture ni le contenu signé en clair. */
object RelayCrypto {
    @Serializable data class Envelope(val v: Int = 2, val revision: Long, val nonce: String, val ciphertext: String)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private fun encode(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun decode(s: String) = Base64.getUrlDecoder().decode(s)
    fun newKey() = encode(SecureRandom().generateSeed(32))
    fun validKey(key: String) = runCatching { decode(key).size == 32 }.getOrDefault(false)
    fun encrypt(raw: String, key: String, path: String, revision: Long): String {
        require(revision > 0 && validKey(key) && raw.toByteArray().size <= League.MAX_CARD)
        val nonce = SecureRandom().generateSeed(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(("SuperHealth relay v2:" + path).toByteArray())
        return json.encodeToString(Envelope.serializer(), Envelope(revision = revision, nonce = encode(nonce), ciphertext = encode(cipher.doFinal(raw.toByteArray()))))
    }
    fun decrypt(raw: String, key: String, path: String): String {
        require(raw.toByteArray().size <= 98304 && validKey(key))
        val e = json.decodeFromString(Envelope.serializer(), raw)
        require(e.v == 2 && e.revision > 0)
        val nonce = decode(e.nonce); require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(("SuperHealth relay v2:" + path).toByteArray())
        return cipher.doFinal(decode(e.ciphertext)).also { require(it.size <= League.MAX_CARD) }.decodeToString()
    }
}
