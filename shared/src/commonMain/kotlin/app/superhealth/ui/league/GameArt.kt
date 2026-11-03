package app.superhealth.ui.league

import app.superhealth.platform.*

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
internal fun TrackArt(track: String, count: Int, modifier: Modifier = Modifier) {
    val asset = when (track) {
        "FOOD" -> "food"; "STRENGTH" -> "strength"; "RUN" -> "run"; "SLEEP" -> "sleep"; "MIND" -> "mind"
        "STEPS" -> when { count >= 60 -> "steps-60"; count >= 30 -> "steps-30"; count >= 7 -> "steps"; else -> "steps-3" }
        else -> "steps"
    }
    AssetImage("game/garmin/$asset.png", modifier, crop = false)
}
