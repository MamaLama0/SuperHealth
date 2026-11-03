package app.superhealth.ui

import app.superhealth.platform.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap

/** Photo facultative, réduite et gardée localement ; elle n'entre jamais dans les cartes partagées. */
object ProfilePhoto {
    val bitmap = mutableStateOf<ImageBitmap?>(null)
    fun file(ctx: Context) = File(ctx.filesDir, "profile.jpg")
    fun load(ctx: Context) { bitmap.value = runCatching { decodeImage(file(ctx).readBytes()) }.getOrNull() }
    fun set(ctx: Context, bytes: ByteArray): Boolean = runCatching {
        val jpeg = downscaleJpeg(bytes, 512, 88)
        check(decodeImage(jpeg) != null)
        val pending = File(ctx.filesDir, "profile.jpg.tmp"); pending.writeBytes(jpeg)
        check(pending.renameTo(file(ctx)))
        load(ctx)
    }.isSuccess
    fun clear(ctx: Context) { file(ctx).delete(); bitmap.value = null }
}
