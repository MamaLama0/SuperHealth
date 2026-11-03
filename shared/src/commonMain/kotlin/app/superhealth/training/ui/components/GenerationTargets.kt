package app.superhealth.training.ui.components

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Goal
import app.superhealth.training.ui.tr
import app.superhealth.ui.theme.Mf

/** État gardé par le parcours parent afin de conserver les champs en revenant sur cette étape. */
@Stable
class GenerationTargetsState {
    var expanded by mutableStateOf(false)
    internal var fields by mutableStateOf<Map<Generator.Template, Fields>>(emptyMap())
    internal data class Fields(val min: String, val max: String, val rir: String) {
        fun parsed(): Generator.TargetPreference? = runCatching {
            Generator.TargetPreference(requireNotNull(min.toIntOrNull()), requireNotNull(max.toIntOrNull()), requireNotNull(rir.toIntOrNull()))
        }.getOrNull()
    }
    val isValid: Boolean get() = fields.values.all { it.parsed() != null }
    fun preferences(): Map<Generator.Template, Generator.TargetPreference> = fields.mapValues { requireNotNull(it.value.parsed()) }
}

/** Options de génération partagées entre l'assistant complet et l'introduction. */
@Composable
fun GenerationTargetsEditor(state: GenerationTargetsState, goal: Goal) {
    SettingRow(tr("RIR et répétitions"), subtitle = tr("Automatiques ou personnalisés par famille")) { state.expanded = !state.expanded }
    if (!state.expanded) return
    Text(tr("Ces cibles concernent les exercices avec charge et répétitions. Gainage, durée et exercices sans charge gardent leurs cibles adaptées."),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
    Generator.Template.entries.forEach { template ->
        val title = when (template) {
            Generator.Template.PRIMARY -> tr("Mouvements principaux")
            Generator.Template.SECONDARY -> tr("Mouvements secondaires")
            Generator.Template.ACCESSORY -> tr("Isolation et accessoires")
        }
        val fields = state.fields[template]
        MfCard(Modifier.padding(vertical = 4.dp)) {
            SwitchRow(title, tr(if (fields == null) "Cibles automatiques" else "Cibles personnalisées"), fields != null) { on ->
                state.fields = if (!on) state.fields - template else {
                    val target = Generator.baseTarget(goal, template)
                    state.fields + (template to GenerationTargetsState.Fields(target.minReps.toString(), target.maxReps.toString(), target.rir.toString()))
                }
            }
            if (fields != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(tr("Rép. min"), fields.min, { state.fields = state.fields + (template to fields.copy(min = it)) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                    NumberField(tr("Rép. max"), fields.max, { state.fields = state.fields + (template to fields.copy(max = it)) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                    NumberField(tr("RIR"), fields.rir, { state.fields = state.fields + (template to fields.copy(rir = it)) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                }
                if (fields.parsed() == null) Text(tr("Répétitions de 1 à 50, minimum ≤ maximum ; RIR de 0 à 10."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Text(tr("Les cibles personnalisées restent fixes pendant les cycles de travail. Une décharge conserve moins de séries et un RIR plus élevé. Les autres exercices suivent la périodisation choisie."),
        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
}
