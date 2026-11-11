package app.superhealth.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.cards.AthleteStats
import app.superhealth.cards.Standards
import app.superhealth.cards.Standards.Lift
import app.superhealth.nutrition.domain.Sex
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf

private val levels = listOf("Débutant", "Novice", "Intermédiaire", "Avancé", "Élite")

private fun Lift.label() = when (this) {
    Lift.BENCH -> "Développé couché"
    Lift.SQUAT -> "Squat"
    Lift.DEADLIFT -> "Soulevé de terre"
    Lift.OHP -> "Développé militaire"
    Lift.ROW -> "Rowing à la barre"
    Lift.PULLUP -> "Tractions"
    Lift.DIP -> "Dips"
    Lift.CURL -> "Curl à la barre"
    Lift.TRICEPS -> "Extension triceps"
    Lift.HIP_THRUST -> "Hip thrust"
    Lift.LEG_PRESS -> "Presse à cuisses"
    Lift.PULLDOWN -> "Tirage vertical"
    Lift.LEG_EXTENSION -> "Extension des jambes"
    Lift.LEG_CURL -> "Flexion des jambes"
    Lift.LATERAL_RAISE -> "Élévation latérale"
    Lift.SHRUG -> "Haussement d’épaules"
    Lift.CALF_RAISE -> "Extension des mollets"
    Lift.FLY -> "Écarté à la machine"
    Lift.LUNGE -> "Fente à la barre"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StrengthStandards(athlete: AthleteStats?, onDismiss: () -> Unit) {
    var lift by rememberSaveable { mutableStateOf(Lift.BENCH) }
    var choices by remember { mutableStateOf(false) }
    val sex = if (athlete?.female == true) Sex.FEMALE else Sex.MALE
    val evidence = athlete?.training?.evidence
    val bodyweight = evidence?.bodyweightKg ?: if (sex == Sex.FEMALE) 60.0 else 80.0
    val thresholds = Standards.athleteThresholds(lift, sex, bodyweight)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Barèmes de force"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Les seuils comparent une charge maximale estimée sur une répétition au poids du corps."),
                color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Box {
                FilledTonalButton({ choices = true }) { Text(tr(lift.label())) }
                DropdownMenu(choices, { choices = false }) {
                    Lift.entries.forEach { item ->
                        DropdownMenuItem({ Text(tr(item.label())) }, onClick = { lift = item; choices = false })
                    }
                }
            }
            Text(trf("Poids de corps utilisé · %1\$s kg", formatNumber(bodyweight, 1)) +
                if (evidence?.bodyweightKnown == true) "" else " · " + tr("estimation"),
                style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Niveau"), Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                    Text(tr("× poids du corps"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text(tr("Charge"), Modifier.weight(.7f), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                thresholds.forEachIndexed { i, ratio ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.2f)) {
                            Text(tr(levels[i]), fontWeight = FontWeight.SemiBold)
                            Text("${Standards.levelPoints[i].toInt()} / 100", color = Mf.colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("× " + formatNumber(ratio, 2), Modifier.weight(1f))
                        Text(formatNumber(ratio * bodyweight, 1) + " kg", Modifier.weight(.7f))
                    }
                }
            }
            if (lift.bodyweightLoad) Text(tr("Pour ce mouvement, la charge totale inclut le poids du corps et le lest."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
    }
}
