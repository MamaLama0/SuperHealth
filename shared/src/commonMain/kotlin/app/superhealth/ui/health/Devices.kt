package app.superhealth.ui.health

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import app.superhealth.ui.theme.container
import app.superhealth.platform.LocalContext
import app.superhealth.health.data.DeviceUi
import app.superhealth.health.data.LOGGABLE_METRICS
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import androidx.navigation.NavHostController
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.health.data.HealthSources
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Apple Santé et montre : sur iOS, l'Apple Watch (et les montres dont l'app écrit dans Santé : Garmin Connect, Polar,
 * Withings…) passe par Apple Santé. Remplace l'écran des appareils Gadgetbridge d'Android.
 */
@Composable
fun DevicesScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val scope = rememberCoroutineScope()
    val available = HealthSources.current.available
    DetailScaffold(tr("Montre et Apple Santé"), onBack = { nav.popBackStack() }) {
        GroupCard {
            SwitchRow(tr("Données de santé"), tr("Pas, cœur, sommeil et séances lus dans Apple Santé"), sh.watchModule) { vm.setWatchModule(it) }
        }
        if (!sh.watchModule) {
            Text(tr("Désactivé : les cartes de la montre disparaissent du tableau de bord."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            return@DetailScaffold
        }
        if (!available) {
            Text(tr("Apple Santé n'est pas disponible sur cet appareil."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
            return@DetailScaffold
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            GroupCard {
                SettingRow(tr("Santé Apple Watch"), subtitle = tr("Signes vitaux de la nuit, cœur, ECG, mobilité, bien-être"),
                    icon = Icons.Rounded.Watch, hue = app.superhealth.ui.theme.HealthHues.Heart) { nav.navigate("h/apple") }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Sources"), Modifier.weight(1f))
            if (syncing) CircularProgressIndicator(Modifier.padding(end = 12.dp, bottom = 6.dp).size(18.dp), strokeWidth = 2.dp, color = Mf.colors.accent)
            else if (devices.isNotEmpty()) Text(tr("Synchroniser"), Modifier.padding(end = 12.dp, bottom = 7.dp).clickable { vm.syncNow(ctx) },
                color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        GroupCard {
            devices.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                DeviceRow(d, onSync = { vm.syncDevice(d) }) { vm.syncNow(ctx) }
            }
            if (devices.isNotEmpty()) RowDivider()
            SettingRow(if (sh.healthConnect) tr("Autorisations d'Apple Santé") else tr("Autoriser l'accès à Apple Santé"),
                subtitle = sh.lastHcSummary ?: tr("Lecture des mesures, écriture des pesées, repas et séances"), icon = Icons.Rounded.Favorite) {
                scope.launch { HealthConnectHub.request(); vm.syncNow(ctx) }
            }
        }
        Text(tr("L'Apple Watch écrit directement dans Santé. Pour une autre montre (Garmin, Polar, Withings…), activez dans son app le partage vers Apple Santé. Pour revoir les accès : Réglages > Santé > Accès aux données > SuperHealth."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
    }
}

@Composable
private fun DeviceRow(d: DeviceUi, onSync: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (d.connected) Mf.colors.accent.container() else Mf.colors.chip),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Watch, null, Modifier.size(24.dp), tint = if (d.connected) Mf.colors.accent else Mf.colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(d.stateText, d.battery?.let { trf("Batterie %1\$s %%", it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (d.busy || d.connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Mf.colors.accent)
        else PillButton(if (d.connected) tr("Synchro") else tr("Connecter"), strong = false, onClick = onSync)
    }
}

/** Saisie manuelle d'une mesure santé (hydratation, glycémie, température) ; le poids passe par la pesée. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(vm: SuperViewModel, initial: Metric = Metric.HYDRATION, onDismiss: () -> Unit) {
    var metric by remember { mutableStateOf(initial.takeIf { it in LOGGABLE_METRICS } ?: Metric.HYDRATION) }
    var text by remember(metric) { mutableStateOf(if (metric == Metric.HYDRATION) "250" else "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mf.colors.background, dragHandle = null) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text(tr("Enregistrer"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            ChipChoice(LOGGABLE_METRICS, metric, { tr(it.label) }) { metric = it }
            Spacer(Modifier.height(16.dp))
            if (metric == Metric.HYDRATION) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 250, 330, 500).forEach { ml ->
                        Text("$ml ml", Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (text == "$ml") Mf.colors.strong else Mf.colors.card)
                            .clickable { text = "$ml" }.padding(vertical = 12.dp), maxLines = 1,
                            color = if (text == "$ml") Mf.colors.onStrong else Mf.colors.text, style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            MfCard { NumberField(tr(metric.label), text, { text = it }, suffix = metric.unit?.let(::tr)) }
            Spacer(Modifier.height(16.dp))
            val v = text.trim().replace(',', '.').toDoubleOrNull()
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = v != null && v > 0) {
                v?.let { vm.log(metric, it) }; onDismiss()
            }
        }
    }
}
