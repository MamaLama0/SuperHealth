package app.superhealth.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.update.Updater
import kotlinx.coroutines.launch

/**
 * Ligne « À propos » des réglages : la version installée et, à droite, « À jour » ou la version disponible. À jour, un appui
 * revérifie sur place ; une version disponible ouvre la page d'installation.
 */
@Composable
internal fun VersionRow(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Updater.state.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    LaunchedEffect(Unit) { if (!Updater.restoreSession(ctx) && st is Updater.State.Idle && sh.updateCheck) Updater.refresh(ctx) }
    val (value, color) = when (val s = st) {
        is Updater.State.Available -> trf("%1\$s disponible", s.release.version) to Mf.colors.accent
        is Updater.State.Downloading -> trf("%1\$s · %2\$s %%", s.release.version, (s.fraction * 100).toInt()) to Mf.colors.accent
        is Updater.State.Ready -> trf("%1\$s prête", s.release.version) to Mf.colors.accent
        is Updater.State.Verifying -> tr("Vérification du fichier…") to Mf.colors.accent
        is Updater.State.Preparing -> tr("Préparation de l'installation…") to Mf.colors.accent
        is Updater.State.Installing -> tr("Installation en cours…") to Mf.colors.accent
        is Updater.State.AwaitingConfirmation -> tr("Confirmation Android requise") to Mf.colors.accent
        Updater.State.Queued -> tr("Mise à jour en attente…") to Mf.colors.accent
        Updater.State.Checking -> tr("Vérification…") to null
        is Updater.State.UpToDate -> tr("À jour") to null
        is Updater.State.Failed -> tr("Non vérifiée") to null
        Updater.State.Idle -> null to null
    }
    val pending = st is Updater.State.Available || st is Updater.State.Ready || Updater.busy(st)
    SettingRow("SuperHealth " + Updater.installed(ctx), value, icon = Icons.Rounded.SystemUpdate, hue = if (pending) Mf.colors.accent else app.superhealth.ui.theme.Base.Grey, valueColor = color,
        onClick = { if (pending) nav.go("update") else if (st !is Updater.State.Checking) scope.launch { Updater.refresh(ctx) } })
}

/** Mises à jour : dernière version publiée sur GitHub (dépôt public, sans jeton), notes, installation ; options de vérification. */
@Composable
fun UpdateScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sh by vm.settings.collectAsState()
    val st by Updater.state.collectAsState()
    LaunchedEffect(Unit) { if (!Updater.restoreSession(ctx) && (st is Updater.State.Idle || st is Updater.State.Failed || st is Updater.State.UpToDate)) Updater.refresh(ctx) }

    fun install() {
        if (!Updater.canInstall(ctx)) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) }
            return
        }
        Updater.startInstall(ctx)
    }

    DetailScaffold(tr("Mises à jour"), onBack = { nav.popBackStack() }) {
        MfCard {
            when (val s = st) {
                Updater.State.Idle, Updater.State.Checking -> Text(tr("Vérification…"), style = MaterialTheme.typography.titleMedium)
                is Updater.State.UpToDate -> {
                    Text(tr("À jour"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(trf("Version %1\$s", Updater.installed(ctx)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                }
                is Updater.State.Failed -> {
                    Text(trf("Échec : %1\$s", s.message), style = MaterialTheme.typography.bodyMedium, color = app.superhealth.ui.theme.HealthHues.Heart)
                    Spacer(Modifier.height(10.dp))
                    PillButton(tr("Réessayer"), strong = false) { scope.launch { Updater.refresh(ctx) } }
                }
                Updater.State.Queued, is Updater.State.Verifying, is Updater.State.Preparing,
                is Updater.State.Installing, is Updater.State.AwaitingConfirmation -> {
                    val label = when (s) {
                        Updater.State.Queued -> tr("Mise à jour en attente…")
                        is Updater.State.Verifying -> tr("Vérification du fichier…")
                        is Updater.State.Preparing -> tr("Préparation de l'installation…")
                        is Updater.State.AwaitingConfirmation -> tr("Confirmation Android requise")
                        else -> tr("Installation en cours…")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Mf.colors.accent, strokeWidth = 2.dp)
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("La mise à jour continue. La confirmation Android s'ouvrira automatiquement si nécessaire ; sinon, touchez la notification."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    if (s is Updater.State.AwaitingConfirmation) {
                        Spacer(Modifier.height(12.dp))
                        PillButton(tr("Confirmer avec Android")) { runCatching { ctx.startActivity(s.intent) } }
                    }
                }
                is Updater.State.Available, is Updater.State.Downloading, is Updater.State.Ready -> {
                    val rel = when (s) { is Updater.State.Available -> s.release; is Updater.State.Downloading -> s.release; is Updater.State.Ready -> s.release; else -> null }!!
                    Text(trf("Version %1\$s disponible", rel.version), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(trf("Installée : %1\$s", Updater.installed(ctx)), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    if (rel.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(rel.notes.lines().filter { it.isNotBlank() }.take(14).joinToString("\n") { it.trimStart('#', ' ').replace("**", "") },
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (s is Updater.State.Downloading) {
                        ProgressBar(s.fraction, Mf.colors.accent, height = 6.dp)
                        Text(trf("Téléchargement… %1\$s %%", (s.fraction * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(tr("Installer"), Modifier.weight(1f)) { install() }
                        PillButton(tr("Ignorer"), Modifier.weight(1f), strong = false) {
                            SuperPrefs.update { it.copy(updateSkip = rel.version) }; Updater.cancelNotification(ctx); nav.popBackStack()
                        }
                    }
                    if (!Updater.canInstall(ctx)) Text(tr("Android demandera d'autoriser SuperHealth à installer des applications."),
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Options"), Modifier.padding(start = 0.dp))
        GroupCard {
            SwitchRow(tr("Demander avant de télécharger"), tr("Notification d'abord ; sinon l'APK est prêt à installer"), sh.updateConfirm) { v ->
                SuperPrefs.update { it.copy(updateConfirm = v) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
