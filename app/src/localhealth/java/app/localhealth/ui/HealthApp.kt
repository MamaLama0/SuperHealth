package app.localhealth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localhealth.HealthViewModel
import app.localhealth.data.Category
import app.localhealth.data.Metric
import app.localhealth.ui.components.Avatar
import app.localhealth.ui.components.GsIcon
import app.localhealth.ui.screens.CategoryScreen
import app.localhealth.ui.screens.ConnectionsScreen
import app.localhealth.ui.screens.HealthTab
import app.localhealth.ui.screens.HistoryScreen
import app.localhealth.ui.screens.MetricDetailScreen
import app.localhealth.ui.screens.ProfileScreen
import app.localhealth.ui.screens.StartScreen
import app.localhealth.ui.screens.TodayTab
import app.localhealth.ui.screens.userInitial
import androidx.compose.material3.IconButton
import nodomain.freeyourgadget.gadgetbridge.R

/** Screens above the two home tabs, encoded as strings so the back stack survives rotation. */
sealed interface Route {
    data object Connections : Route
    data object Profile : Route
    data object Start : Route
    data object History : Route
    data class Detail(val metric: Metric) : Route
    data class CategoryPage(val category: Category) : Route

    fun encode(): String = when (this) {
        Connections -> "connections"
        Profile -> "profile"
        Start -> "start"
        History -> "history"
        is Detail -> "detail:" + metric.name
        is CategoryPage -> "category:" + category.name
    }

    companion object {
        fun decode(s: String): Route? = when {
            s == "connections" -> Connections
            s == "profile" -> Profile
            s == "start" -> Start
            s == "history" -> History
            s.startsWith("detail:") -> runCatching { Detail(Metric.valueOf(s.substringAfter(':'))) }.getOrNull()
            s.startsWith("category:") -> runCatching { CategoryPage(Category.valueOf(s.substringAfter(':'))) }.getOrNull()
            else -> null
        }
    }
}

class Navigator(private val get: () -> List<String>, private val set: (List<String>) -> Unit) {
    fun push(r: Route) = set(get() + r.encode())
    fun pop() = set(get().dropLast(1))
}

@Composable
fun HealthApp(vm: HealthViewModel) {
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val nav = Navigator({ stack }, { stack = it })
    val current = stack.lastOrNull()?.let(Route::decode)

    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val forward = targetState != null && (initialState == null || stack.size > 1)
            if (forward) {
                (slideInHorizontally(tween(250)) { it / 6 } + fadeIn(tween(250))) togetherWith fadeOut(tween(150))
            } else {
                fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(250)) { it / 6 } + fadeOut(tween(200)))
            }
        },
        label = "nav",
    ) { route ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            when (route) {
                null -> Home(vm, nav, tab) { tab = it }
                Route.Connections -> ConnectionsScreen(vm, nav)
                Route.Profile -> ProfileScreen(vm, nav)
                Route.Start -> StartScreen(vm, nav)
                Route.History -> HistoryScreen(vm, nav)
                is Route.Detail -> MetricDetailScreen(vm, nav, route.metric)
                is Route.CategoryPage -> CategoryScreen(vm, nav, route.category)
            }
        }
    }
}

@Composable
private fun Home(vm: HealthViewModel, nav: Navigator, tab: Int, onTab: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = cs.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(Modifier.fillMaxWidth().background(cs.surfaceContainer).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = { nav.push(Route.Connections) }, modifier = Modifier.align(Alignment.CenterStart)) {
                    GsIcon(R.drawable.gs_devices_wearables_vd_theme_24, cs.onSurfaceVariant,
                        description = stringResource(R.string.lh_devices_and_apps))
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    color = cs.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    Avatar(userInitial(), onClick = { nav.push(Route.Profile) })
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surfaceContainer, tonalElevation = 0.dp) {
                val items = listOf(
                    Triple(R.string.lh_nav_today, R.drawable.gs_fitbit_today_vd_theme_24, R.drawable.gs_fitbit_today_vd_theme_24),
                    Triple(R.string.lh_nav_health, R.drawable.gs_heart_check_vd_theme_24, R.drawable.gs_heart_check_fill1_vd_theme_24),
                )
                items.forEachIndexed { i, (label, icon, selectedIcon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(if (tab == i) selectedIcon else icon), null) },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = cs.secondaryContainer,
                            selectedIconColor = cs.onSecondaryContainer,
                            selectedTextColor = cs.secondary,
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (tab) {
                0 -> TodayTab(vm, nav, refresh)
                else -> HealthTab(vm, nav, refresh)
            }
        }
    }
}
