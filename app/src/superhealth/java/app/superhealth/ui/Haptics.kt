package app.superhealth.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Retours haptiques de l'app, tous au même endroit pour garder une seule « grammaire » : un cran léger quand une valeur
 * change sous le doigt, un clic net pour un interrupteur, une confirmation pour ce qui est validé, un refus pour ce qui
 * est annulé. Passe par la vue (constantes système) : Android adapte l'effet au moteur de vibration du téléphone et
 * respecte le réglage « Retour haptique » du système. Coupé aussi par Réglages > Général > Vibrations.
 */
class Haptics internal constructor(private val view: View) {
    private fun play(constant: Int) {
        if (!app.superhealth.SuperPrefs.settings.value.haptics) return
        view.performHapticFeedback(constant)
    }

    /** Cran discret : une valeur parcourue au doigt (graphique, curseur, roue). */
    fun tick() = play(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** Début d'un geste maintenu (appui long qui ouvre un mode). */
    fun press() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.LONG_PRESS)

    /** Fin du geste maintenu. */
    fun release() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK)

    /** Interrupteur, case, puce choisie. */
    fun toggle(on: Boolean = true) = play(when {
        Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        else -> HapticFeedbackConstants.VIRTUAL_KEY
    })

    /** Action validée : série cochée, aliment ajouté, humeur notée. */
    fun confirm() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Action refusée ou annulée. */
    fun reject() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** Appui long (menu contextuel, déplacement d'une carte). */
    fun longPress() = play(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
