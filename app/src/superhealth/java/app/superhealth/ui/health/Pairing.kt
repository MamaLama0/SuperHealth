package app.superhealth.ui.health

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2

/**
 * Apps officielles des montres : une montre ne parle en Bluetooth qu'à une seule app ; si son app officielle reste
 * installée et connectée, elle garde la montre et l'association ici tourne en boucle à l'initialisation.
 */
private val VENDOR_APPS = linkedMapOf(
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.xiaomi.hm.health" to "Zepp Life",
    "com.xiaomi.wearable" to "Mi Fitness",
    "com.mi.health" to "Mi Fitness",
    "com.huawei.health" to "Huawei Santé",
    "com.samsung.android.app.watchmanager" to "Galaxy Wearable",
    "com.fitbit.FitbitMobile" to "Fitbit",
    "fi.polar.polarflow" to "Polar Flow",
    "com.stt.android.suunto" to "Suunto",
    "com.yf.smart.coros.dist" to "COROS",
    "com.withings.wiscale2" to "Withings",
)

fun installedVendorApp(ctx: Context): String? = VENDOR_APPS.entries.firstOrNull { (pkg, _) ->
    runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}?.value

/** Le strict nécessaire pour trouver et garder la montre ; le reste (SMS, appels…) est facultatif, dans Autorisations. */
private fun pairPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/**
 * À appeler juste avant une demande : vrai si Android ne l'affichera pas (déjà demandée, et ni boîte ni explication
 * possibles = refus définitif ou paramètre restreint). Mémorise la demande pour la fois suivante.
 */
internal fun willBeSilent(ctx: Context, perms: Collection<String>): Boolean {
    val act = ctx.activity() ?: return false
    val prefs = ctx.getSharedPreferences("superhealth_perms", Context.MODE_PRIVATE)
    val missing = perms.filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    val silent = missing.isNotEmpty() && missing.all { prefs.getBoolean("asked_$it", false) && !ActivityCompat.shouldShowRequestPermissionRationale(act, it) }
    prefs.edit().apply { missing.forEach { putBoolean("asked_$it", true) } }.apply()
    return silent
}

internal tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Association d'une montre : avertit si l'app officielle est installée (avec la voie Health Connect pour la garder),
 * demande les seules autorisations Bluetooth, puis lance la recherche. Renvoie la fonction qui démarre le parcours.
 */
@Composable
fun rememberPairing(onDone: () -> Unit, onUseHealthConnect: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var vendor by remember { mutableStateOf<String?>(null) }
    var silent by remember { mutableStateOf(false) }
    val discover = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onDone() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val refused = res.filterValues { !it }.keys
        val bluetooth = refused.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        // refus définitif (Android n'affiche plus la demande) : seul Infos de l'appli permet de l'accorder
        if (bluetooth.isNotEmpty() && silent) {
            Toast.makeText(ctx, tr("Autorisez « Appareils à proximité » et « Position » dans Autorisations."), Toast.LENGTH_LONG).show()
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
        } else if (bluetooth.isNotEmpty()) {
            Toast.makeText(ctx, tr("Sans Bluetooth, la montre ne peut pas être trouvée."), Toast.LENGTH_LONG).show()
        } else runCatching { discover.launch(Intent(ctx, DiscoveryActivityV2::class.java)) }
    }
    fun pair() {
        val missing = pairPermissions().filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) runCatching { discover.launch(Intent(ctx, DiscoveryActivityV2::class.java)) }
        else { silent = willBeSilent(ctx, missing); request.launch(missing.toTypedArray()) }
    }

    vendor?.let { app ->
        AlertDialog(onDismissRequest = { vendor = null }, containerColor = Mf.colors.card,
            title = { Text(trf("%1\$s est installé", app)) },
            text = { Text(trf("Une montre ne se connecte qu'à une seule app. Pour garder %1\$s, laissez-le gérer la montre et activez son partage vers Health Connect : SuperHealth lira pas, cœur, sommeil et séances. Pour associer ici, dissociez d'abord la montre dans %1\$s.", app)) },
            confirmButton = { TextButton(onClick = { vendor = null; onUseHealthConnect() }) { Text(tr("Utiliser Health Connect"), color = Mf.colors.accent) } },
            dismissButton = { TextButton(onClick = { vendor = null; pair() }) { Text(tr("Associer quand même"), color = Mf.colors.text) } })
    }
    return { installedVendorApp(ctx)?.let { vendor = it } ?: pair() }
}
