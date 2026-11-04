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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import app.superhealth.ui.activities.sections
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
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Air
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
import app.superhealth.ui.health.ActivityWeek
import app.superhealth.ui.health.RecentActivities
import app.superhealth.ui.health.ActivitiesHistoryScreen
import app.superhealth.ui.health.DevicesScreen
import app.superhealth.ui.health.LogSheet
import app.superhealth.ui.health.MetricScreen
import app.superhealth.ui.health.PermissionsScreen
import app.superhealth.ui.health.WorkoutMapScreen
import app.superhealth.ui.health.WorkoutScreen
import app.superhealth.ui.onboarding.OnboardingScreen
import app.superhealth.ui.settings.SettingsScreen
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.edge
import app.superhealth.ui.theme.onContainer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.SelfImprovement

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

/** Réglages en onglet : « + » au milieu avec un nombre impair d'onglets, pour une barre symétrique. */
private val settingsTab = Tab("settings", "Réglages", Icons.Rounded.Settings)

fun settingsIsTab(s: app.superhealth.SuperSettings) = s.plusSide == "CENTER" && visibleTabs(s).size % 2 == 1

private fun isTabRoute(route: String?) = tabs.any { it.route == route } || (route == "settings" && settingsIsTab(app.superhealth.SuperPrefs.settings.value))

/** L'onglet Activités suit les sections réellement sélectionnées et disponibles. */
private fun visibleTabs(s: app.superhealth.SuperSettings) = tabs.filter {
    when (it.route) {
        "log" -> s.has(app.superhealth.Feature.NUTRITION)
        "activities" -> s.sections().isNotEmpty()
        else -> true
    }
}

@Composable
fun SuperRoot(vm: SuperViewModel) {
    // lectures faites à l'avance, juste après le démarrage : silhouettes et activités récentes de l'onglet Activités
    val preloadCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { app.superhealth.training.ui.components.preloadBodies(vm.training.state.value.settings.sex) }
            val f = app.superhealth.SuperPrefs.settings.value
            runCatching { app.superhealth.ui.health.preloadRecent(preloadCtx, f.has(app.superhealth.Feature.TRAINING), f.has(app.superhealth.Feature.HEALTH)) }
        }
    }
    val sh by vm.settings.collectAsState()
    val tst by vm.training.state.collectAsState()
    val db by vm.training.db.collectAsState()
    if (!sh.onboarded) { OnboardingScreen(vm); return }
    if (db == null) { Box(Modifier.fillMaxSize().background(Mf.colors.background)); return }
    val nav = rememberNavController()
    // Keep the selected home panel across child destinations; only an explicit
    // Home action requests the dashboard. No old navigation event is replayed.
    val homePager = androidx.compose.foundation.pager.rememberPagerState(initialPage = 1) { 2 }
    val back by nav.currentBackStackEntryAsState()
    val current = back?.destination?.route
    val isTab = isTabRoute(current) || current == "feed"
    val groupVisible = sh.socialFeedEnabled && sh.leagueEnabled && homePager.settledPage == 0
    val groupContext = current == "feed" || (current == "home" && groupVisible)
    var plus by remember { mutableStateOf(false) }
    var plusAction by remember { mutableStateOf<String?>(null) }
    var logSheet by remember { mutableStateOf(false) }

    // Fin de séance de musculation : écran « séance terminée ».
    val completed by vm.training.completed.collectAsState()
    // L'écran de séance est retiré de la pile : un retour depuis le résumé ne retombe jamais sur une séance vide (écran blanc
    // quand la séance avait été ouverte depuis l'accueil, au relancement de l'app ou par un widget, hors de l'onglet Activités).
    LaunchedEffect(completed) {
        if (completed == null || nav.currentBackStackEntry?.destination?.route == "complete") return@LaunchedEffect
        val stack = nav.currentBackStack.value.map { it.destination.route }
        nav.navigate("complete") {
            if ("activities" in stack) popUpTo("activities") else if ("session" in stack) popUpTo("session") { inclusive = true }
            launchSingleTop = true
        }
    }
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
        if (r == "home") homePager.requestScrollToPage(1)
        runCatching {
            when {
                r == "mood" -> app.superhealth.ui.wellbeing.moodSheet = true
                r.startsWith("training:") -> { nav.popBackStack(nav.graph.findStartDestination().id, false); handleTrainingRoute(vm.training, nav, r.removePrefix("training:")) }
                isTabRoute(r) -> nav.openTab(r, fresh = true)
                r == current -> Unit
                else -> nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            }
        }
    }

    // la barre flotte au-dessus du contenu, qui défile dessous jusqu'au bas de l'écran ; rien autour d'elle
    var barPx by remember { mutableStateOf(0) }
    val barInset = with(androidx.compose.ui.platform.LocalDensity.current) { barPx.toDp() }
    androidx.compose.runtime.CompositionLocalProvider(LocalTabBarInset provides barInset) { Box(Modifier.fillMaxSize().background(Mf.colors.background)) {
        NavHost(
            nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
            // Axe partagé horizontal de Material : glissement court et fondu ; les onglets changent sans transition (le fondu croisé accrochait pendant la construction de l'écran).
            enterTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) androidx.compose.animation.EnterTransition.None else slideInHorizontally(push) { it / 5 } + fadeIn(tween(220, 60)) },
            exitTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) androidx.compose.animation.ExitTransition.None else slideOutHorizontally(push) { -it / 5 } + fadeOut(tween(120)) },
            popEnterTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) androidx.compose.animation.EnterTransition.None else slideInHorizontally(push) { -it / 5 } + fadeIn(tween(220, 60)) },
            popExitTransition = { if (isTabRoute(targetState.destination.route) && isTabRoute(initialState.destination.route)) androidx.compose.animation.ExitTransition.None else slideOutHorizontally(push) { it / 5 } + fadeOut(tween(120)) },
        ) {
            composable("home") { app.superhealth.ui.league.HomeWithFeed(vm, nav, homePager) }
            composable("feed") { app.superhealth.ui.league.SocialFeed(nav, active = true) }
            composable("social/compose") { app.superhealth.ui.league.SocialComposer(nav) }
            composable("social/share/workout") { app.superhealth.ui.league.SocialComposer(nav, "WORKOUT") }
            composable("group/post/{author}/{post}?reply={reply}", listOf(navArgument("author") { type = NavType.StringType }, navArgument("post") { type = NavType.StringType }, navArgument("reply") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                app.superhealth.ui.league.GroupPostScreen(nav, entry.arguments?.getString("author").orEmpty(),
                    entry.arguments?.getString("post").orEmpty(), entry.arguments?.getString("reply"))
            }
            composable("social/preview") { app.superhealth.ui.league.SharedProfilePreview(nav) }
            composable("group/circles?circle={circle}&post={post}",arguments=listOf(androidx.navigation.navArgument("circle"){nullable=true;defaultValue=null},androidx.navigation.navArgument("post"){nullable=true;defaultValue=null})) { entry -> app.superhealth.ui.league.PrivateCirclesScreen(nav,entry.arguments?.getString("circle"),entry.arguments?.getString("post")) }
            composable("group/lifecycle") { app.superhealth.ui.league.SocialLifecycleScreen(nav) }
            composable("social/outbox") { app.superhealth.ui.league.SocialOutboxScreen(nav) }
            composable("group/preferences") { app.superhealth.ui.league.SocialPreferencesScreen(nav) }
            composable("group/recovery") { app.superhealth.ui.league.GroupRecoveryScreen(nav) }
            composable("group/records") { app.superhealth.ui.league.GroupRecordsScreen(nav) }
            composable("social/share/pr") { app.superhealth.ui.league.SocialComposer(nav, "PR") }
            composable("social/share/activity") { app.superhealth.ui.league.SocialComposer(nav, "ACTIVITY") }
            composable("log") { FoodLogScreen(vm.nutrition, nav) }
            composable("activities") { app.superhealth.ui.activities.ActivitiesTab(vm, nav) }
            composable("widgets") { app.superhealth.ui.settings.HomeWidgetsScreen(nav) }
            composable("settings/find") { app.superhealth.ui.settings.FindSettingScreen(nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("settings/notifications") { app.superhealth.ui.settings.NotificationSettingsScreen(nav) }
            composable("settings/feedback") { app.superhealth.ui.settings.FeedbackScreen(nav) }
            composable("dash/edit") { DashboardEditScreen(vm, nav) }
            composable("backup") { app.superhealth.ui.settings.BackupScreen(vm, nav) }
            composable("update") { app.superhealth.ui.settings.UpdateScreen(vm, nav) }
            composable("settings/look") { app.superhealth.ui.settings.LookSettingsScreen(vm, nav) }
            composable("settings/style") { app.superhealth.ui.settings.StyleLabScreen(vm, nav) }
            composable("settings/units") { app.superhealth.ui.settings.UnitsSettingsScreen(vm, nav) }
            composable("settings/hc") { app.superhealth.ui.settings.HealthConnectSettingsScreen(vm, nav) }
            composable("settings/activity-import") { app.superhealth.ui.health.ActivityImportScreen(vm, nav) }
            composable("settings/security") { app.superhealth.ui.settings.SecurityScreen(nav) }
            composable("modules") { app.superhealth.ui.settings.ModulesScreen(vm, nav) }
            composable("coach/settings") { app.superhealth.ui.coach.CoachSettingsScreen(nav) }
            composable("coach") { app.superhealth.ui.coach.CoachScreen(vm, nav) }
            composable("meds") { app.superhealth.ui.meds.MedsScreen(nav) }
            composable("stretch/edit") { app.superhealth.ui.stretch.StretchEditorScreen(nav) }
            composable("stretch/custom") { app.superhealth.ui.stretch.CustomStretchScreen(nav) }
            composable("stretch/play") { app.superhealth.ui.stretch.StretchPlayScreen(nav) }
            composable("stretch/all") { app.superhealth.ui.stretch.StretchAllScreen(nav) }
            composable("stretch/test") { app.superhealth.ui.stretch.MobilityTestScreen(vm, nav) }
            composable("settings/activities") { app.superhealth.ui.activities.ActivitySettingsScreen(nav) }
            composable("social") { app.superhealth.ui.league.SocialProfileScreen(nav) }
            composable("social/badges?category={category}", listOf(navArgument("category") { type = NavType.StringType; defaultValue = "xp" })) {
                app.superhealth.ui.league.ProfileBadgesScreen(nav,it.arguments?.getString("category") ?: "xp")
            }
            composable("quests") { app.superhealth.ui.league.WeeklyQuestsScreen(nav) }
            composable("progress") { app.superhealth.ui.league.ProgressScreen(nav) }
            composable("league/settings") { app.superhealth.ui.league.LeagueSettingsScreen(nav) }
            composable("group/sharing") { app.superhealth.ui.league.GroupSharingScreen(nav) }
            composable("group/score") { app.superhealth.ui.league.GroupScoreScreen(nav) }
            composable("group/sync") { app.superhealth.ui.league.GroupSyncScreen(nav) }
            composable("friends/add") { app.superhealth.ui.league.AddFriendScreen(nav) }
            composable("friends") { app.superhealth.ui.league.FriendsScreen(nav) }
            composable("stretch/ex/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                app.superhealth.ui.stretch.StretchExerciseScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable("hiking/route/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                app.superhealth.ui.hiking.HikeDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable("hiking/live") { app.superhealth.ui.hiking.HikeLiveScreen(nav) }
            composable("meditation/play") { app.superhealth.ui.meditation.MeditationPlayScreen(nav) }
            composable("league") { app.superhealth.ui.league.LeagueScreen(nav) }
            composable("journal") { app.superhealth.ui.wellbeing.JournalScreen(vm, nav) }
            composable("form") { app.superhealth.ui.wellbeing.FormScreen(vm, nav) }
            composable("cycle") { app.superhealth.ui.wellbeing.CycleScreen(vm, nav) }
            composable("breathe") { app.superhealth.ui.wellbeing.BreatheScreen(nav) }
            composable("feel") { app.superhealth.ui.wellbeing.CheckinScreen(nav) }
            composable("settings/reminders") { app.superhealth.ui.wellbeing.RemindersScreen(vm, nav) }
            composable("settings/sleep") { app.superhealth.ui.settings.SleepSettingsScreen(nav) }
            composable("vitals/edit") { app.superhealth.ui.dashboard.VitalsEditScreen(vm, nav) }
            composable("rings/edit") { app.superhealth.ui.dashboard.RingsEditScreen(vm, nav) }
            composable("plus/edit") { app.superhealth.ui.dashboard.PlusEditScreen(vm, nav) }
            composable("setup/nutrition") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.NUTRITION_SETUP) { nav.popBackStack() } }
            composable("setup/training") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.TRAINING_SETUP) { nav.popBackStack() } }
            composable("sim/nutrition") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.NUTRITION_SIM) { nav.popBackStack() } }
            composable("sim/training") { OnboardingScreen(vm, app.superhealth.ui.onboarding.OnboardingMode.TRAINING_SIM) { nav.popBackStack() } }
            composable("h/metric/{metric}", listOf(navArgument("metric") { type = NavType.StringType })) {
                val m = runCatching { Metric.valueOf(it.arguments!!.getString("metric")!!) }.getOrNull() ?: Metric.STEPS
                // 2.1 : le sommeil a son écran (nuit en détail, semaine, mois, année)
                if (m == Metric.SLEEP) app.superhealth.ui.health.SleepScreen(vm, nav) else MetricScreen(vm, nav, m)
            }
            composable("h/vitals") { app.superhealth.ui.health.VitalsScreen(vm, nav) }
            composable("h/devices") { DevicesScreen(vm, nav) }
            composable("h/activities?kind={kind}", listOf(navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                ActivitiesHistoryScreen(vm, nav, it.arguments?.getString("kind"))
            }
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
        if (isTab) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barPx = it.height }) {
            if (tst.session != null) ActiveSessionBar(tst.session!!.name, tst.session!!.startTime) { nav.navigate("session") }
            BottomBar(current, onTab = { route ->
                if (route == "home") homePager.requestScrollToPage(1)
                nav.openTab(route)
            }, onPlus = {
                if (groupContext) { plusAction = "social/compose"; return@BottomBar }
                // Un seul raccourci choisi : le « + » l'ouvre directement.
                val single = sh.plusActions.mapNotNull { it.resolve(vm) }.singleOrNull()
                if (single != null) plusAction = single.action else plus = true
            })
        }
    } }

    fun runAction(action: String) {
        when (action) {
            "water" -> {
                vm.log(Metric.HYDRATION, 250.0)
                android.widget.Toast.makeText(nav.context, tr("250 ml d'eau ajoutés"), android.widget.Toast.LENGTH_SHORT).show()
            }
            "log_health" -> logSheet = true
            "mood" -> app.superhealth.ui.wellbeing.moodSheet = true
            "sync" -> vm.syncNow(nav.context)
            "next" -> vm.training.activeProgram()?.let { p -> vm.training.startProgramDay(p, p.dayIndex, p.cycleIndex); nav.navigate("session") }
            "empty" -> { vm.training.startEmpty(); nav.navigate("session") }
            "open:MEDITATION", "open:STRETCH" -> {
                app.superhealth.SuperPrefs.update { it.copy(activityCurrent = action.removePrefix("open:")) }
                nav.openTab("activities", fresh = true)
            }
            else -> nav.go(action)
        }
    }
    LaunchedEffect(plusAction) { plusAction?.let { plusAction = null; runAction(it) } }
    if (plus) PlusSheet(vm, current, onDismiss = { plus = false }, onEdit = { plus = false; nav.go("plus/edit") }) { action ->
        plus = false
        runAction(action)
    }
    if (logSheet) LogSheet(vm, onDismiss = { logSheet = false })
    if (app.superhealth.ui.wellbeing.moodSheet) app.superhealth.ui.wellbeing.MoodSheet(nav)
    app.superhealth.ui.league.LeagueIncomingDialog()
}

/** Courbe « emphasized decelerate » de Material. */
private val push = tween<IntOffset>(320, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

/** Place prise par la barre flottante en bas de l'écran : à laisser au bas des listes des onglets. */
val LocalTabBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * Barre de navigation : une pilule flottante bordée d'un filet, les onglets à parts égales (symbole et nom, l'actif sur une
 * pastille d'encre) et, à côté, le « + » rond sous le pouce (ou au milieu de la barre).
 */
@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onPlus: () -> Unit) {
    val sh = app.superhealth.SuperPrefs.settings.collectAsState().value
    val installed = app.superhealth.modules.Modules.installed.collectAsState().value
    val shown = remember(sh, installed) { visibleTabs(sh).let { if (settingsIsTab(sh)) it + settingsTab else it } }
    val dark = Mf.colors.dark
    val shape = RoundedCornerShape(26.dp)
    val pill = Modifier.height(68.dp)
        .shadow(if (dark) 0.dp else 12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.10f))
        .clip(shape).background(if (dark) Mf.colors.elevated else Mf.colors.card).border(1.dp, Mf.colors.edge, shape).padding(horizontal = 4.dp)
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        when (sh.plusSide) {
            // au milieu : le « + » dans la barre, les onglets répartis de part et d'autre
            "CENTER" -> Row(pill.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val left = shown.take(shown.size / 2)
                Row(Modifier.weight(1f).fillMaxHeight()) { left.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } } }
                PlusButton(56.dp, onPlus)
                Row(Modifier.weight(1f).fillMaxHeight()) { (shown - left.toSet()).forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } } }
            }
            else -> {
                val leftHanded = sh.plusSide == "LEFT"
                // moins d'onglets (parties coupées) : barre plus courte, gardée près du « + » sous le pouce
                val short = shown.size < tabs.size
                if (leftHanded) { PlusButton(64.dp, onPlus); Spacer(Modifier.width(10.dp)) } else if (short) Spacer(Modifier.weight(1f))
                Row((if (short) Modifier.width((84 * shown.size + 8).dp) else Modifier.weight(1f)).then(pill)) {
                    shown.forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { onTab(it.route) } }
                }
                if (!leftHanded) { Spacer(Modifier.width(10.dp)); PlusButton(64.dp, onPlus) } else if (short) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlusButton(size: androidx.compose.ui.unit.Dp, onPlus: () -> Unit) {
    val r = RoundedCornerShape(size * 0.31f)
    Box(
        Modifier.size(size).shadow(10.dp, r, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(r).background(app.superhealth.ui.theme.actionFill(Mf.colors.strong, app.superhealth.ui.theme.partnerOf(Mf.colors.strong)))
            .clickable(onClick = onPlus),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, tr("Ajouter"), tint = app.superhealth.ui.theme.actionInk(), modifier = Modifier.size(size * 0.47f))
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // onglet actif : pastille teintée de l'accent derrière le symbole, nom en gras
    val accent = Mf.colors.accent
    val pill by animateDpAsState(if (selected) 56.dp else 24.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "pill")
    Column(
        modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onClick() }),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(pill).height(32.dp).clip(CircleShape).background(if (selected) accent.container(1.4f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(tab.icon, tr(tab.label), tint = if (selected) accent.onContainer() else Mf.colors.textSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(tr(tab.label), fontSize = 12.sp, color = if (selected) Mf.colors.text else Mf.colors.textSecondary,
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
        PlusAction.MOOD -> QuickAction("Noter mon moral", Icons.Rounded.SentimentSatisfied, "mood")
        PlusAction.PERIOD -> QuickAction("Règles", Icons.Rounded.CalendarMonth, "cycle")
        PlusAction.BREATHE -> QuickAction("Respirer", Icons.Rounded.Air, "breathe")
        PlusAction.MEDITATE -> if (app.superhealth.meditation.Meditation.available()) QuickAction("Méditer", Icons.Rounded.SelfImprovement, "open:MEDITATION") else null
        PlusAction.STRETCH -> if (app.superhealth.stretch.Stretch.available()) QuickAction("S'étirer", Icons.Rounded.AccessibilityNew, "open:STRETCH") else null
        PlusAction.LEAGUE -> if (app.superhealth.league.League.available()) QuickAction("Classement", Icons.Rounded.EmojiEvents, "league") else null
        PlusAction.SYNC -> if (sh.watchModule && vm.devices.value.isNotEmpty() || sh.healthConnect) QuickAction("Synchroniser", Icons.Rounded.Sync, "sync") else null
    }
}

/** « + » : les raccourcis choisis (Réglages du « + »), regroupés par module, dans l'ordre choisi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlusSheet(vm: SuperViewModel, current: String?, onDismiss: () -> Unit, onEdit: () -> Unit, onAction: (String) -> Unit) {
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
            val contextual = when (current) {
                "log" -> listOf(PlusAction.SEARCH, PlusAction.SCAN, PlusAction.QUICK).filter { it in chosen }
                "activities" -> listOf(PlusAction.WORKOUT, PlusAction.EMPTY, PlusAction.EXERCISES).filter { it in chosen }
                else -> emptyList()
            }.mapNotNull { it.resolve(vm) }
            if (contextual.isNotEmpty()) ActionGroup(tr("Pour cet écran"), Mf.colors.accent, contextual, onAction)
            app.superhealth.DashCard.Section.entries.forEach { section ->
                val actions = chosen.filter { it.section == section }.mapNotNull { it.resolve(vm) }.filterNot { a -> contextual.any { it.action == a.action } }
                if (actions.isNotEmpty()) ActionGroup(tr(when (section) {
                    app.superhealth.DashCard.Section.NUTRITION -> "Nutrition"
                    app.superhealth.DashCard.Section.TRAINING -> "Entraînement"
                    app.superhealth.DashCard.Section.HEALTH -> "Santé"
                    app.superhealth.DashCard.Section.SOCIAL -> "Groupe"
                }), when (section) {
                    app.superhealth.DashCard.Section.NUTRITION -> app.superhealth.ui.theme.Base.Green
                    app.superhealth.DashCard.Section.TRAINING -> app.superhealth.ui.theme.WorkoutBlue
                    app.superhealth.DashCard.Section.HEALTH -> app.superhealth.ui.theme.HealthHues.Heart
                    app.superhealth.DashCard.Section.SOCIAL -> app.superhealth.ui.theme.HealthHues.Fitness
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
        // rangée à la hauteur de la tuile la plus haute : un libellé sur deux lignes n'est plus coupé
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { a -> ActionTile(a, hue, Modifier.weight(1f).fillMaxHeight()) { onAction(a.action) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Raccourci : symbole à la couleur du module, libellé dessous. */
@Composable
private fun ActionTile(a: QuickAction, hue: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 108.dp).clip(RoundedCornerShape(20.dp)).background(Mf.colors.card).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
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
fun openTabFrom(nav: NavHostController, route: String) = nav.openTab(route)

fun NavHostController.go(route: String) = runCatching { if (isTabRoute(route)) openTab(route) else navigate(route) }

/**
 * Onglet de la barre du bas : un seul exemplaire de chaque onglet dans la pile. [fresh] (widget, notification) : l'onglet
 * s'ouvre à sa racine au lieu de retrouver l'écran où on l'avait laissé.
 */
fun NavHostController.openTab(route: String, fresh: Boolean = false) {
    // Ne pas dépiler/recréer l'écran visible quand on retouche son onglet : défilement et états sont conservés.
    // Un raccourci explicitement fresh garde son comportement de retour à la racine.
    if (!fresh && currentBackStackEntry?.destination?.route == route) return
    val start = graph.findStartDestination()
    navigate(route) {
        popUpTo(start.id) { saveState = !fresh }
        launchSingleTop = true
        // L'accueil, racine jamais dépilée, n'a rien à restaurer : Navigation range sous son id la pile de l'onglet quitté
        // (popUpTo non inclusif), et après un widget « Accueil » restaurait le Journal au lieu du tableau de bord.
        restoreState = !fresh && route != start.route
    }
}
