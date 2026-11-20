package app.superhealth.training.ui

import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.superhealth.modules.Modules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Animations des illustrations d'exercices, apportées par le plugin « animations » : l'app de base n'a que les images fixes.
 * anim.json donne, pour chaque illustration, l'ordre de lecture (0 = image fixe de l'app, k = k-ième case de la planche
 * anim/<illustration>.webp) et la durée de chaque image en millisecondes.
 */
object ExerciseAnimations {
    @Serializable
    data class Anim(val n: Int = 0, val seq: List<Int> = emptyList(), val ms: List<Int> = emptyList())

    @Serializable
    private data class Index(val size: Int = 360, val cols: Int = 3, val anim: Map<String, Anim> = emptyMap())

    class Loaded(val anim: Anim, val frames: List<ImageBitmap>)

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var index: Index? = null

    /** Plugin installé, mis à jour ou retiré : l'index est relu. */
    fun invalidate() { index = null }

    private fun index(): Index? = index ?: Modules.file(Modules.ANIMATIONS, "anim.json")?.let { f ->
        runCatching { json.decodeFromString(Index.serializer(), f.readText()) }.getOrNull()?.also { index = it }
    }

    /** Animation d'une illustration, images décodées (l'image fixe puis les cases de la planche) ; null sans plugin ou sans mouvement. */
    suspend fun load(illustration: String): Loaded? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Modules.isActive(Modules.ANIMATIONS)) return@runCatching null
            val idx = index() ?: return@runCatching null
            val a = idx.anim[illustration]?.takeIf { a -> a.n > 0 && a.seq.size > 1 && a.seq.size == a.ms.size && a.seq.all { it in 0..a.n } }
                ?: return@runCatching null
            val file = Modules.file(Modules.ANIMATIONS, "anim/$illustration.webp") ?: return@runCatching null
            val still = Assets.bitmap("ex/$illustration.webp") ?: return@runCatching null
            // case par case : la planche entière n'est jamais en mémoire (les plus longues font plus de cinquante images)
            @Suppress("DEPRECATION")
            val decoder = (if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(file.path) else BitmapRegionDecoder.newInstance(file.path, false))
                ?: return@runCatching null
            try {
                Loaded(a, listOf(still) + (0 until a.n).map { k ->
                    val x = (k % idx.cols) * idx.size; val y = (k / idx.cols) * idx.size
                    decoder.decodeRegion(Rect(x, y, x + idx.size, y + idx.size), null).asImageBitmap()
                })
            } finally { decoder.recycle() }
        }.getOrNull()
    }
}

/** Illustration d'un exercice, entière sur son fond blanc ; elle s'anime quand le plugin d'animations est installé. */
@Composable
fun ExerciseIllustration(illustration: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val installed by Modules.installed.collectAsState()
    // animations coupées dans les réglages du téléphone : l'image reste fixe
    val still = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val loaded by produceState<ExerciseAnimations.Loaded?>(null, illustration, installed) {
        value = if (still) null else ExerciseAnimations.load(illustration)
    }
    val l = loaded
    var step by remember(illustration, l) { mutableIntStateOf(0) }
    if (l != null) LaunchedEffect(l) {
        while (true) {
            delay(l.anim.ms[step].toLong().coerceIn(40, 2000))
            withFrameMillis { }     // écran éteint ou app en arrière-plan : l'animation attend
            step = (step + 1) % l.anim.seq.size
        }
    }
    Box(modifier.background(Color.White)) {
        val frame = if (l != null) l.frames.getOrNull(l.anim.seq[step]) else null
        if (frame != null) Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else AsyncAsset("ex/$illustration.webp", Modifier.fillMaxSize(), crop = false)
    }
}
