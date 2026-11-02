package app.superhealth.training.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.WarmUpMode
import app.superhealth.training.domain.WarmUpStep
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.toNum
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.components.*
import app.superhealth.ui.theme.Mf

@Composable
fun WarmUpSettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val settings = vm.state.collectAsState().value.settings
    var rows by remember { mutableStateOf(settings.warmUpScheme.map { ((it.percent * 100).toInt()).toString() to it.reps.toString() }) }
    val steps = rows.map { (p, r) ->
        val percent = p.toNum(); val reps = r.toIntOrNull()
        if (percent != null && percent.isFinite() && percent > 0 && percent <= 100 && reps != null && reps in 1..50) WarmUpStep(percent / 100, reps) else null
    }
    DetailScaffold(tr("Schéma d'échauffement"), nav) {
        Text(tr("Pourcentages de la première charge de travail. Le moteur arrondit aux charges disponibles dans votre salle."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        rows.forEachIndexed { i, (percent, reps) ->
            MfCard(Modifier.padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Charge"), percent, { v -> rows = rows.toMutableList().also { it[i] = v to reps } }, Modifier.weight(1f), suffix = "%")
                    NumberField(tr("Répétitions"), reps, { v -> rows = rows.toMutableList().also { it[i] = percent to v } }, Modifier.weight(1f))
                }
                if (rows.size > 1) PillButton(tr("Retirer cette série"), strong = false) { rows = rows.filterIndexed { j, _ -> j != i } }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (rows.size < 8) PillButton(tr("Ajouter une série d'échauffement"), Modifier.fillMaxWidth(), strong = false) { rows = rows + ("80" to "3") }
        Spacer(Modifier.height(12.dp))
        PillButton(tr("Enregistrer le schéma"), Modifier.fillMaxWidth(), enabled = steps.isNotEmpty() && steps.all { it != null }) {
            vm.settings { it.copy(warmUp = WarmUpMode.SCHEME, warmUpScheme = steps.filterNotNull()) }; nav.popBackStack()
        }
    }
}
