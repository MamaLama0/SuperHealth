package app.superhealth.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import app.superhealth.ui.theme.Mf
import androidx.navigation.compose.composable as navComposable

/**
 * Destination de navigation posée sur le fond de l'app : pendant la transition les deux écrans glissent
 * ensemble, et celui du dessous ne doit pas transparaître à travers un écran sans fond.
 */
fun NavGraphBuilder.composable(
    route: String, arguments: List<NamedNavArgument> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = enterTransition,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = exitTransition,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = navComposable(route, arguments, enterTransition = enterTransition, exitTransition = exitTransition,
    popEnterTransition = popEnterTransition, popExitTransition = popExitTransition) { entry ->
    val scope = this
    Box(Modifier.fillMaxSize().background(Mf.colors.background)) { scope.content(entry) }
}
