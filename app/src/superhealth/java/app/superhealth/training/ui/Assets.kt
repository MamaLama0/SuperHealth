package app.superhealth.training.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import app.superhealth.ui.theme.Mf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Images des assets (photos d'exercices, équipements, vignettes de muscles), décodées et mises en cache. */
object Assets {
    private lateinit var app: Context
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun init(context: Context) { app = context.applicationContext }

    fun bitmap(path: String): ImageBitmap? = cache.get(path) ?: runCatching {
        app.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { cache.put(path, it) }
    }.getOrNull()

    fun text(path: String): String? = runCatching { app.assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull()
}

/** Image d'asset chargée hors du fil principal ; repli sur les initiales (comme les vignettes « DS » de MacroFactor). */
@Composable
fun AsyncAsset(path: String?, modifier: Modifier, crop: Boolean = true, fallback: String? = null) {
    val bmp by produceState<ImageBitmap?>(null, path) { value = path?.let { withContext(Dispatchers.IO) { Assets.bitmap(it) } } }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier.background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        if (fallback != null) Text(initials(fallback), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = Mf.colors.textSecondary)
    }
}

fun initials(name: String) = name.split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }.take(2)
    .joinToString("") { it.first().uppercase() }
