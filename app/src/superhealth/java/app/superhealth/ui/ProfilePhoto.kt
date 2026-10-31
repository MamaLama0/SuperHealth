package app.superhealth.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.min

/** Photo de profil : carré de 512 px recadré au centre, gardé sur le téléphone (et dans la sauvegarde), jamais envoyé ailleurs. */
object ProfilePhoto {
    val bitmap = mutableStateOf<ImageBitmap?>(null)

    fun file(ctx: Context) = File(ctx.filesDir, "profile.jpg")

    fun load(ctx: Context) {
        bitmap.value = file(ctx).takeIf { it.isFile }?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
    }

    fun set(ctx: Context, uri: Uri): Boolean = runCatching {
        // lecture réduite d'abord : une photo de 50 Mpx ne doit pas tenir en mémoire
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
        val side = min(src.width, src.height)
        val square = Bitmap.createScaledBitmap(Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side), min(side, 512), min(side, 512), true)
        file(ctx).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        load(ctx)
    }.isSuccess

    fun clear(ctx: Context) { file(ctx).delete(); bitmap.value = null }
}
