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
    val training by app.superhealth.training.WorkoutApp.store.state.collectAsState()
    val planTarget = training.programs.firstOrNull { it.id == training.settings.activeProgramId }?.leagueTarget()
    val modules by app.superhealth.modules.Modules.installed.collectAsState()
    val available = remember(sh.features, modules) { ProgressRepository.availableTracks(sh) }
    val selected = st.tracked ?: ProgressRepository.selectedTracks(sh).map { it.name }.toSet()
    var healthRules by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    DetailScaffold(tr("Classement et progression"), onBack = { nav.popBackStack() }) {
        GroupCard {
            SwitchRow(tr("Activer le classement et la progression"), tr("Les données et les amis restent conservés à la désactivation"), sh.leagueEnabled) { on ->
                SuperPrefs.update { it.copy(leagueEnabled = on) }; LeagueWorker.schedule(ctx)
            }
        }
        SectionLabel(tr("Mes activités et mon score"))
        Text(tr("Seuls les suivis cochés entrent dans votre score. Il faut au moins trois domaines renseignés."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Text(tr("Chaque suivi a un objectif. La musculation suit les séances du programme ; les jours de repos restent libres."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        GroupCard {
            available.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                SwitchRow(tr(t.label), trackingGoal(t), t.name in selected) { on ->
                    League.configure(ctx) { it.copy(tracked = if (on) selected + t.name else selected - t.name) }
                    scope.launch { ProgressRepository.compute(ctx) }
                }
                if (t == Progression.Track.STRENGTH && t.name in selected && planTarget != null) {
                    Text(trf("%1\$s séances prévues", planTarget), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
            if (available.any { it in setOf(Progression.Track.STRENGTH, Progression.Track.RUN) }) {
                RowDivider()
                SwitchRow(tr("Activités, courses et PR"), tr("Agrégats sportifs uniquement, sans tracé GPS"), st.shareActivities) { on -> League.configure(ctx) { it.copy(shareActivities = on) } }
            }
        }
        SectionLabel(tr("Progression et calcul"))
        MfCard {
            Text(tr("Une catégorie rapporte ses XP une fois par jour : nutrition 20, musculation 40, course 30, pas 10, sommeil 10, esprit et mobilité 20. Un PR rapporte 10 XP, au plus deux par jour. Le total est plafonné à 120 XP par jour."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Les XP suivent vos activités et vos objectifs atteints. Ils sont recalculés depuis vos journaux. L'indice de santé reste un calcul séparé."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Text(tr("La reprise des données de montre est limitée aux 90 derniers jours. Une série qui atteint cette limite porte un + ; les périodes en cours ne sont pas déclarées perdues avant leur fin."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        GroupCard { SettingRow(tr("Comment c'est calculé"), subtitle = tr("Indice global de santé")) { healthRules = !healthRules } }
        if (healthRules) MfCard {
            Text(tr("Le score porte sur les sept derniers jours terminés. Il faut au moins trois domaines. La moyenne sur 1 000 utilise uniquement leurs poids."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Chaque pourcent de progrès vaut un point sur 100 dans le domaine sportif. La référence est votre meilleur résultat des 28 jours précédant la période. Sans référence, seuls les points de volume comptent."), style = MaterialTheme.typography.bodySmall)
            listOf(
                "Musculation (poids 20) · Jusqu’à 90 points pour votre objectif de séances et 10 pour un gain de force estimée ou de répétitions sur le même exercice.",
                "Cardio (poids 20) · Jusqu’à 90 points pour 150 minutes et 10 pour un gain de vitesse sur une course d’au moins 1 km de distance comparable (±10 %).",
                "Moyenne des jours renseignés face à votre objectif de pas. Au moins 4 jours. Poids relatif : 15.",
                "Moyenne des nuits renseignées face à 7 h. Au moins 4 nuits. Poids relatif : 25.",
                "Écart relatif entre apports et cibles, évalué progressivement. Au moins 4 jours renseignés. Poids relatif : 15.",
                "Méditation et étirements face à 70 min sur 7 jours. Poids relatif : 5."
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
