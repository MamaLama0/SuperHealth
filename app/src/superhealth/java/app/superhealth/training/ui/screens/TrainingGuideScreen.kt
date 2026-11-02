package app.superhealth.training.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.ui.AppViewModel
import app.superhealth.training.ui.components.*
import app.superhealth.training.ui.tr
import app.superhealth.training.ui.trf
import app.superhealth.ui.theme.Mf
import java.util.UUID

/** Le premier choix ouvre le parcours adapté, sans modifier le programme actif avant validation. */
@Composable
fun TrainingGuideScreen(vm: AppViewModel, nav: NavHostController, initialMethod: String? = null) {
    val state by vm.state.collectAsState()
    var method by rememberSaveable { mutableStateOf(initialMethod) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    var name by rememberSaveable { mutableStateOf(tr("Mon programme")) }
    fun back() { if (method != null && initialMethod == null) method = null else nav.popBackStack() }
    BackHandler { back() }
    app.superhealth.nutrition.ui.screens.DetailScaffold(tr("Mon programme, pas à pas"), onBack = { back() }) {
        when (method) {
            null -> {
                Text(tr("Comment souhaitez-vous préparer votre programme ?"), style = MaterialTheme.typography.headlineSmall)
                Text(tr("Choisissez un point de départ, puis laissez-vous guider."), color = Mf.colors.textSecondary)
                GroupCard {
                    SettingRow(tr("Créer avec l'assistant"), subtitle = tr("Objectif, fréquence, durée, répétitions, RIR et matériel")) { nav.navigate("wizard") }
                    RowDivider()
                    SettingRow(tr("Choisir un programme"), subtitle = tr("Reprendre ma bibliothèque ou découvrir les modèles")) { method = "library" }
                    RowDivider()
                    SettingRow(tr("Composer moi-même"), subtitle = tr("Choisir mes jours, puis ajouter mes exercices")) { method = "manual" }
                    RowDivider()
                    SettingRow(tr("Importer mon programme"), subtitle = tr("Partir d'un fichier SuperHealth ou Workouts")) { nav.navigate("t/settings/data") }
                }
            }
            "library" -> {
                Text(tr("Quel programme souhaitez-vous suivre ?"), style = MaterialTheme.typography.headlineSmall)
                Text(tr("Ouvrez un programme pour le vérifier avant de l'activer."), color = Mf.colors.textSecondary)
                GroupCard {
                    state.programs.forEach { p ->
                        SettingRow(p.name, subtitle = trf("%1\$s jours", p.days.size)) { nav.navigate("program/${p.id}") }
                        RowDivider()
                    }
                    SettingRow(tr("Découvrir les programmes additionnels")) { nav.navigate("t/programs/bonus") }
                }
            }
            "manual" -> {
                Text(tr("Combien de jours comporte votre programme ?"), style = MaterialTheme.typography.headlineSmall)
                ChipChoice((1..7).toList(), days, { it.toString() }) { days = it }
                Spacer(Modifier.height(12.dp))
                TextInput(tr("Nom du programme"), name, { name = it.take(80) })
                Text(tr("Vous ajouterez ensuite les exercices, séries, plages de répétitions et RIR à chaque jour."), color = Mf.colors.textSecondary)
                PillButton(tr("Composer mes séances"), Modifier.fillMaxWidth().padding(top = 16.dp), enabled = name.isNotBlank()) {
                    val p = Program(UUID.randomUUID().toString(), name.trim(), days = (1..days).map {
                        ProgramDay(UUID.randomUUID().toString(), trf("Jour %1\$s", it))
                    }, numCycles = 1, runIndefinitely = true)
                    vm.saveProgram(p)
                    nav.navigate("program/${p.id}") { popUpTo(if (initialMethod == "manual") "t/setup/manual" else "t/setup") { inclusive = true } }
                }
            }
        }
    }
}
