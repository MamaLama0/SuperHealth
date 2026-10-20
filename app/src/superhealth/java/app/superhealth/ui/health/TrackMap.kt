package app.superhealth.ui.health

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.ui.theme.Mf
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Coordonnées « monde » Web Mercator, en unités de tuile de 256 au niveau [z]. */
private fun worldX(lon: Double, z: Int) = (lon + 180.0) / 360.0 * 256.0 * (1 shl z)
private fun worldY(lat: Double, z: Int): Double {
    val r = lat * PI / 180
    return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * 256.0 * (1 shl z)
}

/** Tuiles OpenStreetMap standard, gardées en cache sur le téléphone (politique d'usage : User-Agent identifié, cache local). */
private object Tiles {
    private val memory = object : android.util.LruCache<String, ImageBitmap>(96) {}

    fun cached(key: String): ImageBitmap? = memory.get(key)

    suspend fun load(dir: File, z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "osm/$z/$x/$y"
        memory.get(key)?.let { return@withContext it }
        val f = File(dir, "$key.png")
        // Cartes en ligne désactivées : seules les tuiles déjà en cache s'affichent, aucune requête.
        if ((!f.exists() || f.length() == 0L) && app.superhealth.SuperPrefs.settings.value.onlineMaps) runCatching {
            val c = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "SuperHealth/1.0 (personal health app)")
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode == 200) { f.parentFile?.mkdirs(); c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } } }
            c.disconnect()
        }
        runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()?.also { memory.put(key, it) }
    }
}

/**
 * Carte du tracé (comme la mini-carte de Garmin Connect) : le parcours cadré au plus serré, départ en vert, arrivée en
 * damier. [interactive] : glisser pour déplacer, pincer ou boutons pour zoomer.
 */
@Composable
fun TrackMap(
    track: List<Pair<Double, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (track.size < 2) return
    val ctx = LocalContext.current
    val dark = Mf.colors.dark
    val dir = remember { File(ctx.cacheDir, "map-tiles") }
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.clip(RoundedCornerShape(if (interactive) 0.dp else 20.dp)).background(Mf.colors.chip)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        val w = constraints.maxWidth / density
        val h = constraints.maxHeight / density
        val lats = track.map { it.first }; val lons = track.map { it.second }
        // Zoom le plus fort qui fait tenir tout le parcours avec une marge.
        val fitZoom = remember(track, w, h) {
            (17 downTo 2).firstOrNull { z ->
                worldX(lons.max(), z) - worldX(lons.min(), z) <= w * 0.82 && worldY(lats.min(), z) - worldY(lats.max(), z) <= h * 0.78
            } ?: 2
        }
        var zoom by remember(fitZoom) { mutableIntStateOf(fitZoom) }
        var center by remember(fitZoom) {
            mutableStateOf(Offset(((worldX(lons.min(), fitZoom) + worldX(lons.max(), fitZoom)) / 2).toFloat(),
                ((worldY(lats.min(), fitZoom) + worldY(lats.max(), fitZoom)) / 2).toFloat()))
        }
        var pinch by remember { mutableStateOf(1f) }
        val left = center.x - w / 2; val top = center.y - h / 2
        // Tuiles du niveau suivant (128 unités chacune) : deux fois plus de détails, nettes sur un écran haute densité.
        val tz = (zoom + 1).coerceAtMost(19)
        val tile = 256.0 * (1 shl zoom) / (1 shl tz)
        val tiles = remember(zoom, floor(left / tile), floor(top / tile), w, h) {
            val n = 1 shl tz
            (floor(left / tile).toInt()..floor((left + w) / tile).toInt()).flatMap { tx ->
                (floor(top / tile).toInt()..floor((top + h) / tile).toInt()).filter { it in 0 until n }.map { ty -> tx to ty }
            }
        }
        val loaded = remember { mutableStateMapOf<String, ImageBitmap>() }
        LaunchedEffect(tiles) {
            tiles.forEach { (tx, ty) ->
                val n = 1 shl tz
                val key = "$tz/$tx/$ty"
                if (key !in loaded) Tiles.load(dir, tz, ((tx % n) + n) % n, ty)?.let { loaded[key] = it }
            }
        }
        // Mode sombre : tuiles inversées, teinte tournée de 180° (l'eau reste bleue), désaturées et assombries.
        val filter = remember(dark) {
            if (!dark) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                0.0512f, -0.7915f, -0.0797f, 0f, 209.10f,
                -0.2358f, -0.5045f, -0.0797f, 0f, 209.10f,
                -0.2358f, -0.7915f, 0.2073f, 0f, 209.10f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val casing = if (dark) Color.Black.copy(alpha = 0.55f) else Color.White
        Canvas(Modifier.fillMaxSize().then(if (interactive) Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                center -= pan / density
                pinch *= gestureZoom
                if (pinch > 1.6f && zoom < 18) { zoom++; center *= 2f; pinch = 1f }
                if (pinch < 0.6f && zoom > 2) { zoom--; center /= 2f; pinch = 1f }
            }
        } else Modifier)) {
            val tilePx = (tile * density).toFloat()
            tiles.forEach { (tx, ty) ->
                loaded["$tz/$tx/$ty"]?.let { img ->
                    drawImage(img, srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(((tx * tile - left) * density).toInt(), ((ty * tile - top) * density).toInt()),
                        dstSize = IntSize(tilePx.toInt() + 1, tilePx.toInt() + 1), colorFilter = filter)
                }
            }
            fun px(p: Pair<Double, Double>) = Offset(((worldX(p.second, zoom) - left) * density).toFloat(), ((worldY(p.first, zoom) - top) * density).toFloat())
            val path = Path()
            track.forEachIndexed { i, p -> val o = px(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, casing, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val s = px(track.first()); val e = px(track.last())
            drawCircle(Color.White, 7.dp.toPx(), s); drawCircle(Color(0xFF1F9E85), 5.dp.toPx(), s)
            // Arrivée : petit damier.
            drawCircle(Color.White, 7.dp.toPx(), e)
            val q = 2.5.dp.toPx()
            for (i in -1..0) for (j in -1..0) drawRect(if ((i + j) % 2 == 0) Color.Black else Color.White,
                topLeft = Offset(e.x + i * q, e.y + j * q), size = androidx.compose.ui.geometry.Size(q, q))
        }
        Text("© OpenStreetMap", fontSize = 8.sp, color = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 8.dp, vertical = 4.dp))
        if (interactive) Column(Modifier.align(Alignment.CenterEnd).padding(12.dp)) {
            MapButton(Icons.Rounded.Add) { if (zoom < 18) { zoom++; center *= 2f } }
            MapButton(Icons.Rounded.Remove) { if (zoom > 2) { zoom--; center /= 2f } }
            MapButton(Icons.Rounded.CenterFocusStrong) {
                zoom = fitZoom
                center = Offset(((worldX(lons.min(), fitZoom) + worldX(lons.max(), fitZoom)) / 2).toFloat(),
                    ((worldY(lats.min(), fitZoom) + worldY(lats.max(), fitZoom)) / 2).toFloat())
            }
        }
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.padding(vertical = 4.dp).size(44.dp).clip(CircleShape).background(Mf.colors.card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Mf.colors.text) }
}
