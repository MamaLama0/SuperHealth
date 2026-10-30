package app.superhealth.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.superhealth.SuperPrefs
import app.superhealth.platform.deviceAuthAvailable
import app.superhealth.platform.deviceAuthenticate
import app.superhealth.platform.nowMs
import app.superhealth.ui.tr

/**
 * Verrouillage de l'app par le système : Face ID, Touch ID ou code de l'iPhone (LocalAuthentication). Rien n'est stocké
 * par l'app : c'est le téléphone qui vérifie. L'app se verrouille à l'ouverture et quand on la quitte plus longtemps que
 * le délai choisi.
 */
object AppLock {
    /** Délais proposés avant de redemander (secondes). */
    val DELAYS = listOf(0, 60, 300, 900)

    /** Vrai tant que l'écran verrouillé recouvre l'app. */
    var locked by mutableStateOf(SuperPrefs.settings.value.appLock)
        private set

    /** App en arrière-plan ou dans le sélecteur d'apps : l'aperçu est caché si demandé. */
    var away by mutableStateOf(false)
        private set

    /** Départ de l'app (ms), 0 : pas parti. */
    private var leftAt = 0L
    private var prompting = false
    /** La demande a déjà été montrée d'elle-même pour ce verrouillage (annulée : on attend le bouton). */
    private var asked = false

    /** Le téléphone a un verrouillage (sinon, rien pour vérifier). */
    fun available(): Boolean = deviceAuthAvailable()

    /** L'app passe en arrière-plan. */
    fun left() {
        if (prompting) return
        if (SuperPrefs.settings.value.appLock) leftAt = nowMs()
    }

    /** Retour dans l'app : verrouille si le délai est passé. */
    fun returned() {
        val s = SuperPrefs.settings.value
        if (!s.appLock) { locked = false; leftAt = 0; return }
        // toujours verrouillée en revenant : la demande se remontre une fois
        if (locked || (leftAt != 0L && nowMs() - leftAt >= s.lockDelaySec * 1000L)) { locked = true; asked = false }
        leftAt = 0
    }

    /** Aperçu caché pendant que l'app n'est pas active (la demande Face ID elle-même ne compte pas). */
    fun cover(on: Boolean) { if (!prompting || !on) away = on }

    fun disable() { locked = false; leftAt = 0 }

    /** À l'affichage de l'app verrouillée : une seule demande automatique par verrouillage. */
    fun autoPrompt() {
        if (!locked || asked) return
        asked = true
        authenticate()
    }

    /** Demande Face ID, Touch ID ou le code ; [onDone] reçoit vrai si c'est bien le propriétaire. */
    fun authenticate(onDone: (Boolean) -> Unit = {}) {
        if (prompting) return
        if (!available()) { locked = false; onDone(true); return }
        prompting = true
        deviceAuthenticate(tr("Déverrouiller SuperHealth")) { ok ->
            prompting = false
            if (ok) { locked = false; leftAt = 0 }
            onDone(ok)
        }
    }
}
