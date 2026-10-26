package app.superhealth.platform

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/*
 * Images : android.graphics.Bitmap / BitmapFactory et AssetManager réduits à ce que l'app emploie, décodés par Skia
 * (PNG, JPEG, WebP).
 */

class Bitmap internal constructor(val image: ImageBitmap) {
    val width: Int get() = image.width
    val height: Int get() = image.height
    fun recycle() = Unit
}

fun Bitmap.asImageBitmap(): ImageBitmap = image

/** Décode une image (PNG, JPEG, WebP) ; null si illisible. */
fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { bytes.decodeToImageBitmap() }.getOrNull()

object BitmapFactory {
    class Options {
        var inSampleSize = 1
        var inJustDecodeBounds = false
        var outWidth = 0
        var outHeight = 0
    }

    fun decodeByteArray(b: ByteArray, offset: Int = 0, length: Int = b.size, opts: Options? = null): Bitmap? {
        val img = decodeImage(if (offset == 0 && length == b.size) b else b.copyOfRange(offset, offset + length)) ?: return null
        if (opts != null) { opts.outWidth = img.width; opts.outHeight = img.height; if (opts.inJustDecodeBounds) return null }
        return Bitmap(img)
    }

    fun decodeStream(input: InputStream?, pad: Any? = null, opts: Options? = null): Bitmap? = input?.let { decodeByteArray(it.readBytes(), opts = opts) }
    fun decodeStream(input: InputStream?): Bitmap? = decodeStream(input, null, null)
    fun decodeFile(path: String, opts: Options? = null): Bitmap? = runCatching { File(path).readBytes() }.getOrNull()?.let { decodeByteArray(it, opts = opts) }
}

/** android.content.res.AssetManager : fichiers du dossier « assets » de l'app. */
object AssetManager {
    fun open(path: String): InputStream = InputStream(readAsset(path) ?: throw IOException("asset absent : $path"))
    fun list(dir: String): Array<String>? = listAssets(dir).toTypedArray()
}

val Context.assets: AssetManager get() = AssetManager
