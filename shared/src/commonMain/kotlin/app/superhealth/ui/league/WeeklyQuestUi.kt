package app.superhealth.ui.league

import app.superhealth.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Tune
import app.superhealth.SuperPrefs
import app.superhealth.Feature
import app.superhealth.has
import app.superhealth.modules.Modules
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.launch

private val QuestGreen = androidx.compose.ui.graphics.Color(0xFF58CC02)

internal fun questGoal(q: WeeklyQuests.Quest): String = trf(when(q.metric) {
    WeeklyQuests.Metric.STRENGTH -> if (q.target == 1) "%1\$s séance cette semaine" else "%1\$s séances cette semaine"
    WeeklyQuests.Metric.STRENGTH_REVIEW -> if (q.target == 1) "%1\$s séance avec un effort ressenti noté" else "%1\$s séances avec un effort ressenti noté"
    WeeklyQuests.Metric.MIND -> if (q.target == 1) "%1\$s jour avec une pause de 5 min" else "%1\$s jours avec une pause de 5 min"
    WeeklyQuests.Metric.FOOD -> if (q.target == 1) "%1\$s jour de journal alimentaire" else "%1\$s jours de journal alimentaire"
    WeeklyQuests.Metric.ACTIVE -> if (q.target == 1) "%1\$s jour avec un suivi" else "%1\$s jours avec un suivi"
    else -> if (q.target == 1) "%1\$s jour où votre objectif est atteint" else "%1\$s jours où votre objectif est atteint"
}, q.target)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyQuestsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val sh by SuperPrefs.settings.collectAsState()
    val modules by Modules.installed.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var focus by remember(state.questFocus) { mutableStateOf(state.questFocus.orEmpty()) }
    val today = LocalDate.now()
    val week = state.questWeeks.firstOrNull { it.start == Progression.week(today.toEpochDay()) }
    LaunchedEffect(Unit) { runCatching { ProgressRepository.compute(ctx) } }
    DetailScaffold(tr("Quêtes de la semaine"), { nav.popBackStack() }, contentSpacing = 12.dp, action = {
        IconButton({ focus = state.questFocus.orEmpty(); choosing = true }) { Icon(Icons.Rounded.Tune, tr("Choisir mes domaines")) }
    }) {
        if (state.questFocus == null || choosing) {
            Text(tr("Qu'avez-vous envie de travailler ?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tr("Choisissez les domaines qui peuvent devenir des défis. Vos habitudes récentes serviront à choisir une quête principale et, parfois, une secondaire différente."), color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 10.dp))
            if (error) Text(tr("Impossible d'enregistrer. Réessayez."), color = MaterialTheme.colorScheme.error)
            WeeklyQuests.Focus.entries.forEach { f ->
                val available = when (f) {
                    WeeklyQuests.Focus.NUTRITION -> sh.has(Feature.NUTRITION)
                    WeeklyQuests.Focus.STRENGTH -> sh.has(Feature.TRAINING)
                    WeeklyQuests.Focus.RUN -> sh.has(Feature.HEALTH)
                    WeeklyQuests.Focus.DAILY -> sh.has(Feature.HEALTH) || modules.any { it.enabled && it.kind in setOf(Modules.STRETCH, Modules.MEDITATION) }
                }
                MfCard(onClick = { focus = if (f in focus) focus - f else focus + f }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(f in focus, { focus = if (f in focus) focus - f else focus + f })
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(tr(f.label), style = MaterialTheme.typography.titleMedium)
                            Text(tr(when (f) { WeeklyQuests.Focus.NUTRITION -> "Journal et régularité"; WeeklyQuests.Focus.STRENGTH -> "Séances et progression"; WeeklyQuests.Focus.RUN -> "Sorties et régularité"; WeeklyQuests.Focus.DAILY -> "Pas et pauses" }), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                            if (!available) Text(tr("Suivi à configurer pour valider les quêtes"), style = MaterialTheme.typography.labelSmall, color = Mf.colors.accent)
                        }
                    }
                }
            }
            if (state.questFocus != null) Text(tr("Ces choix s'appliqueront aux prochaines quêtes. Les défis de la semaine en cours restent conservés."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            PillButton(tr(if (state.questFocus == null) "Générer mes quêtes" else "Enregistrer mes choix"), Modifier.fillMaxWidth().padding(top = 12.dp), enabled = focus.isNotEmpty() && !busy) {
                busy = true
                error = false
                scope.launch {
                    try {
                        League.configure(ctx) { it.copy(questFocus = focus) }
                        ProgressRepository.compute(ctx)
                        choosing = false
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }
            if (state.questFocus != null) TextButton({ choosing = false }, enabled = !busy) { Text(tr("Annuler")) }
            return@DetailScaffold
        }
        MfCard {
            Text(tr("Une semaine, quelques petits pas"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val start = LocalDate.ofEpochDay(Progression.week(today.toEpochDay()))
            val fmt = DateTimeFormatter.ofPattern("d MMM", app.superhealth.ui.Lang.locale)
            Text(start.format(fmt) + " – " + start.plusDays(6).format(fmt), color = Mf.colors.textSecondary)
            Text(tr("Des objectifs adaptés à vos suivis, avec de la place pour les jours de repos. Les quêtes se renouvellent le lundi, sans pénalité."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text(trf("%1\$s / %2\$s accomplies", week?.quests?.count { it.complete } ?: 0, week?.quests?.size ?: 0), color = Mf.colors.accent, modifier = Modifier.padding(top = 10.dp))
        }
        if (error) Text(tr("Impossible d'enregistrer. Réessayez."), color = MaterialTheme.colorScheme.error)
        if (week?.quests.isNullOrEmpty()) {
            Text(tr("Activez un suivi dans les domaines choisis pour générer des quêtes adaptées."), color = Mf.colors.textSecondary)
            SettingRow(tr("Configurer mes suivis")) { nav.go("settings") }
        }
        week?.quests?.forEach { q ->
            MfCard {
                FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(if (q.secondary) "Quête secondaire" else "Quête principale"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
                    if (!q.claimed) Text(trf("+%1\$s XP", q.reward), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (q.complete) Icons.Rounded.CheckCircle else Icons.Rounded.Flag, null, tint = if (q.complete) QuestGreen.onContainer() else Mf.colors.accent)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(tr(q.metric.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(questGoal(q), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                }
                Text("${q.progress} / ${q.target}", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(tr(q.reason.text), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                LinearProgressIndicator(progress = { (q.progress.toFloat() / q.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), color = if (q.complete) QuestGreen else Mf.colors.accent)
                when {
                    q.claimed -> Text(trf("+%1\$s XP récupérés", q.reward), color = QuestGreen.onContainer(), style = MaterialTheme.typography.labelLarge)
                    q.complete -> PillButton(trf("Récupérer +%1\$s XP", q.reward), Modifier.fillMaxWidth(), enabled = !busy) {
                        busy = true
                        error = false
                        scope.launch {
                            try { League.claimQuest(ctx, q.metric); ProgressRepository.compute(ctx) }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }
                    else -> PillButton(tr("Continuer mon suivi"), Modifier.fillMaxWidth(), strong = false, enabled = !busy) {
                        q.metric.track?.let { nav.continueTracking(it) } ?: nav.go(q.metric.route)
                    }
                }
            }
        }
        Text(tr("Une récompense par quête et par semaine. Les bonus ajoutent de l'XP, sans modifier votre score global."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 16.dp))
        SectionLabel(tr("Semaines précédentes"))
        val past = state.questWeeks.filter { it.start < Progression.week(today.toEpochDay()) }.sortedByDescending { it.start }.take(4)
        if (past.isEmpty()) Text(tr("Vos prochaines semaines viendront s'ajouter ici."), color = Mf.colors.textSecondary)
        past.forEach { w -> SettingRow(LocalDate.ofEpochDay(w.start).format(DateTimeFormatter.ofPattern("d MMM", app.superhealth.ui.Lang.locale)), subtitle = trf("%1\$s / %2\$s accomplies", w.quests.count { it.complete }, w.quests.size)) }
    }
}
