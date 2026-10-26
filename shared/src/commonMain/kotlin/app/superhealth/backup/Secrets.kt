package app.superhealth.backup

import app.superhealth.platform.*

import app.superhealth.platform.Context

/**
 * Petits secrets de l'app (code de récupération des sauvegardes, mot de passe WebDAV, jeton GitHub), gardés dans le trousseau
 * (Keychain) de l'iPhone, qui ne quitte pas l'appareil. Ils ne sont jamais copiés dans les sauvegardes.
 */
object Secrets {
    const val BACKUP_CODE = "backup_code"
    const val WEBDAV_PASSWORD = "webdav_password"
    const val GITHUB_TOKEN = "github_token"

    fun get(ctx: Context, name: String): String? = runCatching { keychainGet(name) }.getOrNull()

    fun put(ctx: Context, name: String, value: String?) {
        runCatching { if (value.isNullOrEmpty()) keychainDelete(name) else keychainSet(name, value) }
    }

    /** Codes d'anciennes sauvegardes (codes saisis à une restauration, code du téléphone Android), un par ligne. */
    const val KNOWN_CODES = "backup_codes_known"

    /**
     * Code de récupération de cet iPhone : tiré de l'identifiant de l'app pour cet appareil (identifierForVendor), donc le
     * même après une réinstallation tant qu'une app du même éditeur reste installée. Sinon, un code aléatoire gardé ici.
     */
    fun backupCode(ctx: Context): String {
        get(ctx, BACKUP_CODE)?.let { return it }
        val id = deviceIdentifier()
        val code = if (!id.isNullOrBlank()) BackupCrypto.deviceCode(id) else BackupCrypto.newCode()
        put(ctx, BACKUP_CODE, code)
        return code
    }

    /** Codes à essayer pour ouvrir une sauvegarde : celui de l'appareil, puis les anciens. */
    fun restoreCodes(ctx: Context): List<String> =
        (listOf(backupCode(ctx)) + get(ctx, KNOWN_CODES).orEmpty().lines())
            .filter { BackupCrypto.isValidCode(it) }.distinctBy { BackupCrypto.normalize(it) }

    /** Retient [code] pour les prochaines restaurations. */
    fun remember(ctx: Context, code: String) {
        val known = get(ctx, KNOWN_CODES).orEmpty().lines().filter { it.isNotBlank() }
        if (known.none { BackupCrypto.normalize(it) == BackupCrypto.normalize(code) }) put(ctx, KNOWN_CODES, (known + code.trim()).joinToString("\n"))
    }
}
