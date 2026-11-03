package app.superhealth.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.superhealth.SuperPrefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Où envoyer les sauvegardes chiffrées : un serveur WebDAV (Nextcloud, ownCloud, Koofr, NAS…) ou un dossier choisi avec le
 * sélecteur Android (téléphone, carte SD, Syncthing, ou une app de cloud qui expose ses dossiers : Nextcloud, Proton Drive,
 * Google Drive selon les versions…). Les [KEEP] dernières sauvegardes sont gardées, les plus anciennes effacées.
 */
object Remote {
    enum class Kind(val label: String) { NONE("Aucune"), GOOGLE_DRIVE("Google Drive"), NEXTCLOUD("Nextcloud"), WEBDAV("WebDAV"), FOLDER("Dossier") }

    const val KEEP = 10
    private const val FOLDER_NAME = "SuperHealth"

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }

    data class Entry(val name: String, val uri: String, val size: Long)

    fun kind(): Kind = Kind.entries.firstOrNull { it.name == SuperPrefs.settings.value.backupDest } ?: Kind.NONE

    /**
     * Dossier WebDAV complet : pour Nextcloud, l'adresse du serveur suffit (…/remote.php/dav/files/<utilisateur>/SuperHealth/) ;
     * pour un autre serveur, l'adresse saisie telle quelle.
     */
    fun davFolder(): String? {
        val s = SuperPrefs.settings.value
        val base = s.webdavUrl.trim().trimEnd('/').takeIf { raw ->
            val url = raw.toHttpUrlOrNull()
            url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null
        } ?: return null
        return when (kind()) {
            Kind.NEXTCLOUD -> if ("/remote.php/" in base) "$base/" else "$base/remote.php/dav/files/${Uri.encode(s.webdavUser.trim())}/$FOLDER_NAME/"
            Kind.WEBDAV -> "$base/"
            else -> null
        }
    }

    internal fun safeDavEntry(base: String, href: String): String? {
        val origin = base.toHttpUrlOrNull() ?: return null
        val target = origin.resolve(href) ?: return null
        return target.toString().takeIf { target.isHttps && target.scheme == origin.scheme && target.host == origin.host &&
            target.port == origin.port && target.username.isEmpty() && target.password.isEmpty() && target.query == null && target.fragment == null &&
            target.encodedPath.startsWith(origin.encodedPath) && target.encodedPath != origin.encodedPath }
    }
    private fun checkedEntry(entry: Entry): String = safeDavEntry(davFolder() ?: error("Adresse HTTPS requise"), entry.uri) ?: error("Destination de sauvegarde refusée")

    private fun auth(ctx: Context): String = Credentials.basic(SuperPrefs.settings.value.webdavUser.trim(), Secrets.get(ctx, Secrets.WEBDAV_PASSWORD) ?: "")

    /** Envoie [bytes] sous [name], puis efface les sauvegardes au-delà des [KEEP] plus récentes. */
    suspend fun upload(ctx: Context, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        when (kind()) {
            Kind.GOOGLE_DRIVE -> {
                val uri = Uri.parse(SuperPrefs.settings.value.backupDocument ?: error("choisissez un fichier dans Google Drive"))
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: error("Google Drive ne permet pas l'écriture de ce fichier")
            }
            Kind.NEXTCLOUD, Kind.WEBDAV -> {
                val dir = davFolder() ?: error("adresse du serveur manquante")
                // dossier créé au besoin (405 : il existe déjà)
                http.newCall(Request.Builder().url(dir).header("Authorization", auth(ctx)).method("MKCOL", null).build()).execute().use { r ->
                    if (r.code == 401) error("identifiants refusés (401)")
                }
                http.newCall(Request.Builder().url(dir + Uri.encode(name)).header("Authorization", auth(ctx))
                    .put(bytes.toRequestBody(Archive.MIME.toMediaType())).build()).execute().use { r ->
                    if (!r.isSuccessful) error("envoi refusé (${r.code})")
                }
            }
            Kind.FOLDER -> {
                val tree = Uri.parse(SuperPrefs.settings.value.backupFolder ?: error("aucun dossier choisi"))
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                list(ctx).firstOrNull { it.name == name }?.let { DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(it.uri)) }
                val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, Archive.MIME, name) ?: error("écriture refusée par le dossier")
                ctx.contentResolver.openOutputStream(doc, "wt")!!.use { it.write(bytes) }
            }
            Kind.NONE -> error("aucune destination")
        }
        runCatching { list(ctx).sortedByDescending { it.name }.drop(KEEP).forEach { delete(ctx, it) } }
    }

    /** Sauvegardes présentes à destination, de la plus récente à la plus ancienne. */
    suspend fun list(ctx: Context): List<Entry> = withContext(Dispatchers.IO) {
        when (kind()) {
            Kind.GOOGLE_DRIVE -> {
                val uri = SuperPrefs.settings.value.backupDocument ?: return@withContext emptyList()
                ctx.contentResolver.query(Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) listOf(Entry(c.getString(0) ?: "superhealth.shbak", uri, if (c.isNull(1)) 0 else c.getLong(1))) else emptyList()
                } ?: error("fichier Google Drive inaccessible ; sélectionnez-le à nouveau")
            }
            Kind.NEXTCLOUD, Kind.WEBDAV -> {
                val dir = davFolder() ?: return@withContext emptyList()
                val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>"""
                http.newCall(Request.Builder().url(dir).header("Authorization", auth(ctx)).header("Depth", "1")
                    .method("PROPFIND", body.toRequestBody("application/xml".toMediaType())).build()).execute().use { r ->
                    if (r.code == 404) return@withContext emptyList()
                    if (!r.isSuccessful) error("lecture du dossier refusée (${r.code})")
                    parsePropfind(r.body.string()).mapNotNull { (href, size) ->
                        safeDavEntry(dir, href)?.let { target -> Entry(Uri.decode(href.trimEnd('/').substringAfterLast('/')), target, size) }
                    }.filter { it.name.endsWith(".${Archive.EXT}") }.sortedByDescending { it.name }
                }
            }
            Kind.FOLDER -> {
                val tree = Uri.parse(SuperPrefs.settings.value.backupFolder ?: return@withContext emptyList())
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)
                ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val name = c.getString(1) ?: continue
                            if (name.endsWith(".${Archive.EXT}")) add(Entry(name, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString(), c.getLong(2)))
                        }
                    }
                }.orEmpty().sortedByDescending { it.name }
            }
            Kind.NONE -> emptyList()
        }
    }

    suspend fun download(ctx: Context, e: Entry): ByteArray = withContext(Dispatchers.IO) {
        if (kind() == Kind.FOLDER || kind() == Kind.GOOGLE_DRIVE) ctx.contentResolver.openInputStream(Uri.parse(e.uri))!!.use { it.readBytes() }
        else http.newCall(Request.Builder().url(checkedEntry(e)).header("Authorization", auth(ctx)).build()).execute().use { r ->
            if (!r.isSuccessful) error("téléchargement refusé (${r.code})")
            r.body.bytes()
        }
    }

    private fun delete(ctx: Context, e: Entry) {
        if (kind() == Kind.GOOGLE_DRIVE) return
        if (kind() == Kind.FOLDER) DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(e.uri))
        else http.newCall(Request.Builder().url(checkedEntry(e)).header("Authorization", auth(ctx)).delete().build()).execute().close()
    }

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
