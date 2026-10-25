package app.superhealth.training.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Exercise
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.MuscleGroup
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.components.BodyMap
import app.superhealth.training.ui.components.ChipChoice
import app.superhealth.training.ui.components.GroupCard
import app.superhealth.training.ui.components.MfCard
import app.superhealth.training.ui.components.NumberField
import app.superhealth.training.ui.components.PillButton
import app.superhealth.training.ui.components.SectionLabel
import app.superhealth.training.ui.components.TextInput
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.training.ui.toNum
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import java.util.UUID
import kotlin.math.roundToInt

private enum class Metric(val label: String, val codes: List<String>) {
    WEIGHT("Charge et répétitions", listOf("w", "r")), BODYWEIGHT("Poids du corps", listOf("r")),
}

/**
 * Création / modification d'un exercice personnel (edit_exercise_view de MacroFactor Workouts :
 * « Build your exercise » → muscles, équipement, détails finaux). Rôle d'un muscle : touche = principal, encore = secondaire.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(vm: AppViewModel, id: String?, nav: NavHostController) {
    val st by vm.state.collectAsState()
    val db = vm.db.collectAsState().value ?: return
    val old = id?.let { i -> st.customExercises.firstOrNull { it.id == i } }
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    var alt by remember { mutableStateOf(old?.alternatives?.joinToString(", ").orEmpty()) }
    var type by remember { mutableStateOf(old?.type ?: "compound") }
    var lat by remember { mutableStateOf(old?.laterality ?: "bilateral") }
    var metric by remember { mutableStateOf(if (old != null && !old.usesWeight) Metric.BODYWEIGHT else Metric.WEIGHT) }
    var bw by remember { mutableStateOf(old?.bodyweight?.let { (it * 100).roundToInt().toString() } ?: "65") }
    // 1 = principal, 2 = secondaire
    val roles = remember {
        mutableStateMapOf<MuscleGroup, Int>().apply {
            old?.muscleGroups?.forEach { put(it, 1) }; old?.secondaryMuscleGroups?.forEach { if (it !in this) put(it, 2) }
        }
    }
    val equipment = remember { mutableStateMapOf<String, Boolean>().apply { old?.resistanceGroups?.forEach { put(it, true) } } }
    var eqQuery by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val duplicate = db.exercises.any { it.id != old?.id && ExerciseDb.norm(it.name) == ExerciseDb.norm(trimmed) }
    val primary = MuscleGroup.entries.filter { roles[it] == 1 }
    val valid = trimmed.isNotEmpty() && !duplicate && primary.isNotEmpty()

    fun build(): Exercise {
        val upper = primary.count { it.upper }
        val core = primary.count { it in listOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES, MuscleGroup.LOWER_BACK) }
        val region = when { core == primary.size -> "core"; upper == primary.size -> "upper"; upper == 0 -> "lower"; else -> "full" }
        return Exercise(
            id = old?.id ?: (ExerciseDb.CUSTOM_PREFIX + UUID.randomUUID()), name = trimmed,
            alternatives = alt.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            type = type, region = region, laterality = lat,
            primaryGroups = primary.map { it.key }, secondaryGroups = MuscleGroup.entries.filter { roles[it] == 2 }.map { it.key },
            resistanceGroups = if (metric == Metric.WEIGHT) equipment.filterValues { it }.keys.toList() else emptyList(),
            metrics = if (lat == "bilateral") metric.codes else metric.codes.map { if (it == "r") "rps" else it },
            bodyweight = if (metric == Metric.BODYWEIGHT) (bw.toNum() ?: 65.0).coerceIn(0.0, 100.0) / 100 else null,
        )
    }

    DetailScaffold(if (old == null) tr("Nouvel exercice") else tr("Modifier l'exercice"), nav, actions = {
        if (old != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, tr("Supprimer"), tint = app.superhealth.ui.theme.Base.Red) }
    }) {
        TextInput(tr("Nom de l'exercice"), name, { name = it }, placeholder = tr("Ex. : Développé incliné à la machine"))
        if (duplicate) Text(tr("Un exercice porte déjà ce nom"), color = app.superhealth.ui.theme.Base.Red, style = MaterialTheme.typography.bodySmall)
        TextInput(tr("Autres noms (séparés par une virgule)"), alt, { alt = it })

        SectionLabel(tr("Muscles"))
        MfCard {
            val intensity = roles.entries.associate { (g, r) -> g.key to if (r == 1) 1f else 0.45f }
            BodyMap(intensity, st.settings.sex, Modifier.fillMaxWidth().height(170.dp))
            Spacer(Modifier.height(10.dp))
            Text(tr("Touchez une fois pour un muscle principal, deux fois pour un secondaire."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MuscleGroup.entries.forEach { g ->
                    val r = roles[g] ?: 0
                    Text(g.label.tr() + when (r) { 1 -> " ●"; 2 -> " ○"; else -> "" },
                        Modifier.clip(CircleShape).background(when (r) { 1 -> WorkoutBlue; 2 -> WorkoutBlue.copy(alpha = 0.18f); else -> Mf.colors.chip })
                            .clickable { when (r) { 0 -> roles[g] = 1; 1 -> roles[g] = 2; else -> roles.remove(g) } }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (r == 1) Color.White else Mf.colors.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        SectionLabel(tr("Suivi"))
        ChipChoice(Metric.entries, metric, { it.label }) { metric = it }
        if (metric == Metric.BODYWEIGHT) NumberField(tr("Part du poids de corps soulevée"), bw, { bw = it }, suffix = "%")

        if (metric == Metric.WEIGHT) {
            SectionLabel(tr("Équipement de résistance"))
            Text(tr("Pour le calculateur de disques et l'arrondi des charges."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            TextInput(tr("Filtrer l'équipement"), eqQuery, { eqQuery = it })
            val pool = remember(eqQuery, db) {
                db.equipment.filter { it.category != "Body Weights" && it.category != "Cardio" && it.pluralOf == null }
                    .filter { eqQuery.isBlank() || ExerciseDb.norm(it.name).contains(ExerciseDb.norm(eqQuery)) }
                    .sortedWith(compareBy({ equipment[it.id] != true }, { it.name }))
            }
            GroupCard {
                pool.take(if (eqQuery.isBlank()) 12 else 40).forEach { eq ->
                    val sel = equipment[eq.id] == true
                    Row(Modifier.fillMaxWidth().clickable { equipment[eq.id] = !sel }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(eq.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = WorkoutBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        SectionLabel(tr("Détails"))
        Text(tr("Type"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        ChipChoice(listOf("compound", "isolation", "core"), type, { when (it) { "compound" -> "Polyarticulaire"; "isolation" -> "Isolation"; else -> "Gainage" } }) { type = it }
        Spacer(Modifier.height(10.dp))
        Text(tr("Latéralité"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        ChipChoice(listOf("bilateral", "unilateral"), lat, { if (it == "bilateral") "Bilatéral" else "Unilatéral" }) { lat = it }
        if (lat == "unilateral") Text(tr("Les répétitions sont comptées par côté."), style = MaterialTheme.typography.bodySmall,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(20.dp))
        if (primary.isEmpty()) Text(tr("Choisissez au moins un muscle principal."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp))
        PillButton(if (old == null) tr("Créer l'exercice") else tr("Enregistrer"), Modifier.fillMaxWidth(), enabled = valid) {
            val e = build()
            vm.saveCustomExercise(e)
            nav.previousBackStackEntry?.savedStateHandle?.set("createdExercise", e.id)
            nav.popBackStack()
        }
        Text(tr("Exercice personnel, jamais proposé par le générateur."), style = MaterialTheme.typography.labelSmall,
            color = Mf.colors.textTertiary, modifier = Modifier.padding(top = 10.dp))
    }

    if (confirmDelete && old != null) {
        val used = st.history.count { w -> w.exercises.any { it.exerciseId == old.id } }
        AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = Mf.colors.card,
            title = { Text(tr("Supprimer l'exercice ?")) },
            text = { Text(if (used > 0) trf("Il apparaît dans %1\$s séance(s) de l'historique, qui garderont leurs séries.", used) else tr("Il sera retiré de vos programmes.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCustomExercise(old.id); nav.popBackStack("activities", false) }) {
                Text(tr("Supprimer"), color = app.superhealth.ui.theme.Base.Red, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annuler"), color = Mf.colors.text) } })
    }
}
