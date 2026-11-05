package app.superhealth.ui.league

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.superhealth.nutrition.ui.components.AssetImage

/** Badges locaux fournis par le projet ; animation de série Rive pour les grands emplacements. */
enum class GameArtKind(val asset: String) {
    FLAME("game/flame.png"), XP("game/xp.png"), TROPHY("game/trophy.png"),
    PERFECT_WEEK("game/perfect-week.png"), FLAME_INACTIVE("game/flame-inactive.png"), XP_EMPTY("game/xp-empty.png")
}

@Composable
fun GameArt(kind: GameArtKind, modifier: Modifier = Modifier, animated: Boolean = false, count: Int = 1) {
    if (animated && kind == GameArtKind.FLAME) StreakAnimation(count, modifier)
    else AssetImage(kind.asset, modifier, crop = false)
}

/** Visuel de la catégorie de série, partagé par les missions, le parcours et les profils amis. */
@Composable
internal fun TrackArt(track: String, count: Int, modifier: Modifier = Modifier) = androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
    val weekly = app.superhealth.league.Progression.Track.entries.firstOrNull { it.name == track }?.weekly == true
    val rank = (if (weekly) listOf(1, 4, 12, 26, 52, 104) else listOf(3, 7, 30, 60, 100, 365, 730)).count { count >= it }.minus(1).coerceAtLeast(0)
    BadgeEmblem("streak:$track", rank, minOf(maxWidth, maxHeight), locked = count == 0)
}
