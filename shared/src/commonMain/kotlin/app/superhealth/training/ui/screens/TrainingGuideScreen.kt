package app.superhealth.training.ui.screens

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.theme.Mf

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
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(52.dp), tint = app.superhealth.ui.theme.WorkoutBlue)
                }
                Text(tr("Votre entraînement, à votre façon"), Modifier.fillMaxWidth().padding(top = 14.dp),
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(tr("Un point de départ, puis les réglages dont vous avez besoin."), Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 22.dp),
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Mf.colors.textSecondary)
                GuideChoice(Icons.Rounded.AutoAwesome, tr("Créer avec l'assistant"), tr("Objectif, niveau, salle, fréquence, durée, structure, cycles, décharge, répétitions et RIR")) { nav.navigate("wizard") }
                GuideChoice(Icons.Rounded.LibraryBooks, tr("Choisir un programme"), tr("Bibliothèque et modèles ; vérifiez les séances avant activation")) { method = "library" }
                GuideChoice(Icons.Rounded.Edit, tr("Composer moi-même"), tr("Jours, exercices, séries, répétitions, RIR, repos, supersets et cycles")) { method = "manual" }
                GuideChoice(Icons.Rounded.FileDownload, tr("Importer mon programme"), tr("Programme SuperHealth ou Workouts, sauvegarde et export")) { nav.navigate("t/settings/data") }
                Spacer(Modifier.height(14.dp))
                SectionLabel(tr("Adapter mon entraînement"))
                GroupCard {
                    state.programs.firstOrNull { it.id == state.settings.activeProgramId }?.let { active ->
                        SettingRow(tr("Modifier mon programme actif"), subtitle = active.name) { nav.navigate("program/${active.id}") }
                        RowDivider()
                    }
                    SettingRow(tr("Salle et matériel"), subtitle = tr("Salle active, équipement, charges et incréments")) { nav.navigate("t/settings/gyms") }
                    RowDivider()
                    SettingRow(tr("Progression et pré-remplissage"), subtitle = tr("Charges, plages de répétitions et ajustements en séance")) { nav.navigate("t/settings/progression") }
                    RowDivider()
                    SettingRow(tr("Échauffement et récupération"), subtitle = tr("Schéma des séries, durée du repos, sons et minuteur")) { nav.navigate("t/settings/session") }
                    RowDivider()
                    SettingRow(tr("Expérience"), subtitle = tr("Volume et progression adaptés à mon niveau")) { nav.navigate("t/settings/program") }
                }
                if (app.superhealth.ui.coach.coachAvailable()) PillButton(tr("M'aider à choisir mes réglages"), Modifier.fillMaxWidth().padding(top = 12.dp), strong = false) {
                    nav.askCoach(tr("Aide-moi à choisir les réglages de musculation adaptés à mon objectif et à mon expérience. Si je cherche une option, propose un bouton vers le réglage correspondant. Demande les précisions utiles avant de proposer un changement de programme."))
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

@Composable
private fun GuideChoice(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    MfCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(app.superhealth.ui.theme.WorkoutBlue.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = app.superhealth.ui.theme.WorkoutBlue)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
        }
    }
}
