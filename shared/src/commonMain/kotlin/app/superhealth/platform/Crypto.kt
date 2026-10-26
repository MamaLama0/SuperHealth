package app.superhealth.platform

import kotlin.random.Random

/*
 * Cryptographie des sauvegardes, en Kotlin pur pour lire et écrire les mêmes fichiers .shbak que l'app Android :
 * SHA-256, HMAC-SHA256, PBKDF2-HMAC-SHA256 et AES-256-GCM (NIST SP 800-38D). Vérifiés contre javax.crypto (tests desktop).
 */

/** Aléa cryptographique du système (SecRandomCopyBytes sur iOS, SecureRandom sur la JVM). */
expect fun secureRandomBytes(n: Int): ByteArray

class SecureRandom {
    fun nextBytes(b: ByteArray) { secureRandomBytes(b.size).copyInto(b) }
}

object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun digest(data: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLen = data.size.toLong() * 8
        val padLen = ((data.size + 9 + 63) / 64) * 64
        val msg = data.copyOf(padLen)
        msg[data.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (off in 0 until padLen step 64) {
            for (t in 0 until 16) {
                val j = off + t * 4
                w[t] = ((msg[j].toInt() and 0xff) shl 24) or ((msg[j + 1].toInt() and 0xff) shl 16) or
                    ((msg[j + 2].toInt() and 0xff) shl 8) or (msg[j + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val k = (if (key.size > 64) digest(key) else key).copyOf(64)
        val ipad = ByteArray(64) { (k[it].toInt() xor 0x36).toByte() }
        val opad = ByteArray(64) { (k[it].toInt() xor 0x5c).toByte() }
        return digest(opad + digest(ipad + data))
    }

    /** PBKDF2-HMAC-SHA256 ; le mot de passe est encodé en UTF-8 comme PBEKeySpec de la JVM (ASCII ici). */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
        val k = (if (password.size > 64) digest(password) else password).copyOf(64)
        val ipad = ByteArray(64) { (k[it].toInt() xor 0x36).toByte() }
        val opad = ByteArray(64) { (k[it].toInt() xor 0x5c).toByte() }
        fun mac(d: ByteArray) = digest(opad + digest(ipad + d))
        val out = ByteArray(keyBytes)
        var block = 1
        var pos = 0
        while (pos < keyBytes) {
            var u = mac(salt + byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            t.copyInto(out, pos, 0, minOf(32, keyBytes - pos))
            pos += 32; block++
        }
        return out
    }
}

/** AES (chiffrement de bloc seul : GCM n'utilise que le sens direct). */
class Aes(key: ByteArray) {
    private val nr = key.size / 4 + 6
    private val w = expand(key)

    companion object {
        private val SBOX = IntArray(256)
        init {
            var p = 1; var q = 1
            do {
                p = p xor ((p shl 1) and 0xff) xor (if (p and 0x80 != 0) 0x1b else 0)
                q = q xor (q shl 1); q = q xor (q shl 2); q = q xor (q shl 4); q = q and 0xff
                if (q and 0x80 != 0) q = q xor 0x09
                val x = q xor (q.rotl8(1)) xor (q.rotl8(2)) xor (q.rotl8(3)) xor (q.rotl8(4))
                SBOX[p] = (x xor 0x63) and 0xff
            } while (p != 1)
            SBOX[0] = 0x63
        }
        private fun Int.rotl8(s: Int) = ((this shl s) or (this ushr (8 - s))) and 0xff
        private fun xt(a: Int) = ((a shl 1) xor (if (a and 0x80 != 0) 0x1b else 0)) and 0xff
    }

    private fun expand(key: ByteArray): IntArray {
        val nk = key.size / 4
        val words = IntArray(4 * (nr + 1))
        for (i in 0 until nk) words[i] = ((key[4 * i].toInt() and 0xff) shl 24) or ((key[4 * i + 1].toInt() and 0xff) shl 16) or
            ((key[4 * i + 2].toInt() and 0xff) shl 8) or (key[4 * i + 3].toInt() and 0xff)
        var rcon = 1
        for (i in nk until words.size) {
            var t = words[i - 1]
            if (i % nk == 0) {
                t = sub(t.rotateLeft(8)) xor (rcon shl 24)
                rcon = xt(rcon)
            } else if (nk > 6 && i % nk == 4) t = sub(t)
            words[i] = words[i - nk] xor t
        }
        return words
    }

    private fun sub(x: Int) = (SBOX[(x ushr 24) and 0xff] shl 24) or (SBOX[(x ushr 16) and 0xff] shl 16) or
        (SBOX[(x ushr 8) and 0xff] shl 8) or SBOX[x and 0xff]

    fun encryptBlock(input: ByteArray): ByteArray {
        val s = IntArray(16) { input[it].toInt() and 0xff }
        fun addKey(r: Int) { for (c in 0 until 4) { val k = w[r * 4 + c]; for (i in 0 until 4) s[c * 4 + i] = s[c * 4 + i] xor ((k ushr (24 - 8 * i)) and 0xff) } }
        addKey(0)
        for (round in 1..nr) {
            for (i in 0 until 16) s[i] = SBOX[s[i]]
            // ShiftRows (état en colonnes : s[c*4 + ligne])
            val t = s.copyOf()
            for (c in 0 until 4) for (r in 0 until 4) s[c * 4 + r] = t[((c + r) % 4) * 4 + r]
            if (round != nr) for (c in 0 until 4) {
                val a0 = s[c * 4]; val a1 = s[c * 4 + 1]; val a2 = s[c * 4 + 2]; val a3 = s[c * 4 + 3]
                val all = a0 xor a1 xor a2 xor a3
                s[c * 4] = a0 xor all xor xt(a0 xor a1)
                s[c * 4 + 1] = a1 xor all xor xt(a1 xor a2)
                s[c * 4 + 2] = a2 xor all xor xt(a2 xor a3)
                s[c * 4 + 3] = a3 xor all xor xt(a3 xor a0)
            }
            addKey(round)
        }
        return ByteArray(16) { s[it].toByte() }
    }
}

/** AES-GCM, étiquette de 128 bits, nonce de 96 bits (le seul format écrit par l'app). */
class AesGcm(key: ByteArray) {
    private val aes = Aes(key)
    private val h = aes.encryptBlock(ByteArray(16))

    class BadTag : Exception("étiquette GCM invalide")

    private fun mul(x: ByteArray, y: ByteArray): ByteArray {
        val z = ByteArray(16)
        val v = y.copyOf()
        for (i in 0 until 128) {
            if ((x[i / 8].toInt() shr (7 - i % 8)) and 1 == 1) for (j in 0 until 16) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            val lsb = v[15].toInt() and 1
            for (j in 15 downTo 1) v[j] = (((v[j].toInt() and 0xff) ushr 1) or ((v[j - 1].toInt() and 1) shl 7)).toByte()
            v[0] = ((v[0].toInt() and 0xff) ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xe1).toByte()
        }
        return z
    }

    private fun ghash(aad: ByteArray, c: ByteArray): ByteArray {
        var y = ByteArray(16)
        fun absorb(data: ByteArray) {
            var i = 0
            while (i < data.size) {
                val block = data.copyOfRange(i, minOf(i + 16, data.size)).copyOf(16)
                for (j in 0 until 16) y[j] = (y[j].toInt() xor block[j].toInt()).toByte()
                y = mul(y, h); i += 16
            }
        }
        absorb(aad); absorb(c)
        val len = ByteArray(16)
        val a = aad.size.toLong() * 8; val b = c.size.toLong() * 8
        for (i in 0 until 8) { len[7 - i] = (a ushr (8 * i)).toByte(); len[15 - i] = (b ushr (8 * i)).toByte() }
        for (j in 0 until 16) y[j] = (y[j].toInt() xor len[j].toInt()).toByte()
        return mul(y, h)
    }

    private fun ctr(nonce: ByteArray, input: ByteArray): ByteArray {
        val counter = nonce.copyOf(16).also { it[15] = 1 }
        val out = ByteArray(input.size)
        var i = 0
        while (i < input.size) {
            inc(counter)
            val ks = aes.encryptBlock(counter)
            for (j in 0 until minOf(16, input.size - i)) out[i + j] = (input[i + j].toInt() xor ks[j].toInt()).toByte()
            i += 16
        }
        return out
    }

    private fun inc(c: ByteArray) {
        for (i in 15 downTo 12) { c[i] = (c[i] + 1).toByte(); if (c[i].toInt() != 0) break }
    }

    private fun tag(nonce: ByteArray, aad: ByteArray, c: ByteArray): ByteArray {
        val j0 = nonce.copyOf(16).also { it[15] = 1 }
        val s = ghash(aad, c)
        val e = aes.encryptBlock(j0)
        return ByteArray(16) { (s[it].toInt() xor e[it].toInt()).toByte() }
    }

    /** Données chiffrées suivies de l'étiquette (comme Cipher.doFinal de la JVM). */
    fun encrypt(nonce: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(nonce.size == 12)
        val c = ctr(nonce, plain)
        return c + tag(nonce, aad, c)
    }

    fun decrypt(nonce: ByteArray, cipherAndTag: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(nonce.size == 12 && cipherAndTag.size >= 16)
        val c = cipherAndTag.copyOfRange(0, cipherAndTag.size - 16)
        val t = cipherAndTag.copyOfRange(cipherAndTag.size - 16, cipherAndTag.size)
        val expected = tag(nonce, aad, c)
        var diff = 0
        for (i in 0 until 16) diff = diff or (expected[i].toInt() xor t[i].toInt())
        if (diff != 0) throw BadTag()
        return ctr(nonce, c)
    }
}

/** Aléa non cryptographique déterministe (tests). */
internal fun pseudoBytes(n: Int, seed: Int) = Random(seed).nextBytes(n)
