package app.superhealth.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import app.superhealth.plusActions
import app.superhealth.has
import app.superhealth.PlusAction
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.superhealth.bridge.Watch
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.ui.SearchMode
import app.superhealth.nutrition.ui.nutritionGraph
import app.superhealth.nutrition.ui.screens.FoodLogScreen
import app.superhealth.training.ui.ActiveSessionBar
import app.superhealth.training.ui.Picker
import app.superhealth.training.ui.handleTrainingRoute
import app.superhealth.training.ui.screens.WorkoutTabScreen
import app.superhealth.training.ui.trainingGraph
import app.superhealth.ui.dashboard.DashboardEditScreen
import app.superhealth.ui.dashboard.DashboardScreen
import app.superhealth.ui.health.ActivitiesHistoryScreen
import app.superhealth.ui.health.DevicesScreen
import app.superhealth.ui.health.LogSheet
import app.superhealth.ui.health.MetricScreen
import app.superhealth.ui.health.OtherActivities
import app.superhealth.ui.health.PermissionsScreen
import app.superhealth.ui.health.WorkoutMapScreen
import app.superhealth.ui.health.WorkoutScreen
import app.superhealth.ui.onboarding.OnboardingScreen
import app.superhealth.ui.settings.SettingsScreen
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import androidx.compose.foundation.layout.width

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * Trois onglets : tableau de bord, journal alimentaire, activités (musculation + cardio). Le « + » termine la barre, sous
 * le pouce ; les réglages, rarement ouverts, passent par l'avatar en haut de l'accueil.
 */
private val tabs = listOf(
    Tab("home", "Accueil", Icons.Rounded.GridView),
    Tab("log", "Journal", Icons.Rounded.RestaurantMenu),
    Tab("activities", "Activités", Icons.AutoMirrored.Rounded.DirectionsRun),
)

private fun isTabRoute(route: String?) = tabs.any { it.route == route }

/** Onglets des parties activées : Journal avec la nutrition, Activités avec la musculation ou les activités. */
private fun visibleTabs(s: app.superhealth.SuperSettings) = tabs.filter {
    when (it.route) {
        "log" -> s.has(app.superhealth.Feature.NUTRITION)
        "activities" -> s.has(app.superhealth.Feature.TRAINING) || s.has(app.superhealth.Feature.HEALTH)
        else -> true
    }
}

@Composable
fun SuperRoot(vm: SuperViewModel) {
    val sh by vm.settings.collectAsState()
    val tst by vm.training.state.collectAsState()
    val db by vm.training.db.collectAsState()
    if (!sh.onboarded) { OnboardingScreen(vm); return }
    if (db == null) { Box(Modifier.fillMaxSize().background(Mf.colors.background)); return }
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route
    val isTab = isTabRoute(current)
    var plus by remember { mutableStateOf(false) }
    var plusAction by remember { mutableStateOf<String?>(null) }
    var logSheet by remember { mutableStateOf(false) }

    // Fin de séance de musculation : écran « séance terminée ».
    val completed by vm.training.completed.collectAsState()
    LaunchedEffect(completed) { if (completed != null) nav.navigate("complete") { popUpTo("activities"); launchSingleTop = true } }
    // Une séance en cours : l'application s'ouvre directement dessus (une fois par processus).
    LaunchedEffect(Unit) {
        if (!vm.training.resumedOnLaunch) {
            vm.training.resumedOnLaunch = true
            if (tst.session != null && vm.pendingRoute.value == null) nav.navigate("session")
        }
    }
    val pending by vm.pendingRoute.collectAsState()
    LaunchedEffect(pending) {
        val r = pending ?: return@LaunchedEffect
        vm.pendingRoute.value = null
        // Widget ou notification : les feuilles ouvertes se ferment et la pile repart de l'accueil (jamais deux tableaux de bord
        // empilés : un second « home » faisait ensuite revenir les onglets sur l'accueil, cartes et onglets semblant ne plus répondre).
        plus = false; logSheet = false
        runCatching {
            when {
                r.startsWith("training:") -> { nav.popBackStack(nav.graph.findStartDestination().id, false); handleTrainingRoute(vm.training, nav, r.removePrefix("training:")) }
                isTabRoute(r) -> nav.openTab(r, fresh = true)
                r == current -> Unit
                else -> nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background)) {
        NavHost(
            nav, startDestination = "home", modifier = Modifier.weight(1f),
            // Axe partagé horizontal de Material : glissement court et fondu ; les onglets se croisent en fondu.
            enterTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) fadeIn(tween(160)) else slideInHorizontally(push) { it / 5 } + fadeIn(tween(220, 60)) },
            exitTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) fadeOut(tween(120)) else slideOutHorizontally(push) { -it / 5 } + fadeOut(tween(120)) },
            popEnterTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) fadeIn(tween(160)) else slideInHorizontally(push) { -it / 5 } + fadeIn(tween(220, 60)) },
            popExitTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) fadeOut(tween(120)) else slideOutHorizontally(push) { it / 5 } + fadeOut(tween(120)) },
        ) {
            composable("home") { DashboardScreen(vm, nav) }
            composable("log") { FoodLogScreen(vm.nutrition, nav) }
            composable("activities") {
                // onglet adapté aux parties choisies : musculation, activités de la montre, ou l'endurance d'abord
                val f by app.superhealth.SuperPrefs.settings.collectAsState()
                val endurance = vm.nutrition.program.collectAsState().value.training == app.superhealth.nutrition.domain.Training.CARDIO
                WorkoutTabScreen(vm.training, nav, strength = f.has(app.superhealth.Feature.TRAINING),
                    cardioFirst = endurance && f.has(app.superhealth.Feature.HEALTH)) {
                    if (f.has(app.superhealth.Feature.HEALTH)) OtherActivities(vm, nav)
                }
            }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("dash/edit") { DashboardEditScreen(vm, nav) }
            composable("backup") { app.superhealth.ui.settings.BackupScreen(vm, nav) }
            composable("update") { app.superhealth.ui.settings.UpdateScreen(vm, nav) }
            composable("settings/look") { app.superhealth.ui.settings.LookSettingsScreen(vm, nav) }
            composable("settings/style") { app.superhealth.ui.settings.StyleLabScreen(vm, nav) }
            composable("settings/units") { app.superhealth.ui.settings.UnitsSettingsScreen(vm, nav) }
            composable("settings/hc") { app.superhealth.ui.settings.HealthConnectSettingsScreen(vm, nav) }
            composable("modules") { app.superhealth.ui.settings.ModulesScreen(vm, nav) }
            composable("coach") { app.superhealth.ui.coach.CoachScreen(vm, nav) }
            composable("meds") { app.superhealth.ui.meds.MedsScreen(nav) }
            composable("vitals/edit") { app.superhealth.ui.dashboard.VitalsEditScreen(vm, nav) }
            composable("rings/edit") { app.superhealth.ui.dashboard.RingsEditScreen(vm, nav) }
            composable("plus/edit") { app.superhealth.ui.dashboard.PlusEditScreen(vm, nav) }
            composable("sim/nutrition") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.NUTRITION_SIM) { nav.popBackStack() } }
            composable("sim/training") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.TRAINING_SIM) { nav.popBackStack() } }
            composable("h/metric/{metric}", listOf(navArgument("metric") { type = NavType.StringType })) {
                val m = runCatching { Metric.valueOf(it.arguments!!.getString("metric")!!) }.getOrNull() ?: Metric.STEPS
                MetricScreen(vm, nav, m)
            }
            composable("h/devices") { DevicesScreen(vm, nav) }
            composable("h/activities") { ActivitiesHistoryScreen(vm, nav) }
            composable("h/permissions") { PermissionsScreen(vm, nav) }
            composable("h/performance") { app.superhealth.ui.health.PerformanceScreen(vm, nav) }
            composable("h/workout/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("h/workout/{id}/map", listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutMapScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            nutritionGraph(vm.nutrition, nav)
            trainingGraph(vm.training, nav)
        }
        if (isTab) Box(Modifier.fillMaxWidth().height(0.dp)) {
            // fondu au-dessus de la barre flottante : le contenu s'efface au lieu d'être coupé net
            Box(Modifier.fillMaxWidth().height(22.dp).offset(y = (-22).dp)
                .background(Brush.verticalGradient(listOf(Mf.colors.background.copy(alpha = 0f), Mf.colors.background))))
        }
        if (isTab && tst.session != null) ActiveSessionBar(tst.session!!.name, tst.session!!.startTime) { nav.navigate("session") }
        if (isTab) BottomBar(current, onTab = { route -> nav.openTab(route) }, onPlus = {
            // Un seul raccourci choisi : le « + » l'ouvre directement.
            val single = sh.plusActions.mapNotNull { it.resolve(vm) }.singleOrNull()
            if (single != null) plusAction = single.action else plus = true
        })
    }

    fun runAction(action: String) {
        when (action) {
            "water" -> {
                vm.log(Metric.HYDRATION, 250.0)
                android.widget.Toast.makeText(nav.context, tr("250 ml d'eau ajoutés"), android.widget.Toast.LENGTH_SHORT).show()
            }
            "log_health" -> logSheet = true
            "sync" -> vm.syncNow(nav.context)
            "next" -> vm.training.activeProgram()?.let { p -> vm.training.startProgramDay(p, p.dayIndex, p.cycleIndex); nav.navigate("session") }
            "empty" -> { vm.training.startEmpty(); nav.navigate("session") }
            else -> nav.go(action)
        }
    }
    LaunchedEffect(plusAction) { plusAction?.let { plusAction = null; runAction(it) } }
    if (plus) PlusSheet(vm, onDismiss = { plus = false }, onEdit = { plus = false; nav.go("plus/edit") }) { action ->
        plus = false
        runAction(action)
    }
    if (logSheet) LogSheet(vm, onDismiss = { logSheet = false })
}

/** Courbe « emphasized decelerate » de Material. */
private val push = tween<IntOffset>(320, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

/**
 * Barre de navigation flottante : quatre onglets (pastille teintée derrière l'icône active, comme Material 3) et, à côté,
 * le « + » en bouton d'action carré arrondi.
 */
@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Mf.colors.background).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val shown = visibleTabs(app.superhealth.SuperPrefs.settings.collectAsState().value)
        // moins d'onglets (parties coupées) : barre plus courte, gardée près du « + » sous le pouce
        if (shown.size < tabs.size) Spacer(Modifier.weight(1f))
        Row((if (shown.size < tabs.size) Modifier.width((100 * shown.size + 8).dp) else Modifier.weight(1f)).height(68.dp)
            .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(26.dp)).background(if (Mf.colors.dark) Mf.colors.elevated else Mf.colors.card).padding(horizontal = 4.dp)) {
            shown.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(64.dp).shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(RoundedCornerShape(20.dp)).background(Mf.colors.strong).clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, tr("Ajouter"), tint = Mf.colors.onStrong, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = Mf.colors.accent
    val pill by animateDpAsState(if (selected) 56.dp else 24.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "pill")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(pill).height(32.dp).clip(CircleShape).background(if (selected) accent.container(1.4f) else Color.Transparent),
            contentAlignment = Alignment.Center) {
            Icon(tab.icon, tr(tab.label), tint = if (selected) accent.onContainer() else Mf.colors.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(tr(tab.label), fontSize = 11.sp, color = if (selected) Mf.colors.text else Mf.colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class QuickAction(val label: String, val icon: ImageVector, val action: String)

/** Raccourci du « + » tel qu'affiché : libellé, icône et action ; null quand il n'a pas de sens maintenant. */
private fun PlusAction.resolve(vm: SuperViewModel): QuickAction? {
    val ns = vm.nutrition.settings.value
    val st = vm.training.state.value
    val sh = vm.settings.value
    return when (this) {
        PlusAction.SEARCH -> QuickAction("Rechercher", Icons.Rounded.Search, "search/${SearchMode.SEARCH}/-1")
        PlusAction.SCAN -> QuickAction("Code-barres", Icons.Rounded.QrCodeScanner, "search/${SearchMode.SCAN}/-1")
        PlusAction.QUICK -> QuickAction("Ajout rapide", Icons.Rounded.Bolt, "search/${SearchMode.QUICK}/-1")
        PlusAction.AI -> if (ns.aiEnabled) QuickAction("Décrire (IA)", Icons.Rounded.AutoAwesome, "ai") else null
        PlusAction.COACH -> if (app.superhealth.ui.coach.coachAvailable()) QuickAction("Demander au coach", Icons.Rounded.AutoAwesome, "coach") else null
        PlusAction.RECIPES -> QuickAction("Recettes", Icons.AutoMirrored.Rounded.MenuBook, "search/${SearchMode.RECIPES}/-1")
        PlusAction.CUSTOM_FOOD -> QuickAction("Aliment perso", Icons.Rounded.EditNote, "search/${SearchMode.CREATE}/-1")
        PlusAction.WORKOUT -> {
            val next = vm.training.activeProgram()?.let { p -> p.days.getOrNull(p.dayIndex)?.takeIf { !it.isRest }?.name }
            when {
                st.session != null -> QuickAction("Reprendre", Icons.Rounded.PlayArrow, "session")
                next != null -> QuickAction(next, Icons.Rounded.PlayArrow, "next")
                else -> null
            }
        }
        PlusAction.EMPTY -> QuickAction("Séance vide", Icons.Rounded.Bolt, "empty")
        PlusAction.EXERCISES -> QuickAction("Exercices", Icons.AutoMirrored.Rounded.PlaylistAdd, "picker/${Picker.BROWSE}")
        PlusAction.PROGRAM_NEW -> QuickAction("Nouveau programme", Icons.Rounded.AutoAwesome, "wizard")
        PlusAction.WEIGHT -> QuickAction("Pesée", Icons.Rounded.MonitorWeight, "weight")
        PlusAction.WATER -> QuickAction("Verre d'eau (250 ml)", Icons.Rounded.LocalDrink, "water")
        PlusAction.VITALS -> QuickAction("Hydratation et constantes", Icons.Rounded.WaterDrop, "log_health")
        PlusAction.BODY -> QuickAction("Mesures", Icons.Rounded.Straighten, "body")
        PlusAction.SYNC -> if (sh.watchModule && vm.devices.value.isNotEmpty() || sh.healthConnect) QuickAction("Synchroniser", Icons.Rounded.Sync, "sync") else null
    }
}

/** « + » : les raccourcis choisis (Réglages du « + »), regroupés par module, dans l'ordre choisi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlusSheet(vm: SuperViewModel, onDismiss: () -> Unit, onEdit: () -> Unit, onAction: (String) -> Unit) {
    val sh by vm.settings.collectAsState()
    vm.nutrition.settings.collectAsState()
    vm.training.state.collectAsState()
    val chosen = sh.plusActions
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mf.colors.background, dragHandle = null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Ajouter"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.Tune, tr("Personnaliser"), onClick = onEdit)
                    app.superhealth.nutrition.ui.components.CircleAction(Icons.Rounded.Close, tr("Fermer"), onClick = onDismiss)
                }
            }
            app.superhealth.DashCard.Section.entries.forEach { section ->
                val actions = chosen.filter { it.section == section }.mapNotNull { it.resolve(vm) }
                if (actions.isNotEmpty()) ActionGroup(tr(when (section) {
                    app.superhealth.DashCard.Section.NUTRITION -> "Nutrition"
                    app.superhealth.DashCard.Section.TRAINING -> "Entraînement"
                    app.superhealth.DashCard.Section.HEALTH -> "Santé"
                }), when (section) {
                    app.superhealth.DashCard.Section.NUTRITION -> app.superhealth.ui.theme.Base.Green
                    app.superhealth.DashCard.Section.TRAINING -> app.superhealth.ui.theme.WorkoutBlue
                    app.superhealth.DashCard.Section.HEALTH -> app.superhealth.ui.theme.HealthHues.Heart
                }, actions, onAction)
            }
            if (chosen.isEmpty()) Text(tr("Aucun raccourci : touchez l'icône de réglage pour en choisir."), style = MaterialTheme.typography.bodyMedium,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionGroup(title: String, hue: Color, actions: List<QuickAction>, onAction: (String) -> Unit) {
    app.superhealth.nutrition.ui.components.GroupHeader(title, Modifier.padding(top = 4.dp))
    actions.chunked(3).forEachIndexed { i, row ->
        if (i > 0) Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { a -> ActionTile(a, hue, Modifier.weight(1f)) { onAction(a.action) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Raccourci : symbole à la couleur du module, libellé dessous. */
@Composable
private fun ActionTile(a: QuickAction, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(108.dp).clip(RoundedCornerShape(20.dp)).background(Mf.colors.card).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(hue.container(1.3f)), contentAlignment = Alignment.Center) {
            Icon(a.icon, null, Modifier.size(22.dp), tint = hue.onContainer())
        }
        Spacer(Modifier.height(9.dp))
        Text(tr(a.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp)
    }
}

/** Ouvre un écran depuis n'importe où (cartes du tableau de bord, réglages…) ; un onglet passe par [openTab]. */
fun NavHostController.go(route: String) = runCatching { if (isTabRoute(route)) openTab(route) else navigate(route) }

/**
 * Onglet de la barre du bas : un seul exemplaire de chaque onglet dans la pile. [fresh] (widget, notification) : l'onglet
 * s'ouvre à sa racine au lieu de retrouver l'écran où on l'avait laissé.
 */
fun NavHostController.openTab(route: String, fresh: Boolean = false) {
    val start = graph.findStartDestination().id
    navigate(route) {
        popUpTo(start) { saveState = !fresh }
        launchSingleTop = true
        restoreState = !fresh
    }
}
