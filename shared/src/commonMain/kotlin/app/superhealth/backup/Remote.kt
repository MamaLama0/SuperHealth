package app.superhealth.backup

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.SuperPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Où envoyer les sauvegardes chiffrées : un serveur WebDAV (Nextcloud, ownCloud, Koofr, NAS…). Le choix « Dossier » d'Android
 * n'existe pas sur iOS (pas d'accès durable à un dossier) : on exporte alors un fichier par la feuille de partage. Les [KEEP] dernières sauvegardes sont gardées, les plus anciennes effacées.
 */
object Remote {
    enum class Kind(val label: String) { NONE("Aucune"), NEXTCLOUD("Nextcloud"), WEBDAV("WebDAV"), FOLDER("Dossier") }

    const val KEEP = 10
    private const val FOLDER_NAME = "SuperHealth"

    /** Encodage d'un segment d'URL (Uri.encode d'Android : espaces en %20). */
    private fun enc(s: String) = URLEncoder.encode(s).replace("+", "%20")
    private fun dec(s: String): String {
        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            if (s[i] == '%' && i + 2 < s.length + 0 && s.substring(i + 1, minOf(i + 3, s.length)).toIntOrNull(16) != null) {
                out += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
            } else { s[i].toString().encodeToByteArray().forEach { out += it }; i++ }
        }
        return out.toByteArray().decodeToString()
    }

    private suspend fun call(ctx: Context, url: String, method: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap()) =
        httpCall(url, method, mapOf("Authorization" to auth(ctx)) + headers, body, 120_000)

    data class Entry(val name: String, val uri: String, val size: Long)

    fun kind(): Kind = Kind.entries.firstOrNull { it.name == SuperPrefs.settings.value.backupDest } ?: Kind.NONE

    /**
     * Dossier WebDAV complet : pour Nextcloud, l'adresse du serveur suffit (…/remote.php/dav/files/<utilisateur>/SuperHealth/) ;
     * pour un autre serveur, l'adresse saisie telle quelle.
     */
    fun davFolder(): String? {
        val s = SuperPrefs.settings.value
        val base = s.webdavUrl.trim().trimEnd('/').takeIf { it.startsWith("http") } ?: return null
        return when (kind()) {
            Kind.NEXTCLOUD -> if ("/remote.php/" in base) "$base/" else "$base/remote.php/dav/files/${enc(s.webdavUser.trim())}/$FOLDER_NAME/"
            Kind.WEBDAV -> "$base/"
            else -> null
        }
    }

    private fun auth(ctx: Context): String =
        "Basic " + Base64.getEncoder().encodeToString((SuperPrefs.settings.value.webdavUser.trim() + ":" + (Secrets.get(ctx, Secrets.WEBDAV_PASSWORD) ?: "")).encodeToByteArray())

    /** Envoie [bytes] sous [name], puis efface les sauvegardes au-delà des [KEEP] plus récentes. */
    suspend fun upload(ctx: Context, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        when (kind()) {
            Kind.NEXTCLOUD, Kind.WEBDAV -> {
                val dir = davFolder() ?: error("adresse du serveur manquante")
                // dossier créé au besoin (405 : il existe déjà)
                if (call(ctx, dir, "MKCOL").first == 401) error("identifiants refusés (401)")
                val (code, _) = call(ctx, dir + enc(name), "PUT", bytes, mapOf("Content-Type" to Archive.MIME))
                if (code !in 200..299) error("envoi refusé ($code)")
            }
            Kind.FOLDER, Kind.NONE -> error("aucune destination")
        }
        runCatching { list(ctx).sortedByDescending { it.name }.drop(KEEP).forEach { delete(ctx, it) } }
    }

    /** Sauvegardes présentes à destination, de la plus récente à la plus ancienne. */
    suspend fun list(ctx: Context): List<Entry> = withContext(Dispatchers.IO) {
        when (kind()) {
            Kind.NEXTCLOUD, Kind.WEBDAV -> {
                val dir = davFolder() ?: return@withContext emptyList()
                val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>"""
                val (code, resp) = call(ctx, dir, "PROPFIND", body.encodeToByteArray(), mapOf("Depth" to "1", "Content-Type" to "application/xml"))
                if (code == 404) return@withContext emptyList()
                if (code !in 200..299) error("lecture du dossier refusée ($code)")
                val origin = URI(dir).let { "${it.scheme}://${it.host}" + (if (it.port > 0) ":${it.port}" else "") }
                parsePropfind(resp.decodeToString()).map { (href, size) ->
                    Entry(dec(href.trimEnd('/').substringAfterLast('/')), if (href.startsWith("http")) href else origin + href, size)
                }.filter { it.name.endsWith(".${Archive.EXT}") }.sortedByDescending { it.name }
            }
            else -> emptyList()
        }
    }

    suspend fun download(ctx: Context, e: Entry): ByteArray = withContext(Dispatchers.IO) {
        val (code, bytes) = call(ctx, e.uri, "GET")
        if (code !in 200..299) error("téléchargement refusé ($code)")
        bytes
    }

    private suspend fun delete(ctx: Context, e: Entry) { call(ctx, e.uri, "DELETE") }

    /** Liens (href) et tailles d'une réponse PROPFIND ; le dossier lui-même (sans taille) est ignoré. */
    internal fun parsePropfind(xml: String): List<Pair<String, Long>> {
        val responses = Regex("<(?:[A-Za-z0-9]+:)?response[ >].*?</(?:[A-Za-z0-9]+:)?response>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        return responses.mapNotNull { m ->
            val r = m.value
            val href = Regex("<(?:[A-Za-z0-9]+:)?href>(.*?)</(?:[A-Za-z0-9]+:)?href>", RegexOption.DOT_MATCHES_ALL).find(r)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val size = Regex("<(?:[A-Za-z0-9]+:)?getcontentlength>(\\d+)</").find(r)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            href to size
        }.toList()
    }
}
