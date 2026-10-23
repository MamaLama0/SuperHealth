package app.superhealth.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement des sauvegardes : AES-256-GCM, clé tirée du code de récupération par PBKDF2-HMAC-SHA256 avec un sel propre à
 * chaque fichier. Le code (160 bits) est tiré de l'identifiant du téléphone ([deviceCode]) : la même installation, ou une
 * réinstallation sur le même téléphone, retrouve le même code. Sans lui, le fichier est illisible, y compris pour l'hébergeur.
 *
 * Format `.shbak` : « SHBK » · version (1) · itérations (u32 BE) · sel (16) · nonce (12) · données chiffrées + étiquette GCM (16).
 * L'en-tête entier est authentifié (données associées) : le modifier rend le fichier illisible au lieu de l'affaiblir.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'H'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    const val ITERATIONS = 120_000
    private const val SALT = 16
    private const val NONCE = 12
    private const val HEADER = 4 + 1 + 4 + SALT + NONCE

    /** Alphabet de Crockford : sans I, L, O ni U, pour un code facile à recopier. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    class WrongCode : Exception("code de récupération incorrect ou fichier altéré")

    /** Nouveau code aléatoire : 32 caractères (160 bits), en 8 groupes de 4 (repli si le téléphone n'a pas d'identifiant). */
    fun newCode(random: SecureRandom = SecureRandom()): String = format(ByteArray(20).also(random::nextBytes))

    /**
     * Code déterministe tiré de [seed] (ANDROID_ID : propre au téléphone, à l'utilisateur Android et à la clé de signature,
     * inchangé après une réinstallation, renouvelé par une remise à zéro) : HMAC-SHA256, 160 premiers bits.
     */
    fun deviceCode(seed: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("SuperHealth backup code v1".toByteArray(), "HmacSHA256"))
        return format(mac.doFinal(seed.trim().lowercase().toByteArray()).copyOf(20))
    }

    /** 20 octets → 32 caractères de 5 bits, en 8 groupes de 4. */
    private fun format(bytes: ByteArray): String {
        val chars = CharArray(32) { i ->
            val bit = i * 5
            val v = ((bytes[bit / 8].toInt() and 0xff) shl 8 or (bytes.getOrElse(bit / 8 + 1) { 0 }.toInt() and 0xff)) shr (11 - bit % 8)
            ALPHABET[v and 31]
        }
        return chars.toList().chunked(4).joinToString("-") { it.joinToString("") }
    }

    /** Code tel que saisi : majuscules, sans tirets ni espaces, confusions courantes corrigées (O→0, I/L→1). */
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }
        .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }.joinToString("")

    fun isValidCode(code: String): Boolean = normalize(code).let { c -> c.length == 32 && c.all { it in ALPHABET } }

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size > HEADER && (0 until 4).all { bytes[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray, code: String, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + int(iterations) + salt + nonce
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(code, salt, iterations), GCMParameterSpec(128, nonce))
        c.updateAAD(header)
        return header + c.doFinal(plain)
    }

    fun decrypt(file: ByteArray, code: String): ByteArray {
        require(isEncrypted(file)) { "pas une sauvegarde chiffrée" }
        require(file[4] == VERSION) { "version de sauvegarde inconnue" }
        val iterations = ((file[5].toInt() and 0xff) shl 24) or ((file[6].toInt() and 0xff) shl 16) or
            ((file[7].toInt() and 0xff) shl 8) or (file[8].toInt() and 0xff)
        require(iterations in 1..10_000_000) { "en-tête invalide" }
        val salt = file.copyOfRange(9, 9 + SALT)
        val nonce = file.copyOfRange(9 + SALT, HEADER)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(code, salt, iterations), GCMParameterSpec(128, nonce))
        c.updateAAD(file, 0, HEADER)
        return try { c.doFinal(file, HEADER, file.size - HEADER) } catch (e: javax.crypto.AEADBadTagException) { throw WrongCode() }
    }

    private fun key(code: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(normalize(code).toCharArray(), salt, iterations, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
