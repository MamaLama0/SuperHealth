package app.superhealth.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.superhealth.platform.Context

/**
 * Le sachet 3D des boosters (pages de assets/arsenal, modèles du plugin Groupe). Sur iPhone : WKWebView ; ailleurs
 * (aperçu ordinateur), rien n'est dessiné et l'écran garde la silhouette du sachet.
 */
internal object PackWeb {
    const val PAGE = "packs.html"
    /** Vrai dès qu'un sachet a été dessiné une fois : avant, l'écran montre une silhouette d'attente. */
    val rendered = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Android prépare la page hors écran ; sur iPhone la page se charge à l'ouverture de l'écran des paquets. */
    @Suppress("UNUSED_PARAMETER") fun warm(context: Context) {}
}

/**
 * Page de l'Arsenal : [script] est rejoué quand il change (et au chargement), [action] reçoit les messages
 * `ArsenalHost.action(kind, value)` de la page.
 */
@Composable
internal expect fun EquipmentWeb(
    page: String,
    script: String,
    modifier: Modifier,
    enabled: Boolean = true,
    ownsGestures: Boolean = false,
    keepAlive: Boolean = false,
    action: ((String, String) -> Unit)? = null,
)
