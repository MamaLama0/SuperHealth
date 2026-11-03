package app.superhealth.ui.wellbeing

import android.app.TimePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.ui.tr
import app.superhealth.wellbeing.Bedtime
import app.superhealth.wellbeing.PromptTiming
import app.superhealth.wellbeing.Reminders
import kotlinx.coroutines.delay

/** Met à jour les horaires même si l'accueil reste ouvert, et immédiatement au retour dans l'app. */
@Composable
fun promptNow(): Long {
    val owner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(15_000)
            }
        }
    }
    return now
}

@Composable
fun WeighPromptTimeRow() {
    val ctx = LocalContext.current
    val sh by SuperPrefs.settings.collectAsState()
    val minute = PromptTiming.weighMinute(sh)
    SettingRow(tr("Heure de la pesée et du check-in"), Bedtime.clock(minute),
        subtitle = tr("Les bandeaux apparaissent à partir de cette heure"), icon = Icons.Rounded.Schedule) {
        TimePickerDialog(ctx, { _, h, m ->
            val chosen = h * 60 + m
            SuperPrefs.update { it.copy(weighPromptMinute = chosen, remindWeigh = if (it.remindWeigh >= 0) chosen else -1) }
            Reminders.schedule(ctx)
        }, minute / 60, minute % 60, true).show()
    }
}
