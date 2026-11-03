package app.superhealth.ui.activities

import app.superhealth.platform.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.superhealth.ui.theme.edge
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

/** Sections affichées ; avant le premier choix, ce que les parties activées donnent déjà. Sans son plugin installé et activé, une section n'apparaît pas. */
fun SuperSettings.sections(): List<ActivitySection> =
    (if (activityAsked || activitySections.isNotEmpty()) activitySections.mapNotNull { n -> ActivitySection.entries.firstOrNull { it.name == n } }.distinct()
    else listOfNotNull(ActivitySection.STRENGTH.takeIf { has(Feature.TRAINING) }, ActivitySection.RUN.takeIf { has(Feature.HEALTH) }))
        .filter { (it.feature == null || has(it.feature)) && (it.plugin == null || Modules.isActive(it.plugin)) }

private val hubScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Enregistre le choix : active la partie de chaque section et installe les plugins qui manquent, en tâche de fond. */
fun applyActivities(ctx: Context, chosen: Set<ActivitySection>) {
    SuperPrefs.update { s ->
        s.copy(activitySections = chosen.map { it.name }, activityAsked = true, features = s.features + chosen.mapNotNull { it.feature?.name })
    }
    val context = ctx.applicationContext
    chosen.mapNotNull { it.plugin }.forEach { kind ->
        hubScope.launch { if (!Modules.ensure(context, kind)) app.superhealth.platform.Toast.makeText(context, tr("Plugin non installé"), app.superhealth.platform.Toast.LENGTH_LONG).show() }
    }
}

/** Le choix des activités : même présentation à l'ouverture de l'onglet et dans l'accueil. */
@Composable
fun ActivityChoices(chosen: Set<ActivitySection>, includePlugins: Boolean = true, onChange: (Set<ActivitySection>) -> Unit) {
    ActivitySection.entries.filter { includePlugins || it.plugin == null }.forEach { a ->
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
            Text(tr("Une section par activité. Les plugins nécessaires s'installent tout seuls, et vous pourrez toujours les télécharger dans Réglages > Plugins."), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            ActivityChoices(chosen) { chosen = it }
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Valider"), Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty()) { applyActivities(ctx, chosen); onDismiss() }
        }
    }
}

/** Ouvertes une seule fois par lancement : ensuite, seule la bulle du coin reste. */
private var bubblesShown = false

/**
 * Bulles flottantes, du côté du bouton + : la section en cours dans le coin, les autres au-dessus d'elle. Elles se rétractent après
 * quelques secondes pour laisser voir la page ; toucher la bulle du coin les rouvre.
 */
@Composable
private fun Bubbles(list: List<ActivitySection>, current: ActivitySection?, start: Boolean, modifier: Modifier, onSelect: (ActivitySection) -> Unit) {
    if (list.size < 2 || current == null) return
    // fond surélevé et filet : les bulles se détachent de ce qui défile dessous
    val border = Mf.colors.edge
    val bg = if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card
    var open by remember { mutableStateOf(!bubblesShown) }
    val dark = Mf.colors.dark
    LaunchedEffect(Unit) { bubblesShown = true }
    LaunchedEffect(open, current) { if (open) { delay(4000); open = false } }
    // le libellé sort de la bulle (et y rentre) au lieu d'apparaître d'un coup
    val labelFrom = if (start) Alignment.Start else Alignment.End
    @Composable
    fun Bubble(a: ActivitySection, animateLabel: Boolean) {
        val on = a == current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (start) BubbleIcon(a, on, bg, border) { if (on) open = !open else { onSelect(a); open = false } }
            AnimatedVisibility(visible = if (animateLabel) open else true,
                enter = expandHorizontally(expandFrom = labelFrom, animationSpec = tween(200)) + fadeIn(tween(160)),
                exit = shrinkHorizontally(shrinkTowards = labelFrom, animationSpec = tween(150)) + fadeOut(tween(120))) {
                Text(tr(a.label), style = MaterialTheme.typography.labelLarge, maxLines = 1, color = Mf.colors.text,
                    modifier = Modifier.clip(CircleShape).background(bg).border(1.dp, border, CircleShape)
                        .clickable { onSelect(a); open = false }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            if (!start) BubbleIcon(a, on, bg, border) { if (on) open = !open else { onSelect(a); open = false } }
        }
    }
    // chaque bulle monte du bouton + vers le haut, l'une après l'autre (les plus proches d'abord)
    val others = list.filter { it != current }
    Column(modifier, horizontalAlignment = if (start) Alignment.Start else Alignment.End) {
        others.forEachIndexed { i, a ->
            val step = (others.lastIndex - i) * 45
            AnimatedVisibility(open,
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(240, delayMillis = step)) +
                    fadeIn(tween(180, delayMillis = step)) +
                    slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(280, delayMillis = step)),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(160)) + fadeOut(tween(110))) {
                Column(horizontalAlignment = if (start) Alignment.Start else Alignment.End) {
                    Bubble(a, animateLabel = false)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Bubble(current, animateLabel = true)
    }
}

/**
 * Ombre douce en dégradé radial, dessinée derrière une bulle. `Modifier.shadow` ressortait en carré gris pendant
 * l'ouverture des bulles en mode clair (l'ombre suit la forme avec une trame de retard) ; un dégradé reste rond.
 */
private fun Modifier.bubbleShadow(dark: Boolean): Modifier = if (dark) this else drawBehind {
    val center = this.center
    val halo = size.minDimension / 2 + 9.dp.toPx()
    drawCircle(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0f)),
            center = center, radius = halo),
        radius = halo, center = center,
    )
}

@Composable
private fun BubbleIcon(a: ActivitySection, on: Boolean, bg: Color, border: Color, onClick: () -> Unit) {
    Box(Modifier.size(58.dp).bubbleShadow(Mf.colors.dark).clip(CircleShape).background(if (on) a.hue else bg)
        .border(1.dp, if (on) a.hue else border, CircleShape).clickable(onClickLabel = tr(a.label), onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(a.icon, tr(a.label), Modifier.size(28.dp), tint = if (on) Color.White else a.hue)
    }
}

/**
 * Onglet Activités : une section à la fois, choisie par les bulles flottantes (à droite ou à gauche, du côté du bouton +). Le choix des sections reste dans Réglages.
 * À la première ouverture (ou après la mise à jour qui l'apporte), on demande ce que la personne veut faire.
 */
@Composable
fun ActivitiesTab(vm: SuperViewModel, nav: NavHostController) {
    val sh by SuperPrefs.settings.collectAsState()
    // relu à chaque changement de plugin : désactivé ou désinstallé, sa section disparaît aussitôt
    val modules = Modules.installed.collectAsState().value
    val list = remember(sh, modules) { sh.sections() }
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
            ActivitySection.MEDITATION -> Frame(tr("Méditation"), nav, null) { MeditationSection(nav) }
            ActivitySection.STRETCH -> Frame(tr("Étirements"), nav, null) { StretchSection(nav) }
            null -> Frame(tr("Activités"), nav, null) {
                MfCard(onClick = { ask = true }) { Text(tr("Choisissez vos activités"), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Bubbles(list, current, sh.plusSide == "LEFT", Modifier.align(if (sh.plusSide == "LEFT") Alignment.BottomStart else Alignment.BottomEnd)
            .padding(start = 14.dp, end = 14.dp, bottom = app.superhealth.ui.LocalTabBarInset.current + 10.dp),
            onSelect = { a -> SuperPrefs.update { it.copy(activityCurrent = a.name) } })
    }
    if (ask) ChooserSheet(list.toSet()) { ask = false }
}

@Composable
private fun Frame(title: String, nav: NavHostController, historyRoute: String?, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        TabHeader(longToday(), title) {
            historyRoute?.let { r -> CircleAction(Icons.Rounded.History, tr("Historique")) { nav.navigate(r) } }
            CircleAction(androidx.compose.material.icons.Icons.Rounded.Tune, tr("Activités affichées")) { nav.go("settings/activities") }
            SettingsAvatar { nav.go("settings") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            content()
            // place laissée à la barre et à la bulle flottantes
            Spacer(Modifier.height(app.superhealth.ui.LocalTabBarInset.current + 72.dp))
        }
    }
}
