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

    /** Codes d'anciennes sauvegardes (code aléatoire d'avant la 1.3, codes saisis à une restauration), un par ligne. */
    const val KNOWN_CODES = "backup_codes_known"

    /**
     * Code de récupération de ce téléphone : tiré de ANDROID_ID, donc le même après une réinstallation. Sans identifiant
     * (cas théorique), un code aléatoire gardé ici.
     */
    @Synchronized fun backupCode(ctx: Context): String {
        val id = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        if (!id.isNullOrBlank()) return BackupCrypto.deviceCode(id)
        return get(ctx, BACKUP_CODE) ?: BackupCrypto.newCode().also { put(ctx, BACKUP_CODE, it) }
    }

    /** Codes à essayer pour ouvrir une sauvegarde : celui du téléphone, puis les anciens. */
    fun restoreCodes(ctx: Context): List<String> =
        (listOf(backupCode(ctx)) + get(ctx, KNOWN_CODES).orEmpty().lines() + listOfNotNull(get(ctx, BACKUP_CODE)))
            .filter { BackupCrypto.isValidCode(it) }.distinctBy { BackupCrypto.normalize(it) }

    /** Retient [code] pour les prochaines restaurations (le code de ce téléphone ne change pas). */
    @Synchronized fun remember(ctx: Context, code: String) {
        val known = get(ctx, KNOWN_CODES).orEmpty().lines().filter { it.isNotBlank() }
        if (known.none { BackupCrypto.normalize(it) == BackupCrypto.normalize(code) }) put(ctx, KNOWN_CODES, (known + code.trim()).joinToString("\n"))
    }

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
