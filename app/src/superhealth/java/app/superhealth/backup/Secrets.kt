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

    fun get(ctx: Context, name: String): String? = runCatching {
        val raw = prefs(ctx).getString(name, null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(c.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    fun put(ctx: Context, name: String, value: String?) {
        if (value.isNullOrEmpty()) { prefs(ctx).edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.toByteArray())
        prefs(ctx).edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    /** Code de récupération, créé au premier démarrage. */
    @Synchronized fun backupCode(ctx: Context): String =
        get(ctx, BACKUP_CODE) ?: BackupCrypto.newCode().also { put(ctx, BACKUP_CODE, it) }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }
}
