package app.superhealth.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.league.League
import app.superhealth.league.LeagueWorker
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

@Composable
fun NotificationSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by SuperPrefs.settings.collectAsState()
    val social by League.state.collectAsState()
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun system() { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }
    fun updateSocial(f: (League.State) -> League.State) { League.configure(ctx, f); LeagueWorker.schedule(ctx) }
    DetailScaffold(tr("Notifications"), { nav.popBackStack() }) {
        MfCard {
            Text(tr(if (allowed) "Notifications Android autorisées" else "Notifications Android désactivées"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Choisissez les alertes utiles. Les sons et l'affichage sur l'écran verrouillé se règlent aussi dans Android."), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            PillButton(tr(if (allowed) "Réglages Android" else "Autoriser les notifications"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) {
                if (!allowed && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else system()
            }
            if (!allowed) TextButtonRow(tr("Ouvrir les réglages Android"), ::system)
        }
        SectionLabel(tr("Social"))
        GroupCard {
            SwitchRow(tr("Ajouts d'amis"), tr("Quand une personne utilise votre code"), social.notifyFriends) { v -> updateSocial { it.copy(notifyFriends = v) } }
            RowDivider()
            SwitchRow(tr("Publications des amis"), tr("Séances, sorties, photos, vidéos et messages"), social.notifyPosts) { v -> updateSocial { it.copy(notifyPosts = v) } }
            RowDivider()
            SwitchRow(tr("Réactions à mes publications"), tr("J'aime, je n'aime pas et emojis"), social.notifyReactions) { v -> updateSocial { it.copy(notifyReactions = v) } }
            RowDivider()
            SwitchRow(tr("Badges et niveaux"), tr("Vos nouvelles récompenses, sans répéter l'historique"), social.notifyProgress) { v -> updateSocial { it.copy(notifyProgress = v) } }
        }
        Text(tr("Les nouveautés des amis arrivent à la synchronisation. Android peut décaler les passages en arrière-plan."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        SectionLabel(tr("Rappels"))
        GroupCard {
            SettingRow(tr("Pesée, repas, moral et coucher"), subtitle = tr("Activer chaque rappel et choisir son heure")) { nav.go("settings/reminders") }
            RowDivider()
            SettingRow(tr("Médicaments et compléments"), subtitle = tr("Horaires et rappels de chaque prise")) { nav.go("meds") }
        }
        SectionLabel(tr("Entraînement"))
        GroupCard {
            SwitchRow(tr("Séance en cours"), tr("Chronomètre et commandes dans les notifications"), settings.notifyWorkout) { on ->
                SuperPrefs.update { it.copy(notifyWorkout = on) }
                app.superhealth.training.ui.SessionNotification.update(ctx, app.superhealth.training.WorkoutApp.store.state.value)
            }
            RowDivider()
            SwitchRow(tr("Alerte de fin de repos"), tr("Son et vibration à la fin du minuteur"), settings.notifyRest) { on -> SuperPrefs.update { it.copy(notifyRest = on) } }
        }
        SectionLabel(tr("Application"))
        GroupCard {
            SwitchRow(tr("Nouvelles versions"), tr("Être prévenu lorsqu'une mise à jour est disponible"), settings.updateCheck) { on ->
                SuperPrefs.update { it.copy(updateCheck = on) }
                if (!on && !app.superhealth.update.Updater.busy()) app.superhealth.update.Updater.cancelNotification(ctx)
            }
            RowDivider()
            SettingRow(tr("Mises à jour")) { nav.go("update") }
        }
    }
}

@Composable private fun TextButtonRow(label: String, action: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = action) { Text(label) }
}
