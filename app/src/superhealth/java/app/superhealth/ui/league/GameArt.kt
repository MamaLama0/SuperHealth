package app.superhealth.ui.league

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import app.superhealth.modules.Modules

/** Badges du Groupe : livrés par son plugin (dossier game/), absents de l'app de base. */
enum class GameArtKind(val asset: String) {
    FLAME("game/flame.png"), XP("game/xp.png"), TROPHY("game/trophy.png"),
    PERFECT_WEEK("game/perfect-week.png"), FLAME_INACTIVE("game/flame-inactive.png"), XP_EMPTY("game/xp-empty.png")
}

private val gameArt = HashMap<String, ImageBitmap>()

/** Image du plugin Groupe ([path] relatif à son dossier), gardée en mémoire ; null sans plugin. */
fun pluginImage(path: String): ImageBitmap? = synchronized(gameArt) {
    gameArt[path] ?: Modules.file(Modules.LEAGUE, path)?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }?.also { gameArt[path] = it }
}

@Composable
fun GameArt(kind: GameArtKind, modifier: Modifier = Modifier, animated: Boolean = false, count: Int = 1) {
    val bmp = remember(kind) { pluginImage(kind.asset) } ?: return
    if (animated && kind == GameArtKind.FLAME) StreakFlame(bmp, count, modifier)
    else Image(bmp, null, modifier, contentScale = ContentScale.Fit)
}

/**
 * Flamme de série animée en Compose (sans bibliothèque d'animation) : elle vacille et rayonne dans la couleur de la
 * série, avec le nombre de jours dessous. Immobile quand les animations du système sont coupées.
 */
@Composable
private fun StreakFlame(bmp: ImageBitmap, count: Int, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val motion = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val t = if (motion) androidx.compose.animation.core.rememberInfiniteTransition(label = "flame").animateFloat(0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "t").value else 0f
    val wave = kotlin.math.sin(t * 2 * Math.PI).toFloat(); val flick = kotlin.math.sin(t * 6 * Math.PI).toFloat()
    val hue = streakColor(count)
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.matchParentSize()) {
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(hue.copy(alpha = 0.45f + 0.15f * wave), Color.Transparent),
                center.copy(y = size.height * 0.55f), size.minDimension * (0.52f + 0.04f * flick)))
        }
        Image(bmp, null, androidx.compose.ui.Modifier.matchParentSize().flicker(wave, flick), contentScale = ContentScale.Fit)
        androidx.compose.material3.Text(count.toString(), androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            .background(app.superhealth.ui.theme.Mf.colors.card, androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = hue)
    }
}

/** Vacillement : la flamme s'étire et penche un peu, ancrée à sa base. */
private fun Modifier.flicker(wave: Float, flick: Float) = graphicsLayer {
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.92f)
    scaleY = 1f + 0.05f * wave + 0.02f * flick; scaleX = 1f - 0.025f * wave
    rotationZ = 2.5f * flick
}

internal fun streakColor(count: Int): Color = when {
    count >= 100 -> Color(0xFF9B6DFF)
    count >= 30 -> Color(0xFF39B9F8)
    count >= 14 -> Color(0xFFEF5261)
    count >= 7 -> Color(0xFFFFB628)
    else -> Color(0xFFFF7C25)
}

/** Visuel de la catégorie de série, partagé par les missions, le parcours et les profils amis. */
@Composable
internal fun TrackArt(track: String, count: Int, modifier: Modifier = Modifier) = androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
    val weekly = app.superhealth.league.Progression.Track.entries.firstOrNull { it.name == track }?.weekly == true
    val rank = (if (weekly) listOf(1, 4, 12, 26, 52, 104) else listOf(3, 7, 30, 60, 100, 365, 730)).count { count >= it }.minus(1).coerceAtLeast(0)
    BadgeEmblem("streak:$track", rank, minOf(maxWidth, maxHeight), locked = count == 0)
}
