package app.superhealth.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.superhealth.SuperPrefs
import app.superhealth.ui.tr

/**
 * Verrouillage de l'app par le système : empreinte, visage ou code du téléphone (BiometricPrompt, Android 11+ ; avant,
 * l'écran de confirmation du code de l'écran de verrouillage). Rien n'est stocké par l'app : c'est le téléphone qui vérifie.
 * L'app se verrouille à l'ouverture et quand on la quitte (Accueil, Récents, écran éteint) plus longtemps que le délai choisi.
 */
object AppLock {
    /** Délais proposés avant de redemander (secondes). */
    val DELAYS = listOf(0, 60, 300, 900)

    /** Vrai tant que l'écran verrouillé recouvre l'app. */
    var locked by mutableStateOf(SuperPrefs.settings.value.appLock)
        private set

    /** Départ de l'app (horloge depuis le démarrage), 0 : pas parti. */
    private var leftAt = 0L
    private var prompting = false
    /** La demande a déjà été montrée d'elle-même pour ce verrouillage (annulée : on attend le bouton). */
    private var asked = false

    /** Le téléphone a un verrouillage d'écran (sinon, rien pour vérifier). */
    fun available(ctx: Context): Boolean = ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** L'utilisateur quitte l'app (Accueil, Récents, écran éteint, retour). */
    fun left() {
        if (SuperPrefs.settings.value.appLock) leftAt = SystemClock.elapsedRealtime()
    }

    /** Retour dans l'app : verrouille si le délai est passé. */
    fun returned() {
        val s = SuperPrefs.settings.value
        if (!s.appLock) { locked = false; leftAt = 0; return }
        // toujours verrouillée en revenant : la demande se remontre une fois
        if (locked || (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt >= s.lockDelaySec * 1000L)) { locked = true; asked = false }
        leftAt = 0
    }

    /** À l'affichage de l'app verrouillée : une seule demande automatique par verrouillage. */
    fun autoPrompt(activity: Activity) {
        if (!locked || asked) return
        asked = true
        authenticate(activity)
    }

    fun disable() { locked = false; leftAt = 0 }

    /** Demande l'empreinte ou le code ; [onDone] reçoit vrai si c'est bien le propriétaire. */
    fun authenticate(activity: Activity, onDone: (Boolean) -> Unit = {}) {
        if (prompting) return
        if (!available(activity)) { locked = false; onDone(true); return }
        prompting = true
        val finish: (Boolean) -> Unit = { ok -> prompting = false; if (ok) { locked = false; leftAt = 0 }; onDone(ok) }
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(tr("Déverrouiller SuperHealth"))
                .setSubtitle(tr("Empreinte, visage ou code du téléphone"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            runCatching {
                prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish(false)
                })
            }.onFailure { finish(false) }
        } else {
            @Suppress("DEPRECATION")
            val intent = activity.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(tr("Déverrouiller SuperHealth"), null)
            if (intent == null) { finish(true); return }
            pending = finish
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST)
        }
    }

    const val REQUEST = 4721
    private var pending: ((Boolean) -> Unit)? = null

    /** Résultat de l'écran de confirmation du code (avant Android 11). */
    fun onResult(requestCode: Int, ok: Boolean): Boolean {
        if (requestCode != REQUEST) return false
        pending?.invoke(ok); pending = null
        return true
    }
}
