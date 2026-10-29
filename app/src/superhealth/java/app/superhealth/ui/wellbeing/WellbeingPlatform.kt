package app.superhealth.ui.wellbeing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.superhealth.SuperPrefs
import app.superhealth.bridge.HealthConnectHub
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.tr
import app.superhealth.wellbeing.Reminders

/** Garde l'écran allumé tant que l'écran qui l'appelle est affiché. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
}

/** Demande l'autorisation de notifier (Android 13+) au premier rappel activé. */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Règles notées par une autre appli (Clue, Flo, Samsung Health…) : lues dans Health Connect, sur autorisation séparée. */
@Composable
fun CycleImport(vm: SuperViewModel) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { granted = HealthConnectHub.granted(ctx).containsAll(HealthConnectHub.CYCLE_PERMISSIONS) }
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { g ->
        granted = g.containsAll(HealthConnectHub.CYCLE_PERMISSIONS)
        if (granted) { SuperPrefs.update { it.copy(healthConnect = true) }; vm.syncNow(ctx) }
    }
    GroupCard {
        if (HealthConnectHub.status(ctx) != HealthConnectHub.Status.AVAILABLE)
            SettingRow(tr("Health Connect"), subtitle = tr("Non disponible sur cet appareil"), icon = Icons.Rounded.Favorite)
        else SettingRow(tr("Health Connect"), subtitle = if (granted) tr("Règles importées à chaque synchronisation") else tr("Importer les règles d'une autre appli"),
            icon = Icons.Rounded.Favorite) { if (granted) vm.syncNow(ctx) else ask.launch(HealthConnectHub.CYCLE_PERMISSIONS) }
    }
}

/** Android 14+ refuse par défaut les alarmes précises : sans elles, un rappel peut sonner jusqu'à une heure plus tard. */
@Composable
fun ExactAlarmRow() {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val ctx = LocalContext.current
    val am = ctx.getSystemService(android.app.AlarmManager::class.java)
    var exact by remember { mutableStateOf(am.canScheduleExactAlarms()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && am.canScheduleExactAlarms() != exact) { exact = am.canScheduleExactAlarms(); Reminders.schedule(ctx) }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (exact) return
    GroupCard {
        SettingRow(tr("À l'heure précise"), subtitle = tr("Sinon jusqu'à 1 h de retard"), icon = Icons.Rounded.Alarm) {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName))) }
        }
    }
}
