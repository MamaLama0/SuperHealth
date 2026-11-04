package app.superhealth.league

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.superhealth.training.domain.Workout
import app.superhealth.training.domain.Analytics
import app.superhealth.training.WorkoutApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Only explicitly published summaries enter the signed, encrypted friend card. GPS routes require the visible, per-post sharing option. */
object SocialPosts {
    const val MAX_PHOTO_BYTES = 15 * 1024 * 1024
    const val MAX_BYTES = 50 * 1024 * 1024
    fun sourceLimit(mime: String) = if (mime.startsWith("image/")) MAX_PHOTO_BYTES else MAX_BYTES
    const val MAX_POSTS = 12
    @Serializable data class Media(val id: String, val key: String, val mime: String, val hash: String, val bytes: Int)
    @Serializable data class Post(val id: String, val created: Long, val text: String = "", val kind: String = "TEXT",
        val title: String = "", val detail: String = "", val media: Media? = null, val record: GroupRecords.Record? = null,
        val workout: SharedActivity.Strength? = null, val activity: SharedActivity.Outdoor? = null)
    @Serializable data class Draft(val title: String, val detail: String, val kind: String, val record: GroupRecords.Record? = null,
        val workout: SharedActivity.Strength? = null, val activity: SharedActivity.Outdoor? = null) {
        fun shared(route: Boolean, details: Boolean) = copy(
            detail = if (!details && workout != null) "${workout.seconds / 60} min" else detail,
            record = record?.let { if (details) it else it.copy(previous = null, trail = emptyList(), muscles = emptyList()) },
            workout = workout?.let { if (details) it else it.copy(exercises = emptyList()) },
            activity = activity?.let { (if (details) it else it.withoutDetails()).copy(route = it.route.takeIf { route }) })
    }
    data class Published(val post: Post, val sent: Boolean)
    internal class MissingMediaMailbox : IllegalStateException("La boîte de partage doit être créée avant le média.")
    val draft = MutableStateFlow<Draft?>(null)
    private val kinds = setOf("TEXT", "PHOTO", "VIDEO", "WORKOUT", "RUN", "STREAK", "PR")
    private val mimes = setOf("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm", "video/3gpp")
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val postJson = kotlinx.serialization.json.Json { encodeDefaults = true }
    fun valid(post: Post, now: Long = System.currentTimeMillis()): Boolean = post.id.matches(Regex("[a-f0-9]{32}")) &&
        post.created in 1..(now + 300000) && post.text.length <= 500 && post.title.length <= 100 && post.detail.length <= 1500 &&
        post.kind in kinds && (post.workout == null || (post.kind == "WORKOUT" && post.activity == null && SharedActivity.valid(post.workout, now))) &&
        (post.activity == null || (post.kind in setOf("RUN", "WORKOUT") && SharedActivity.valid(post.activity, now))) &&
        (post.record?.let { post.kind == "PR" && post.workout == null && post.activity == null && GroupRecords.valid(it, now) } ?: (post.kind != "PR")) && (post.media?.let(::validMedia) ?: (post.kind !in setOf("PHOTO", "VIDEO"))) &&
        postJson.encodeToString(Post.serializer(), post).toByteArray().size <= 11000
    /** Preserve the message and totals; bound optional visual samples to the relay's signed-event budget. */
    internal fun bounded(original: Post): Post {
        var p = if (original.workout != null || original.activity != null) original.copy(detail = original.detail.take(320)) else original
        while (postJson.encodeToString(Post.serializer(), p).toByteArray().size > 11000) {
            val w = p.workout; val a = p.activity
            p = when {
                w != null && w.exercises.isNotEmpty() -> p.copy(workout = w.copy(exercises = w.exercises.dropLast(1)))
                a?.route != null && SharedActivity.Route.decode(a.route)!!.size > 12 -> p.copy(activity = a.copy(route =
                    SharedActivity.Route.encode(SharedActivity.sample(SharedActivity.Route.decode(a.route)!!, SharedActivity.Route.decode(a.route)!!.size / 2))))
                a != null && (a.speed.isNotEmpty() || a.elevation.isNotEmpty() || a.heart.isNotEmpty()) -> p.copy(activity = a.copy(speed = emptyList(), elevation = emptyList(), heart = emptyList()))
                else -> error("Publication trop volumineuse.")
            }
        }
        return p
    }
    /** Discovery cards stay compact; the independent signed archive carries full activity snapshots. */
    internal fun compact(post: Post) = post.copy(workout = null, activity = null,
        record = post.record?.copy(previous = null, trail = emptyList(), muscles = emptyList()))
    fun validMedia(m: Media) = m.id.matches(Regex("[a-f0-9]{64}")) && RelayCrypto.validKey(m.key) &&
        m.mime in mimes && m.hash.matches(Regex("[a-f0-9]{64}")) && m.bytes in 29..(MAX_BYTES + 28)
    fun visible(posts: List<Post>, now: Long = System.currentTimeMillis()) = posts.filter { valid(it, now) && it.created >= now - GroupHistory.RETENTION_DAYS.toLong() * 86400000 }.sortedByDescending { it.created }.take(MAX_POSTS)
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
            total += f.length(); if (total > 128L * 1024 * 1024) f.delete()
        }
    }
    private fun read(ctx: Context, uri: Uri): Pair<ByteArray, String> {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime in mimes) { "Format non pris en charge." }
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= sourceLimit(mime)) { "Choisissez une photo de moins de 15 Mo ou une vidéo de moins de 50 Mo." }; out.write(buffer, 0, n) }
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
    suspend fun publish(ctx: Context, text: String, source: Draft?, uri: Uri?, publicationId: String = UUID.randomUUID().toString().replace("-", "")): Published = withContext(Dispatchers.IO) {
        check(!SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        require(text.isNotBlank() || source != null || uri != null) { "Ajoutez un message ou un média." }
        require(Relay.base() != null) { "Activez le serveur dans les préférences sociales." }
        require(publicationId.matches(Regex("[a-f0-9]{32}")))
        GroupHistory.posts(ctx).firstOrNull { it.second == null && it.first.id == publicationId }?.let {
            val sent = try { Relay.publishOnly(ctx); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
            return@withContext Published(it.first, sent)
        }
        val upload = uri?.let { prepareUpload(ctx, it) }
        try {
            val post = bounded(Post(publicationId, System.currentTimeMillis(), text.trim().take(500),
                source?.kind ?: upload?.let { if (it.media.mime.startsWith("video/")) "VIDEO" else "PHOTO" } ?: "TEXT",
                source?.title.orEmpty().take(100), source?.detail.orEmpty().take(1500), upload?.media, source?.record, source?.workout, source?.activity))
            require(valid(post))
            // Persist the encrypted attachment and its exact post before any network operation.
            SocialOutbox.enqueue(ctx, post, upload?.encrypted)
            GroupHistory.record(ctx, post)
            League.configure(ctx) { it.copy(posts = visible(listOf(post) + it.posts)) }
            upload?.let { it.plain.copyTo(cache(ctx, it.media), overwrite = true); trimCache(ctx) }
            val sent = try { Relay.publishOnly(ctx); true }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { false }
            Published(post, sent)
        } finally { upload?.plain?.delete(); upload?.encrypted?.delete() }
    }

    suspend fun edit(ctx: Context, post: Post, text: String): Boolean = withContext(Dispatchers.IO) {
        check(!SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        require(GroupHistory.posts(ctx).any { it.second == null && it.first.id == post.id })
        val updated = post.copy(text = text.trim().take(500))
        require(valid(updated) && (updated.text.isNotBlank() || updated.media != null || updated.kind != "TEXT"))
        SocialSpaces.repository(ctx).moderateLegacy(post.id,"EDIT")
        SocialOutbox.replace(ctx, updated)
        GroupHistory.record(ctx, updated)
        League.configure(ctx) { state -> state.copy(posts = state.posts.map { if (it.id == post.id) updated else it }) }
        try { Relay.publishOnly(ctx); true }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    internal fun localMedia(ctx: Context, post: Post): File? = post.media?.let { cache(ctx, it).takeIf(File::exists) }
    internal fun sendAttachment(ctx: Context, endpoint: String, media: Media, file: File, writer: String? = null) {
        require(file.isFile && file.length() == media.bytes.toLong())
        val digest = MessageDigest.getInstance("SHA-256")
        java.security.DigestInputStream(file.inputStream(), digest).use { input -> val buffer = ByteArray(65536); while (input.read(buffer) >= 0) { } }
        require(digest.digest().joinToString("") { "%02x".format(it) } == media.hash)
        val request = Relay.auth(ctx, Request.Builder().url(path(endpoint, media.id))).header("X-Content-SHA256", media.hash)
        writer?.let { request.header("X-Write-Token", it) }
        http.newCall(request.put(file.asRequestBody("application/octet-stream".toMediaType())).build()).execute().use {
            if (it.code == 409) {
                http.newCall(Request.Builder().url(path(endpoint, media.id)).get().build()).execute().use { existing ->
                    check(existing.isSuccessful)
                    val previous = MessageDigest.getInstance("SHA-256"); var count = 0L
                    existing.body.byteStream().use { input -> val buffer = ByteArray(65536); while (true) {
                        val n = input.read(buffer); if (n < 0) break; count += n; require(count <= media.bytes); previous.update(buffer, 0, n)
                    } }
                    require(count == media.bytes.toLong() && previous.digest().joinToString("") { b -> "%02x".format(b) } == media.hash)
                }
                return
            }
            check(it.isSuccessful) { when (it.code) {
                413 -> "Ce média dépasse la limite du serveur."
                507 -> "Le stockage du serveur est plein."
                else -> "Envoi du média impossible. Réessayez."
            } }
        }
    }
    suspend fun download(ctx: Context, post: Post, friend: League.Friend?): File = withContext(Dispatchers.IO) {
        require(valid(post)); val media = post.media ?: error("Média absent.")
        downloadMedia(ctx, media, friend)
    }
    internal suspend fun downloadMedia(ctx: Context, media: Media, friend: League.Friend?, endpointOverride: String? = null): File = withContext(Dispatchers.IO) {
        require(validMedia(media))
        val cached = cache(ctx, media)
        if (cached.exists()) { cached.setLastModified(System.currentTimeMillis()); return@withContext cached }
        val endpoint = (endpointOverride ?: if (friend == null) Relay.myEndpoint(ctx) else friend.remote)?.takeIf(Relay::encryptedEndpoint) ?: error("Média indisponible.")
        val encrypted = File.createTempFile("social-download-", ".enc", ctx.cacheDir)
        val plain = File.createTempFile("social-download-", ".plain", ctx.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            http.newCall(Request.Builder().url(path(endpoint, media.id)).get().build()).execute().use { r ->
                check(r.isSuccessful) { "Média expiré ou indisponible." }
                r.body.byteStream().use { input -> encrypted.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        count += n; check(count <= media.bytes) { "Média non valable." }
                        digest.update(buffer, 0, n); out.write(buffer, 0, n)
                    }
                } }
            }
            check(count == media.bytes.toLong() && digest.digest().joinToString("") { "%02x".format(it) } == media.hash) { "Média non valable." }
            encrypted.inputStream().use { input ->
                val nonce = ByteArray(12); java.io.DataInputStream(input).readFully(nonce)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.getUrlDecoder().decode(media.key), "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(("SuperHealth media v1:" + media.id).toByteArray())
                javax.crypto.CipherInputStream(input, cipher).use { source -> plain.outputStream().use { source.copyTo(it) } }
            }
            check(plain.length() == media.bytes - 28L) { "Média non valable." }
            check(plain.renameTo(cached))
            trimCache(ctx)
            cached
        } finally { encrypted.delete(); plain.delete() }
    }

    suspend fun prepareCircleMedia(ctx: Context, uri: Uri): app.superhealth.social.SpaceAttachment = withContext(Dispatchers.IO) {
        val repository = SocialSpaces.repository(ctx)
        repository.initialize()
        val endpoint = repository.state.value.own!!.endpoint
        val directory = File(ctx.filesDir, "league/space-media").apply { mkdirs() }
        require(directory.listFiles().orEmpty().sumOf { it.length() } < 250L * 1024 * 1024) { "La file de médias est pleine." }
        val upload = prepareUpload(ctx, uri)
        try {
            val target = android.util.AtomicFile(File(directory, upload.media.id + ".enc")); val out = target.startWrite()
            try { upload.encrypted.inputStream().use { it.copyTo(out) }; target.finishWrite(out) }
            catch (e: Exception) { target.failWrite(out); throw e }
            upload.plain.copyTo(cache(ctx, upload.media), overwrite = true)
            val media = upload.media
            app.superhealth.social.SpaceAttachment(media.id, media.key, media.mime, media.hash, media.bytes, endpoint)
        } finally { upload.plain.delete(); upload.encrypted.delete() }
    }
    fun sendCircleMedia(ctx: Context, media: app.superhealth.social.SpaceAttachment) {
        require(media.endpoint == SocialSpaces.contact()?.endpoint)
        val directory = File(ctx.filesDir, "league/space-media")
        val marker = File(directory, media.id + ".uploaded")
        if (marker.isFile) return
        val file = File(directory, media.id + ".enc")
        sendAttachment(ctx, media.endpoint, Media(media.id, media.key, media.mime, media.hash, media.bytes), file, app.superhealth.backup.Secrets.get(ctx, "social_space_writer"))
        val atomic = android.util.AtomicFile(marker); val out = atomic.startWrite()
        try { out.write(media.hash.toByteArray()); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
        file.delete()
    }

    private data class Upload(val media: Media, val plain: File, val encrypted: File)
    private suspend fun prepareUpload(ctx: Context, uri: Uri): Upload {
        val mime = ctx.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime in mimes) { "Format non pris en charge." }
        val plain = File.createTempFile("social-upload-", ".plain", ctx.cacheDir)
        val encrypted = File.createTempFile("social-upload-", ".enc", ctx.cacheDir)
        try {
            val actualMime = if (mime.startsWith("image/")) {
                val image = read(ctx, uri); plain.writeBytes(image.first); image.second
            } else {
                var count = 0L
                ctx.contentResolver.openInputStream(uri)!!.use { input -> plain.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        count += n; require(count <= MAX_BYTES) { "Choisissez une vidéo de moins de 50 Mo." }
                        out.write(buffer, 0, n)
                    }
                } }; mime
            }
            require(plain.length() > 0) { "Média illisible." }
            val id = SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
            val key = RelayCrypto.newKey(); val nonce = SecureRandom().generateSeed(12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.getUrlDecoder().decode(key), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(("SuperHealth media v1:" + id).toByteArray())
            encrypted.outputStream().use { out ->
                out.write(nonce)
                javax.crypto.CipherOutputStream(out, cipher).use { target -> plain.inputStream().use { it.copyTo(target) } }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            java.security.DigestInputStream(encrypted.inputStream(), digest).use { input ->
                val buffer = ByteArray(64 * 1024); while (input.read(buffer) >= 0) { kotlinx.coroutines.currentCoroutineContext().ensureActive() }
            }
            return Upload(Media(id, key, actualMime, digest.digest().joinToString("") { "%02x".format(it) }, encrypted.length().toInt()), plain, encrypted)
        } catch (e: Exception) { plain.delete(); encrypted.delete(); throw e }
    }

    internal fun uploadAvatar(ctx: Context, endpoint: String, bytes: ByteArray): Media {
        require(bytes.size in 1..100_000)
        val id = SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
        val key = RelayCrypto.newKey(); val encrypted = crypt(bytes, key, id, true)
        val media = Media(id, key, "image/jpeg", hash(encrypted), encrypted.size)
        http.newCall(Relay.auth(ctx, Request.Builder().url(path(endpoint, id)))
            .put(encrypted.toRequestBody("application/octet-stream".toMediaType())).build()).execute().use {
            if (it.code == 404) throw MissingMediaMailbox()
            check(it.isSuccessful) { "Envoi de la photo de profil impossible. Réessayez." }
        }
        cache(ctx, media).writeBytes(bytes)
        return media
    }
    suspend fun delete(ctx: Context, id: String) = withContext(Dispatchers.IO) {
        check(!SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        val post = GroupHistory.posts(ctx).firstOrNull { it.second == null && it.first.id == id }?.first ?: return@withContext
        GroupHistory.delete(ctx, post)
        SocialOutbox.cancel(ctx, id)
        League.configure(ctx) { it.copy(posts = it.posts.filterNot { p -> p.id == id }) }
        Relay.publishOnly(ctx)
        post.media?.let { cache(ctx, it).delete() }
    }
    fun workoutDraft(w: Workout): Draft {
        val db = WorkoutApp.db.value
        val lines = w.exercises.mapNotNull { entry ->
            val sets = Analytics.workingSets(entry); if (sets.isEmpty()) return@mapNotNull null
            (db?.exercise(entry.exerciseId)?.name ?: "Exercice") + " · " + sets.joinToString(" / ") { set ->
                listOfNotNull(set.weight?.let { "$it kg" }, set.reps?.let { "$it reps" }).joinToString(" × ")
            }
        }
        return Draft(w.name, "${w.durationSec / 60} min\n" + lines.joinToString("\n").take(1400), "WORKOUT", workout = SharedActivity.strength(w, db))
    }

    fun activityDraft(a: app.superhealth.bridge.Bridge.Activity): Draft = Draft(a.title,
        listOfNotNull(a.distanceM?.takeIf { it.isFinite() && it > 0 }?.let { app.superhealth.nutrition.ui.num(it / 1000, 2) + " km" },
            "${a.minutes.coerceAtLeast(0)} min").joinToString(" · "),
        if (a.kind == app.superhealth.bridge.Bridge.Kind.RUN) "RUN" else "WORKOUT")
}
