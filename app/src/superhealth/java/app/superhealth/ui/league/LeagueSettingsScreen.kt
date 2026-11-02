package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

@Composable
fun LeagueSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val st by League.state.collectAsState(); val sh by SuperPrefs.settings.collectAsState()
    val progress by ProgressRepository.current.collectAsState()
    val selected = st.tracked ?: progress?.summary?.streaks?.map { it.track }?.toSet() ?: setOf("FOOD", "STRENGTH")
    var healthRules by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    DetailScaffold(tr("Classement et progression"), onBack = { nav.popBackStack() }) {
        GroupCard {
            SwitchRow(tr("Activer le classement et la progression"), tr("Les données et les amis restent conservés à la désactivation"), sh.leagueEnabled) { on ->
                SuperPrefs.update { it.copy(leagueEnabled = on) }; LeagueWorker.schedule(ctx)
            }
        }
        SectionLabel(tr("Mes séries"))
        Text(tr("Chaque suivi a un objectif. La musculation suit les séances du programme ; les jours de repos restent libres."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        GroupCard {
            Progression.Track.entries.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                SwitchRow(tr(t.label), trackingGoal(t), t.name in selected) { on ->
                    League.configure(ctx) { it.copy(tracked = if (on) selected + t.name else selected - t.name) }
                    scope.launch { ProgressRepository.compute(ctx) }
                }
                if (t == Progression.Track.STRENGTH && t.name in selected && app.superhealth.training.WorkoutApp.store.state.value.let { data -> data.programs.any { it.id == data.settings.activeProgramId && it.days.any { day -> !day.isRestAt(it.cycleIndex) } } }) {
                    Text(trf("%1\$s séances prévues", progress?.summary?.streaks?.firstOrNull { it.track == t.name }?.target ?: 2), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else if (t.weekly && t.name in selected) {
                    ChipChoice((1..7).toList(), st.weeklyTargets[t.name] ?: 2, { if (t == Progression.Track.STRENGTH) trf("%1\$s séances", it) else trf("%1\$s j", it) }) { days ->
                        League.configure(ctx) { it.copy(weeklyTargets = it.weeklyTargets + (t.name to days)) }; scope.launch { ProgressRepository.compute(ctx) }
                    }
                }
            }
        }
        SectionLabel(tr("Ce que je partage"))
        GroupCard {
            SwitchRow(tr("Niveaux, XP et séries"), tr("Votre progression agrégée, sans journal détaillé"), st.shareProgress) { on -> League.configure(ctx) { it.copy(shareProgress = on) } }
            RowDivider()
            SwitchRow(tr("Détail par partie"), tr("Partager les composantes de l'indice"), st.sharePillars) { League.setSharePillars(ctx, it) }
            RowDivider()
            SwitchRow(tr("Activités, courses et PR"), tr("Agrégats sportifs uniquement, sans tracé GPS"), st.shareActivities) { on -> League.configure(ctx) { it.copy(shareActivities = on) } }
        }
        SectionLabel(tr("Progression et calcul"))
        MfCard {
            Text(tr("Une catégorie rapporte ses XP une fois par jour : nutrition 20, musculation 40, course 30, pas 10, sommeil 10, esprit et mobilité 20. Un PR rapporte 10 XP, au plus deux par jour. Le total est plafonné à 120 XP par jour."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Les XP suivent vos activités et vos objectifs atteints. Ils sont recalculés depuis vos journaux. L'indice de santé reste un calcul séparé."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Text(tr("La reprise des données de montre est limitée aux 90 derniers jours. Une série qui atteint cette limite porte un + ; les périodes en cours ne sont pas déclarées perdues avant leur fin."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        GroupCard { SettingRow(tr("Comment c'est calculé"), subtitle = tr("Indice global de santé")) { healthRules = !healthRules } }
        if (healthRules) MfCard {
            Text(tr("Indice SuperHealth v2 : moyenne pondérée sur 1000, calculée sur les 7 derniers jours terminés, sans remise à zéro le lundi. Au moins 3 parties renseignées ; leurs poids sont renormalisés. Les sources absentes restent inconnues. C’est un indice d’habitudes de l’app, pas une mesure médicale. Comparez aussi les parties renseignées, car leur couverture peut différer."), style = MaterialTheme.typography.bodySmall)
            listOf(
                "Séances sur 7 jours terminés, plafonnées à 2. Poids : 20 %.",
                "Minutes hors musculation face à 150 min sur 7 jours. Poids : 20 %.",
                "Moyenne des jours renseignés face à votre objectif de pas. Au moins 4 jours. Poids : 15 %.",
                "Moyenne des nuits renseignées face à 7 h. Au moins 4 nuits. Poids : 25 %.",
                "Écart relatif entre apports et cibles, évalué progressivement. Au moins 4 jours renseignés. Poids : 15 %.",
                "Méditation et étirements face à 70 min sur 7 jours. Poids : 5 %."
            ).forEach { Text(tr(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
        RelaySettings()
        SectionLabel(tr("Confidentialité avancée"))
        MfCard {
            Text(tr("Le relais conserve une seule carte chiffrée par personne. Il ne reçoit ni prénom, ni score, ni clé de lecture en clair. Les adresses et capacités sont aléatoires ; l'hébergeur peut toujours observer des métadonnées de connexion."), style = MaterialTheme.typography.bodySmall)
            PillButton(tr("Renouveler mes liens de partage"), Modifier.fillMaxWidth().padding(top = 10.dp), strong = false) { confirmRotate = true }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirmRotate) AlertDialog(onDismissRequest = { confirmRotate = false }, title = { Text(tr("Révoquer les anciennes invitations ?")) },
        text = { Text(tr("L'ancienne carte distante sera supprimée. Vous devrez partager une nouvelle invitation avec vos amis pour rétablir les mises à jour à distance.")) },
        confirmButton = { TextButton({ confirmRotate = false; scope.launch { runCatching { Relay.rotate(ctx); Relay.sync(ctx) }.onSuccess { message = tr("Partage renouvelé : envoyez votre nouvelle invitation.") }.onFailure { message = it.message } } }) { Text(tr("Renouveler")) } },
        dismissButton = { TextButton({ confirmRotate = false }) { Text(tr("Annuler")) } })
}
