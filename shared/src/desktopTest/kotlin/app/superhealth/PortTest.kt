package app.superhealth

import app.superhealth.backup.BackupCrypto
import app.superhealth.platform.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Le code porté (crypto, ZIP, printf, dates) doit donner exactement ce que donnait la JVM d'Android. */
class PortTest {
    @Test fun sha256() {
        repeat(20) { n ->
            val b = Random(n).nextBytes(n * 37)
            assertContentEquals(java.security.MessageDigest.getInstance("SHA-256").digest(b), Sha256.digest(b))
        }
    }

    @Test fun hmacAndPbkdf2() {
        val key = "SuperHealth backup code v1".toByteArray()
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        assertContentEquals(mac.doFinal("abc".toByteArray()), Sha256.hmac(key, "abc".toByteArray()))
        val salt = Random(3).nextBytes(16)
        val jvm = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec("0123ABCD".toCharArray(), salt, 1000, 256)).encoded
        assertContentEquals(jvm, Sha256.pbkdf2("0123ABCD".toByteArray(), salt, 1000, 32))
    }

    @Test fun aesGcm() {
        repeat(10) { n ->
            val key = Random(n).nextBytes(32); val nonce = Random(n + 100).nextBytes(12)
            val plain = Random(n + 200).nextBytes(n * 53 + 7); val aad = Random(n + 300).nextBytes(n * 5)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad) }
            val jvm = c.doFinal(plain)
            assertContentEquals(jvm, AesGcm(key).encrypt(nonce, plain, aad))
            assertContentEquals(plain, AesGcm(key).decrypt(nonce, jvm, aad))
        }
    }

    @Test fun backupRoundTrip() {
        val code = BackupCrypto.deviceCode("test-device")
        val data = "{\"foods\":[]}".encodeToByteArray()
        val enc = BackupCrypto.encrypt(data, code, iterations = 2000)
        assertContentEquals(data, BackupCrypto.decrypt(enc, code))
    }

    @Test fun zipReadsJvmDeflate() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("a/nutrition.json")); z.write("bonjour ".repeat(500).toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("b.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        val entries = Zip.read(out.toByteArray()).toMap()
        assertEquals("bonjour ".repeat(500), entries["a/nutrition.json"]!!.decodeToString())
        // et l'inverse : notre archive se lit avec java.util.zip
        val ours = Zip.write(listOf("x.json" to "salut".encodeToByteArray()))
        java.util.zip.ZipInputStream(ours.inputStream()).use { z -> assertEquals("x.json", z.nextEntry.name); assertEquals("salut", z.readBytes().decodeToString()) }
    }

    @Test fun printfLikeJava() {
        val fr = java.util.Locale.FRANCE
        listOf("%.1f" to arrayOf<Any?>(72.45), "%.2f" to arrayOf<Any?>(2.675), "%d:%02d" to arrayOf<Any?>(7, 5), "%1\$s h %2\$s" to arrayOf<Any?>(3, "05"),
            "%+d" to arrayOf<Any?>(4), "%,d" to arrayOf<Any?>(12345), "%.0f %%" to arrayOf<Any?>(99.5)).forEach { (f, a) ->
            assertEquals(String.format(java.util.Locale.US, f, *a), Printf.format(Locale.US, f, a), f)
            assertEquals(String.format(fr, f, *a).replace(' ', ' '), Printf.format(Locale.FRANCE, f, a), f)
        }
    }

    @Test fun dates() {
        val d = LocalDate.of(2026, 10, 26)
        assertEquals("lun. 26 oct.", d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)))
        assertEquals(java.time.LocalDate.of(2026, 10, 26).toEpochDay(), d.toEpochDay())
        assertEquals(LocalDate.of(2026, 10, 26), d.plusDays(6).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
}
