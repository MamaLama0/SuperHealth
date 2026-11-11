package app.superhealth.ui.cards

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Local-only sprite: trim transparent margins so the silhouette's bottom meets the arena floor. */
@Composable
internal fun duelPortrait(image: ImageBitmap?): String? {
    val encoded by produceState<String?>(null, image) {
        value = null
        if (image == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val source = image.asAndroidBitmap()
            val ratio = minOf(1f, 768f / maxOf(source.width, source.height))
            val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(source,
                (source.width * ratio).toInt().coerceAtLeast(1), (source.height * ratio).toInt().coerceAtLeast(1), true) else source
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            var left = scaled.width; var top = scaled.height; var right = -1; var bottom = -1
            for (y in 0 until scaled.height) for (x in 0 until scaled.width) {
                if ((pixels[y * scaled.width + x] ushr 24) > 8) {
                    left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            val trimmed = if (right >= left) Bitmap.createBitmap(scaled, left, top, right - left + 1, bottom - top + 1) else scaled
            val bytes = ByteArrayOutputStream().use { output -> trimmed.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
            if (trimmed !== source && trimmed !== scaled) trimmed.recycle()
            if (scaled !== source) scaled.recycle()
            "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
    return encoded
}
