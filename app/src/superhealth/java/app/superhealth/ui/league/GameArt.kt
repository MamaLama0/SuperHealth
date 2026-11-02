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
