package app.superhealth.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Petits secrets de l'app (code de récupération des sauvegardes, mot de passe WebDAV, jeton GitHub), chiffrés par une clé
 * du Keystore Android qui ne quitte pas le téléphone. Ils ne sont jamais copiés dans les sauvegardes.
 */
object Secrets {
    private const val ALIAS = "superhealth_secrets"
    private const val PREFS = "superhealth_secrets"
    const val BACKUP_CODE = "backup_code"
    const val WEBDAV_PASSWORD = "webdav_password"
    const val GITHUB_TOKEN = "github_token"

    fun get(ctx: Context, name: String): String? {
        val raw = prefs(ctx).getString(name, null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(c.doFinal(bytes, 12, bytes.size - 12))
    }

    fun put(ctx: Context, name: String, value: String?) = putAll(ctx, mapOf(name to value))

    /** One durable preference transaction, including relay capability rotations. */
    fun putAll(ctx: Context, values: Map<String, String?>) {
        val editor = prefs(ctx).edit()
        values.forEach { (name, value) ->
            if (value.isNullOrEmpty()) editor.remove(name)
            else {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key())
                editor.putString(name, Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP))
            }
        }
        check(editor.commit()) { "Enregistrement sécurisé impossible." }
    }

    /** Codes d'anciennes sauvegardes (code aléatoire d'avant la 1.3, codes saisis à une restauration), un par ligne. */
    const val KNOWN_CODES = "backup_codes_known"

    /**
     * Code aléatoire de récupération, conservé dans le Keystore. Les anciens codes dérivés de l’appareil
     * restent essayés pour ouvrir les archives historiques.
     */
    @Synchronized fun backupCode(ctx: Context): String {
        return get(ctx, BACKUP_CODE) ?: BackupCrypto.newCode().also { put(ctx, BACKUP_CODE, it) }
    }

    /** Codes à essayer pour ouvrir une sauvegarde : celui du téléphone, puis les anciens. */
    fun restoreCodes(ctx: Context): List<String> =
        (listOf(backupCode(ctx)) + listOfNotNull(android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }?.let(BackupCrypto::deviceCode)) + get(ctx, KNOWN_CODES).orEmpty().lines() + listOfNotNull(get(ctx, BACKUP_CODE)))
            .filter { BackupCrypto.isValidCode(it) }.distinctBy { BackupCrypto.normalize(it) }

    /** Retient [code] pour les prochaines restaurations (le code de ce téléphone ne change pas). */
    @Synchronized fun remember(ctx: Context, code: String) {
        val known = get(ctx, KNOWN_CODES).orEmpty().lines().filter { it.isNotBlank() }
        if (known.none { BackupCrypto.normalize(it) == BackupCrypto.normalize(code) }) put(ctx, KNOWN_CODES, (known + code.trim()).joinToString("\n"))
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }
}
