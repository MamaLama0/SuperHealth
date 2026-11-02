package app.superhealth.training.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.ProgramTransfer
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.components.*
import app.superhealth.ui.theme.Mf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Programmes fournis avec la référence Workouts autorisée, installés en bibliothèque sur demande. */
@Composable
fun BonusProgramsScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val db by vm.db.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DetailScaffold(tr("Programmes additionnels"), nav) {
        MfCard {
            Text(tr("Spécialisation bras · 30 jours"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Programme additionnel de Workouts 1.4.2 : cinq cycles, séries et supersets variables. Les exercices non prévus dans certains cycles restent exclus. Vous pourrez modifier chaque cible."),
                style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
            PillButton(tr("Ajouter à ma bibliothèque"), Modifier.fillMaxWidth(), enabled = db != null && !busy) {
                scope.launch {
                    busy = true
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ctx.assets.open("programs/arms-30-days.json").bufferedReader().use { ProgramTransfer.read(it, db!!.exercises.map { e -> e.id }.toSet()) }
                        }
                    }.onSuccess { p -> vm.saveProgram(p); nav.navigate("program/${p.id}") }
                        .onFailure { error = it.message }
                    busy = false
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
