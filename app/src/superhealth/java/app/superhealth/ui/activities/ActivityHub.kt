package app.superhealth.ui.activities

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.SuperSettings
import app.superhealth.has
import app.superhealth.modules.Modules
import app.superhealth.nutrition.ui.components.CircleAction
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingsAvatar
import app.superhealth.nutrition.ui.components.TabHeader
import app.superhealth.training.ui.screens.WorkoutTabScreen
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.go
import app.superhealth.ui.health.ActivityWeek
import app.superhealth.ui.health.RecentActivities
import app.superhealth.ui.longToday
import app.superhealth.ui.meditation.MeditationSection
import app.superhealth.ui.stretch.StretchSection
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Sections de l'onglet Activités. Musculation et course sont dans l'app ; méditation et étirements viennent de leur plugin. */
enum class ActivitySection(val label: String, val summary: String, val plugin: String? = null, val feature: Feature? = null) {
    STRENGTH("Musculation", "Programmes et séances guidées", feature = Feature.TRAINING),
    RUN("Course", "Sorties de la montre, allure, performances", feature = Feature.HEALTH),
    MEDITATION("Méditation", "Minuteur, séances guidées, respiration", Modules.MEDITATION),
    STRETCH("Étirements", "Routines en vidéo selon vos zones", Modules.STRETCH);

    val icon: ImageVector get() = when (this) {
        STRENGTH -> Icons.Rounded.FitnessCenter
        RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
        MEDITATION -> Icons.Rounded.SelfImprovement
        STRETCH -> Icons.Rounded.AccessibilityNew
    }
    val hue: Color get() = when (this) {
        STRENGTH -> WorkoutBlue
        RUN -> HealthHues.Activity
        MEDITATION -> app.superhealth.ui.meditation.MeditationHue
        STRETCH -> app.superhealth.ui.stretch.StretchHue
    }
}

/** Sections affichées ; avant le premier choix, ce que les parties activées donnent déjà. */
fun SuperSettings.sections(): List<ActivitySection> =
    if (activitySections.isNotEmpty()) ActivitySection.entries.filter { it.name in activitySections }
    else listOfNotNull(ActivitySection.STRENGTH.takeIf { has(Feature.TRAINING) }, ActivitySection.RUN.takeIf { has(Feature.HEALTH) })

private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Enregistre le choix : active la partie de chaque section et installe les plugins qui manquent, en tâche de fond. */
fun applyActivities(ctx: Context, chosen: Set<ActivitySection>) {
    SuperPrefs.update { s ->
        s.copy(activitySections = chosen.map { it.name }, activityAsked = true, features = s.features + chosen.mapNotNull { it.feature?.name })
    }
    val app = ctx.applicationContext
    chosen.mapNotNull { it.plugin }.forEach { kind -> hubScope.launch { Modules.ensure(app, kind) } }
}

/** Le choix des activités : même présentation à l'ouverture de l'onglet et dans l'accueil. */
@Composable
fun ActivityChoices(chosen: Set<ActivitySection>, onChange: (Set<ActivitySection>) -> Unit) {
    ActivitySection.entries.forEach { a ->
        val on = a in chosen
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
            .background(if (on) a.hue.container() else Mf.colors.card)
            .clickable { onChange(if (on) chosen - a else chosen + a) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, tint = if (on) a.hue.onContainer() else Mf.colors.textSecondary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(a.label), style = MaterialTheme.typography.titleMedium)
                Text(tr(a.summary), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            }
            Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) a.hue.onContainer() else Mf.colors.textTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooserSheet(initial: Set<ActivitySection>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = { applyActivities(ctx, chosen); onDismiss() }, containerColor = Mf.colors.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Que voulez-vous faire ?"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("Une section par activité. Les plugins nécessaires s'installent tout seuls."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            ActivityChoices(chosen) { chosen = it }
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Valider"), Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty()) { applyActivities(ctx, chosen); onDismiss() }
        }
    }
}

/** Bulles flottantes, du côté du bouton + : une par section, puis celle qui en ajoute. */
@Composable
private fun Bubbles(list: List<ActivitySection>, current: ActivitySection?, modifier: Modifier, onSelect: (ActivitySection) -> Unit, onManage: () -> Unit) {
    // fond surélevé et filet : les bulles se détachent de ce qui défile dessous
    val border = Mf.colors.line
    val bg = if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a ->
            val on = a == current
            Box(Modifier.size(46.dp).shadow(if (Mf.colors.dark) 0.dp else 6.dp, CircleShape).clip(CircleShape).background(if (on) a.hue else bg)
                .border(1.dp, if (on) a.hue else border, CircleShape).clickable(onClickLabel = tr(a.label)) { onSelect(a) }, contentAlignment = Alignment.Center) {
                Icon(a.icon, tr(a.label), Modifier.size(23.dp), tint = if (on) Color.White else a.hue)
            }
        }
        Box(Modifier.size(46.dp).shadow(if (Mf.colors.dark) 0.dp else 6.dp, CircleShape).clip(CircleShape).background(bg).border(1.dp, border, CircleShape).clickable(onClickLabel = tr("Choisir mes activités"), onClick = onManage),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, tr("Choisir mes activités"), Modifier.size(21.dp), tint = Mf.colors.textSecondary)
        }
    }
}

/** Plugin d'une section absent : installation en cours, ou nouvel essai. */
@Composable
private fun PluginGate(a: ActivitySection, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val kind = a.plugin ?: return content()
    val installed by Modules.installed.collectAsState()
    val jobs by Modules.jobs.collectAsState()
    if (installed.any { it.kind == kind && it.enabled }) return content()
    val busy = jobs.values.any { it is Modules.Job.Downloading || it is Modules.Job.Installing }
    val failed = jobs.values.filterIsInstance<Modules.Job.Failed>().firstOrNull()
    LaunchedEffect(kind) { if (!busy) hubScope.launch { Modules.ensure(ctx.applicationContext, kind) } }
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = a.hue, strokeWidth = 2.5.dp)
            else Icon(a.icon, null, tint = a.hue)
            Spacer(Modifier.width(12.dp))
            Text(if (busy) tr("Installation du plugin…") else failed?.message ?: tr("Plugin non installé"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (!busy) {
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Réessayer"), Modifier.fillMaxWidth(), strong = false) { hubScope.launch { Modules.ensure(ctx.applicationContext, kind) } }
        }
    }
}

/**
 * Onglet Activités : une section à la fois, choisie par les bulles flottantes (à droite ou à gauche, du côté du bouton +).
 * À la première ouverture (ou après la mise à jour qui l'apporte), on demande ce que la personne veut faire.
 */
@Composable
fun ActivitiesTab(vm: SuperViewModel, nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    val list = sh.sections()
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(sh.activityAsked) { if (!sh.activityAsked) ask = true }
    val current = list.firstOrNull { it.name == sh.activityCurrent } ?: list.firstOrNull()
    val strength = sh.has(Feature.TRAINING); val health = sh.has(Feature.HEALTH)
    // sans section Course, les sorties de la montre restent visibles dans les activités récentes
    val mixed = health && ActivitySection.RUN !in list

    Box(Modifier.fillMaxSize()) {
        when (current) {
            ActivitySection.STRENGTH -> WorkoutTabScreen(vm.training, nav, strength = strength, cardioFirst = false,
                week = { ActivityWeek(vm, nav, strength, mixed) }, recent = { RecentActivities(vm, nav, strength, mixed) },
                historyRoute = if (health) "h/activities" else "history", title = tr("Musculation"))
            ActivitySection.RUN -> Frame(tr("Course"), nav, "h/activities") {
                ActivityWeek(vm, nav, false, true)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    PillButton(tr("Performances"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Speed) { nav.go("h/performance") }
                    if (coachAvailable()) PillButton(tr("Coach"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.AutoAwesome) {
                        nav.askCoach(tr("Analyse mes dernières sorties de course et ma charge : que faire cette semaine ?"))
                    }
                }
                RecentActivities(vm, nav, false, true)
            }
            ActivitySection.MEDITATION -> Frame(tr("Méditation"), nav, null) { PluginGate(current) { MeditationSection(nav) } }
            ActivitySection.STRETCH -> Frame(tr("Étirements"), nav, null) { PluginGate(current) { StretchSection(nav) } }
            null -> Frame(tr("Activités"), nav, null) {
                MfCard(onClick = { ask = true }) { Text(tr("Choisissez vos activités"), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Bubbles(list, current, Modifier.align(if (sh.plusSide == "LEFT") Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = 14.dp, vertical = 10.dp),
            onSelect = { a -> SuperPrefs.update { it.copy(activityCurrent = a.name) } }, onManage = { ask = true })
    }
    if (ask) ChooserSheet(list.toSet()) { ask = false }
}

@Composable
private fun Frame(title: String, nav: NavHostController, historyRoute: String?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        TabHeader(longToday(), title) {
            historyRoute?.let { r -> CircleAction(Icons.Rounded.History, tr("Historique")) { nav.navigate(r) } }
            SettingsAvatar { nav.go("settings") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            content()
            // place laissée aux bulles flottantes en bas de page
            Spacer(Modifier.height(96.dp))
        }
    }
}
