package app.superhealth.health.data

import app.superhealth.platform.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** Type d'appareil qui a mesuré une valeur (affiché devant son nom). */
enum class SourceKind(val label: String, val icon: ImageVector) {
    WATCH("Montre", Icons.Rounded.Watch),
    BAND("Bracelet", Icons.Rounded.Watch),
    RING("Bague", Icons.Rounded.RadioButtonUnchecked),
    CHEST("Ceinture cardio", Icons.Rounded.Favorite),
    SCALE("Balance", Icons.Rounded.MonitorWeight),
    PHONE("Téléphone", Icons.Rounded.PhoneAndroid),
    MANUAL("Saisie", Icons.Rounded.Edit),
    OTHER("Appareil", Icons.Rounded.Watch),
}

/**
 * D'où vient une mesure : type d'appareil, nom de l'appareil (« Forerunner 165 ») et appli qui l'a transmise
 * (« Garmin Connect » via Apple Santé ; null pour l’Apple Watch et l’iPhone).
 */
@Serializable
data class DataSource(val kind: SourceKind, val device: String? = null, val app: String? = null) {
    /** « Montre · Forerunner 165 · Garmin Connect » (sans répéter un nom déjà inclus dans l'autre). */
    fun parts(): List<String> = listOfNotNull(device, app?.takeIf { a -> device?.contains(a, ignoreCase = true) != true })

    companion object {
        /** Applis qui écrivent dans Health Connect / Apple Santé : nom affiché et appareil habituel. */
        private val APPS = mapOf(
            "com.garmin.android.apps.connectmobile" to ("Garmin Connect" to SourceKind.WATCH),
            "com.garmin.connect.mobile" to ("Garmin Connect" to SourceKind.WATCH),
            "com.sec.android.app.shealth" to ("Samsung Health" to SourceKind.WATCH),
            "com.samsung.health" to ("Samsung Health" to SourceKind.WATCH),
            "com.fitbit.FitbitMobile" to ("Fitbit" to SourceKind.BAND),
            "com.google.android.apps.fitness" to ("Google Fit" to SourceKind.PHONE),
            "com.google.android.apps.healthdata" to ("Android" to SourceKind.PHONE),
            "com.ouraring.oura" to ("Oura" to SourceKind.RING),
            "com.whoop.android" to ("WHOOP" to SourceKind.BAND),
            "com.whoop.iphone" to ("WHOOP" to SourceKind.BAND),
            "com.withings.wiscale2" to ("Withings" to SourceKind.SCALE),
            "com.withings.wiScaleNG" to ("Withings" to SourceKind.SCALE),
            "fi.polar.polarflow" to ("Polar Flow" to SourceKind.WATCH),
            "com.stt.android.suunto" to ("Suunto" to SourceKind.WATCH),
            "com.coros.coros" to ("COROS" to SourceKind.WATCH),
            "com.huawei.health" to ("Huawei Santé" to SourceKind.WATCH),
            "com.mi.health" to ("Mi Fitness" to SourceKind.BAND),
            "com.xiaomi.wearable" to ("Mi Fitness" to SourceKind.BAND),
            "com.zepp.health" to ("Zepp" to SourceKind.WATCH),
            "com.huami.watch.hmwatchmanager" to ("Zepp" to SourceKind.WATCH),
            "com.ultrahuman.android" to ("Ultrahuman" to SourceKind.RING),
            "com.strava" to ("Strava" to SourceKind.WATCH),
            "com.apple.health" to ("Santé" to SourceKind.PHONE),
        )

        fun appName(pkg: String): String? = APPS[pkg]?.first

        /**
         * Source d'un enregistrement d'une autre appli : le type d'appareil déclaré par l'enregistrement s'il est connu,
         * sinon celui que l'appli utilise d'habitude (une bague Oura, une montre Garmin…).
         */
        fun fromApp(pkg: String, appLabel: String?, kind: SourceKind?, device: String?): DataSource {
            val known = APPS[pkg]
            return DataSource(kind ?: known?.second ?: SourceKind.OTHER, device?.takeIf { it.isNotBlank() }, appLabel ?: known?.first ?: pkg)
        }
    }
}
