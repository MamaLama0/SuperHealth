package app.superhealth.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import kotlinx.coroutines.delay

/**
 * Clavier fermé par « Retour » : le champ restait focalisé avec sa session de saisie ouverte, et la zone du clavier
 * pouvait rester vide (carré de la couleur du fond) dans toute l'app. On fait alors comme « Valider » : focus relâché,
 * puis les marges de fenêtre sont redemandées au système une fois l'animation du clavier finie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardGuard() {
    val ime = WindowInsets.isImeVisible
    val focus = LocalFocusManager.current
    val view = LocalView.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ime) {
        if (ime) { shown = true; return@LaunchedEffect }
        if (!shown) return@LaunchedEffect
        shown = false
        focus.clearFocus(force = true)
        delay(350)
        ViewCompat.requestApplyInsets(view)
    }
}
