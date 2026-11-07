package app.superhealth.league

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.superhealth.backup.Secrets
import app.superhealth.ui.ProfilePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Visage public du Groupe : une vignette de 96 px signée par la clé de groupe, lisible par tout le monde sur le serveur.
 * Elle remplace l'initiale (ou, avant, sa propre photo par erreur) pour les personnes qu'on croise en commentaire sans les avoir en ami.
 * La photo complète des amis reste chiffrée dans leur carte (voir ProfileAvatars).
 */
object Faces {
    internal val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private const val REFRESH_MS = 20L * 86_400_000
    private const val CACHE_MS = 86_400_000L
    private const val MISS_MS = 3_600_000L
    /** Absences récentes (404, réseau) : on ne redemande pas à chaque affichage. */
    private val misses = ConcurrentHashMap<String, Long>()

    fun id(key: String) = GroupHistory.author(key)

    /** Publie (ou retire) ma vignette quand ma photo, son partage ou ma clé changent, et la rafraîchit tous les 20 jours. */
    suspend fun publishIfNeeded(ctx: Context) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: return@withContext
        val key = League.publicKey(); val id = id(key)
        val photo = ProfilePhoto.file(ctx).takeIf { League.state.value.shareProfilePhoto && it.isFile }
        val source = photo?.readBytes()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((source ?: ByteArray(0)) + (base + key).toByteArray()).joinToString("") { "%02x".format(it) }
        val at = Secrets.get(ctx, "face_published_at")?.toLongOrNull() ?: 0
        if (Secrets.get(ctx, "face_published") == fingerprint && (source == null || System.currentTimeMillis() - at < REFRESH_MS)) return@withContext
        // Rien à retirer si rien n'a jamais été publié.
        if (source == null && Secrets.get(ctx, "face_published") == null) return@withContext
        val image = source?.let(::thumbnail) ?: ""
        val now = System.currentTimeMillis()
        val payload = JsonObject(mapOf("t" to JsonPrimitive("face"), "id" to JsonPrimitive(id), "at" to JsonPrimitive(now), "image" to JsonPrimitive(image))).toString()
        val body = JsonObject(mapOf("key" to JsonPrimitive(key), "signed" to JsonPrimitive(League.signSocial(payload)))).toString()
        http.newCall(Request.Builder().url("$base/v2/faces/$id").put(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
            // 404 : serveur pas encore à jour ; 409 : une version plus récente existe déjà.
            if (!it.isSuccessful && it.code != 409) return@withContext
        }
        Secrets.put(ctx, "face_published", fingerprint); Secrets.put(ctx, "face_published_at", now.toString())
    }

    private fun thumbnail(bytes: ByteArray): String {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        val small = Bitmap.createScaledBitmap(original, 96, 96, true)
        val jpeg = ByteArrayOutputStream().use { out -> small.compress(Bitmap.CompressFormat.JPEG, 78, out); out.toByteArray() }
        if (small !== original) small.recycle()
        original.recycle()
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(jpeg)
    }

    /**
     * Photo détourée de la carte Athlète, publique comme le visage : tout membre du Groupe, ami ou non, voit la vraie carte.
     * WebP transparent de 360 px de haut au plus (~40 Ko), signé par la clé de groupe ; retirée quand la carte n'utilise
     * plus la photo détourée. Publiée dès qu'elle change, puis rafraîchie tous les 20 jours.
     */
    suspend fun publishCardIfNeeded(ctx: Context) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: return@withContext
        val key = League.publicKey(); val id = id(key)
        app.superhealth.cards.CardStore.load(ctx)
        val cutout = app.superhealth.cards.CardStore.cutoutFile(ctx)
            .takeIf { app.superhealth.cards.CardStore.state.value.photo == app.superhealth.cards.PhotoSource.DETOUREE && it.isFile }
        val source = cutout?.readBytes()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((source ?: ByteArray(0)) + (base + key).toByteArray()).joinToString("") { "%02x".format(it) }
        val at = Secrets.get(ctx, "card_published_at")?.toLongOrNull() ?: 0
        if (Secrets.get(ctx, "card_published") == fingerprint && (source == null || System.currentTimeMillis() - at < REFRESH_MS)) return@withContext
        if (source == null && Secrets.get(ctx, "card_published") == null) return@withContext
        val image = source?.let(::cardImage) ?: ""
        val now = System.currentTimeMillis()
        val payload = JsonObject(mapOf("t" to JsonPrimitive("card"), "id" to JsonPrimitive(id), "at" to JsonPrimitive(now), "image" to JsonPrimitive(image))).toString()
        val body = JsonObject(mapOf("key" to JsonPrimitive(key), "signed" to JsonPrimitive(League.signSocial(payload)))).toString()
        http.newCall(Request.Builder().url("$base/v2/faces/$id/card").put(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
            if (!it.isSuccessful && it.code != 409) return@withContext
        }
        Secrets.put(ctx, "card_published", fingerprint); Secrets.put(ctx, "card_published_at", now.toString())
        cardMisses.clear()
    }

    /** WebP transparent qui tient dans la limite du serveur : la qualité, puis la taille, baissent jusqu'à ce qu'il passe. */
    private fun cardImage(bytes: ByteArray): String {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        for (height in intArrayOf(360, 300, 240)) {
            val scale = minOf(1f, height.toFloat() / original.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), true) else original
            for (quality in intArrayOf(82, 68, 52)) {
                val webp = ByteArrayOutputStream().use { out -> small.compress(format, quality, out); out.toByteArray() }
                val text = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(webp)
                if (text.length <= CARD_MAX) { if (small !== original) small.recycle(); original.recycle(); return text }
            }
            if (small !== original) small.recycle()
        }
        original.recycle()
        return ""
    }

    private const val CARD_MAX = 78_000
    private val cardMisses = ConcurrentHashMap<String, Long>()

    /** Photo détourée de la carte de [key] (ami ou non), vérifiée contre sa clé ; null si elle n'en publie pas. */
    suspend fun cardBitmap(ctx: Context, key: String): Bitmap? = withContext(Dispatchers.IO) {
        if (key.length !in 32..512) return@withContext null
        val id = id(key)
        val file = File(File(ctx.cacheDir, "faces").apply { mkdirs() }, "$id.card.webp")
        val stale = !file.isFile || System.currentTimeMillis() - file.lastModified() > CACHE_MS
        if (stale && System.currentTimeMillis() - (cardMisses[id] ?: 0) > MISS_MS) {
            val fetched = runCatching { fetchCard(key, id) }
            fetched.onSuccess { image -> if (image == null) file.delete() else { file.writeBytes(image); file.setLastModified(System.currentTimeMillis()) } }
            if (fetched.getOrNull() == null) cardMisses[id] = System.currentTimeMillis()
        }
        if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    }

    private fun fetchCard(key: String, id: String): ByteArray? {
        val base = Relay.base() ?: error("Serveur indisponible.")
        val raw = http.newCall(Request.Builder().url("$base/v2/faces/$id/card").get().build()).execute().use { r ->
            if (r.code == 404) return null
            check(r.isSuccessful)
            r.peekBody(200_000).string()
        }
        val card = json.parseToJsonElement(raw).jsonObject
        check(card["key"]?.jsonPrimitive?.content == key)
        val payload = json.parseToJsonElement(League.verifySocial(card["signed"]!!.jsonPrimitive.content, key) ?: error("Signature non valable.")).jsonObject
        check(payload["t"]?.jsonPrimitive?.content == "card" && payload["id"]?.jsonPrimitive?.content == id && (payload["at"]?.jsonPrimitive?.long ?: 0) > 0)
        val image = payload["image"]?.jsonPrimitive?.content.orEmpty()
        if (image.isEmpty()) return null
        return java.util.Base64.getUrlDecoder().decode(image).also { bytes ->
            // WebP (RIFF…WEBP) ou PNG uniquement.
            check(bytes.size in 16..60_000 && (String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" || bytes[0] == 0x89.toByte() && String(bytes, 1, 3) == "PNG"))
        }
    }

    /** Vignette de la personne dont la clé de groupe est [key], vérifiée contre cette clé ; null si elle n'en publie pas. */
    suspend fun bitmap(ctx: Context, key: String): Bitmap? = withContext(Dispatchers.IO) {
        if (key.length !in 32..512) return@withContext null
        val id = id(key)
        val file = File(File(ctx.cacheDir, "faces").apply { mkdirs() }, "$id.jpg")
        val stale = !file.isFile || System.currentTimeMillis() - file.lastModified() > CACHE_MS
        if (stale && System.currentTimeMillis() - (misses[id] ?: 0) > MISS_MS) {
            val fetched = runCatching { fetch(key, id) }
            fetched.onSuccess { jpeg -> if (jpeg == null) file.delete() else { file.writeBytes(jpeg); file.setLastModified(System.currentTimeMillis()) } }
            if (fetched.getOrNull() == null) misses[id] = System.currentTimeMillis()
        }
        if (file.isFile) BitmapFactory.decodeFile(file.path) else null
    }

    private fun fetch(key: String, id: String): ByteArray? {
        val base = Relay.base() ?: error("Serveur indisponible.")
        val raw = http.newCall(Request.Builder().url("$base/v2/faces/$id").get().build()).execute().use { r ->
            if (r.code == 404) return null
            check(r.isSuccessful)
            r.peekBody(32_000).string()
        }
        val face = json.parseToJsonElement(raw).jsonObject
        // Le serveur n'est pas cru sur parole : la vignette doit être signée par la clé de l'auteur du commentaire.
        check(face["key"]?.jsonPrimitive?.content == key)
        val payload = json.parseToJsonElement(League.verifySocial(face["signed"]!!.jsonPrimitive.content, key) ?: error("Signature non valable.")).jsonObject
        check(payload["t"]?.jsonPrimitive?.content == "face" && payload["id"]?.jsonPrimitive?.content == id && (payload["at"]?.jsonPrimitive?.long ?: 0) > 0)
        val image = payload["image"]?.jsonPrimitive?.content.orEmpty()
        if (image.isEmpty()) return null
        return java.util.Base64.getUrlDecoder().decode(image).also { check(it.size <= 9_000 && it[0] == 0xFF.toByte() && it[1] == 0xD8.toByte()) }
    }
}
