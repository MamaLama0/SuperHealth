package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.platform.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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

    /** [sample] > 1 : image réduite (vignettes), décodage bien plus rapide et cache moins encombré. */
    fun bitmap(path: String, sample: Int = 1): ImageBitmap? = cached(path, sample) ?: runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        app.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()?.also { cache.put(key(path, sample), it) }
    }.getOrNull()

    fun cached(path: String, sample: Int = 1): ImageBitmap? = cache.get(key(path, sample))
    private fun key(path: String, sample: Int) = if (sample <= 1) path else "$path@$sample"

    fun text(path: String): String? = runCatching { app.assets.open(path).use { it.readBytes().decodeToString() } }.getOrNull()
}

/** Image d'asset chargée hors du fil principal ; repli sur les initiales (comme les vignettes « DS » de MacroFactor). */
@Composable
fun AsyncAsset(path: String?, modifier: Modifier, crop: Boolean = true, fallback: String? = null, sample: Int = 1) {
    // image déjà en cache : affichée tout de suite, sans passer par les initiales
    val bmp by produceState(path?.let { Assets.cached(it, sample) }, path) { if (value == null) value = path?.let { withContext(Dispatchers.IO) { Assets.bitmap(it, sample) } } }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier.background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        if (fallback != null) Text(initials(fallback), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = Mf.colors.textSecondary)
    }
}

fun initials(name: String) = name.split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }.take(2)
    .joinToString("") { it.first().uppercase() }
