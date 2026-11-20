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
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val settings by SuperPrefs.settings.collectAsState()
    val social by League.state.collectAsState()
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    // notification de connexion de la montre : relue au retour des réglages Android et à chaque changement de la montre
    var resumed by remember { mutableIntStateOf(0) }
    val watches by app.superhealth.bridge.WatchStatus.state.collectAsState()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { allowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled(); resumed++ }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun system() { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }
    fun updateSocial(f: (League.State) -> League.State) { League.configure(ctx, f); LeagueWorker.schedule(ctx) }
    DetailScaffold(tr("Notifications"), { nav.popBackStack() }) {
        MfCard {
            Text(tr(if (allowed) "Notifications Android autorisées" else "Notifications Android désactivées"), style = MaterialTheme.typography.titleMedium)
            PillButton(tr(if (allowed) "Réglages Android" else "Autoriser les notifications"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) {
                if (!allowed && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else system()
            }
            if (!allowed) TextButtonRow(tr("Ouvrir les réglages Android"), ::system)
        }
        SectionLabel(tr("Groupe"))
        GroupCard {
            SwitchRow(tr("Ajouts d'amis"), tr("Quand une personne utilise votre code"), social.notifyFriends) { v -> updateSocial { it.copy(notifyFriends = v) } }
            RowDivider()
            SwitchRow(tr("Publications des amis"), tr("Séances, sorties, photos, vidéos et messages"), social.notifyPosts) { v -> updateSocial { it.copy(notifyPosts = v) } }
            RowDivider()
            SwitchRow(tr("Réponses à mes publications"), tr("Messages dans les conversations du Groupe"), social.notifyReplies) { v -> updateSocial { it.copy(notifyReplies = v) } }
            SwitchRow(tr("Réactions à mes publications"), tr("J'aime, je n'aime pas et emojis"), social.notifyReactions) { v -> updateSocial { it.copy(notifyReactions = v) } }
            RowDivider()
            SwitchRow(tr("Badges et niveaux"), tr("Nouvelles récompenses"), social.notifyProgress) { v -> updateSocial { it.copy(notifyProgress = v) } }
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
            SwitchRow(tr("Alerte de fin de repos"), tr("Vibration à la fin du minuteur (le bip se règle dans Musculation)"), settings.notifyRest) { on -> SuperPrefs.update { it.copy(notifyRest = on) } }
            RowDivider()
            SwitchRow(tr("Fin du repos sur la montre"), tr("La montre connectée à SuperHealth vibre et affiche la prochaine série"), settings.restOnWatch) { on -> SuperPrefs.update { it.copy(restOnWatch = on) } }
            RowDivider()
            SwitchRow(tr("Via l'app de la montre"), tr("Notification relayée par Wear OS, Garmin Connect, Zepp ou Galaxy Wearable"), settings.restWatchNotification) { on -> SuperPrefs.update { it.copy(restWatchNotification = on) } }
            if (settings.restOnWatch || settings.restWatchNotification) {
                RowDivider()
                SettingRow(tr("Essayer sur la montre"), subtitle = tr("Envoie une fin de repos d'essai")) { app.superhealth.training.ui.WatchRest.test(ctx) }
            }
        }
        if (settings.watchModule) WatchConnectionSettings(resumed, watches)
        SectionLabel(tr("Application"))
        GroupCard {
            SwitchRow(tr("Retour haptique"), tr("Légères vibrations au toucher des graphiques, interrupteurs et validations"), settings.haptics) { on ->
                SuperPrefs.update { it.copy(haptics = on) }
            }
            RowDivider()
            SwitchRow(tr("Nouvelles versions"), tr("Être prévenu lorsqu'une mise à jour est disponible"), settings.updateCheck) { on ->
                SuperPrefs.update { it.copy(updateCheck = on) }
                if (!on && !app.superhealth.update.Updater.busy()) app.superhealth.update.Updater.cancelNotification(ctx)
            }
            RowDivider()
            SettingRow(tr("Mises à jour")) { nav.go("update") }
        }
    }
}

/**
 * Notification permanente de la montre (« Connectée · Batterie 80 % ») : son état, un raccourci vers son canal dans les
 * réglages Android (seul l'utilisateur peut la bloquer ou la rétablir), la batterie dedans, et la réafficher après un balayage.
 */
@Composable private fun WatchConnectionSettings(resumed: Int, watches: List<app.superhealth.bridge.WatchStatus.Info>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val connected = watches.any { it.connected }
    val state = remember(resumed, tick, watches) { app.superhealth.bridge.ConnectionNotification.state(ctx) }
    var battery by remember(resumed, watches.size) { mutableStateOf(app.superhealth.bridge.ConnectionNotification.batteryShown()) }
    val on = app.superhealth.bridge.ConnectionNotification.allowed(state)
    val detail = when (state) {
        app.superhealth.bridge.ConnectionNotification.State.APP_BLOCKED -> tr("Coupée : les notifications de SuperHealth sont désactivées")
        app.superhealth.bridge.ConnectionNotification.State.CHANNEL_BLOCKED -> tr("Coupée dans les réglages Android")
        app.superhealth.bridge.ConnectionNotification.State.SHOWN -> tr("Affichée")
        app.superhealth.bridge.ConnectionNotification.State.QUIET -> tr("Affichée en mode discret")
        app.superhealth.bridge.ConnectionNotification.State.DISMISSED -> tr("Balayée : elle revient au prochain changement de la montre")
        app.superhealth.bridge.ConnectionNotification.State.NO_WATCH -> tr("Autorisée ; aucune montre connectée")
    }
    SectionLabel(tr("Montre"))
    GroupCard {
        // Android seul peut couper ou rétablir ce canal : l'interrupteur ouvre ses réglages.
        SwitchRow(tr("Notification de connexion"), detail, on) { app.superhealth.bridge.ConnectionNotification.openSettings(ctx, state) }
        if (!on) {
            PillButton(tr("Réactiver dans Android"), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), strong = false) {
                app.superhealth.bridge.ConnectionNotification.openSettings(ctx, state)
            }
        } else if (state == app.superhealth.bridge.ConnectionNotification.State.DISMISSED) {
            PillButton(tr("Réafficher maintenant"), Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), strong = false) {
                app.superhealth.bridge.ConnectionNotification.repost(ctx)
                scope.launch { kotlinx.coroutines.delay(400); tick++ }
            }
        }
        RowDivider()
        SwitchRow(tr("Batterie dans la notification"), tr("Niveau de batterie de la montre à côté de son état"), battery) { v ->
            app.superhealth.bridge.ConnectionNotification.setBatteryShown(ctx, v)
            battery = v
        }
    }
    if (!connected) Text(tr("Elle s'affiche quand une montre est connectée."),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
}

@Composable private fun TextButtonRow(label: String, action: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = action) { Text(label) }
}
