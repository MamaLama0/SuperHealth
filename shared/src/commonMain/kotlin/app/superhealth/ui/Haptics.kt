package app.superhealth.ui

import app.superhealth.platform.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Retours haptiques de l'app, tous au même endroit pour garder une seule « grammaire » : un cran léger quand une valeur
 * change sous le doigt, un clic net pour un interrupteur, une confirmation pour ce qui est validé, un refus pour ce qui
 * est annulé. Coupé par Réglages › Général › Vibrations.
 */
class Haptics internal constructor() {
    private fun play(kind: HapticKind, intensity: Float = 1f) {
        if (!app.superhealth.SuperPrefs.settings.value.haptics) return
        hapticEffect(kind, intensity)
    }

    /** Cran discret : une valeur parcourue au doigt (graphique, curseur, roue). */
    fun tick() = play(HapticKind.TICK)

    /** Début d'un geste maintenu (appui long qui ouvre un mode). */
    fun press() = play(HapticKind.THUD, 0.6f)

    /** Fin du geste maintenu. */
    fun release() = play(HapticKind.TICK)

    /** Interrupteur, case, puce choisie. */
    fun toggle(on: Boolean = true) = play(HapticKind.CLICK, if (on) 0.7f else 0.4f)

    /** Action validée : série cochée, aliment ajouté, humeur notée. */
    fun confirm() = play(HapticKind.SUCCESS)

    /** Action refusée ou annulée. */
    fun reject() = play(HapticKind.ERROR)

    /** Appui long (menu contextuel, déplacement d'une carte). */
    fun longPress() = play(HapticKind.THUD, 0.8f)
}

@Composable
fun rememberHaptics(): Haptics = remember { Haptics() }
