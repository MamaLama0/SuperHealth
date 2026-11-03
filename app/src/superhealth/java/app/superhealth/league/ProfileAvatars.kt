package app.superhealth.league

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.superhealth.backup.Secrets
import app.superhealth.ui.ProfilePhoto
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Vignette de profil chiffrée et référencée dans la carte signée, hors des invitations publiques. */
object ProfileAvatars {
    fun valid(media: SocialPosts.Media) = SocialPosts.validMedia(media) && media.mime == "image/jpeg" && media.bytes <= 100_028

    // Appelé sous le verrou de publication de Relay, avant de signer la carte complète.
    internal fun prepare(ctx: Context, endpoint: String): SocialPosts.Media? {
        if (!League.state.value.shareProfilePhoto || !ProfilePhoto.file(ctx).isFile) return null
        val bytes = ProfilePhoto.file(ctx).readBytes()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes + endpoint.toByteArray()).joinToString("") { "%02x".format(it) }
        val previous = Secrets.get(ctx, "profile_avatar_media")?.let { runCatching { Json.decodeFromString(SocialPosts.Media.serializer(), it) }.getOrNull() }
        val published = Secrets.get(ctx, "profile_avatar_at")?.toLongOrNull() ?: 0
        if (previous != null && valid(previous) && Secrets.get(ctx, "profile_avatar_source") == fingerprint &&
            System.currentTimeMillis() - published < 86400000) return previous
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val small = Bitmap.createScaledBitmap(original, 192, 192, true)
        val image = ByteArrayOutputStream().use { out -> small.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
        if (small !== original) small.recycle()
        original.recycle()
        return SocialPosts.uploadAvatar(ctx, endpoint, image).also { media ->
            Secrets.put(ctx, "profile_avatar_media", Json.encodeToString(SocialPosts.Media.serializer(), media))
            Secrets.put(ctx, "profile_avatar_source", fingerprint)
            Secrets.put(ctx, "profile_avatar_at", System.currentTimeMillis().toString())
        }
    }
}
