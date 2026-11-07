package app.superhealth.league

import app.superhealth.platform.*

import app.superhealth.backup.Secrets
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Photo détourée publique de la carte Athlète (route /v2/faces/<id>/card du serveur, comme sur Android) : tout membre du
 * Groupe, ami ou non, voit la vraie carte. WebP transparent signé par la clé de groupe ; retiré quand la carte n'utilise
 * plus la photo détourée. Identifiant : début du SHA-256 de la clé de groupe.
 */
object Faces {
    private val http = SocialHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private const val REFRESH_MS = 20L * 86_400_000
    private const val CACHE_MS = 86_400_000L
    private const val MISS_MS = 3_600_000L
    private const val CARD_MAX = 78_000
    private val misses = mutableMapOf<String, Long>()
    /** Le système de fichiers iOS ne donne pas la date du cache : on la garde en mémoire. */
    private val fetched = mutableMapOf<String, Long>()

    fun id(key: String) = MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(key)).take(8).joinToString("") { "%02x".format(it) }

    suspend fun publishCardIfNeeded(ctx: Context) = withContext(Dispatchers.Default) {
        val base = Relay.base() ?: return@withContext
        val key = League.publicKey(); val id = id(key)
        app.superhealth.cards.CardStore.load(ctx)
        val cutout = app.superhealth.cards.CardStore.cutoutFile(ctx)
            .takeIf { app.superhealth.cards.CardStore.state.value.photo == app.superhealth.cards.PhotoSource.DETOUREE && it.isFile }
        val source = cutout?.readBytes()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((source ?: ByteArray(0)) + (base + key).encodeToByteArray()).joinToString("") { "%02x".format(it) }
        val at = Secrets.get(ctx, "card_published_at")?.toLongOrNull() ?: 0
        if (Secrets.get(ctx, "card_published") == fingerprint && (source == null || System.currentTimeMillis() - at < REFRESH_MS)) return@withContext
        if (source == null && Secrets.get(ctx, "card_published") == null) return@withContext
        val image = source?.let(::cardImage) ?: ""
        val now = System.currentTimeMillis()
        val payload = JsonObject(mapOf("t" to JsonPrimitive("card"), "id" to JsonPrimitive(id), "at" to JsonPrimitive(now), "image" to JsonPrimitive(image))).toString()
        val body = JsonObject(mapOf("key" to JsonPrimitive(key), "signed" to JsonPrimitive(League.signSocial(payload)))).toString()
        val ok = http.newCall(SocialRequest.Builder().url("$base/v2/faces/$id/card").put(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
            it.isSuccessful || it.code == 409
        }
        if (!ok) return@withContext
        Secrets.put(ctx, "card_published", fingerprint); Secrets.put(ctx, "card_published_at", now.toString())
        misses.clear()
    }

    /** WebP qui tient dans la limite du serveur : la qualité, puis la taille, baissent jusqu'à ce qu'il passe. */
    private fun cardImage(png: ByteArray): String {
        for (height in intArrayOf(360, 300, 240)) for (quality in intArrayOf(82, 68, 52)) {
            val webp = encodeCardPhoto(png, height, quality) ?: return ""
            val text = Base64.getUrlEncoder().withoutPadding().encodeToString(webp)
            if (text.length <= CARD_MAX) return text
        }
        return ""
    }

    /** Photo détourée de la carte de [key] (ami ou non), vérifiée contre sa clé ; null si elle n'en publie pas. */
    suspend fun cardBitmap(ctx: Context, key: String): ImageBitmap? = withContext(Dispatchers.Default) {
        if (key.length !in 32..512) return@withContext null
        val id = id(key)
        val file = File(File(ctx.cacheDir, "faces").apply { mkdirs() }, "$id.card.webp")
        val stale = !file.isFile || System.currentTimeMillis() - (fetched[id] ?: 0) > CACHE_MS
        if (stale && System.currentTimeMillis() - (misses[id] ?: 0) > MISS_MS) {
            result(id, file, runCatching { fetchCard(key, id) })
        }
        if (file.isFile) decodeImage(file.readBytes()) else null
    }

    private fun result(id: String, file: File, outcome: Result<ByteArray?>) {
        outcome.onSuccess { image -> if (image == null) file.delete() else { file.writeBytes(image); fetched[id] = System.currentTimeMillis() } }
        if (outcome.getOrNull() == null) misses[id] = System.currentTimeMillis()
    }

    private fun fetchCard(key: String, id: String): ByteArray? {
        val base = Relay.base() ?: error("Serveur indisponible.")
        val raw = http.newCall(SocialRequest.Builder().url("$base/v2/faces/$id/card").maxResponseBytes(200_000).get().build()).execute().use { r ->
            if (r.code == 404) return null
            check(r.isSuccessful)
            r.body.bytes.decodeToString()
        }
        val card = json.parseToJsonElement(raw).jsonObject
        check(card["key"]?.jsonPrimitive?.content == key)
        val payload = json.parseToJsonElement(League.verifySocial(card["signed"]!!.jsonPrimitive.content, key) ?: error("Signature non valable.")).jsonObject
        check(payload["t"]?.jsonPrimitive?.content == "card" && payload["id"]?.jsonPrimitive?.content == id && (payload["at"]?.jsonPrimitive?.long ?: 0) > 0)
        val image = payload["image"]?.jsonPrimitive?.content.orEmpty()
        if (image.isEmpty()) return null
        return Base64.getUrlDecoder().decode(image).also { bytes ->
            check(bytes.size in 16..60_000 && (bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP" ||
                bytes[0] == 0x89.toByte() && bytes.copyOfRange(1, 4).decodeToString() == "PNG"))
        }
    }
}
