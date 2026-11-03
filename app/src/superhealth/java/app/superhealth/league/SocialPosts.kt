package app.superhealth.league

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.superhealth.training.domain.Workout
import app.superhealth.training.domain.Analytics
import app.superhealth.training.WorkoutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Only explicitly published summaries enter the signed, encrypted friend card. No GPS tracks. */
object SocialPosts {
    const val MAX_BYTES = 15 * 1024 * 1024
    const val MAX_POSTS = 12
    @Serializable data class Media(val id: String, val key: String, val mime: String, val hash: String, val bytes: Int)
    @Serializable data class Post(val id: String, val created: Long, val text: String = "", val kind: String = "TEXT",
        val title: String = "", val detail: String = "", val media: Media? = null)
    @Serializable data class Draft(val title: String, val detail: String, val kind: String)
    data class Published(val post: Post, val sent: Boolean)
    val draft = MutableStateFlow<Draft?>(null)
    private val kinds = setOf("TEXT", "PHOTO", "VIDEO", "WORKOUT", "RUN", "STREAK")
    private val mimes = setOf("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm", "video/3gpp")
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    fun valid(post: Post, now: Long = System.currentTimeMillis()): Boolean = post.id.matches(Regex("[a-f0-9]{32}")) &&
        post.created in 1..(now + 300000) && post.text.length <= 500 && post.title.length <= 100 && post.detail.length <= 1500 &&
        post.kind in kinds && (post.media?.let { m -> m.id.matches(Regex("[a-f0-9]{64}")) && RelayCrypto.validKey(m.key) &&
            m.mime in mimes && m.hash.matches(Regex("[a-f0-9]{64}")) && m.bytes in 29..(MAX_BYTES + 28) } ?: (post.kind !in setOf("PHOTO", "VIDEO")))
    fun visible(posts: List<Post>, now: Long = System.currentTimeMillis()) = posts.filter { valid(it, now) && it.created >= now - 34L * 86400000 }.sortedByDescending { it.created }.take(MAX_POSTS)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun crypt(bytes: ByteArray, key: String, id: String, encrypt: Boolean): ByteArray {
        val nonce = if (encrypt) SecureRandom().generateSeed(12) else bytes.take(12).toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            SecretKeySpec(Base64.getUrlDecoder().decode(key), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(("SuperHealth media v1:" + id).toByteArray())
        return if (encrypt) nonce + cipher.doFinal(bytes) else cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    private fun path(endpoint: String, id: String) = endpoint + "/media/" + id
    private fun cache(ctx: Context, media: Media): File = File(ctx.cacheDir, "social").apply { mkdirs() }
        .let { File(it, media.hash + if (media.mime.startsWith("video/")) ".mp4" else ".jpg") }
    private fun trimCache(ctx: Context) {
        var total = 0L
        File(ctx.cacheDir, "social").listFiles().orEmpty().sortedByDescending { it.lastModified() }.forEach { f ->
            total += f.length(); if (total > 64L * 1024 * 1024) f.delete()
        }
    }
    private fun read(ctx: Context, uri: Uri): Pair<ByteArray, String> {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime in mimes) { "Format non pris en charge." }
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= MAX_BYTES) { "Choisissez un média de moins de 15 Mo." }; out.write(buffer, 0, n) }
            out.toByteArray()
        }
        if (!mime.startsWith("image/")) return bytes to mime
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image illisible." }
        var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image illisible.")
        val exif = runCatching { androidx.exifinterface.media.ExifInterface(bytes.inputStream()) }.getOrNull()
        val matrix = android.graphics.Matrix().apply {
            exif?.rotationDegrees?.takeIf { it != 0 }?.let { postRotate(it.toFloat()) }
            if (exif?.isFlipped == true) postScale(-1f, 1f)
        }
        val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return java.io.ByteArrayOutputStream().use { out ->
            oriented.compress(Bitmap.CompressFormat.JPEG, 88, out)
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle(); out.toByteArray() to "image/jpeg"
        }
    }
    suspend fun previewPhoto(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        if (ctx.contentResolver.getType(uri)?.startsWith("image/") != true) return@withContext null
        val (bytes, _) = read(ctx, uri)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    suspend fun publish(ctx: Context, text: String, source: Draft?, uri: Uri?): Published = withContext(Dispatchers.IO) {
        require(text.isNotBlank() || source != null || uri != null) { "Ajoutez un message ou un média." }
        val endpoint = Relay.myEndpoint(ctx) ?: error("Activez le serveur dans les préférences sociales.")
        try { Relay.publishOnly(ctx) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error("Connexion au serveur impossible. Réessayez.") }
        var uploaded: Media? = null
        var committed = false
        try {
            if (uri != null) {
                val (bytes, mime) = read(ctx, uri)
                val id = SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
                val key = RelayCrypto.newKey(); val encrypted = crypt(bytes, key, id, true)
                uploaded = Media(id, key, mime, hash(encrypted), encrypted.size)
                http.newCall(Relay.auth(ctx, Request.Builder().url(path(endpoint, id)))
                    .put(encrypted.toRequestBody("application/octet-stream".toMediaType())).build()).execute().use {
                    check(it.isSuccessful) { if (it.code == 404) "Mettez à jour le serveur pour les photos et vidéos." else "Envoi du média impossible. Réessayez." }
                }
                trimCache(ctx); cache(ctx, uploaded).writeBytes(bytes)
            }
            val post = Post(UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis(), text.trim().take(500),
                source?.kind ?: uploaded?.let { if (it.mime.startsWith("video/")) "VIDEO" else "PHOTO" } ?: "TEXT",
                source?.title.orEmpty().take(100), source?.detail.orEmpty().take(1500), uploaded)
            check(valid(post))
            League.configure(ctx) { it.copy(posts = visible(listOf(post) + it.posts)) }
            committed = true
            val sent = try { Relay.publishOnly(ctx); true }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                League.configure(ctx) { it.copy(syncError = "Publication enregistrée. Actualisez le fil pour réessayer l'envoi.") }
                false
            }
            Published(post, sent)
        } catch (e: Exception) {
            if (!committed) uploaded?.let { media -> runCatching { http.newCall(Relay.auth(ctx, Request.Builder().url(path(endpoint, media.id))).delete().build()).execute().close() } }
            throw e
        }
    }
    suspend fun download(ctx: Context, post: Post, friend: League.Friend?): File = withContext(Dispatchers.IO) {
        require(valid(post)); val media = post.media ?: error("Média absent.")
        val cached = cache(ctx, media)
        if (cached.exists()) { cached.setLastModified(System.currentTimeMillis()); return@withContext cached }
        val endpoint = (if (friend == null) Relay.myEndpoint(ctx) else friend.remote)?.takeIf(Relay::encryptedEndpoint) ?: error("Média indisponible.")
        val encrypted = http.newCall(Request.Builder().url(path(endpoint, media.id)).get().build()).execute().use { r ->
            check(r.isSuccessful) { "Média expiré ou indisponible." }
            r.body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    check(out.size() + n <= media.bytes) { "Média non valable." }; out.write(buffer, 0, n) }
                out.toByteArray()
            }
        }
        check(encrypted.size == media.bytes && hash(encrypted) == media.hash) { "Média non valable." }
        val bytes = crypt(encrypted, media.key, media.id, false)
        trimCache(ctx); cached.writeBytes(bytes); cached
    }
    suspend fun delete(ctx: Context, id: String) = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
        val post = League.state.value.posts.firstOrNull { it.id == id } ?: return@withContext
        League.configure(ctx) { it.copy(posts = it.posts.filterNot { p -> p.id == id }) }
        try { Relay.publishOnly(ctx) }
        catch (e: Exception) {
            League.configure(ctx) { it.copy(posts = visible(listOf(post) + it.posts)) }
            error("Suppression distante à réessayer.")
        }
        post.media?.let { media -> Relay.myEndpoint(ctx)?.let { endpoint ->
            http.newCall(Relay.auth(ctx, Request.Builder().url(path(endpoint, media.id))).delete().build()).execute().use { check(it.isSuccessful) }
            cache(ctx, media).delete()
        } }
    }
    fun workoutDraft(w: Workout): Draft {
        val db = WorkoutApp.db.value
        val lines = w.exercises.mapNotNull { entry ->
            val sets = Analytics.workingSets(entry); if (sets.isEmpty()) return@mapNotNull null
            (db?.exercise(entry.exerciseId)?.name ?: "Exercice") + " · " + sets.joinToString(" / ") { set ->
                listOfNotNull(set.weight?.let { "$it kg" }, set.reps?.let { "$it reps" }).joinToString(" × ")
            }
        }
        return Draft(w.name, "${w.durationSec / 60} min\n" + lines.joinToString("\n").take(1400), "WORKOUT")
    }
}
