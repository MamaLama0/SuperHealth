package app.superhealth.ui.wellbeing

import app.superhealth.platform.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthSources
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.tr
import kotlinx.coroutines.launch

/** Garde l'écran allumé tant que l'écran qui l'appelle est affiché. */
@Composable
fun KeepScreenOn() {
    DisposableEffect(Unit) { keepScreenOn(true); onDispose { keepScreenOn(false) } }
}

/** Demande l'autorisation de notifier au premier rappel activé (iOS ne la redemande pas une fois répondue). */
@Composable
fun rememberNotificationAsk(): () -> Unit = { requestNotificationPermission() }

/** Règles notées par une autre app (Clue, Flo, Cycles d'Apple…) : lues dans Apple Santé, sur autorisation séparée. */
@Composable
fun CycleImport(vm: SuperViewModel) {
    val ctx = LocalContext.current
    val sh by SuperPrefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    GroupCard {
        if (!HealthSources.current.available)
            SettingRow(tr("Apple Santé"), subtitle = tr("Non disponible sur cet appareil"), icon = Icons.Rounded.Favorite)
        else SettingRow(tr("Apple Santé"), subtitle = if (sh.cycleSync) tr("Règles importées à chaque synchronisation") else tr("Importer les règles d'une autre appli"),
            icon = Icons.Rounded.Favorite) {
            scope.launch {
                if (sh.cycleSync || HealthSources.current.requestCycleAuthorization()) {
                    SuperPrefs.update { it.copy(cycleSync = true, healthConnect = true) }
                    vm.syncNow(ctx)
                }
            }
        }
    }
}

/** Les notifications locales d'iOS sonnent à l'heure dite : rien à régler. */
@Composable
fun ExactAlarmRow() = Unit
