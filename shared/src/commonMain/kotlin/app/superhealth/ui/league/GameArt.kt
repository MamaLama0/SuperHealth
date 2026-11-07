package app.superhealth.ui.league

import app.superhealth.platform.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.modules.Modules
import app.superhealth.ui.theme.Mf

/** Badges du Groupe : livrés par son plugin (dossier game/), absents de l'app de base. */
enum class GameArtKind(val asset: String) {
    FLAME("game/flame.png"), XP("game/xp.png"), TROPHY("game/trophy.png"),
    PERFECT_WEEK("game/perfect-week.png"), FLAME_INACTIVE("game/flame-inactive.png"), XP_EMPTY("game/xp-empty.png")
}

private val gameArt = HashMap<String, ImageBitmap>()

/** Image du plugin Groupe ([path] relatif à son dossier), gardée en mémoire ; null sans plugin. */
fun pluginImage(path: String): ImageBitmap? = gameArt[path]
    ?: Modules.file(Modules.LEAGUE, path)?.let { decodeImage(it.readBytes()) }?.also { gameArt[path] = it }

@Composable
private fun PluginImage(path: String, modifier: Modifier) {
    val bmp = remember(path) { pluginImage(path) } ?: return
    Image(bmp, null, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun GameArt(kind: GameArtKind, modifier: Modifier = Modifier, animated: Boolean = false, count: Int = 1) {
    if (animated && kind == GameArtKind.FLAME) StreakFlame(count, modifier)
    else PluginImage(kind.asset, modifier)
}

/** Flamme de série avec son compteur, lisible avec Réduire les animations. */
@Composable
private fun StreakFlame(count: Int, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        PluginImage(GameArtKind.FLAME.asset, Modifier.matchParentSize())
        Text(count.toString(), Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).background(Mf.colors.card, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

internal fun streakColor(count: Int): Color = when {
    count >= 100 -> Color(0xFF9B6DFF); count >= 30 -> Color(0xFF39B9F8); count >= 14 -> Color(0xFFEF5261)
    count >= 7 -> Color(0xFFFFB628); else -> Color(0xFFFF7C25)
}

/** Visuel de la catégorie de série, partagé par les missions, le parcours et les profils amis. */
@Composable
internal fun TrackArt(track: String, count: Int, modifier: Modifier = Modifier) {
    val asset = when (track) {
        "FOOD" -> "food"; "STRENGTH" -> "strength"; "RUN" -> "run"; "SLEEP" -> "sleep"; "MIND" -> "mind"
        "STEPS" -> when { count >= 60 -> "steps-60"; count >= 30 -> "steps-30"; count >= 7 -> "steps"; else -> "steps-3" }
        else -> "steps"
    }
    PluginImage("game/garmin/$asset.png", modifier)
}
