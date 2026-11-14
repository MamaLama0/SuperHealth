package app.superhealth.ui.league

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.ui.LocalTabBarInset
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.DashboardScreen

/**
 * Accueil 4.1 : le fil du Groupe à gauche du tableau de bord (glisser vers la droite depuis l'accueil). Les cartes
 * s'ouvrent depuis « Mes cartes » et la publication depuis « Publier », dans la barre du bas.
 */
@Composable
internal fun HomeWithFeed(vm: SuperViewModel, nav: NavHostController, pager: PagerState, onDashboard: () -> Unit) {
    val settings by SuperPrefs.settings.collectAsState()
    if (settings.socialFeedEnabled && settings.leagueEnabled) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
            if (page == 0) SocialFeed(nav, onDashboard)
            else DashboardScreen(vm, nav)
        }
    } else DashboardScreen(vm, nav)
}

/** Le panneau social : le fil d'activités, et rien d'autre. */
@Composable
internal fun SocialFeed(nav: NavHostController, onDashboard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = LocalTabBarInset.current)) {
        FriendsFeedScreen(nav, embedded = true)
    }
}
