package app.superhealth.training.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.training.domain.WeightUnit
import app.superhealth.training.domain.WorkoutImport
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.components.SettingRow
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ligne « Importer un historique » : fichier CSV de Strong, Hevy ou FitNotes, aperçu des correspondances puis ajout. */
@Composable
fun HistoryImportRow(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<WorkoutImport.Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching {
                val db = vm.db.value ?: error(tr("Base d'exercices en cours de chargement"))
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } ?: error(tr("Fichier illisible"))
                val st = vm.state.value
                withContext(Dispatchers.Default) { WorkoutImport.preview(text, db, st.history, defaultKg = st.settings.unit == WeightUnit.KG) }
            }
            busy = false
            result.onSuccess { preview = it }.onFailure {
                Toast.makeText(ctx, if (it is WorkoutImport.UnknownFormat) tr("Format non reconnu : export CSV de Strong, Hevy ou FitNotes attendu") else it.message ?: tr("Fichier illisible"), Toast.LENGTH_LONG).show()
            }
        }
    }
    SettingRow(tr("Importer un historique"), subtitle = tr("Export CSV de Strong, Hevy ou FitNotes ; ajouté sans remplacer vos séances"), icon = Icons.Rounded.History) {
        if (!busy) pick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
    }
    preview?.let { p ->
        AlertDialog(onDismissRequest = { preview = null }, containerColor = Mf.colors.card,
            title = { Text(trf("Importer depuis %1\$s", p.source.label)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (p.workouts.isEmpty()) Text(tr("Aucune nouvelle séance dans ce fichier."), color = Mf.colors.textSecondary)
                    else Text(trf("%1\$s séances, %2\$s séries", p.workouts.size, p.sets), style = MaterialTheme.typography.bodyLarge)
                    if (p.duplicates > 0) Text(trf("%1\$s séances déjà présentes, ignorées", p.duplicates), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (p.skippedRows > 0) Text(trf("%1\$s lignes sans série de musculation, ignorées", p.skippedRows), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (p.newExercises.isNotEmpty()) Text(trf("%1\$s exercices introuvables, créés comme exercices personnels", p.newExercises.size), color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Correspondances"), style = MaterialTheme.typography.labelLarge)
                    p.matches.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(m.sourceName + "  →  " + (m.exercise?.name ?: tr("nouvel exercice")), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = if (m.exercise == null) Mf.colors.textSecondary else Mf.colors.text)
                        }
                    }
                }
            },
            confirmButton = {
                if (p.workouts.isNotEmpty()) TextButton(onClick = {
                    vm.update { s ->
                        val ids = s.history.map { it.id }.toSet()
                        s.copy(history = (s.history + p.workouts.filter { it.id !in ids }).sortedBy { it.startTime },
                            customExercises = s.customExercises + p.newExercises.filter { e -> s.customExercises.none { it.id == e.id } })
                    }
                    preview = null
                    Toast.makeText(ctx, trf("%1\$s séances importées", p.workouts.size), Toast.LENGTH_LONG).show()
                }) { Text(tr("Importer"), color = Mf.colors.text) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(tr("Annuler"), color = Mf.colors.textSecondary) } })
    }
}
