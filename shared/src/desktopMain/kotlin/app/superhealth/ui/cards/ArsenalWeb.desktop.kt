package app.superhealth.ui.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Aperçu ordinateur : pas de moteur web, la silhouette du sachet reste affichée. */
@Composable
internal actual fun EquipmentWeb(page: String, script: String, modifier: Modifier, enabled: Boolean, ownsGestures: Boolean,
                                 keepAlive: Boolean, action: ((String, String) -> Unit)?) {
    Box(modifier)
}
