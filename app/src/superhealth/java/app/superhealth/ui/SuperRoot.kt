package app.superhealth.ui

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
import androidx.navigation.compose.composable
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
 * Quatre onglets de part et d'autre du « + » (comme MacroFactor) :
 * tableau de bord, journal alimentaire | activités (musculation + cardio), réglages.
 */
private val tabs = listOf(
    Tab("home", "Accueil", Icons.Rounded.GridView),
    Tab("log", "Journal", Icons.Rounded.RestaurantMenu),
    Tab("activities", "Activités", Icons.AutoMirrored.Rounded.DirectionsRun),
    Tab("settings", "Réglages", Icons.Rounded.Settings),
)

private fun isTabRoute(route: String?) = tabs.any { it.route == route }

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
        runCatching {
            when {
                r.startsWith("training:") -> handleTrainingRoute(vm.training, nav, r.removePrefix("training:"))
                r == "log" -> nav.navigate("log") { popUpTo("home"); launchSingleTop = true }
                else -> nav.navigate(r)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Mf.colors.background)) {
        NavHost(
            nav, startDestination = "home", modifier = Modifier.weight(1f),
            enterTransition = { if (isTabRoute(targetState.destination.route)) fadeIn() else slideInHorizontally { it / 3 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { if (isTabRoute(initialState.destination.route)) fadeOut() else slideOutHorizontally { it / 3 } + fadeOut() },
        ) {
            composable("home") { DashboardScreen(vm, nav) }
            composable("log") { FoodLogScreen(vm.nutrition, nav) }
            composable("activities") { WorkoutTabScreen(vm.training, nav) { OtherActivities(vm, nav) } }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("dash/edit") { DashboardEditScreen(vm, nav) }
            composable("plus/edit") { app.superhealth.ui.dashboard.PlusEditScreen(vm, nav) }
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
        if (isTab && tst.session != null) ActiveSessionBar(tst.session!!.name, tst.session!!.startTime) { nav.navigate("session") }
        if (isTab) BottomBar(current, onTab = { route ->
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }, onPlus = {
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

@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Mf.colors.card).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Mf.colors.divider))
        Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.take(2).forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Mf.colors.strong).clickable(onClick = onPlus),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, tr("Ajouter"), tint = Mf.colors.onStrong, modifier = Modifier.size(28.dp)) }
            }
            tabs.drop(2).forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) Mf.colors.text else Mf.colors.textSecondary
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Onglet actif : pastille teintée de la couleur d'accent derrière l'icône (comme Google Health).
        val accent = Mf.colors.accent
        Box(Modifier.width(56.dp).height(30.dp).clip(CircleShape).background(if (selected) accent.container() else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center) {
            Icon(tab.icon, tr(tab.label), tint = if (selected) accent.onContainer() else color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(tr(tab.label), fontSize = 10.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text(tr("Ajouter"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box(Modifier.padding(end = 8.dp).size(36.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onEdit), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, tr("Personnaliser"), Modifier.size(20.dp))
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Mf.colors.chip).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, tr("Fermer"), Modifier.size(20.dp))
                }
            }
            app.superhealth.DashCard.Section.entries.forEach { section ->
                val actions = chosen.filter { it.section == section }.mapNotNull { it.resolve(vm) }
                if (actions.isNotEmpty()) ActionGroup(tr(when (section) {
                    app.superhealth.DashCard.Section.NUTRITION -> "Nutrition"
                    app.superhealth.DashCard.Section.TRAINING -> "Entraînement"
                    app.superhealth.DashCard.Section.HEALTH -> "Santé"
                }), actions, onAction)
            }
            if (chosen.isEmpty()) Text(tr("Aucun raccourci : touchez l'icône de réglage pour en choisir."), style = MaterialTheme.typography.bodyMedium,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionGroup(title: String, actions: List<QuickAction>, onAction: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
    actions.chunked(3).forEachIndexed { i, row ->
        if (i > 0) Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { a -> ActionTile(a, Modifier.weight(1f)) { onAction(a.action) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ActionTile(a: QuickAction, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(104.dp).clip(RoundedCornerShape(16.dp)).background(Mf.colors.card).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(a.icon, null, Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(tr(a.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp)
    }
}

/** Ouvre un écran depuis n'importe où (cartes du tableau de bord, réglages…). */
fun NavHostController.go(route: String) = runCatching { navigate(route) }
