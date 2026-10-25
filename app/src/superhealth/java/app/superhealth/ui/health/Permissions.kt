package app.superhealth.ui.health

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import nodomain.freeyourgadget.gadgetbridge.externalevents.NotificationListener

/** Autorisations utiles à la montre : ce qu'elle peut afficher (notifications, SMS, appels) et ce qui la garde connectée. */
private enum class Perm(val title: String, val about: String, val icon: ImageVector, val restricted: Boolean = false) {
    NOTIFY("Notifications de l'app", "Synchronisation, séance en cours, rappels", Icons.Rounded.Notifications),
    LISTENER("Accès aux notifications", "Afficher les notifications du téléphone sur la montre", Icons.Rounded.NotificationsActive, restricted = true),
    SMS("SMS", "Afficher les SMS et y répondre depuis la montre", Icons.Rounded.Sms, restricted = true),
    CALLS("Appels et contacts", "Nom de l'appelant, décrocher ou refuser depuis la montre", Icons.Rounded.Call),
    OVERLAY("Superposition aux autres apps", "Ouvrir l'app quand la montre le demande (localiser le téléphone…)", Icons.Rounded.Layers, restricted = true),
    BATTERY("Sans restriction de batterie", "Garder la montre connectée en arrière-plan", Icons.Rounded.BatteryChargingFull),
}

private val SMS_PERMS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
private val CALL_PERMS = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS,
    Manifest.permission.ANSWER_PHONE_CALLS)

private fun Context.has(vararg p: String) = p.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

private fun granted(ctx: Context, p: Perm): Boolean = when (p) {
    Perm.NOTIFY -> Build.VERSION.SDK_INT < 33 || ctx.has(Manifest.permission.POST_NOTIFICATIONS)
    Perm.LISTENER -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    Perm.SMS -> ctx.has(*SMS_PERMS)
    Perm.CALLS -> ctx.has(*CALL_PERMS)
    Perm.OVERLAY -> Settings.canDrawOverlays(ctx)
    Perm.BATTERY -> (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
}

/**
 * Android 13+ bloque certains réglages (accès aux notifications, SMS, superposition) pour les apps installées depuis
 * un fichier APK : « Paramètre restreint ». Ils se débloquent dans Infos de l'appli > ⋮ > Autoriser les paramètres restreints.
 */
fun restrictedSettings(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), ctx.packageName) != AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

fun missingWatchPermissions(ctx: Context): Int = Perm.entries.count { !granted(ctx, it) }

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))

@Composable
fun PermissionsScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // Les autorisations changent dans les réglages système : relues au retour dans l'app.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    // Refus sans boîte de dialogue = paramètre restreint : le guide passe en tête.
    var blocked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        if (res.values.any { !it } && Build.VERSION.SDK_INT >= 33) blocked = true
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    fun ask(p: Perm) {
        when (p) {
            Perm.NOTIFY -> if (Build.VERSION.SDK_INT >= 33) request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            Perm.SMS -> request.launch(SMS_PERMS)
            Perm.CALLS -> request.launch(CALL_PERMS)
            Perm.LISTENER -> settings.launch(
                if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, NotificationListener::class.java).flattenToString())
                else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Perm.OVERLAY -> settings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
            Perm.BATTERY -> settings.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        }
    }

    DetailScaffold(tr("Autorisations"), onBack = { nav.popBackStack() }) {
        val state = remember(tick) { Perm.entries.associateWith { granted(ctx, it) } }
        val restricted = remember(tick) { restrictedSettings(ctx) }
        val restrictedMissing = Perm.entries.any { it.restricted && state[it] == false }
        if (Build.VERSION.SDK_INT >= 33 && restrictedMissing && (restricted || blocked)) RestrictedGuide { settings.launch(appDetails(ctx)) }

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Montre"), Modifier.padding(start = 0.dp))
        GroupCard {
            Perm.entries.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                PermRow(p, state[p] == true) { ask(p) }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && restrictedMissing && !(restricted || blocked)) {
            Text(tr("« Paramètre restreint » ? Infos de l'appli > ⋮ > Autoriser les paramètres restreints."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp).clickable { settings.launch(appDetails(ctx)) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RestrictedGuide(onOpen: () -> Unit) {
    val hue = HealthHues.Mind
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(hue.container()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = hue.onContainer())
            Spacer(Modifier.width(10.dp))
            Text(tr("Paramètres restreints par Android"), style = MaterialTheme.typography.titleMedium, color = hue.onContainer())
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Installée depuis un APK : Android bloque notifications, SMS et superposition tant que vous ne les autorisez pas."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.text)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Ouvrez Infos de l'appli (bouton ci-dessous)",
            "Touchez ⋮ en haut à droite",
            "Choisissez « Autoriser les paramètres restreints » et confirmez",
            "Revenez ici et accordez les autorisations",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(hue), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text(tr(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(tr("Le menu ⋮ n'apparaît qu'après un premier refus : touchez d'abord l'autorisation ci-dessous."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Ouvrir Infos de l'appli"), Modifier.fillMaxWidth(), onClick = onOpen)
    }
}

@Composable
private fun PermRow(p: Perm, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val hue = if (ok) HealthHues.Activity else Mf.colors.textSecondary
        Box(Modifier.size(40.dp).clip(CircleShape).background(hue.container()), contentAlignment = Alignment.Center) {
            Icon(p.icon, null, Modifier.size(22.dp), tint = if (ok) HealthHues.Activity else Mf.colors.text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(p.title), style = MaterialTheme.typography.bodyLarge)
            Text(tr(p.about), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
        if (ok) Icon(Icons.Rounded.Check, tr("Accordée"), tint = HealthHues.Activity)
        else PillButton(tr("Autoriser"), strong = true, onClick = onClick)
    }
}
