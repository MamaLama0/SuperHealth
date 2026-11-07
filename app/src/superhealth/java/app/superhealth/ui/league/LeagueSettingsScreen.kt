package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import app.superhealth.ui.settings.IconSwitchRow
import app.superhealth.ui.theme.HealthHues
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.go
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

/**
 * Page du Groupe : deux interrupteurs (fil, classement) puis des lignes vers des pages courtes, regroupées comme les
 * réglages de l'app (profil, score, lecture, données). Les longs textes de calcul vivent dans leurs sous-pages.
 */
@Composable
fun LeagueSettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by League.state.collectAsState(); val sh by SuperPrefs.settings.collectAsState()
    val history by GroupHistory.state.collectAsState()
    DetailScaffold(tr("Groupe"), onBack = { nav.popBackStack() }) {
        GroupCard {
            IconSwitchRow(tr("Fil du Groupe"), tr("Glissez vers la droite depuis l’accueil"), Icons.Rounded.DynamicFeed, HealthHues.Fitness, sh.socialFeedEnabled) { on ->
                SuperPrefs.update { it.copy(socialFeedEnabled = on) }
            }
            RowDivider(56.dp)
            // le Groupe se retire comme les autres plugins : collection, amis et publications restent sur le téléphone
            SettingRow(tr("Plugin Groupe"), subtitle = tr("Mettre à jour ou désinstaller"), icon = Icons.Rounded.EmojiEvents) { nav.go("modules") }
        }
        GroupHeader(tr("Profil"))
        GroupCard {
            SettingRow(tr("Mon profil"), subtitle = tr("Titre, badges et aperçu pour vos amis"), icon = Icons.Rounded.AccountCircle) { nav.go("social") }
            RowDivider(56.dp)
            SettingRow(tr("Amis"), st.friends.size.takeIf { it > 0 }?.toString(), subtitle = tr("Ajouter par code ou QR"), icon = Icons.Rounded.People) { nav.go("friends") }
            RowDivider(56.dp)
            SettingRow(tr("Ce que je partage"), subtitle = tr("Indice, photo, progression et activités"), icon = Icons.Rounded.Visibility) { nav.go("group/sharing") }
        }
        GroupHeader(tr("Score et objectifs"))
        GroupCard {
            SettingRow(tr("Suivis et objectifs"), subtitle = tr("Ce qui compte dans votre score, et comment il est calculé"), icon = Icons.Rounded.Insights) { nav.go("group/score") }
        }
        GroupHeader(tr("Lecture et alertes"))
        GroupCard {
            SettingRow(tr("Notifications"), subtitle = tr("Publications, réponses et réactions"), icon = Icons.Rounded.NotificationsActive) { nav.go("settings/notifications") }
            RowDivider(56.dp)
            SettingRow(tr("Contenus masqués et sourdines"), (st.hiddenSocialPosts.size + st.mutedSocialFriends.size).takeIf { it > 0 }?.toString(),
                icon = Icons.Rounded.VisibilityOff) { nav.go("group/preferences") }
        }
        GroupHeader(tr("Compte et données"))
        GroupCard {
            SettingRow(tr("Synchronisation et serveur"), subtitle = tr("Fréquence, serveur personnalisé et liens de partage"), icon = Icons.Rounded.Sync) { nav.go("group/sync") }
            RowDivider(56.dp)
            SettingRow(tr("Envois en attente"), history.pending.size.takeIf { it > 0 }?.toString(), icon = Icons.Rounded.CloudUpload) { nav.go("social/outbox") }
            RowDivider(56.dp)
            SettingRow(tr("Retrouver mon Groupe"), subtitle = tr("Code de récupération du profil et des amis"), icon = Icons.Rounded.Key) { nav.go("group/recovery") }
            RowDivider(56.dp)
            SettingRow(tr("Mes données sociales"), subtitle = tr("Export, suppression et appareil perdu"), icon = Icons.Rounded.Shield) { nav.go("group/lifecycle") }
        }
        Text(tr("Les journaux de santé restent sur votre téléphone. Les profils et partages sont chiffrés."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** Ce que vos amis reçoivent : chaque élément se coupe indépendamment. */
@Composable
fun GroupSharingScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val st by League.state.collectAsState(); val sh by SuperPrefs.settings.collectAsState()
    val modules by app.superhealth.modules.Modules.installed.collectAsState()
    val available = remember(sh.features, modules) { ProgressRepository.availableTracks(sh) }
    DetailScaffold(tr("Ce que je partage"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp) {
        Text(tr("Vos amis ne reçoivent que ces éléments, chiffrés. Vos journaux détaillés restent sur le téléphone."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        GroupCard {
            SwitchRow(tr("Partager mon indice"), tr("Le score et ses composantes peuvent être masqués tout en participant au Groupe."), st.shareIndex) { on ->
                League.configure(ctx) { it.copy(shareIndex = on) }; LeagueWorker.onOpen(ctx, force = true)
            }
            RowDivider()
            SwitchRow(tr("Photo de profil"), tr("Une petite vignette chiffrée, visible par vos amis"), st.shareProfilePhoto) { on ->
                League.configure(ctx) { it.copy(shareProfilePhoto = on) }; LeagueWorker.onOpen(ctx, force = true)
            }
            RowDivider()
            SwitchRow(tr("Progression"), tr("Votre progression agrégée, sans journal détaillé"), st.shareProgress) { on -> League.configure(ctx) { it.copy(shareProgress = on) } }
            RowDivider()
            SwitchRow(tr("Détail par partie"), tr("Partager les composantes de l'indice"), st.sharePillars) { League.setSharePillars(ctx, it) }
            if (available.any { it in setOf(Progression.Track.STRENGTH, Progression.Track.RUN, Progression.Track.HIKE) }) {
                RowDivider()
                SwitchRow(tr("Activités, courses et PR"), tr("Agrégats sportifs uniquement, sans tracé GPS"), st.shareActivities) { on -> League.configure(ctx) { it.copy(shareActivities = on) } }
            }
        }
        GroupCard { SettingRow(tr("Voir mon profil comme un ami"), icon = Icons.Rounded.Visibility) { nav.go("social/preview") } }
    }
}

/** Suivis comptés dans le score, objectifs hebdomadaires et explications du calcul. */
@Composable
fun GroupScoreScreen(nav: NavHostController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val st by League.state.collectAsState(); val sh by SuperPrefs.settings.collectAsState()
    val training by app.superhealth.training.WorkoutApp.store.state.collectAsState()
    val planTarget = training.programs.firstOrNull { it.id == training.settings.activeProgramId }?.leagueTarget()
    val modules by app.superhealth.modules.Modules.installed.collectAsState()
    val available = remember(sh.features, modules) { ProgressRepository.availableTracks(sh) }
    val selected = st.tracked ?: ProgressRepository.selectedTracks(sh).map { it.name }.toSet()
    var healthRules by remember { mutableStateOf(false) }
    DetailScaffold(tr("Suivis et objectifs"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp) {
        GroupHeader(tr("Suivis comptés"), Modifier.padding(start = 0.dp))
        Text(modifier = Modifier.padding(horizontal = 4.dp), text = tr("Seuls les suivis cochés entrent dans votre score. Il faut au moins trois domaines renseignés."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Text(modifier = Modifier.padding(horizontal = 4.dp), text = tr("Chaque suivi a un objectif. La musculation suit les séances du programme ; les jours de repos restent libres."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        GroupCard {
            available.forEachIndexed { i, t ->
                if (i > 0) RowDivider()
                SwitchRow(tr(t.label), trackingGoal(t).takeIf { t != Progression.Track.FOOD }, t.name in selected) { on ->
                    League.configure(ctx) { it.copy(tracked = if (on) selected + t.name else selected - t.name) }
                    scope.launch { ProgressRepository.compute(ctx) }
                }
                if (t == Progression.Track.STRENGTH && t.name in selected && planTarget != null) {
                    Text(trf("%1\$s séances prévues", planTarget), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else if (t.weekly && t.name in selected) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    ChipChoice((1..7).toList(), st.weeklyTargets[t.name] ?: if(t==Progression.Track.HIKE) 1 else 2, { if (t == Progression.Track.STRENGTH) trf(if (it > 1) "%1\$s séances" else "%1\$s séance", it) else trf("%1\$s j", it) }) { days ->
                        League.configure(ctx) { it.copy(weeklyTargets = it.weeklyTargets + (t.name to days)) }; scope.launch { ProgressRepository.compute(ctx) }
                    }
                    }
                }
            }
        }
        GroupCard { SettingRow(tr("Comment c'est calculé"), subtitle = tr("Indice global de santé")) { healthRules = !healthRules } }
        if (healthRules) MfCard {
            Text(tr("Le score porte sur les sept derniers jours terminés. Il faut au moins trois domaines. Chaque domaine compte selon son poids et la part des jours mesurés."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Un jour validé pour les XP vaut 100 dans son domaine. En dehors, la note baisse avec l'écart à la zone visée."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            listOf(
                "Musculation (poids 20) · Séances prévues pour 85 %, et votre tendance de force pour 15 %, selon toutes vos séries récentes.",
                "Cardio (poids 20) · 150 minutes et vos jours de course pour 85 %, et la tendance de votre allure pour 15 %.",
                "Pas (poids 15) · Chaque jour face à votre objectif de pas, qui vaut 100 une fois atteint. Au moins 4 jours.",
                "Sommeil (poids 25) · Chaque nuit face à votre objectif de sommeil, et régularité pour un cinquième. Au moins 4 nuits.",
                "Nutrition (poids 15) · Calories dans la zone de validation et protéines atteintes, sur les journées complètes. Au moins 4 jours.",
                "Esprit et mobilité (poids 5) · Méditation et étirements face à 70 min sur 7 jours."
            ).forEach { Text(tr(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** Synchronisation avec le relais chiffré et renouvellement des liens de partage. */
@Composable
fun GroupSyncScreen(nav: NavHostController) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var confirmRotate by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    DetailScaffold(tr("Synchronisation et serveur"), onBack = { nav.popBackStack() }, contentSpacing = 12.dp) {
        RelaySettings()
        GroupHeader(tr("Confidentialité avancée"), Modifier.padding(start = 0.dp))
        MfCard {
            Text(tr("Le Groupe conserve vos publications, réponses et médias pendant cinq ans. Les journaux de santé restent sur votre téléphone. Les profils et partages sont chiffrés ; l'hébergeur peut observer les métadonnées de connexion."), style = MaterialTheme.typography.bodySmall)
            PillButton(tr("Renouveler mes liens de partage"), Modifier.fillMaxWidth().padding(top = 10.dp), strong = false) { confirmRotate = true }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirmRotate) AlertDialog(onDismissRequest = { confirmRotate = false }, title = { Text(tr("Révoquer les anciennes invitations ?")) },
        text = { Text(tr("Les anciens accès seront révoqués après conservation de vos publications et médias. Vos amis à jour suivront le nouveau lien ; les autres auront besoin d'une nouvelle invitation.")) },
        confirmButton = { TextButton({ confirmRotate = false; scope.launch { runCatching { Relay.rotate(ctx); Relay.sync(ctx) }.onSuccess { message = tr("Accès renouvelés. Une nouvelle invitation reste disponible si nécessaire.") }.onFailure { message = it.message } } }) { Text(tr("Renouveler")) } },
        dismissButton = { TextButton({ confirmRotate = false }) { Text(tr("Annuler")) } })
}
