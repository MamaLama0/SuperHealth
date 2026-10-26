package app.superhealth.platform

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog as M3DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation.NavHostController
import androidx.savedstate.SavedState
import androidx.savedstate.read
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * Éléments d'interface Android sans équivalent direct en Compose Multiplatform : dialogues de date et d'heure
 * appelés depuis un clic (DatePickerDialog(...).show()), arguments de navigation, taille d'écran, maintien de l'écran.
 */

private sealed interface PendingDialog
private class DateDialog(val year: Int, val month0: Int, val day: Int, val minMs: Long?, val maxMs: Long?, val onSet: (Int, Int, Int) -> Unit) : PendingDialog
private class TimeDialog(val hour: Int, val minute: Int, val onSet: (Int, Int) -> Unit) : PendingDialog

private val pendingDialog = MutableStateFlow<PendingDialog?>(null)

/** android.app.DatePickerDialog : [month0] de 0 à 11 comme sur Android. */
class DatePickerDialog(ctx: Any?, private val listener: (Any?, Int, Int, Int) -> Unit, private val year: Int, private val month0: Int, private val day: Int) {
    val datePicker = Limits()
    class Limits { var minDate: Long? = null; var maxDate: Long? = null }
    fun show() { pendingDialog.value = DateDialog(year, month0, day, datePicker.minDate, datePicker.maxDate) { y, m, d -> listener(null, y, m, d) } }
}

class TimePickerDialog(ctx: Any?, private val listener: (Any?, Int, Int) -> Unit, private val hour: Int, private val minute: Int, is24: Boolean) {
    fun show() { pendingDialog.value = TimeDialog(hour, minute) { h, m -> listener(null, h, m) } }
}

/** À poser une fois à la racine : affiche les dialogues demandés par DatePickerDialog / TimePickerDialog. */
@Composable
fun PlatformDialogHost() {
    val d by pendingDialog.collectAsState()
    when (val x = d) {
        is DateDialog -> {
            val utc = LocalDate(x.year, x.month0 + 1, x.day).atStartOfDay(ZoneId.UTC).toInstant().toEpochMilli()
            val state = rememberDatePickerState(initialSelectedDateMillis = utc, selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val local = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    return (x.minMs == null || local >= x.minMs - 86_400_000L) && (x.maxMs == null || local <= x.maxMs)
                }
            })
            M3DatePickerDialog(onDismissRequest = { pendingDialog.value = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val ld = Instant.ofEpochMilli(ms).atZone(ZoneId.UTC).toLocalDate()
                            x.onSet(ld.year, ld.monthValue - 1, ld.dayOfMonth)
                        }
                        pendingDialog.value = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pendingDialog.value = null }) { Text(app.superhealth.ui.tr("Annuler")) } },
            ) { DatePicker(state) }
        }
        is TimeDialog -> {
            val state = rememberTimePickerState(x.hour, x.minute, is24Hour = !app.superhealth.ui.Lang.english)
            androidx.compose.material3.AlertDialog(onDismissRequest = { pendingDialog.value = null },
                confirmButton = { TextButton(onClick = { x.onSet(state.hour, state.minute); pendingDialog.value = null }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { pendingDialog.value = null }) { Text(app.superhealth.ui.tr("Annuler")) } },
                text = { TimePicker(state) })
        }
        null -> Unit
    }
}

// ---- Navigation -----------------------------------------------------------------------------------

fun SavedState.getString(key: String): String? = read { if (contains(key)) getStringOrNull(key) else null }
fun SavedState.getInt(key: String): Int = read { if (contains(key)) getInt(key) else 0 }
fun SavedState.getLong(key: String): Long = read { if (contains(key)) getLong(key) else 0L }
fun SavedState.getBoolean(key: String): Boolean = read { if (contains(key)) getBoolean(key) else false }

/** Le contexte de l'app, comme NavHostController.context sur Android. */
val NavHostController.context: Context get() = appContext

/** Retour arrière système : sur iOS, le geste de bord est géré par la navigation ; rien à intercepter. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = Unit

// ---- Écran ----------------------------------------------------------------------------------------

class Configuration(val screenWidthDp: Int, val screenHeightDp: Int)

object LocalConfiguration {
    val current: Configuration
        @Composable get() {
            val size = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current.density
            return Configuration((size.width / density).toInt(), (size.height / density).toInt())
        }
}

/** Garde l'écran allumé (séance en cours). */
expect fun keepScreenOn(on: Boolean)

// ---- Collections de la JVM ------------------------------------------------------------------------

fun <K : Comparable<K>, V> Map<K, V>.toSortedMap(): Map<K, V> = entries.sortedBy { it.key }.associate { it.key to it.value }
fun <K, V> Map<K, V>.getOrDefault(key: K, default: V): V = if (containsKey(key)) @Suppress("UNCHECKED_CAST") (this[key] as V) else default

object ZoneOffset { val UTC: ZoneId = kotlinx.datetime.TimeZone.UTC }

val <T : Any> kotlin.reflect.KClass<T>.java: kotlin.reflect.KClass<T> get() = this

/**
 * Apparence de la fenêtre (barre d'état, claviers, feuilles du système) : null suit le système, sinon le thème forcé
 * dans l'app.
 */
expect fun setWindowAppearance(dark: Boolean?)
